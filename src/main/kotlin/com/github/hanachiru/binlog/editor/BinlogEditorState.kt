package com.github.hanachiru.binlog.editor

internal sealed interface BinlogEditorState {
    data class Loading(val fileName: String) : BinlogEditorState

    data class Loaded(val document: BinlogDocumentDto) : BinlogEditorState

    data class Failed(val fileName: String, val error: Throwable) : BinlogEditorState
}