package io.github.hanachiru.binlog.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBColor
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import io.github.hanachiru.binlog.helper.BinlogHelperRunner
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.JTable
import javax.swing.JTree
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class BinlogEditorPanel(
    private val file: VirtualFile,
) : JPanel(BorderLayout()), Disposable {
    companion object {
        private val PANEL_BACKGROUND = JBColor(Color(245, 245, 245), Color(60, 63, 65))
        private val CONTENT_BACKGROUND = JBColor(Color.WHITE, Color(43, 43, 43))
        private val MUTED_FOREGROUND = JBColor(Color(110, 110, 110), Color(155, 159, 161))
        private val BORDER_COLOR = JBColor(Color(210, 210, 210), Color(82, 86, 88))
        private val SELECTION_BACKGROUND = JBColor(Color(214, 228, 246), Color(67, 97, 126))
        private val ERROR_FOREGROUND = JBColor(Color(178, 49, 36), Color(242, 125, 112))
        private val WARNING_FOREGROUND = JBColor(Color(168, 104, 13), Color(242, 191, 73))
        private val PROJECT_FOREGROUND = JBColor(Color(36, 87, 153), Color(123, 180, 255))
        private val TASK_FOREGROUND = JBColor(Color(82, 85, 145), Color(170, 178, 255))
    }

    private val helperRunner = BinlogHelperRunner()

    private val titleLabel = JBLabel(file.name).apply {
        font = font.deriveFont(Font.BOLD)
    }

    private val searchField = SearchTextField().apply {
        textEditor.toolTipText = "Filter the loaded build tree"
    }

    private val statusLabel = JBLabel("Loading...").apply {
        foreground = MUTED_FOREGROUND
    }

    private val reloadButton = JButton("Reload")

    private val treeModel = DefaultTreeModel(DefaultMutableTreeNode("Loading..."))
    private val tree = Tree(treeModel).apply {
        isRootVisible = true
        showsRootHandles = true
        setLargeModel(true)
        rowHeight = JBUI.scale(24)
        background = CONTENT_BACKGROUND
        putClientProperty("JTree.lineStyle", "Angled")
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        cellRenderer = object : ColoredTreeCellRenderer() {
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
                    is BinlogNodeDto -> {
                        append(userObject.displayText, nodeTextAttributes(userObject))
                    }

                    else -> append(userObject?.toString().orEmpty())
                }
            }
        }
    }

    private val detailsTableModel = object : DefaultTableModel(arrayOf("Property", "Value"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }

    private val detailsTable = JTable(detailsTableModel).apply {
        fillsViewportHeight = true
        autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        configureTable(this)
    }

    private val detailTitleLabel = JBLabel("No selection").apply {
        font = font.deriveFont(Font.BOLD)
    }

    private var loadedDocument: BinlogDocumentDto? = null
    private var disposed = false

    val preferredFocusComponent: JComponent
        get() = tree

    init {
        background = PANEL_BACKGROUND
        border = JBUI.Borders.empty()

        add(createTopBar(), BorderLayout.NORTH)
        add(createContent(), BorderLayout.CENTER)

        reloadButton.addActionListener { loadDocument() }
        tree.addTreeSelectionListener {
            val node = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? BinlogNodeDto
            updateDetails(node)
        }
        searchField.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = rebuildTree()

            override fun removeUpdate(e: DocumentEvent?) = rebuildTree()

            override fun changedUpdate(e: DocumentEvent?) = rebuildTree()
        })

        loadDocument()
    }

    private fun createTopBar(): JComponent {
        val actions = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(statusLabel, BorderLayout.CENTER)
            add(reloadButton, BorderLayout.EAST)
        }

        return JPanel(BorderLayout(10, 0)).apply {
            background = PANEL_BACKGROUND
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR),
                JBUI.Borders.empty(8, 10),
            )
            add(titleLabel, BorderLayout.WEST)
            add(searchField, BorderLayout.CENTER)
            add(actions, BorderLayout.EAST)
        }
    }

    private fun createContent(): JComponent {
        val treePanel = JPanel(BorderLayout()).apply {
            background = CONTENT_BACKGROUND
            border = BorderFactory.createMatteBorder(1, 1, 1, 1, BORDER_COLOR)
            add(JBScrollPane(tree).apply {
                border = JBUI.Borders.empty()
                viewport.background = CONTENT_BACKGROUND
            }, BorderLayout.CENTER)
        }

        val detailsScrollPane = JBScrollPane(detailsTable).apply {
            border = JBUI.Borders.empty()
            viewport.background = CONTENT_BACKGROUND
        }

        val inspectorPanel = JPanel(BorderLayout()).apply {
            background = CONTENT_BACKGROUND
            border = BorderFactory.createMatteBorder(1, 1, 1, 1, BORDER_COLOR)
            add(
                JPanel(BorderLayout()).apply {
                    background = CONTENT_BACKGROUND
                    border = BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR)
                    add(detailTitleLabel, BorderLayout.CENTER)
                    border = BorderFactory.createCompoundBorder(border, JBUI.Borders.empty(10))
                },
                BorderLayout.NORTH,
            )
            add(detailsScrollPane, BorderLayout.CENTER)
        }

        return JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
            resizeWeight = 0.56
            border = JBUI.Borders.empty(10)
            leftComponent = treePanel
            rightComponent = inspectorPanel
            minimumSize = Dimension(JBUI.scale(640), JBUI.scale(360))
        }
    }

    private fun loadDocument() {
        statusLabel.text = "Loading"
        reloadButton.isEnabled = false
        detailTitleLabel.text = "Loading ${file.name}"

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching { helperRunner.load(file) }

            ApplicationManager.getApplication().invokeLater {
                if (disposed) {
                    return@invokeLater
                }

                reloadButton.isEnabled = true

                result.onSuccess { document ->
                    loadedDocument = document
                    rebuildTree()
                }.onFailure { error ->
                    loadedDocument = null
                    showLoadError(error)
                }
            }
        }
    }

    private fun rebuildTree() {
        val document = loadedDocument ?: return
        val query = searchField.text.trim()
        val filteredRoot = if (query.isBlank()) document.root else filterNode(document.root, query)
        val treeRoot = filteredRoot?.let(::toSwingNode) ?: DefaultMutableTreeNode("No matches")
        treeModel.setRoot(treeRoot)

        if (filteredRoot != null) {
            tree.selectionPath = TreePath(treeRoot.path)
            if (query.isBlank()) {
                TreeUtil.expand(tree, 2)
            } else {
                TreeUtil.expandAll(tree)
            }
            updateDetails(filteredRoot)
        } else {
            updateDetails(null)
        }

        statusLabel.text = buildStatusText(document, filteredRoot)
    }

    private fun showLoadError(error: Throwable) {
        val root = DefaultMutableTreeNode("Failed to load ${file.name}")
        treeModel.setRoot(root)
        tree.selectionPath = TreePath(root.path)

        detailsTableModel.rowCount = 0
        detailsTableModel.addRow(arrayOf("File", file.path))
        detailsTableModel.addRow(arrayOf("Error", error.message ?: error::class.java.simpleName))

        statusLabel.text = "Failed"
        detailTitleLabel.text = "Failed to load ${file.name}"
    }

    private fun updateDetails(node: BinlogNodeDto?) {
        detailsTableModel.rowCount = 0

        if (node == null) {
            detailTitleLabel.text = "No selection"
            return
        }

        val rows = linkedMapOf<String, String>()
        node.durationText?.takeIf { it.isNotBlank() }?.let { rows["Duration"] = it }
        preferredDetailKeys.forEach { key ->
            node.details[key]?.takeIf { it.isNotBlank() && it != node.title && it != node.displayText }?.let { value ->
                rows[key] = value
            }
        }

        rows.forEach { (key, value) ->
            detailsTableModel.addRow(arrayOf(key, value))
        }

        detailTitleLabel.text = node.title.ifBlank { node.typeName }
    }

    private fun filterNode(node: BinlogNodeDto, query: String): BinlogNodeDto? {
        val filteredChildren = node.children.mapNotNull { child ->
            filterNode(child, query)
        }

        if (!node.matches(query) && filteredChildren.isEmpty()) {
            return null
        }

        return node.copy(children = filteredChildren)
    }

    private fun toSwingNode(node: BinlogNodeDto): DefaultMutableTreeNode {
        val swingNode = DefaultMutableTreeNode(node)
        node.children.forEach { child ->
            swingNode.add(toSwingNode(child))
        }
        return swingNode
    }

    private fun configureTable(table: JTable) {
        table.background = CONTENT_BACKGROUND
        table.setShowGrid(false)
        table.intercellSpacing = Dimension(0, 0)
        table.rowHeight = JBUI.scale(24)
        table.selectionBackground = SELECTION_BACKGROUND
        table.tableHeader.background = PANEL_BACKGROUND
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

    private fun buildStatusText(document: BinlogDocumentDto, filteredRoot: BinlogNodeDto?): String {
        if (filteredRoot == null) {
            return "No matches"
        }

        return document.summary["Outcome"] ?: "Loaded"
    }

    private fun nodeTextAttributes(node: BinlogNodeDto): SimpleTextAttributes {
        val color = when (node.typeName.lowercase()) {
            "error" -> ERROR_FOREGROUND
            "warning" -> WARNING_FOREGROUND
            "project", "build" -> PROJECT_FOREGROUND
            "task", "target" -> TASK_FOREGROUND
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

    override fun dispose() {
        disposed = true
    }
}
