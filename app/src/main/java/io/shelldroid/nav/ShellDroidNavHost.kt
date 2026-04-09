package io.shelldroid.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.shelldroid.feature.hosts.HostEditScreen
import io.shelldroid.feature.hosts.HostKeyDialogHost
import io.shelldroid.feature.hosts.HostsListViewModel
import io.shelldroid.feature.hosts.HostsScreen
import io.shelldroid.feature.hosts.tofu.ComposeHostKeyPrompter
import io.shelldroid.feature.identities.IdentitiesScreen
import io.shelldroid.feature.identities.IdentityEditScreen
import io.shelldroid.feature.portforward.PortForwardEditScreen
import io.shelldroid.feature.portforward.PortForwardsScreen
import io.shelldroid.feature.snippets.SnippetEditScreen
import io.shelldroid.feature.snippets.SnippetsScreen
import io.shelldroid.feature.terminal.TerminalLaunchRequest
import io.shelldroid.feature.terminal.TerminalScreen
import io.shelldroid.ui.settings.SettingsScreen

object Routes {
    const val HOSTS = "hosts"
    const val HOST_EDIT = "host/edit?id={id}"
    fun hostEdit(id: String? = null): String = "host/edit?id=${id.orEmpty()}"

    const val IDENTITIES = "identities"
    const val IDENTITY_EDIT = "identity/edit?id={id}"
    fun identityEdit(id: String? = null): String = "identity/edit?id=${id.orEmpty()}"

    const val TERMINAL = "terminal/{hostId}"
    fun terminal(hostId: String): String = "terminal/$hostId"

    const val SNIPPETS = "snippets"
    const val SNIPPET_EDIT = "snippet/edit?id={id}"
    fun snippetEdit(id: String? = null): String = "snippet/edit?id=${id.orEmpty()}"

    const val PORTFORWARDS = "portforwards"
    const val PORTFORWARD_EDIT = "portforward/edit?id={id}"
    fun portForwardEdit(id: String? = null): String = "portforward/edit?id=${id.orEmpty()}"

    const val SETTINGS = "settings"
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface HostKeyPrompterEntryPoint {
    fun composeHostKeyPrompter(): ComposeHostKeyPrompter
}

@Composable
private fun rememberHostKeyPrompter(): ComposeHostKeyPrompter {
    val context = LocalContext.current.applicationContext
    val entryPoint = EntryPointAccessors.fromApplication(
        context,
        HostKeyPrompterEntryPoint::class.java,
    )
    return entryPoint.composeHostKeyPrompter()
}

@Composable
fun ShellDroidNavHost(navController: NavHostController = rememberNavController()) {
    // Mount TOFU dialog host above NavHost so prompts overlay any screen.
    val prompter = rememberHostKeyPrompter()
    HostKeyDialogHost(prompter)

    // Observe the foreground-service notification "Abrir terminal" deep
    // link. When MainActivity receives an intent with EXTRA_HOST_ID it
    // emits on TerminalLaunchRequest; we navigate to the terminal for
    // that host. The underlying TerminalBridgeRegistry returns the
    // already-running bridge so the screen reattaches instantly without
    // touching the libssh session.
    LaunchedEffect(Unit) {
        TerminalLaunchRequest.requests.collect { hostId ->
            navController.navigate(Routes.terminal(hostId)) {
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.HOSTS) {
        composable(Routes.HOSTS) {
            // Hoist HostsListViewModel so we can observe Connected and navigate.
            val vm: HostsListViewModel = hiltViewModel()
            val connectState by vm.connectState.collectAsState()
            LaunchedEffect(connectState) {
                val s = connectState
                if (s is HostsListViewModel.ConnectState.Connected) {
                    navController.navigate(Routes.terminal(s.hostId))
                    vm.resetConnectState()
                }
            }
            HostsScreen(
                onAddHost = { navController.navigate(Routes.hostEdit()) },
                onEditHost = { id -> navController.navigate(Routes.hostEdit(id)) },
                onOpenIdentities = { navController.navigate(Routes.IDENTITIES) },
                onOpenSnippets = { navController.navigate(Routes.SNIPPETS) },
                onOpenPortForwards = { navController.navigate(Routes.PORTFORWARDS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                prompter = null, // mounted at NavHost level
                viewModel = vm,
            )
        }
        composable(Routes.SNIPPETS) {
            SnippetsScreen(
                onAddSnippet = { navController.navigate(Routes.snippetEdit()) },
                onEditSnippet = { id -> navController.navigate(Routes.snippetEdit(id)) },
                onRunSnippet = { /* TODO wire to terminal */ },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.SNIPPET_EDIT,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStack ->
            val id = backStack.arguments?.getString("id")?.takeIf { it.isNotEmpty() }
            SnippetEditScreen(
                snippetId = id,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.HOST_EDIT,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStack ->
            val id = backStack.arguments?.getString("id")?.takeIf { it.isNotEmpty() }
            HostEditScreen(
                hostId = id,
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.IDENTITIES) {
            IdentitiesScreen(
                onAddIdentity = { navController.navigate(Routes.identityEdit()) },
                onEditIdentity = { id -> navController.navigate(Routes.identityEdit(id)) },
            )
        }
        composable(
            route = Routes.IDENTITY_EDIT,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStack ->
            val id = backStack.arguments?.getString("id")?.takeIf { it.isNotEmpty() }
            IdentityEditScreen(
                identityId = id,
                onDone = { navController.popBackStack() },
            )
        }
        composable(Routes.PORTFORWARDS) {
            PortForwardsScreen(
                onAdd = { navController.navigate(Routes.portForwardEdit()) },
                onEdit = { id -> navController.navigate(Routes.portForwardEdit(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.PORTFORWARD_EDIT,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStack ->
            val id = backStack.arguments?.getString("id")?.takeIf { it.isNotEmpty() }
            PortForwardEditScreen(
                portForwardId = id,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.TERMINAL) { backStack ->
            val hostId = backStack.arguments?.getString("hostId").orEmpty()
            TerminalScreen(
                hostId = hostId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}
