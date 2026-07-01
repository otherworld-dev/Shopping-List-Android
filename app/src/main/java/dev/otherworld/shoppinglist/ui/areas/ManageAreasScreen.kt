package dev.otherworld.shoppinglist.ui.areas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import dev.otherworld.shoppinglist.ui.common.ConfirmDialog
import dev.otherworld.shoppinglist.ui.common.parseHexColor

private val AREA_COLORS = listOf(
    "#4CAF50", "#2196F3", "#FF9800", "#F44336", "#9C27B0", "#00BCD4",
    "#795548", "#607D8B", "#E91E63", "#8BC34A", "#FFC107", "#3F51B5",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageAreasScreen(
    onBack: () -> Unit,
    viewModel: ManageAreasViewModel = hiltViewModel(),
) {
    val areas by viewModel.areas.collectAsStateWithLifecycle()
    val otherLists by viewModel.otherLists.collectAsStateWithLifecycle()
    var overflow by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ShopAreaModel?>(null) }
    var showCopy by remember { mutableStateOf(false) }

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
                title = { Text(stringResource(R.string.areas_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    IconButton(onClick = { overflow = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more))
                    }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_copy_from_list)) },
                            enabled = otherLists.isNotEmpty(),
                            onClick = { overflow = false; showCopy = true },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.fab_new_area)) },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(areas, key = { it.id }) { area ->
                ListItem(
                    modifier = Modifier.clickable { editTarget = area },
                    leadingContent = {
                        val color = parseHexColor(area.color) ?: MaterialTheme.colorScheme.outline
                        Box(Modifier.size(20.dp).background(color, CircleShape))
                    },
                    headlineContent = { Text(area.name) },
                    supportingContent = {
                        Text(
                            if (area.keywords.isEmpty()) stringResource(R.string.area_no_keywords)
                            else stringResource(R.string.area_keywords_count, area.keywords.size),
                        )
                    },
                )
                HorizontalDivider()
            }
        }
    }

    if (showCreate) {
        AreaEditDialog(
            title = stringResource(R.string.dialog_new_area_title),
            initial = null,
            onSave = { name, color, keywords ->
                showCreate = false
                viewModel.createArea(name, color, keywords)
            },
            onDelete = null,
            onDismiss = { showCreate = false },
        )
    }
    editTarget?.let { area ->
        AreaEditDialog(
            title = stringResource(R.string.dialog_edit_area_title),
            initial = area,
            onSave = { name, color, keywords ->
                editTarget = null
                viewModel.updateArea(area.id, name, color, keywords)
            },
            onDelete = { editTarget = null; viewModel.deleteArea(area.id) },
            onDismiss = { editTarget = null },
        )
    }
    if (showCopy) {
        AlertDialog(
            onDismissRequest = { showCopy = false },
            title = { Text(stringResource(R.string.dialog_copy_areas_title)) },
            text = {
                Column {
                    otherLists.forEach { list ->
                        Text(
                            list.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCopy = false; viewModel.copyFrom(list.id) }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showCopy = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AreaEditDialog(
    title: String,
    initial: ShopAreaModel?,
    onSave: (String, String?, List<String>) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color) }
    var keywords by remember { mutableStateOf(initial?.keywords?.joinToString(", ").orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.field_colour), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AREA_COLORS.forEach { hex ->
                        val swatch = parseHexColor(hex) ?: Color.Gray
                        val selected = color?.equals(hex, ignoreCase = true) == true
                        Box(
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .size(32.dp)
                                .background(swatch, CircleShape)
                                .then(
                                    if (selected) {
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { color = hex },
                        )
                    }
                }
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text(stringResource(R.string.field_keywords)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val kw = keywords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (name.isNotBlank()) onSave(name, color, kw)
                },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            if (onDelete != null) {
                TextButton(onClick = onDelete) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
