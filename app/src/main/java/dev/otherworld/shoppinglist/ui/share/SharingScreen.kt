package dev.otherworld.shoppinglist.ui.share

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.domain.model.ShareModel
import dev.otherworld.shoppinglist.domain.model.ShareType
import dev.otherworld.shoppinglist.domain.share.ShareeOption
import dev.otherworld.shoppinglist.ui.common.TextEntryDialog
import dev.otherworld.shoppinglist.ui.common.asString

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                title = {
                    Text(stringResource(R.string.share_title, viewModel.listTitle), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                Text(it.asString(), color = MaterialTheme.colorScheme.error)
            }

            SectionTitle(stringResource(R.string.section_people_groups))
            ShareSearch(
                query = state.query,
                results = state.results,
                searching = state.searching,
                failed = state.searchFailed,
                onQueryChange = viewModel::onQueryChange,
                onPick = viewModel::shareWith,
            )
            if (state.people.isEmpty()) {
                Text(stringResource(R.string.share_none_yet), style = MaterialTheme.typography.bodyMedium)
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
            SectionTitle(stringResource(R.string.section_public_link))
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
            title = stringResource(R.string.dialog_set_link_password_title),
            label = stringResource(R.string.field_password),
            confirmLabel = stringResource(R.string.action_set),
            onConfirm = { passwordTarget = null; viewModel.setLinkPassword(target, it) },
            onDismiss = { passwordTarget = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ShareSearch(
    query: String,
    results: List<ShareeOption>,
    searching: Boolean,
    failed: Boolean,
    onQueryChange: (String) -> Unit,
    onPick: (ShareeOption) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.share_search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = if (searching) {
                { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
            } else null,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            query.isBlank() || searching -> Unit
            failed -> Text(
                stringResource(R.string.share_search_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            results.isEmpty() -> Text(
                stringResource(R.string.share_search_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> results.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(option) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (option.type == ShareType.GROUP) Icons.Filled.Group else Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        if (option.type == ShareType.GROUP) stringResource(R.string.share_search_group, option.label) else option.label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
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
                if (share.type == dev.otherworld.shoppinglist.domain.model.ShareType.GROUP) stringResource(R.string.label_group) else stringResource(R.string.label_user),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(stringResource(R.string.label_edit), style = MaterialTheme.typography.labelMedium)
        Switch(checked = share.canWrite, onCheckedChange = onToggleWrite)
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_remove), tint = MaterialTheme.colorScheme.error)
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
        OutlinedButton(onClick = onCreate) { Text(stringResource(R.string.link_create)) }
        return
    }
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                link.token?.let { "…/s/${it.take(10)}…" } ?: stringResource(R.string.link_public),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.label_can_edit), modifier = Modifier.weight(1f))
                Switch(checked = link.canWrite, onCheckedChange = onToggleWrite)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                link.token?.let { token -> TextButton(onClick = { onCopy(token) }) { Text(stringResource(R.string.link_copy)) } }
                if (link.hasPassword) {
                    TextButton(onClick = onClearPassword) { Text(stringResource(R.string.link_remove_password)) }
                } else {
                    TextButton(onClick = onPassword) { Text(stringResource(R.string.link_set_password)) }
                }
            }
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.link_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
