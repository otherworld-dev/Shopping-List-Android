package dev.otherworld.shoppinglist.ui.items

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.data.prefs.Density
import dev.otherworld.shoppinglist.domain.model.ItemModel
import dev.otherworld.shoppinglist.domain.model.ShopAreaModel
import dev.otherworld.shoppinglist.domain.model.ShoppingListModel
import dev.otherworld.shoppinglist.domain.sort.BoughtSort
import dev.otherworld.shoppinglist.domain.sort.CollapsedAreas
import dev.otherworld.shoppinglist.domain.sort.OpenSort
import dev.otherworld.shoppinglist.domain.sort.groupOpenItems
import dev.otherworld.shoppinglist.domain.sort.sortBought
import dev.otherworld.shoppinglist.domain.text.SmartInput
import dev.otherworld.shoppinglist.domain.text.formatListAsText
import dev.otherworld.shoppinglist.ui.common.PollEffect
import dev.otherworld.shoppinglist.ui.common.parseHexColor
import dev.otherworld.shoppinglist.ui.theme.LocalRowShade
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemsScreen(
    onBack: () -> Unit,
    onManageAreas: () -> Unit,
    viewModel: ItemsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PollEffect { viewModel.poll() }
    var overflow by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<ItemModel?>(null) }
    var showReorderAreas by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.list_copied)
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeNotice()
        }
    }

    val areaById = remember(state.areas) { state.areas.associateBy { it.id } }
    val locale = Locale.getDefault()
    var rows by remember {
        mutableStateOf(buildRows(state.items, state.areas, state.openSort, state.boughtSort, state.collapsedAreas, locale))
    }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.items, state.areas, state.openSort, state.boughtSort, state.collapsedAreas) {
        if (!dragging) rows = buildRows(state.items, state.areas, state.openSort, state.boughtSort, state.collapsedAreas, locale)
    }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        rows = rows.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    fun commitReorder() {
        dragging = false
        val plan = planReorder(rows)
        plan.areaMoves.forEach { (item, areaId) -> viewModel.moveToArea(item, areaId) }
        viewModel.reorder(plan.orderedIds)
    }

    Box(Modifier.fillMaxSize().systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
      Column(
        Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .padding(horizontal = 16.dp),
      ) {
        // Title header
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
            Text(
                state.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            Box {
                IconButton(onClick = { overflow = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more))
                }
                DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_refresh)) }, onClick = { overflow = false; viewModel.refresh() })
                    // Display-only, so available even in read-only lists. Labelled by what it
                    // switches to, matching the app's other menu-verb entries.
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (state.density == Density.COMPACT) R.string.menu_rows_comfortable
                                    else R.string.menu_rows_compact,
                                ),
                            )
                        },
                        onClick = { overflow = false; viewModel.toggleDensity() },
                    )
                    // Read-only, so offered on shared read-only lists too. Writes the outstanding
                    // items in the same shape the add box parses, so a list round-trips through
                    // a chat message (mirrors the web app's "Copy list as text").
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_copy_as_text)) },
                        enabled = state.items.any { !it.checked },
                        onClick = {
                            overflow = false
                            clipboard.setText(AnnotatedString(formatListAsText(state.items)))
                            // Android 13+ shows its own system "Copied" confirmation.
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                                scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                            }
                        },
                    )
                    // Display-only, like density, so offered on read-only lists too. One
                    // choice for all lists, remembered on this device (mirrors the web app).
                    HorizontalDivider()
                    MenuCaption(stringResource(R.string.menu_sort_items))
                    SortRadioItem(stringResource(R.string.sort_by_area), state.openSort == OpenSort.AREA) {
                        overflow = false; viewModel.setOpenSort(OpenSort.AREA)
                    }
                    SortRadioItem(stringResource(R.string.sort_by_area_a_to_z), state.openSort == OpenSort.AREA_ALPHA) {
                        overflow = false; viewModel.setOpenSort(OpenSort.AREA_ALPHA)
                    }
                    SortRadioItem(stringResource(R.string.sort_a_to_z), state.openSort == OpenSort.ALPHA) {
                        overflow = false; viewModel.setOpenSort(OpenSort.ALPHA)
                    }
                    HorizontalDivider()
                    MenuCaption(stringResource(R.string.menu_sort_checked))
                    SortRadioItem(stringResource(R.string.sort_by_area), state.boughtSort == BoughtSort.AREA) {
                        overflow = false; viewModel.setBoughtSort(BoughtSort.AREA)
                    }
                    SortRadioItem(stringResource(R.string.sort_a_to_z), state.boughtSort == BoughtSort.ALPHA) {
                        overflow = false; viewModel.setBoughtSort(BoughtSort.ALPHA)
                    }
                    SortRadioItem(stringResource(R.string.sort_most_recent), state.boughtSort == BoughtSort.RECENT) {
                        overflow = false; viewModel.setBoughtSort(BoughtSort.RECENT)
                    }
                    if (state.canWrite) {
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_manage_areas)) }, onClick = { overflow = false; onManageAreas() })
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_reorder_areas)) },
                            enabled = state.areas.size >= 2,
                            onClick = { overflow = false; showReorderAreas = true },
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_restore_checked)) }, onClick = { overflow = false; viewModel.uncheckAll() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.menu_clear_checked)) }, onClick = { overflow = false; viewModel.clearChecked() })
                    }
                }
            }
        }

        // The list "card"
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 16.dp).imePadding(),
        ) {
            Column(Modifier.fillMaxSize()) {
                if (state.canWrite) {
                    AddItemRow(onAdd = { name -> viewModel.addItem(name, null) })
                    RowDivider()
                }
                when {
                    state.loading && state.items.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    state.items.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                if (state.canWrite) stringResource(R.string.items_empty_writable) else stringResource(R.string.items_empty_readonly),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp),
                            )
                        }
                    else -> Box(Modifier.fillMaxSize()) {
                        LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                            items(rows, key = { it.key }) { row ->
                                ReorderableItem(reorderState, key = row.key) { _ ->
                                    when (row) {
                                        is Row.AreaHeaderRow -> SectionHeader(
                                            name = row.area?.name ?: stringResource(R.string.section_other),
                                            color = parseHexColor(row.area?.color),
                                            count = row.count,
                                            collapsed = row.collapsed,
                                            onToggle = { viewModel.toggleCollapsed(row.area?.id) },
                                        )
                                        is Row.CheckedHeaderRow -> SectionHeader(stringResource(R.string.section_checked), null, row.count)
                                        is Row.ItemRowData -> {
                                            val dragModifier = if (row.draggable && state.canWrite) {
                                                Modifier.longPressDraggableHandle(
                                                    onDragStarted = { dragging = true },
                                                    onDragStopped = { commitReorder() },
                                                )
                                            } else {
                                                Modifier
                                            }
                                            ItemRow(
                                                item = row.item,
                                                area = row.item.shopAreaId?.let { areaById[it] },
                                                showAreaName = row.showAreaName,
                                                enabled = state.canWrite,
                                                alt = row.alt,
                                                onToggle = { viewModel.toggleCheck(row.item) },
                                                onClick = { if (state.canWrite) editTarget = row.item },
                                                compact = state.density == Density.COMPACT,
                                                handleModifier = dragModifier,
                                            )
                                            RowDivider()
                                        }
                                    }
                                }
                            }
                        }
                        PinnedSectionHeader(rows, lazyListState, onToggleArea = { viewModel.toggleCollapsed(it) })
                    }
                }
            }
        }
      }
      SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    editTarget?.let { item ->
        ItemEditDialog(
            item = item,
            areas = state.areas,
            moveTargets = state.otherLists,
            onSave = { name, qty, areaId -> editTarget = null; viewModel.editItem(item, name, qty, areaId) },
            onMove = { target -> editTarget = null; viewModel.moveItem(item, target) },
            onDelete = { editTarget = null; viewModel.deleteItem(item) },
            onDismiss = { editTarget = null },
        )
    }

    if (showReorderAreas) {
        ReorderAreasSheet(
            areas = state.areas,
            onReorder = viewModel::reorderAreas,
            onDismiss = { showReorderAreas = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReorderAreasSheet(
    areas: List<ShopAreaModel>,
    onReorder: (List<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    // Seed once from the areas at open time (no key) so a background poll refresh can't reset an
    // in-progress drag. Every reorder — a drag drop or an accessibility action — commits
    // immediately, so the order is durable and survives rotation / process death (no reliance on
    // a dismiss-time commit).
    var order by remember { mutableStateOf(areas.sortedBy { it.sortOrder }) }
    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        order = order.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    val moveUpLabel = stringResource(R.string.a11y_move_up)
    val moveDownLabel = stringResource(R.string.a11y_move_down)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
        ) {
            Text(
                stringResource(R.string.reorder_areas_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                stringResource(R.string.reorder_areas_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
            ) {
                itemsIndexed(order, key = { _, area -> area.id }) { index, area ->
                    ReorderableItem(reorderState, key = area.id) { _ ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Screen readers can't perform the drag gesture, so expose the
                                // reorder as Move up / Move down custom actions on the row.
                                .semantics {
                                    customActions = buildList {
                                        if (index > 0) add(
                                            CustomAccessibilityAction(moveUpLabel) {
                                                val moved = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                                                order = moved
                                                onReorder(moved.map { it.id })
                                                true
                                            },
                                        )
                                        if (index < order.size - 1) add(
                                            CustomAccessibilityAction(moveDownLabel) {
                                                val moved = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                                                order = moved
                                                onReorder(moved.map { it.id })
                                                true
                                            },
                                        )
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(16.dp)
                                    .background(parseHexColor(area.color) ?: MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                area.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = stringResource(R.string.cd_drag_reorder),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.draggableHandle(
                                    onDragStopped = { onReorder(order.map { it.id }) },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.fillMaxWidth().size(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
}

@Composable
private fun MenuCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun SortRadioItem(label: String, selected: Boolean, onSelect: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { RadioButton(selected = selected, onClick = null) },
        onClick = onSelect,
    )
}

/**
 * A copy of the section the list is currently scrolled into, drawn over the top of the list.
 * Item rows no longer repeat the area name, so this keeps it on screen for groups that run
 * longer than the viewport. It is an overlay rather than a real `stickyHeader` because the
 * headers must stay ordinary list entries — `commitReorder` derives an item's area from the
 * header it was dropped under, so pulling them out of the list would break drag-to-recategorise.
 */
@Composable
internal fun PinnedSectionHeader(rows: List<Row>, lazyListState: LazyListState, onToggleArea: (Long?) -> Unit) {
    val pinned by remember(rows) {
        derivedStateOf {
            rows.take(lazyListState.firstVisibleItemIndex + 1)
                .lastOrNull { it is Row.AreaHeaderRow || it is Row.CheckedHeaderRow }
        }
    }
    when (val header = pinned) {
        is Row.AreaHeaderRow ->
            SectionHeader(
                name = header.area?.name ?: stringResource(R.string.section_other),
                color = parseHexColor(header.area?.color),
                count = header.count,
                collapsed = header.collapsed,
                onToggle = { onToggleArea(header.area?.id) },
            )
        is Row.CheckedHeaderRow ->
            SectionHeader(stringResource(R.string.section_checked), null, header.count)
        else -> Unit
    }
}

@Composable
internal fun SectionHeader(
    name: String,
    color: Color?,
    count: Int,
    collapsed: Boolean = false,
    onToggle: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // Card-coloured rather than a filled contrasting bar: the stripe and small caps
            // are enough to mark a section. Opaque (not transparent) so the pinned copy can
            // sit over scrolling rows.
            .background(MaterialTheme.colorScheme.surface)
            .heightIn(min = 44.dp)
            .let { modifier ->
                if (onToggle == null) modifier
                else modifier.clickable(
                    onClickLabel = stringResource(if (collapsed) R.string.a11y_expand_area else R.string.a11y_collapse_area),
                    onClick = onToggle,
                )
            }
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Fixed height, not fillMaxHeight: this is also drawn as the pinned copy inside a
        // fillMaxSize Box, where filling the height would blow the header up to cover the list.
        Box(Modifier.width(4.dp).height(28.dp).background(color ?: MaterialTheme.colorScheme.onSurfaceVariant))
        if (onToggle != null) {
            Spacer(Modifier.width(4.dp))
            Icon(
                if (collapsed) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            name.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ItemRow(
    item: ItemModel,
    area: ShopAreaModel?,
    showAreaName: Boolean,
    enabled: Boolean,
    alt: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
    compact: Boolean = false,
    handleModifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (alt) LocalRowShade.current else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val checkbox = @Composable {
            Checkbox(
                checked = item.checked,
                onCheckedChange = { onToggle() },
                enabled = enabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
        if (compact) {
            // The 48dp min touch target is what makes rows tall. Shrink it to a 36dp box in
            // compact mode -- still a workable one-handed tap point, but far shorter rows.
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
                    checkbox()
                }
            }
        } else {
            checkbox()
        }
        Spacer(Modifier.width(8.dp))
        Text(
            (item.quantity?.takeIf { it.isNotBlank() } ?: "1"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            item.name,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
            color = if (item.checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).then(handleModifier),
        )
        Spacer(Modifier.width(8.dp))
        AreaTag(area, showName = showAreaName)
    }
}

@Composable
private fun AreaTag(area: ShopAreaModel?, showName: Boolean) {
    val color = parseHexColor(area?.color) ?: MaterialTheme.colorScheme.onSurfaceVariant
    val name = area?.name ?: stringResource(R.string.section_other)
    Row(verticalAlignment = Alignment.CenterVertically) {
        // With the name hidden the dot is the only cue on the row, so it carries the
        // area name for TalkBack instead of leaving it as colour-only.
        Box(
            Modifier
                .size(8.dp)
                .background(color, CircleShape)
                .semantics { contentDescription = name },
        )
        if (showName) {
            Spacer(Modifier.width(6.dp))
            Text(
                name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 110.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddItemRow(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { new ->
                // A multi-line paste is a whole list: add one item per line straight away, like
                // the web app's paste handler. A single (possibly newline-padded) line just lands
                // in the field for editing. singleLine only shapes the IME/layout — pasted
                // newlines do arrive here.
                if (new.contains('\n')) {
                    val lines = SmartInput.splitLines(new)
                    if (lines.size > 1) {
                        onAdd(new)
                        text = ""
                    } else {
                        text = lines.firstOrNull().orEmpty()
                    }
                } else {
                    text = new
                }
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = MaterialTheme.colorScheme.onSurface)),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(
                        stringResource(R.string.item_add_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun ItemEditDialog(
    item: ItemModel,
    areas: List<ShopAreaModel>,
    moveTargets: List<ShoppingListModel>,
    onSave: (String, String?, Long?) -> Unit,
    onMove: (ShoppingListModel) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(item.name) }
    var quantity by remember { mutableStateOf(item.quantity.orEmpty()) }
    var areaId by remember { mutableStateOf(item.shopAreaId) }
    var areaMenu by remember { mutableStateOf(false) }
    var moveMenu by remember { mutableStateOf(false) }
    val areaName = areas.firstOrNull { it.id == areaId }?.name ?: stringResource(R.string.item_no_area)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_edit_item_title)) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(stringResource(R.string.field_quantity)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                Box {
                    TextButton(onClick = { areaMenu = true }) { Text(stringResource(R.string.item_area_label, areaName)) }
                    DropdownMenu(expanded = areaMenu, onDismissRequest = { areaMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.item_no_area)) }, onClick = { areaId = null; areaMenu = false })
                        areas.sortedWith(compareBy({ it.sortOrder }, { it.id })).forEach { area ->
                            DropdownMenuItem(text = { Text(area.name) }, onClick = { areaId = area.id; areaMenu = false })
                        }
                    }
                }
                // Moves right away when a target is picked (online-only), like the web app's
                // per-row "Move to list" menu — it isn't part of Save.
                if (moveTargets.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { moveMenu = true }) { Text(stringResource(R.string.item_move_to_list)) }
                        DropdownMenu(expanded = moveMenu, onDismissRequest = { moveMenu = false }) {
                            moveTargets.forEach { list ->
                                DropdownMenuItem(text = { Text(list.title) }, onClick = { moveMenu = false; onMove(list) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name, quantity, areaId) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
