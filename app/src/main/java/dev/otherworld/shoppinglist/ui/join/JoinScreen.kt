package dev.otherworld.shoppinglist.ui.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.domain.model.Permission
import dev.otherworld.shoppinglist.ui.common.CertTrustDialog
import dev.otherworld.shoppinglist.ui.common.asString

@Composable
fun JoinScreen(
    onOpened: (OpenedList) -> Unit,
    onClose: () -> Unit,
    viewModel: JoinViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var password by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.opened) { state.opened?.let(onOpened) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val preview = state.preview
        when {
            state.checking -> {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.join_checking))
            }
            state.error != null && preview == null -> {
                Text(state.error!!.asString(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) }
            }
            preview != null -> {
                // A password link has no title until it's unlocked: the server heads the screen alone.
                Text(
                    preview.title.ifBlank { state.serverLabel },
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                )
                if (preview.title.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(state.serverLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(
                        when {
                            preview.passwordRequired -> R.string.join_has_password
                            preview.permission >= Permission.WRITE -> R.string.join_can_edit
                            else -> R.string.join_view_only
                        },
                    ),
                    textAlign = TextAlign.Center,
                )
                if (preview.passwordRequired) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.join_password_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = state.passwordWrong,
                        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                    )
                    if (state.passwordWrong) {
                        Text(stringResource(R.string.join_password_wrong), color = MaterialTheme.colorScheme.error)
                    }
                }
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it.asString(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.open(password.takeIf { preview.passwordRequired }) },
                    enabled = !state.opening && (!preview.passwordRequired || password.isNotBlank()),
                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                ) {
                    if (state.opening) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.join_open))
                    }
                }
                TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) }
            }
        }
    }

    state.pendingCert?.let { cert ->
        CertTrustDialog(
            info = cert,
            showBrowserNote = false,
            onTrust = viewModel::trustPendingCert,
            onDismiss = viewModel::dismissPendingCert,
        )
    }
}
