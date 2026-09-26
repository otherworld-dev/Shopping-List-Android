package dev.otherworld.shoppinglist.ui.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.data.prefs.ThemeMode
import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import dev.otherworld.shoppinglist.ui.common.ConfirmDialog
import dev.otherworld.shoppinglist.ui.common.TextEntryDialog
import dev.otherworld.shoppinglist.ui.common.asString

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
    var showTheme by remember { mutableStateOf(false) }
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                title = {
                    Column {
                        Text(stringResource(R.string.header_shopping_list), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
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
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.cd_refresh))
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_manage_tags)) },
                            onClick = { menuOpen = false; onManageTags() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_theme)) },
                            onClick = { menuOpen = false; showTheme = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_log_out)) },
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
                text = { Text(stringResource(R.string.fab_new_list)) },
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
                        Text(state.error!!.asString(), textAlign = TextAlign.Center)
                    }
                }
                state.lists.isEmpty() -> {
                    Text(
                        stringResource(R.string.lists_empty),
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
                        // Pinned lists, yours and shared ones, go first; the rest follow,
                        // captioned Others only when there is a Pinned section above them
                        // (mirrors the web app's sidebar).
                        val pinned = state.lists.filter { it.isPinned }
                        val others = state.lists.filterNot { it.isPinned }
                        LazyColumn(Modifier.fillMaxSize()) {
                            if (pinned.isNotEmpty()) {
                                item(key = "caption-pinned") { ListsCaption(stringResource(R.string.lists_section_pinned)) }
                                listRows(pinned, onOpenList, onShareList, viewModel,
                                    onRename = { renameTarget = it }, onDelete = { deleteTarget = it })
                                if (others.isNotEmpty()) {
                                    item(key = "caption-others") { ListsCaption(stringResource(R.string.lists_section_others)) }
                                }
                            }
                            listRows(others, onOpenList, onShareList, viewModel,
                                onRename = { renameTarget = it }, onDelete = { deleteTarget = it })
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        TextEntryDialog(
            title = stringResource(R.string.dialog_new_list_title),
            label = stringResource(R.string.dialog_list_name_label),
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { showCreate = false; viewModel.createList(it) },
            onDismiss = { showCreate = false },
        )
    }
    renameTarget?.let { target ->
        TextEntryDialog(
            title = stringResource(R.string.dialog_rename_list_title),
            label = stringResource(R.string.dialog_list_name_label),
            initialValue = target.title,
            onConfirm = { renameTarget = null; viewModel.renameList(target.id, it) },
            onDismiss = { renameTarget = null },
        )
    }
    if (showTheme) {
        ThemeDialog(
            current = themeMode,
            onPick = { showTheme = false; viewModel.setThemeMode(it) },
            onDismiss = { showTheme = false },
        )
    }
    deleteTarget?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.dialog_delete_list_title),
            message = stringResource(R.string.dialog_delete_list_message, target.title),
            onConfirm = { deleteTarget = null; viewModel.deleteList(target.id) },
            onDismiss = { deleteTarget = null },
        )
    }
}

private fun LazyListScope.listRows(
    lists: List<ShoppingListModel>,
    onOpenList: (ShoppingListModel) -> Unit,
    onShareList: (ShoppingListModel) -> Unit,
    viewModel: ListsViewModel,
    onRename: (ShoppingListModel) -> Unit,
    onDelete: (ShoppingListModel) -> Unit,
) {
    itemsIndexed(lists, key = { _, item -> item.id }) { index, list ->
        ListRow(
            list = list,
            alt = index % 2 == 1,
            onClick = { onOpenList(list) },
            onPin = { viewModel.setPinned(list, !list.isPinned) },
            onRename = { onRename(list) },
            onDelete = { onDelete(list) },
            onShare = { onShareList(list) },
        )
    }
}

@Composable
private fun ThemeDialog(current: ThemeMode, onPick: (ThemeMode) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_theme_title)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = mode == current, onClick = null)
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 12.dp))
                        Text(
                            stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.theme_system
                                    ThemeMode.LIGHT -> R.string.theme_light
                                    ThemeMode.DARK -> R.string.theme_dark
                                },
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ListsCaption(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ListRow(
    list: ShoppingListModel,
    alt: Boolean,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = if (alt) {
                dev.otherworld.shoppinglist.ui.theme.LocalRowShade.current
            } else {
                androidx.compose.ui.graphics.Color.Transparent
            },
        ),
        leadingContent = {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null)
        },
        headlineContent = { Text(list.title) },
        supportingContent = if (!list.isOwner) {
            { Text(if (list.canWrite) stringResource(R.string.list_shared_with_you) else stringResource(R.string.list_shared_readonly)) }
        } else null,
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_list_options))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    // Pinning works on shared lists too — the pin is this user's own.
                    DropdownMenuItem(
                        text = { Text(stringResource(if (list.isPinned) R.string.menu_unpin_list else R.string.menu_pin_list)) },
                        onClick = { menu = false; onPin() },
                    )
                    if (list.isOwner) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_share)) },
                            onClick = { menu = false; onShare() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_rename)) },
                            onClick = { menu = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_delete)) },
                            onClick = { menu = false; onDelete() },
                        )
                    }
                }
            }
        },
    )
}
