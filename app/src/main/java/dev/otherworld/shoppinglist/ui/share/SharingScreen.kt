package dev.otherworld.shoppinglist.ui.share

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.otherworld.shoppinglist.domain.model.ShareModel
import dev.otherworld.shoppinglist.ui.common.TextEntryDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharingScreen(
    onBack: () -> Unit,
    viewModel: SharingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var passwordTarget by remember { mutableStateOf<ShareModel?>(null) }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    Text("Share “${viewModel.listTitle}”", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            SectionTitle("People & groups")
            AddShareRow(onAdd = viewModel::addShare)
            if (state.people.isEmpty()) {
                Text("Not shared with anyone yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                state.people.forEach { share ->
                    PersonShareRow(
                        share = share,
                        onToggleWrite = { viewModel.setSharePermission(share, it) },
                        onRemove = { viewModel.removeShare(share) },
                    )
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.size(8.dp))
            SectionTitle("Public link")
            LinkSection(
                link = state.link,
                onCreate = { viewModel.createLink(write = false) },
                onToggleWrite = { viewModel.setLinkPermission(state.link!!, it) },
                onCopy = { clipboard.setText(AnnotatedString(viewModel.linkUrl(it))) },
                onPassword = { passwordTarget = state.link },
                onClearPassword = { viewModel.setLinkPassword(state.link!!, null) },
                onRemove = { viewModel.removeLink(state.link!!) },
            )
        }
    }

    passwordTarget?.let { target ->
        TextEntryDialog(
            title = "Set link password",
            label = "Password",
            confirmLabel = "Set",
            onConfirm = { passwordTarget = null; viewModel.setLinkPassword(target, it) },
            onDismiss = { passwordTarget = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddShareRow(onAdd: (String, Int, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var isGroup by remember { mutableStateOf(false) }
    var canEdit by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(if (isGroup) "Group name" else "Username") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !isGroup, onClick = { isGroup = false }, label = { Text("User") })
            FilterChip(selected = isGroup, onClick = { isGroup = true }, label = { Text("Group") })
            Spacer(Modifier.size(8.dp))
            Text("Can edit")
            Switch(checked = canEdit, onCheckedChange = { canEdit = it })
        }
        Button(
            onClick = {
                if (name.isNotBlank()) {
                    onAdd(name.trim(), if (isGroup) SharingViewModel.TYPE_GROUP else SharingViewModel.TYPE_USER, canEdit)
                    name = ""
                }
            },
            enabled = name.isNotBlank(),
        ) { Text("Add") }
    }
}

@Composable
private fun PersonShareRow(
    share: ShareModel,
    onToggleWrite: (Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(share.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (share.type == dev.otherworld.shoppinglist.domain.model.ShareType.GROUP) "Group" else "User",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("Edit", style = MaterialTheme.typography.labelMedium)
        Switch(checked = share.canWrite, onCheckedChange = onToggleWrite)
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun LinkSection(
    link: ShareModel?,
    onCreate: () -> Unit,
    onToggleWrite: (Boolean) -> Unit,
    onCopy: (String) -> Unit,
    onPassword: () -> Unit,
    onClearPassword: () -> Unit,
    onRemove: () -> Unit,
) {
    if (link == null) {
        OutlinedButton(onClick = onCreate) { Text("Create public link") }
        return
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                link.token?.let { "…/s/${it.take(10)}…" } ?: "Public link",
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Can edit", modifier = Modifier.weight(1f))
                Switch(checked = link.canWrite, onCheckedChange = onToggleWrite)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                link.token?.let { token -> TextButton(onClick = { onCopy(token) }) { Text("Copy link") } }
                if (link.hasPassword) {
                    TextButton(onClick = onClearPassword) { Text("Remove password") }
                } else {
                    TextButton(onClick = onPassword) { Text("Set password") }
                }
            }
            TextButton(onClick = onRemove) {
                Text("Delete link", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
