package io.github.hanachiru.binlog.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import io.github.hanachiru.binlog.helper.BinlogHelperRunner

internal class BinlogDocumentLoader(
    private val file: VirtualFile,
    private val helperRunner: BinlogHelperRunner = BinlogHelperRunner(),
) {
    fun load(
        isDisposed: () -> Boolean,
        onStateChanged: (BinlogEditorState) -> Unit,
    ) {
        if (isDisposed()) {
            return
        }

        onStateChanged(BinlogEditorState.Loading(file.name))

        val application = ApplicationManager.getApplication()
        application.executeOnPooledThread {
            val nextState: BinlogEditorState = runCatching { helperRunner.load(file) }
                .fold(
                    onSuccess = { document -> BinlogEditorState.Loaded(document) },
                    onFailure = { error -> BinlogEditorState.Failed(file.name, error) },
                )

            application.invokeLater {
                if (isDisposed()) {
                    return@invokeLater
                }

                onStateChanged(nextState)
            }
        }
    }
}