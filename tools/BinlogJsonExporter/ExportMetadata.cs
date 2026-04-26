using System.Collections.Concurrent;

namespace BinlogJsonExporter;

/// <summary>
/// Holds shared reflection metadata used while exporting optional node properties.
/// </summary>
internal static class ExportMetadata
{
    internal static readonly ConcurrentDictionary<Type, PropertyGetter[]> OptionalPropertyCache = new();

    internal static readonly (string PropertyName, string DisplayName)[] OptionalPropertyDefinitions =
    [
        new("ProjectFile", "ProjectFile"),
        new("SourceFilePath", "SourceFile"),
        new("LineNumber", "Line"),
        new("ColumnNumber", "Column"),
        new("Code", "Code"),
        new("FromAssembly", "FromAssembly"),
        new("CommandLineArguments", "CommandLineArguments"),
        new("ParentTarget", "ParentTarget"),
        new("DependsOnTargets", "DependsOnTargets"),
        new("Skipped", "Skipped"),
        new("Succeeded", "Succeeded")
    ];
}