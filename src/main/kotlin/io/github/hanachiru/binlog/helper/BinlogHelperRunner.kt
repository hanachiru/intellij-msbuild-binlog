package io.github.hanachiru.binlog.helper

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.vfs.VirtualFile
import io.github.hanachiru.binlog.editor.BinlogDocumentDto
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class BinlogHelperRunner {
    private val objectMapper = jacksonObjectMapper()

    fun load(file: VirtualFile): BinlogDocumentDto {
        val helperDll = ensureHelperExtracted()

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

    private fun ensureHelperExtracted(): Path {
        val pluginVersion = PluginManagerCore
            .getPlugin(PluginId.getId(PLUGIN_ID))
            ?.version
            ?: "dev"

        val extractionRoot = Path.of(PathManager.getSystemPath(), "msbuild-binlog-viewer", pluginVersion)
        val helperDll = extractionRoot.resolve(HELPER_DLL_NAME)

        if (Files.exists(helperDll)) {
            return helperDll
        }

        Files.createDirectories(extractionRoot)

        val manifestStream = javaClass.getResourceAsStream("/$HELPER_RESOURCE_ROOT/manifest.txt")
            ?: throw BinlogHelperException("The bundled helper manifest was not found in the plugin resources.")

        manifestStream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.forEach { relativePath ->
                val target = extractionRoot.resolve(relativePath)
                target.parent?.let(Files::createDirectories)

                val resourceStream = javaClass.getResourceAsStream("/$HELPER_RESOURCE_ROOT/$relativePath")
                    ?: throw BinlogHelperException("The bundled helper resource $relativePath is missing.")

                resourceStream.use { input ->
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }

        if (!Files.exists(helperDll)) {
            throw BinlogHelperException("The bundled helper DLL was not extracted correctly.")
        }

        return helperDll
    }

    private companion object {
        const val PLUGIN_ID = "io.github.hanachiru.intellij.msbuild.binlog"
        const val HELPER_DLL_NAME = "BinlogJsonExporter.dll"
        const val HELPER_RESOURCE_ROOT = "binlog-helper"
    }
}

class BinlogHelperException(
    message: String,
    val stdout: String = "",
    val stderr: String = "",
    cause: Throwable? = null,
) : RuntimeException(message, cause)
