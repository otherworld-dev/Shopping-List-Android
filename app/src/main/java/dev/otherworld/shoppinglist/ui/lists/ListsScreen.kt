package dev.otherworld.shoppinglist.ui.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import dev.otherworld.shoppinglist.ui.common.ConfirmDialog
import dev.otherworld.shoppinglist.ui.common.TextEntryDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListsScreen(
    onOpenList: (ShoppingListModel) -> Unit,
    onShareList: (ShoppingListModel) -> Unit,
    onManageTags: () -> Unit,
    viewModel: ListsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    dev.otherworld.shoppinglist.ui.common.PollEffect { viewModel.poll() }
    var menuOpen by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ShoppingListModel?>(null) }
    var deleteTarget by remember { mutableStateOf<ShoppingListModel?>(null) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                title = {
                    Column {
                        Text("Shopping List", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        if (viewModel.accountLabel.isNotEmpty()) {
                            Text(
                                viewModel.accountLabel,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Manage tags") },
                            onClick = { menuOpen = false; onManageTags() },
                        )
                        DropdownMenuItem(
                            text = { Text("Log out") },
                            onClick = { menuOpen = false; viewModel.logout() },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New list") },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading && state.lists.isEmpty() -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                state.error != null && state.lists.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.error!!, textAlign = TextAlign.Center)
                    }
                }
                state.lists.isEmpty() -> {
                    Text(
                        "No lists yet. Tap “New list” to create one.",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    androidx.compose.material3.Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .widthIn(max = 680.dp)
                            .fillMaxHeight()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        LazyColumn(Modifier.fillMaxSize()) {
                            itemsIndexed(
                                state.lists,
                                key = { _, item -> item.id },
                            ) { index, list ->
                                ListRow(
                                    list = list,
                                    alt = index % 2 == 1,
                                    onClick = { onOpenList(list) },
                                    onRename = { renameTarget = list },
                                    onDelete = { deleteTarget = list },
                                    onShare = { onShareList(list) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        TextEntryDialog(
            title = "New list",
            label = "List name",
            confirmLabel = "Create",
            onConfirm = { showCreate = false; viewModel.createList(it) },
            onDismiss = { showCreate = false },
        )
    }
    renameTarget?.let { target ->
        TextEntryDialog(
            title = "Rename list",
            label = "List name",
            initialValue = target.title,
            onConfirm = { renameTarget = null; viewModel.renameList(target.id, it) },
            onDismiss = { renameTarget = null },
        )
    }
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = "Delete list?",
            message = "“${target.title}” and all its items will be permanently deleted.",
            onConfirm = { deleteTarget = null; viewModel.deleteList(target.id) },
            onDismiss = { deleteTarget = null },
        )
    }
}

@Composable
private fun ListRow(
    list: ShoppingListModel,
    alt: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = if (alt) {
                dev.otherworld.shoppinglist.ui.theme.NcRowAlt
            } else {
                androidx.compose.ui.graphics.Color.Transparent
            },
        ),
        leadingContent = {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
        },
        headlineContent = { Text(list.title) },
        supportingContent = if (!list.isOwner) {
            { Text(if (list.canWrite) "Shared with you" else "Shared (read-only)") }
        } else null,
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "List options")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (list.isOwner) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = { menu = false; onShare() },
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = { menu = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        },
    )
}
