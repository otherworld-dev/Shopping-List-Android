package dev.otherworld.shoppinglist.ui

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewModelScope
import androidx.navigation.navArgument
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.otherworld.shoppinglist.data.auth.Account
import dev.otherworld.shoppinglist.data.auth.CredentialStore
import dev.otherworld.shoppinglist.data.sync.RealtimeController
import dev.otherworld.shoppinglist.data.sync.SyncEngine
import dev.otherworld.shoppinglist.data.theme.ServerTheme
import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.data.tls.CertAlertController
import dev.otherworld.shoppinglist.data.tls.CertInfo
import dev.otherworld.shoppinglist.ui.common.CertTrustDialog
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import dev.otherworld.shoppinglist.ui.areas.ManageAreasScreen
import dev.otherworld.shoppinglist.ui.items.ItemsScreen
import dev.otherworld.shoppinglist.ui.lists.ListsScreen
import dev.otherworld.shoppinglist.ui.login.LoginScreen
import dev.otherworld.shoppinglist.ui.share.SharingScreen
import dev.otherworld.shoppinglist.ui.tags.ManageTagsScreen
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
}

@HiltViewModel
class AppViewModel @Inject constructor(
    credentialStore: CredentialStore,
    serverTheme: ServerTheme,
    private val realtime: RealtimeController,
    private val syncEngine: SyncEngine,
    private val certAlerts: CertAlertController,
) : ViewModel() {
    val account: StateFlow<Account?> = credentialStore.accountFlow
    val certAlert: StateFlow<CertInfo?> = certAlerts.alert
    val suppressedCert: StateFlow<CertInfo?> = certAlerts.suppressed

    init {
        // On login: adopt the server's brand colour and open the real-time push connection.
        account
            .onEach {
                if (it != null) {
                    serverTheme.refresh()
                    realtime.ensureConnected()
                } else {
                    serverTheme.clear()
                    certAlerts.onLoggedOut()
                }
            }
            .launchIn(viewModelScope)

        // After the user approves a changed certificate, drain queued edits, reopen push, and
        // pull fresh data so the visible screen isn't stale.
        certAlerts.retry
            .onEach {
                syncEngine.requestSync()
                realtime.ensureConnected()
                realtime.signalRefresh()
            }
            .launchIn(viewModelScope)
    }

    fun onTrustCert() = certAlerts.trust()
    fun onDismissCert() = certAlerts.dismiss()
    fun reviewCert() = certAlerts.review()
}

@Composable
fun AppRoot(
    modifier: Modifier = Modifier,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val certAlert by viewModel.certAlert.collectAsStateWithLifecycle()
    val suppressedCert by viewModel.suppressedCert.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    Column(modifier.fillMaxSize()) {
        // Persistent banner when the user dismissed a changed-certificate prompt but sync is
        // still failing — so the app never silently stops syncing without any indication.
        if (account != null && suppressedCert != null) {
            CertPausedBanner(onClick = viewModel::reviewCert)
        }

    NavHost(
        navController = navController,
        startDestination = if (account == null) Routes.LOGIN else Routes.HOME,
        modifier = Modifier.weight(1f),
    ) {
        composable(Routes.LOGIN) { LoginScreen() }

        composable(Routes.HOME) {
            ListsScreen(
                onOpenList = { list ->
                    val title = Uri.encode(list.title)
                    navController.navigate(
                        "items/${list.id}?title=$title&canWrite=${list.canWrite}",
                    )
                },
                onShareList = { list ->
                    navController.navigate("share/${list.id}?title=${Uri.encode(list.title)}")
                },
                onManageTags = { navController.navigate("tags") },
            )
        }

        composable(
            route = "items/{listId}?title={title}&canWrite={canWrite}",
            arguments = listOf(
                navArgument("listId") { type = NavType.LongType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("canWrite") { type = NavType.BoolType; defaultValue = true },
            ),
        ) { backStackEntry ->
            val listId = backStackEntry.arguments?.getLong("listId") ?: 0L
            val title = backStackEntry.arguments?.getString("title").orEmpty()
            ItemsScreen(
                onBack = { navController.popBackStack() },
                onManageAreas = { navController.navigate("areas/$listId?title=$title") },
            )
        }

        composable(
            route = "areas/{listId}?title={title}",
            arguments = listOf(
                navArgument("listId") { type = NavType.LongType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            ManageAreasScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = "share/{listId}?title={title}",
            arguments = listOf(
                navArgument("listId") { type = NavType.LongType },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
            ),
        ) {
            SharingScreen(onBack = { navController.popBackStack() })
        }

        composable("tags") {
            ManageTagsScreen(onBack = { navController.popBackStack() })
        }
    }
    } // Column

    // A server certificate that changed after login: prompt to re-approve, app-wide. Gated on
    // an active account so a stale alert can't outlive logout.
    if (account != null) {
        certAlert?.let { info ->
            CertTrustDialog(
                info = info,
                showBrowserNote = false,
                onTrust = viewModel::onTrustCert,
                onDismiss = viewModel::onDismissCert,
            )
        }
    }

    // React to login/logout from anywhere by switching the active destination.
    LaunchedEffect(account) {
        if (account != null) {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.LOGIN) { inclusive = true }
                launchSingleTop = true
            }
        } else {
            navController.navigate(Routes.LOGIN) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }
}

@Composable
private fun CertPausedBanner(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.cert_paused_banner),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
