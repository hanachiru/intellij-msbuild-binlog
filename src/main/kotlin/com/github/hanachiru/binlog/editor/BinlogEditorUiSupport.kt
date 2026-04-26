package com.github.hanachiru.binlog.editor

import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.tree.DefaultMutableTreeNode

internal object BinlogEditorTheme {
    val panelBackground = JBColor(Color(245, 245, 245), Color(60, 63, 65))
    val contentBackground = JBColor(Color.WHITE, Color(43, 43, 43))
    val mutedForeground = JBColor(Color(110, 110, 110), Color(155, 159, 161))
    val borderColor = JBColor(Color(210, 210, 210), Color(82, 86, 88))
    val selectionBackground = JBColor(Color(214, 228, 246), Color(67, 97, 126))
    val errorForeground = JBColor(Color(178, 49, 36), Color(242, 125, 112))
    val warningForeground = JBColor(Color(168, 104, 13), Color(242, 191, 73))
    val projectForeground = JBColor(Color(36, 87, 153), Color(123, 180, 255))
    val taskForeground = JBColor(Color(82, 85, 145), Color(170, 178, 255))
}

internal fun createBinlogTreeCellRenderer(): ColoredTreeCellRenderer {
    return object : ColoredTreeCellRenderer() {
        override fun customizeCellRenderer(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ) {
            val userObject = (value as? DefaultMutableTreeNode)?.userObject
            when (userObject) {
                is BinlogNodeDto -> append(userObject.displayText, userObject.textAttributes())
                else -> append(userObject?.toString().orEmpty())
            }
        }
    }
}

internal fun configureDetailsTable(table: JTable) {
    table.background = BinlogEditorTheme.contentBackground
    table.setShowGrid(false)
    table.intercellSpacing = Dimension(0, 0)
    table.rowHeight = JBUI.scale(24)
    table.selectionBackground = BinlogEditorTheme.selectionBackground
    table.tableHeader.background = BinlogEditorTheme.panelBackground
    table.tableHeader.foreground = table.foreground
    table.tableHeader.reorderingAllowed = false
    table.tableHeader.resizingAllowed = true
    table.setDefaultRenderer(
        Any::class.java,
        object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int,
            ): Component {
                return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column).apply {
                    border = JBUI.Borders.empty(0, 8)
                }
            }
        },
    )
}

internal fun BinlogDocumentDto.statusText(filteredRoot: BinlogNodeDto?): String {
    if (filteredRoot == null) {
        return "No matches"
    }

    return summary["Outcome"] ?: "Loaded"
}

internal fun BinlogNodeDto.filterByQuery(query: String): BinlogNodeDto? {
    if (query.isBlank()) {
        return this
    }

    val filteredChildren = children.mapNotNull { child ->
        child.filterByQuery(query)
    }

    if (!matches(query) && filteredChildren.isEmpty()) {
        return null
    }

    return copy(children = filteredChildren)
}

internal fun BinlogNodeDto.toSwingNode(): DefaultMutableTreeNode {
    val swingNode = DefaultMutableTreeNode(this)
    children.forEach { child ->
        swingNode.add(child.toSwingNode())
    }
    return swingNode
}

internal fun BinlogNodeDto.detailRows(): Map<String, String> {
    val rows = linkedMapOf<String, String>()
    durationText?.takeIf { it.isNotBlank() }?.let { rows["Duration"] = it }

    preferredDetailKeys.forEach { key ->
        details[key]?.takeIf { it.isNotBlank() && it != title && it != displayText }?.let { value ->
            rows[key] = value
        }
    }

    return rows
}

private fun BinlogNodeDto.textAttributes(): SimpleTextAttributes {
    val color = when (typeName.lowercase()) {
        "error" -> BinlogEditorTheme.errorForeground
        "warning" -> BinlogEditorTheme.warningForeground
        "project", "build" -> BinlogEditorTheme.projectForeground
        "task", "target" -> BinlogEditorTheme.taskForeground
        else -> null
    }

    return if (color == null) {
        SimpleTextAttributes.REGULAR_ATTRIBUTES
    } else {
        SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, color)
    }
}

private val preferredDetailKeys = listOf(
    "ProjectFile",
    "SourceFile",
    "CommandLineArguments",
    "FromAssembly",
    "ParentTarget",
    "DependsOnTargets",
    "Code",
    "Line",
    "Column",
    "Succeeded",
    "Skipped",
)