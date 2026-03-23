package br.ufu.chatapp.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.ufu.chatapp.MainActivity
import br.ufu.chatapp.ui.auth.AuthViewModel
import br.ufu.chatapp.ui.auth.LoginScreen
import br.ufu.chatapp.ui.auth.RegisterScreen
import br.ufu.chatapp.ui.chat.ChatScreen
import br.ufu.chatapp.ui.chat.ChatViewModel
import br.ufu.chatapp.ui.contacts.ContactsScreen
import br.ufu.chatapp.ui.contacts.ContactsViewModel
import br.ufu.chatapp.ui.conversations.ConversationsScreen
import br.ufu.chatapp.ui.conversations.ConversationsViewModel
import br.ufu.chatapp.ui.profile.ProfileScreen
import br.ufu.chatapp.ui.profile.ProfileViewModel

@Composable
fun ChatAppRoot() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val authVm: AuthViewModel = hiltViewModel()
    val sessionValid by authVm.sessionValid.collectAsState()
    val pendingConversationId by MainActivity.pendingConversationId.collectAsState()
    val start = if (authVm.isLoggedIn()) Nav.Conversations.route else Nav.Login.route
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        authVm.refreshSession()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PermissionChecker.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(sessionValid) {
        if (!sessionValid) {
            nav.navigate(Nav.Login.route) {
                popUpTo(nav.graph.startDestinationId) { inclusive = true }
            }
        }
    }

    LaunchedEffect(pendingConversationId, sessionValid) {
        val activity = context as? MainActivity ?: return@LaunchedEffect
        val conversationId = pendingConversationId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (!authVm.isLoggedIn() || !sessionValid) return@LaunchedEffect

        nav.navigate(Nav.Chat.create(conversationId)) {
            launchSingleTop = true
        }
        activity.clearPendingConversationId()
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Nav.Login.route) {
            LoginScreen(
                vm = authVm,
                onOpenRegister = { nav.navigate(Nav.Register.route) },
                onSuccess = {
                    nav.navigate(Nav.Conversations.route) {
                        popUpTo(Nav.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Nav.Register.route) {
            RegisterScreen(
                vm = authVm,
                onBack = { nav.popBackStack() },
                onSuccess = {
                    nav.navigate(Nav.Conversations.route) {
                        popUpTo(Nav.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Nav.Conversations.route) {
            val vm: ConversationsViewModel = hiltViewModel()
            ConversationsScreen(
                vm = vm,
                onOpenChat = { nav.navigate(Nav.Chat.create(it)) },
                onOpenContacts = { nav.navigate(Nav.Contacts.route) },
                onOpenProfile = { nav.navigate(Nav.Profile.route) },
                onLogout = {
                    authVm.logout {
                        nav.navigate(Nav.Login.route) {
                            popUpTo(nav.graph.startDestinationId) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Nav.Contacts.route) {
            val vm: ContactsViewModel = hiltViewModel()
            val convVm: ConversationsViewModel = hiltViewModel()
            ContactsScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenChatWithUser = { uid ->
                    convVm.createDirectChat(uid) { conversationId ->
                        nav.navigate(Nav.Chat.create(conversationId))
                    }
                }
            )
        }

        composable(Nav.Profile.route) {
            val vm: ProfileViewModel = hiltViewModel()
            ProfileScreen(
                vm = vm,
                authVm = authVm,
                onBack = { nav.popBackStack() },
                onLogout = {
                    nav.navigate(Nav.Login.route) {
                        popUpTo(nav.graph.startDestinationId) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Nav.Chat.route,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val vm: ChatViewModel = hiltViewModel()
            val conversationId = backStackEntry.arguments?.getString("conversationId").orEmpty()
            ChatScreen(
                conversationId = conversationId,
                vm = vm,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
