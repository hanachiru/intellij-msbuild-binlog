using System.Reflection;
using Microsoft.Build.Logging.StructuredLogger;
using Task = Microsoft.Build.Logging.StructuredLogger.Task;

namespace BinlogJsonExporter;

/// <summary>
/// Represents the serialized binlog document returned to the plugin.
/// </summary>
internal sealed record BinlogDocument(
    string FilePath,
    Dictionary<string, string> Summary,
    BinlogNode Root);

/// <summary>
/// Represents a serialized node in the exported build tree.
/// </summary>
internal sealed record BinlogNode(
    string TypeName,
    string Title,
    string DisplayText,
    string? StartTime,
    string? EndTime,
    string? DurationText,
    string? FullText,
    string? ToolTip,
    Dictionary<string, string> Details,
    BinlogNode[] Children);

/// <summary>
/// Stores a reflected property together with the display name used in the UI.
/// </summary>
internal readonly record struct PropertyGetter(string DisplayName, PropertyInfo Property);

/// <summary>
/// Counts node categories while the tree is being exported.
/// </summary>
internal struct SummaryCounter
{
    public int Projects;
    public int Evaluations;
    public int Targets;
    public int Tasks;
    public int Warnings;
    public int Errors;

    /// <summary>
    /// Updates the counters for a single structured logger node.
    /// </summary>
    public void CountNode(BaseNode node)
    {
        switch (node)
        {
            case Project:
                Projects++;
                break;
            case ProjectEvaluation:
                Evaluations++;
                break;
            case Target:
                Targets++;
                break;
            case Task:
                Tasks++;
                break;
            case Warning:
                Warnings++;
                break;
            case Error:
                Errors++;
                break;
        }
    }
}