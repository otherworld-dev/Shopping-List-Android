package dev.otherworld.shoppinglist.ui.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
    var pasted by remember { mutableStateOf(false) }
    val submit = { viewModel.submit(input, server) }

    LaunchedEffect(state.link) {
        state.link?.let {
            viewModel.onLinkHandled()
            onLink(it)
        }
    }
    // Only after a paste: typing a host whose first part looks like a code ("nextcamp.example.com")
    // mustn't send the rest of it into the Server field.
    LaunchedEffect(showServer) {
        if (showServer && server.isEmpty() && pasted) serverFocus.requestFocus()
    }

    // Scrolls, so Continue stays reachable in landscape with the keyboard and Server field up;
    // the Box keeps it centred while it fits.
    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.join_entry_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.join_entry_help), textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = input,
            onValueChange = {
                pasted = looksPasted(input, it)
                input = it
            },
            label = { Text(stringResource(R.string.join_entry_input_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                autoCorrectEnabled = false,
                imeAction = if (showServer) ImeAction.Next else ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { submit() }, onNext = { serverFocus.requestFocus() }),
            isError = state.error != null,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
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
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().focusRequester(serverFocus),
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
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        ) {
            if (state.busy) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.join_entry_continue))
            }
        }
        TextButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) }
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

/** Whether an edit added several characters at once, as a paste does and typing doesn't. */
internal fun looksPasted(before: String, after: String): Boolean = after.length - before.length > 1
