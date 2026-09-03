package com.aipdfreader.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aipdfreader.app.ui.account.AccountScreen
import com.aipdfreader.app.ui.auth.LoginScreen
import com.aipdfreader.app.ui.chat.ChatScreen
import com.aipdfreader.app.ui.library.LibraryScreen
import com.aipdfreader.app.ui.reader.ReaderScreen

/** Route definitions kept in one place so screens never hand-build paths. */
object Routes {
    const val LOGIN = "login"
    const val LIBRARY = "library"
    const val READER = "reader/{pdfId}"
    const val CHAT = "chat/{pdfId}?highlightId={highlightId}"
    const val ACCOUNT = "account"

    fun reader(pdfId: Long) = "reader/$pdfId"
    fun chat(pdfId: Long, highlightId: Long? = null) =
        "chat/$pdfId?highlightId=${highlightId ?: -1L}"
}

/**
 * The app is gated behind [Routes.LOGIN]: the Android client authenticates
 * once against our backend, and everything downstream (library, reader,
 * chat) assumes a valid session — enforced server-side via the bearer token
 * [com.aipdfreader.app.data.remote.AuthInterceptor] attaches to every call.
 */
@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.LOGIN) {

        composable(Routes.LOGIN) {
            LoginScreen(
                onAuthenticated = {
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LIBRARY) {
            LibraryScreen(
                onOpenPdf = { pdfId -> navController.navigate(Routes.reader(pdfId)) },
                onOpenAccount = { navController.navigate(Routes.ACCOUNT) }
            )
        }

        composable(Routes.ACCOUNT) {
            AccountScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.READER,
            arguments = listOf(navArgument("pdfId") { type = NavType.LongType })
        ) { backStackEntry ->
            val pdfId = backStackEntry.arguments?.getLong("pdfId") ?: return@composable
            ReaderScreen(
                pdfId = pdfId,
                onBack = { navController.popBackStack() },
                onAskAi = { highlightId, text ->
                    navController.navigate(Routes.chat(pdfId, highlightId)) {
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("pdfId") { type = NavType.LongType },
                navArgument("highlightId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            val pdfId = backStackEntry.arguments?.getLong("pdfId") ?: return@composable
            val highlightIdArg = backStackEntry.arguments?.getLong("highlightId") ?: -1L
            ChatScreen(
                pdfId = pdfId,
                highlightId = if (highlightIdArg == -1L) null else highlightIdArg,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
