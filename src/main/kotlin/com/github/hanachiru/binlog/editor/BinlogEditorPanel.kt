package com.github.hanachiru.binlog.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import com.github.hanachiru.binlog.helper.BinlogHelperRunner
import java.awt.BorderLayout
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
import javax.swing.table.DefaultTableModel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class BinlogEditorPanel(
    private val file: VirtualFile,
) : JPanel(BorderLayout()), Disposable {
    private val documentLoader = BinlogDocumentLoader(file)

    private val titleLabel = JBLabel(file.name).apply {
        font = font.deriveFont(Font.BOLD)
    }

    private val searchField = SearchTextField().apply {
        textEditor.toolTipText = "Filter the loaded build tree"
    }

    private val statusLabel = JBLabel("Loading...").apply {
        foreground = BinlogEditorTheme.mutedForeground
    }

    private val reloadButton = JButton("Reload")

    private val treeModel = DefaultTreeModel(DefaultMutableTreeNode("Loading..."))
    private val tree = Tree(treeModel).apply {
        isRootVisible = true
        showsRootHandles = true
        setLargeModel(true)
        rowHeight = JBUI.scale(24)
        background = BinlogEditorTheme.contentBackground
        putClientProperty("JTree.lineStyle", "Angled")
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        cellRenderer = createBinlogTreeCellRenderer()
    }

    private val detailsTableModel = object : DefaultTableModel(arrayOf("Property", "Value"), 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }

    private val detailsTable = JTable(detailsTableModel).apply {
        fillsViewportHeight = true
        autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        configureDetailsTable(this)
    }

    private val detailTitleLabel = JBLabel("No selection").apply {
        font = font.deriveFont(Font.BOLD)
    }

    private var loadedDocument: BinlogDocumentDto? = null
    private var disposed = false

    val preferredFocusComponent: JComponent
        get() = tree

    init {
        background = BinlogEditorTheme.panelBackground
        border = JBUI.Borders.empty()

        add(createTopBar(), BorderLayout.NORTH)
        add(createContent(), BorderLayout.CENTER)

        reloadButton.addActionListener { requestReload() }
        tree.addTreeSelectionListener {
            val node = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? BinlogNodeDto
            updateDetails(node)
        }
        searchField.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = rebuildTree()

            override fun removeUpdate(e: DocumentEvent?) = rebuildTree()

            override fun changedUpdate(e: DocumentEvent?) = rebuildTree()
        })

        requestReload()
    }

    private fun createTopBar(): JComponent {
        val actions = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
            add(statusLabel, BorderLayout.CENTER)
            add(reloadButton, BorderLayout.EAST)
        }

        return JPanel(BorderLayout(10, 0)).apply {
            background = BinlogEditorTheme.panelBackground
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BinlogEditorTheme.borderColor),
                JBUI.Borders.empty(8, 10),
            )
            add(titleLabel, BorderLayout.WEST)
            add(searchField, BorderLayout.CENTER)
            add(actions, BorderLayout.EAST)
        }
    }

    private fun createContent(): JComponent {
        val treePanel = JPanel(BorderLayout()).apply {
            background = BinlogEditorTheme.contentBackground
            border = BorderFactory.createMatteBorder(1, 1, 1, 1, BinlogEditorTheme.borderColor)
            add(JBScrollPane(tree).apply {
                border = JBUI.Borders.empty()
                viewport.background = BinlogEditorTheme.contentBackground
            }, BorderLayout.CENTER)
        }

        val detailsScrollPane = JBScrollPane(detailsTable).apply {
            border = JBUI.Borders.empty()
            viewport.background = BinlogEditorTheme.contentBackground
        }

        val inspectorPanel = JPanel(BorderLayout()).apply {
            background = BinlogEditorTheme.contentBackground
            border = BorderFactory.createMatteBorder(1, 1, 1, 1, BinlogEditorTheme.borderColor)
            add(
                JPanel(BorderLayout()).apply {
                    background = BinlogEditorTheme.contentBackground
                    border = BorderFactory.createMatteBorder(0, 0, 1, 0, BinlogEditorTheme.borderColor)
                    add(detailTitleLabel, BorderLayout.CENTER)
                    border = BorderFactory.createCompoundBorder(border, JBUI.Borders.empty(10))
                },
                BorderLayout.NORTH,
            )
            add(detailsScrollPane, BorderLayout.CENTER)
        }

        return JSplitPane(JSplitPane.VERTICAL_SPLIT).apply {
            resizeWeight = 0.64
            border = JBUI.Borders.empty(10)
            topComponent = treePanel
            bottomComponent = inspectorPanel
            minimumSize = Dimension(JBUI.scale(640), JBUI.scale(360))
        }
    }

    private fun requestReload() {
        documentLoader.load(
            isDisposed = { disposed },
            onStateChanged = ::renderState,
        )
    }

    private fun renderState(state: BinlogEditorState) {
        when (state) {
            is BinlogEditorState.Loading -> renderLoadingState(state)
            is BinlogEditorState.Loaded -> {
                reloadButton.isEnabled = true
                loadedDocument = state.document
                rebuildTree()
            }

            is BinlogEditorState.Failed -> {
                reloadButton.isEnabled = true
                loadedDocument = null
                showLoadError(state.fileName, state.error)
            }
        }
    }

    private fun renderLoadingState(state: BinlogEditorState.Loading) {
        loadedDocument = null
        reloadButton.isEnabled = false
        statusLabel.text = "Loading"
        detailTitleLabel.text = "Loading ${state.fileName}"
        detailsTableModel.rowCount = 0

        val root = DefaultMutableTreeNode("Loading...")
        treeModel.setRoot(root)
        tree.selectionPath = TreePath(root.path)
    }

    private fun rebuildTree() {
        val document = loadedDocument ?: return
        val query = searchField.text.trim()
        val filteredRoot = document.root.filterByQuery(query)
        val treeRoot = filteredRoot?.toSwingNode() ?: DefaultMutableTreeNode("No matches")
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

        statusLabel.text = document.statusText(filteredRoot)
    }

    private fun showLoadError(fileName: String, error: Throwable) {
        val root = DefaultMutableTreeNode("Failed to load $fileName")
        treeModel.setRoot(root)
        tree.selectionPath = TreePath(root.path)

        detailsTableModel.rowCount = 0
        detailsTableModel.addRow(arrayOf("File", file.path))
        detailsTableModel.addRow(arrayOf("Error", error.message ?: error::class.java.simpleName))

        statusLabel.text = "Failed"
        detailTitleLabel.text = "Failed to load $fileName"
    }

    private fun updateDetails(node: BinlogNodeDto?) {
        detailsTableModel.rowCount = 0

        if (node == null) {
            detailTitleLabel.text = "No selection"
            return
        }

        node.detailRows().forEach { (key, value) ->
            detailsTableModel.addRow(arrayOf(key, value))
        }

        detailTitleLabel.text = node.title.ifBlank { node.typeName }
    }

    override fun dispose() {
        disposed = true
    }
}
