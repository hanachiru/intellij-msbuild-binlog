using System.Globalization;
using System.Reflection;
using Microsoft.Build.Logging.StructuredLogger;

namespace BinlogJsonExporter;

/// <summary>
/// Converts a structured MSBuild build tree into the JSON document consumed by the Rider plugin.
/// </summary>
internal static class BinlogExporter
{
    /// <summary>
    /// Exports the parsed build into a serializable binlog document.
    /// </summary>
    internal static BinlogDocument Export(string filePath, Build build)
    {
        var summaryCounter = new SummaryCounter();
        var rootNode = ExportNode(build, ref summaryCounter);

        return new BinlogDocument(
            filePath,
            CreateSummary(build, summaryCounter),
            rootNode);
    }

    /// <summary>
    /// Creates the top-level build summary shown by the viewer.
    /// </summary>
    private static Dictionary<string, string> CreateSummary(Build build, SummaryCounter summaryCounter)
    {
        return new Dictionary<string, string>(8, StringComparer.Ordinal)
        {
            ["Outcome"] = build.Succeeded ? "Succeeded" : "Failed",
            ["Duration"] = NullIfEmpty(build.DurationText) ?? "0",
            ["Start"] = FormatDateTime(build.StartTime),
            ["End"] = FormatDateTime(build.EndTime),
            ["Projects"] = summaryCounter.Projects.ToString(CultureInfo.InvariantCulture),
            ["Evaluations"] = summaryCounter.Evaluations.ToString(CultureInfo.InvariantCulture),
            ["Targets"] = summaryCounter.Targets.ToString(CultureInfo.InvariantCulture),
            ["Tasks"] = summaryCounter.Tasks.ToString(CultureInfo.InvariantCulture),
            ["Warnings"] = summaryCounter.Warnings.ToString(CultureInfo.InvariantCulture),
            ["Errors"] = summaryCounter.Errors.ToString(CultureInfo.InvariantCulture)
        };
    }

    /// <summary>
    /// Recursively exports a structured logger node and all of its children.
    /// </summary>
    private static BinlogNode ExportNode(BaseNode node, ref SummaryCounter summaryCounter)
    {
        summaryCounter.CountNode(node);

        var timedNode = node as TimedNode;
        var typeName = node.TypeName;
        var title = node.Title ?? typeName;
        var startTime = timedNode is null ? null : FormatDateTimeOrNull(timedNode.StartTime);
        var endTime = timedNode is null ? null : FormatDateTimeOrNull(timedNode.EndTime);
        var durationText = timedNode is null ? null : NullIfEmpty(timedNode.DurationText);
        var details = CreateDetails(node, timedNode, title, startTime, endTime, durationText);
        var displayText = BuildDisplayText(node, timedNode);
        var fullText = NormalizeText(node.GetFullText());
        if (string.Equals(fullText, displayText, StringComparison.Ordinal)) fullText = null;

        var toolTip = node is TreeNode treeNode
            ? NormalizeText(treeNode.ToolTip)
            : null;

        if (string.Equals(toolTip, fullText, StringComparison.Ordinal) ||
            string.Equals(toolTip, displayText, StringComparison.Ordinal)) toolTip = null;

        return new BinlogNode(
            typeName,
            title,
            displayText,
            startTime,
            endTime,
            durationText,
            fullText,
            toolTip,
            details,
            ExportChildren(node, ref summaryCounter));
    }

    /// <summary>
    /// Collects the compact property set displayed for a single node.
    /// </summary>
    private static Dictionary<string, string> CreateDetails(
        BaseNode node,
        TimedNode? timedNode,
        string title,
        string? startTime,
        string? endTime,
        string? durationText)
    {
        var optionalProperties = GetOptionalProperties(node.GetType());
        var capacity = 4 + optionalProperties.Length + (timedNode is null ? 0 : 6);
        var details = new Dictionary<string, string>(capacity, StringComparer.OrdinalIgnoreCase);

        AddString(details, "Type", node.TypeName);
        AddString(details, "Title", title);

        switch (node)
        {
            case Build build:
                AddBoolean(details, "Succeeded", build.Succeeded);
                AddString(details, "LogFile", build.LogFilePath);
                AddBoxed(details, "FileFormatVersion", build.FileFormatVersion);
                break;

            case NameValueNode nameValue:
                AddString(details, "Name", nameValue.Name);
                AddString(details, "Value", nameValue.Value);
                break;

            case TextNode textNode:
                AddString(details, "Text", textNode.Text);
                break;
        }

        if (timedNode is not null)
        {
            AddBoxed(details, "Id", timedNode.Id);
            AddBoxed(details, "NodeId", timedNode.NodeId);
            AddBoxed(details, "Index", timedNode.Index);
            AddString(details, "Start", startTime);
            AddString(details, "End", endTime);
            AddString(details, "Duration", durationText);
        }

        foreach (var property in optionalProperties)
            AddBoxed(details, property.DisplayName, property.Property.GetValue(node));

        if (node is Project project) AddString(details, "ProjectName", project.Name);

        return details;
    }

    /// <summary>
    /// Exports the direct children of a node into a contiguous array.
    /// </summary>
    private static BinlogNode[] ExportChildren(BaseNode node, ref SummaryCounter summaryCounter)
    {
        if (node is not TreeNode { HasChildren: true } treeNode) return Array.Empty<BinlogNode>();

        var childCount = treeNode.Children.Count;
        if (childCount == 0) return Array.Empty<BinlogNode>();

        var children = new BinlogNode[childCount];
        var index = 0;

        foreach (var child in treeNode.Children) children[index++] = ExportNode(child, ref summaryCounter);

        return children;
    }

    /// <summary>
    /// Builds the text shown in the tree for a single node.
    /// </summary>
    private static string BuildDisplayText(BaseNode node, TimedNode? timedNode)
    {
        var baseText = NormalizeText(node.ToString()) ?? node.TypeName;

        if (!string.IsNullOrWhiteSpace(timedNode?.DurationText))
            return string.Concat(baseText, " [", timedNode.DurationText, "]");

        return baseText;
    }

    /// <summary>
    /// Resolves optional reflected properties that may be available for a node type.
    /// </summary>
    private static PropertyGetter[] GetOptionalProperties(Type nodeType)
    {
        return ExportMetadata.OptionalPropertyCache.GetOrAdd(nodeType, static type =>
        {
            var properties = new List<PropertyGetter>(ExportMetadata.OptionalPropertyDefinitions.Length);

            foreach (var (propertyName, displayName) in ExportMetadata.OptionalPropertyDefinitions)
            {
                var property = type.GetProperty(propertyName, BindingFlags.Public | BindingFlags.Instance);
                if (property is null || property.GetIndexParameters().Length != 0) continue;

                properties.Add(new PropertyGetter(displayName, property));
            }

            return properties.Count == 0 ? Array.Empty<PropertyGetter>() : properties.ToArray();
        });
    }

    /// <summary>
    /// Adds a non-empty string value to the details dictionary.
    /// </summary>
    private static void AddString(Dictionary<string, string> target, string key, string? value)
    {
        var text = NullIfEmpty(value);
        if (text is not null) target[key] = text;
    }

    /// <summary>
    /// Adds a boolean value to the details dictionary.
    /// </summary>
    private static void AddBoolean(Dictionary<string, string> target, string key, bool value)
    {
        target[key] = value ? "true" : "false";
    }

    /// <summary>
    /// Formats and adds an arbitrary boxed value to the details dictionary.
    /// </summary>
    private static void AddBoxed(Dictionary<string, string> target, string key, object? value)
    {
        switch (value)
        {
            case null:
                return;
            case string text:
                AddString(target, key, text);
                return;
            case bool boolean:
                AddBoolean(target, key, boolean);
                return;
            case DateTime dateTime:
                AddString(target, key, FormatDateTimeOrNull(dateTime));
                return;
            case IFormattable formattable:
                AddString(target, key, formattable.ToString(null, CultureInfo.InvariantCulture));
                return;
            default:
                AddString(target, key, value.ToString());
                return;
        }
    }

    /// <summary>
    /// Formats a timestamp using the round-trip date format.
    /// </summary>
    private static string FormatDateTime(DateTime value)
    {
        if (value == default) return string.Empty;

        return value.ToString("O");
    }

    /// <summary>
    /// Formats a timestamp or returns <see langword="null" /> when the value is not set.
    /// </summary>
    private static string? FormatDateTimeOrNull(DateTime value)
    {
        return value == default ? null : value.ToString("O");
    }

    /// <summary>
    /// Trims and normalizes line endings in text captured from the structured logger model.
    /// </summary>
    private static string? NormalizeText(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return null;

        var span = value.AsSpan().Trim();
        if (span.IsEmpty) return null;

        if (span.IndexOf('\r') < 0) return span.Length == value.Length ? value : span.ToString();

        return span.ToString().Replace("\r\n", "\n");
    }

    /// <summary>
    /// Returns <see langword="null" /> when a string is empty or whitespace.
    /// </summary>
    private static string? NullIfEmpty(string? value)
    {
        return string.IsNullOrWhiteSpace(value) ? null : value;
    }
}