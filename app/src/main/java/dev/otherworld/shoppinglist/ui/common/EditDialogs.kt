package dev.otherworld.shoppinglist.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import dev.otherworld.shoppinglist.R

/**
 * Generic single-field text-entry dialog used for creating/renaming lists and similar.
 *
 * With [blankValue] the field can be left empty and that is used as the text, so a new list
 * doesn't have to be named (#34). It's shown under the field while it's empty, as a placeholder
 * only shows once a labelled field has focus.
 */
@Composable
fun TextEntryDialog(
    title: String,
    label: String,
    initialValue: String = "",
    confirmLabel: String = stringResource(R.string.action_save),
    blankValue: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialValue) }
    val result = text.trim().ifEmpty { blankValue.orEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                placeholder = blankValue?.let { { Text(it) } },
                supportingText = blankValue?.takeIf { text.isBlank() }?.let {
                    { Text(stringResource(R.string.field_blank_name, it)) }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (result.isNotBlank()) onConfirm(result)
                }),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (result.isNotBlank()) onConfirm(result) },
                enabled = result.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Simple confirmation dialog for destructive actions. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = stringResource(R.string.action_delete),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
