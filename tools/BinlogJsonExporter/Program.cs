using System.Text.Json;
using BinlogJsonExporter;
using Microsoft.Build.Logging.StructuredLogger;

if (args.Length != 1)
{
    Console.Error.WriteLine("Usage: BinlogJsonExporter <path-to-binlog>");
    return 1;
}

var filePath = Path.GetFullPath(args[0]);
if (!File.Exists(filePath))
{
    Console.Error.WriteLine($"File not found: {filePath}");
    return 1;
}

try
{
    var build = Serialization.Read(filePath);
    build.LogFilePath = filePath;
    BuildAnalyzer.AnalyzeBuild(build);

    var document = BinlogExporter.Export(filePath, build);

    using var stdout = Console.OpenStandardOutput();
    using var writer = new Utf8JsonWriter(stdout);
    JsonSerializer.Serialize(writer, document, BinlogJsonSerializerContext.Default.BinlogDocument);
    writer.Flush();
    return 0;
}
catch (Exception error)
{
    Console.Error.WriteLine(error);
    return 2;
}