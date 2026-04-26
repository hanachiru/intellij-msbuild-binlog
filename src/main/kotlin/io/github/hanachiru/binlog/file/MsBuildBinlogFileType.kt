package io.github.hanachiru.binlog.file

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

object MsBuildBinlogFileType : FileType {
    override fun getName(): String = "MSBuildBinlog"

    override fun getDescription(): String = "MSBuild binary log"

    override fun getDefaultExtension(): String = "binlog"

    override fun getIcon(): Icon? = null

    override fun isBinary(): Boolean = true

    override fun isReadOnly(): Boolean = true

    override fun getCharset(file: VirtualFile, content: ByteArray): String? = null
}
