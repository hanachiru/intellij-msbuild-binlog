package com.github.hanachiru.binlog.helper

import com.intellij.openapi.application.PathManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal class BundledHelperExtractor(
    private val resourceAnchor: Class<*>,
    private val systemPath: String = PathManager.getSystemPath(),
) {
    @Volatile
    private var cachedPluginVersion: String? = null

    @Volatile
    private var cachedHelperDll: Path? = null

    @Synchronized
    fun ensureExtracted(pluginVersion: String): Path {
        val cachedPath = cachedHelperDll
        if (cachedPluginVersion == pluginVersion && cachedPath != null && Files.exists(cachedPath)) {
            return cachedPath
        }

        val extractionRoot = Path.of(systemPath, EXTRACTION_DIRECTORY_NAME, pluginVersion)
        val helperDll = extractionRoot.resolve(HELPER_DLL_NAME)

        if (!Files.exists(helperDll)) {
            Files.createDirectories(extractionRoot)
            manifestEntries().forEach { relativePath ->
                copyBundledResource(relativePath, extractionRoot.resolve(relativePath))
            }
        }

        if (!Files.exists(helperDll)) {
            throw BinlogHelperException("The bundled helper DLL was not extracted correctly.")
        }

        cachedPluginVersion = pluginVersion
        cachedHelperDll = helperDll
        return helperDll
    }

    private fun manifestEntries(): List<String> {
        val manifestStream = resourceAnchor.getResourceAsStream("/$HELPER_RESOURCE_ROOT/manifest.txt")
            ?: throw BinlogHelperException("The bundled helper manifest was not found in the plugin resources.")

        return manifestStream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.toList()
        }
    }

    private fun copyBundledResource(relativePath: String, target: Path) {
        target.parent?.let(Files::createDirectories)

        val resourceStream = resourceAnchor.getResourceAsStream("/$HELPER_RESOURCE_ROOT/$relativePath")
            ?: throw BinlogHelperException("The bundled helper resource $relativePath is missing.")

        resourceStream.use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val EXTRACTION_DIRECTORY_NAME = "msbuild-binlog-viewer"
        const val HELPER_DLL_NAME = "BinlogJsonExporter.dll"
        const val HELPER_RESOURCE_ROOT = "binlog-helper"
    }
}