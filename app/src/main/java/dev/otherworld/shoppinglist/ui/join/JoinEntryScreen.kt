package dev.otherworld.shoppinglist.ui.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.domain.guest.ShareLink
import dev.otherworld.shoppinglist.domain.guest.isBareCode
import dev.otherworld.shoppinglist.ui.common.CertTrustDialog
import dev.otherworld.shoppinglist.ui.common.asString

@Composable
fun JoinEntryScreen(
    onLink: (ShareLink) -> Unit,
    onClose: () -> Unit,
    viewModel: JoinEntryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var server by rememberSaveable { mutableStateOf("") }
    // The Server field is for a code on its own; it stays while it has text.
    val showServer = server.isNotEmpty() || isBareCode(input)
    val serverFocus = remember { FocusRequester() }
    val submit = { viewModel.submit(input, server) }

    LaunchedEffect(state.link) {
        state.link?.let {
            viewModel.onLinkHandled()
            onLink(it)
        }
    }
    LaunchedEffect(showServer) {
        if (showServer && server.isEmpty()) serverFocus.requestFocus()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.join_entry_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.join_entry_help), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            label = { Text(stringResource(R.string.join_entry_input_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false,
                imeAction = if (showServer) ImeAction.Next else ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { submit() }, onNext = { serverFocus.requestFocus() }),
            isError = state.error != null,
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        )
        if (showServer) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = server,
                onValueChange = { server = it },
                label = { Text(stringResource(R.string.join_entry_server_label)) },
                placeholder = { Text(stringResource(R.string.join_entry_server_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).focusRequester(serverFocus),
            )
        }
        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it.asString(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = submit,
            enabled = input.isNotBlank() && !state.busy,
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.join_entry_continue))
            }
        }
        TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) }
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
