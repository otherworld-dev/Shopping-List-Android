package dev.otherworld.shoppinglist.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A Switch whose off state can be seen. Material draws the off thumb and border in the outline
 * colour, which is the same grey as the cards and the off track here, so an off switch all but
 * disappeared (Shopping-List#61); they use the secondary text colour instead.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val offColour = MaterialTheme.colorScheme.onSurfaceVariant
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(uncheckedThumbColor = offColour, uncheckedBorderColor = offColour),
    )
}
