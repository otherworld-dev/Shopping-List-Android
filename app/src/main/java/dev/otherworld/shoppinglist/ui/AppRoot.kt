package dev.otherworld.shoppinglist.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import dev.otherworld.shoppinglist.data.theme.ServerTheme
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
    realtime: RealtimeController,
) : ViewModel() {
    val account: StateFlow<Account?> = credentialStore.accountFlow

    init {
        // On login: adopt the server's brand colour and open the real-time push connection.
        account
            .onEach {
                if (it != null) {
                    serverTheme.refresh()
                    realtime.ensureConnected()
                } else {
                    serverTheme.clear()
                }
            }
            .launchIn(viewModelScope)
    }
}

@Composable
fun AppRoot(
    modifier: Modifier = Modifier,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val account by viewModel.account.collectAsStateWithLifecycle()
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = if (account == null) Routes.LOGIN else Routes.HOME,
        modifier = modifier,
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
