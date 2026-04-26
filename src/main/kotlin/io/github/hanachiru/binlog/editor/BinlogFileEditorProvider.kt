package io.github.hanachiru.binlog.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.WeighedFileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import io.github.hanachiru.binlog.file.MsBuildBinlogFileType

class BinlogFileEditorProvider : WeighedFileEditorProvider() {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.fileType == MsBuildBinlogFileType
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return BinlogFileEditor(file)
    }

    override fun getEditorTypeId(): String = "msbuild-binlog-viewer"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    override fun getWeight(): Double = 1000.0
}
