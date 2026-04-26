package io.github.hanachiru.binlog.helper

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.vfs.VirtualFile
import io.github.hanachiru.binlog.editor.BinlogDocumentDto
import java.nio.file.Path

class BinlogHelperRunner {
    private val objectMapper = jacksonObjectMapper()
    private val helperExtractor = BundledHelperExtractor(javaClass)

    fun load(file: VirtualFile): BinlogDocumentDto {
        val helperDll = helperExtractor.ensureExtracted(resolvePluginVersion())

        val commandLine = GeneralCommandLine(
            "dotnet",
            helperDll.toAbsolutePath().toString(),
            file.path,
        ).withCharset(Charsets.UTF_8)

        val output = try {
            CapturingProcessHandler(commandLine).runProcess(10 * 60 * 1000)
        } catch (error: ExecutionException) {
            throw BinlogHelperException(
                message = "Failed to start the .NET helper. Ensure the .NET SDK or runtime is available.",
                cause = error,
            )
        }

        if (output.exitCode != 0) {
            throw BinlogHelperException(
                message = "The .NET helper failed while parsing ${file.name}.",
                stdout = output.stdout,
                stderr = output.stderr,
            )
        }

        if (output.stdout.isBlank()) {
            throw BinlogHelperException(
                message = "The .NET helper returned no JSON payload.",
                stdout = output.stdout,
                stderr = output.stderr,
            )
        }

        return try {
            objectMapper.readValue(output.stdout)
        } catch (error: Exception) {
            throw BinlogHelperException(
                message = "Failed to decode the helper JSON output.",
                stdout = output.stdout,
                stderr = output.stderr,
                cause = error,
            )
        }
    }

    private fun resolvePluginVersion(): String {
        return PluginManagerCore
            .getPlugin(PluginId.getId(PLUGIN_ID))
            ?.version
            ?: "dev"
    }

    private companion object {
        const val PLUGIN_ID = "io.github.hanachiru.intellij.msbuild.binlog"
    }
}

class BinlogHelperException(
    message: String,
    val stdout: String = "",
    val stderr: String = "",
    cause: Throwable? = null,
) : RuntimeException(message, cause)
