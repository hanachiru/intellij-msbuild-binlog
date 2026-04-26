using System.Text.Json.Serialization;

namespace BinlogJsonExporter;

[JsonSourceGenerationOptions(
    PropertyNamingPolicy = JsonKnownNamingPolicy.CamelCase,
    DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull)]
[JsonSerializable(typeof(BinlogDocument))]
[JsonSerializable(typeof(BinlogNode))]
[JsonSerializable(typeof(BinlogNode[]))]
[JsonSerializable(typeof(Dictionary<string, string>))]
internal sealed partial class BinlogJsonSerializerContext : JsonSerializerContext;