package io.github.hanachiru.binlog.editor

data class BinlogDocumentDto(
    val filePath: String,
    val summary: Map<String, String> = emptyMap(),
    val root: BinlogNodeDto,
)

data class BinlogNodeDto(
    val typeName: String,
    val title: String,
    val displayText: String,
    val startTime: String? = null,
    val endTime: String? = null,
    val durationText: String? = null,
    val fullText: String? = null,
    val toolTip: String? = null,
    val details: Map<String, String> = emptyMap(),
    val children: List<BinlogNodeDto> = emptyList(),
) {
    fun matches(query: String): Boolean {
        if (query.isBlank()) {
            return true
        }

        return title.contains(query, ignoreCase = true) ||
            displayText.contains(query, ignoreCase = true) ||
            startTime?.contains(query, ignoreCase = true) == true ||
            endTime?.contains(query, ignoreCase = true) == true ||
            durationText?.contains(query, ignoreCase = true) == true ||
            fullText?.contains(query, ignoreCase = true) == true ||
            toolTip?.contains(query, ignoreCase = true) == true ||
            details.any { (key, value) ->
                key.contains(query, ignoreCase = true) || value.contains(query, ignoreCase = true)
            }
    }
}
