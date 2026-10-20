package dev.otherworld.shoppinglist.ui.items

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.data.prefs.Density
import dev.otherworld.shoppinglist.domain.sort.BoughtSort
import dev.otherworld.shoppinglist.domain.sort.OpenSort

/** What the Display sheet shows: the phone's own choices for how every list looks. */
internal data class DisplayChoices(
    val density: Density,
    val showAreaHeadings: Boolean,
    val showImages: Boolean,
    val imagesServerTooOld: Boolean,
    val openSort: OpenSort,
    val boughtSort: BoughtSort,
)

/**
 * How lists look, gathered in one place (#24) instead of spread through the list's menu and
 * Settings. Each choice applies as it's made, so the list behind the sheet shows the result
 * and the sheet stays open for the next one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DisplaySheet(
    choices: DisplayChoices,
    onDensity: (Density) -> Unit,
    onShowAreaHeadings: (Boolean) -> Unit,
    onShowImages: (Boolean) -> Unit,
    onOpenSort: (OpenSort) -> Unit,
    onBoughtSort: (BoughtSort) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp),
        ) {
            Text(stringResource(R.string.display_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            SheetCaption(stringResource(R.string.display_rows))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                val options = listOf(Density.COMFY to R.string.display_rows_comfortable, Density.COMPACT to R.string.display_rows_compact)
                options.forEachIndexed { i, (density, label) ->
                    SegmentedButton(
                        selected = choices.density == density,
                        onClick = { onDensity(density) },
                        shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                    ) { Text(stringResource(label)) }
                }
            }

            Spacer(Modifier.height(8.dp))
            // A to Z has no headings to hide, so the switch waits for an area sort.
            SheetSwitch(
                stringResource(R.string.display_area_headings),
                checked = choices.showAreaHeadings,
                enabled = choices.openSort != OpenSort.ALPHA,
                onChange = onShowAreaHeadings,
            )
            SheetHint(stringResource(R.string.display_area_headings_hint))
            SheetSwitch(stringResource(R.string.settings_show_item_images), checked = choices.showImages, onChange = onShowImages)
            SheetHint(stringResource(R.string.settings_item_images_hint))
            if (choices.imagesServerTooOld) SheetHint(stringResource(R.string.settings_item_images_old_server))

            SheetCaption(stringResource(R.string.menu_sort_items))
            Column(Modifier.selectableGroup()) {
                SheetRadio(stringResource(R.string.sort_by_area), choices.openSort == OpenSort.AREA) { onOpenSort(OpenSort.AREA) }
                SheetRadio(stringResource(R.string.sort_by_area_a_to_z), choices.openSort == OpenSort.AREA_ALPHA) { onOpenSort(OpenSort.AREA_ALPHA) }
                SheetRadio(stringResource(R.string.sort_a_to_z), choices.openSort == OpenSort.ALPHA) { onOpenSort(OpenSort.ALPHA) }
            }

            SheetCaption(stringResource(R.string.menu_sort_checked))
            Column(Modifier.selectableGroup()) {
                SheetRadio(stringResource(R.string.sort_by_area), choices.boughtSort == BoughtSort.AREA) { onBoughtSort(BoughtSort.AREA) }
                SheetRadio(stringResource(R.string.sort_a_to_z), choices.boughtSort == BoughtSort.ALPHA) { onBoughtSort(BoughtSort.ALPHA) }
                SheetRadio(stringResource(R.string.sort_most_recent), choices.boughtSort == BoughtSort.RECENT) { onBoughtSort(BoughtSort.RECENT) }
            }
        }
    }
}

@Composable
private fun SheetCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun SheetHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun SheetSwitch(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, onValueChange = onChange, role = Role.Switch)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun SheetRadio(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
