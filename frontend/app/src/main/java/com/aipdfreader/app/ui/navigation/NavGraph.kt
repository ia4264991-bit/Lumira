package com.aipdfreader.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.aipdfreader.app.ui.account.AccountScreen
import com.aipdfreader.app.ui.auth.LoginScreen
import com.aipdfreader.app.ui.home.CardHomeScreen
import com.aipdfreader.app.ui.library.LibraryScreen
import com.aipdfreader.app.ui.notifications.NotificationsScreen
import com.aipdfreader.app.ui.reader.ReaderScreen
import com.aipdfreader.app.ui.workspace.CardWorkspaceScreen
import com.aipdfreader.app.ui.sarah.ContextualSarahScreen
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

/** Route definitions kept in one place so screens never hand-build paths. */
object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val LIBRARY = "library"
    const val READER = "reader/{pdfId}?resourceId={resourceId}&cardId={cardId}"
    const val SARAH_RESOURCE = "sarah-resource/{pdfId}/{resourceId}/{cardId}?highlightId={highlightId}"
    const val ACCOUNT = "account"
    const val NOTIFICATIONS = "notifications"
    const val CARD = "card/{cardId}/{cardName}?isShared={isShared}&role={role}"

    fun reader(pdfId: Long, resourceId: String? = null, cardId: String? = null) =
        if (resourceId == null || cardId == null) "reader/$pdfId"
        else "reader/$pdfId?resourceId=$resourceId&cardId=$cardId"
    fun resourceSarah(pdfId: Long, resourceId: String, cardId: String, highlightId: Long? = null) =
        "sarah-resource/$pdfId/$resourceId/$cardId?highlightId=${highlightId ?: -1L}"
    fun card(cardId: String, cardName: String, isShared: Boolean, role: String?) =
        "card/$cardId/${android.net.Uri.encode(cardName)}?isShared=$isShared&role=${role ?: "MEMBER"}"
}

/**
 * A0 opens on a local Card-first shell. Authentication and backend wiring are
 * intentionally left to A1; existing login, local PDF library, reader, and
 * chat destinations remain available.
 */
@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val startDestination = if (FirebaseAuth.getInstance().currentUser == null) Routes.LOGIN else Routes.HOME
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.LOGIN) {
            LoginScreen(
                onAuthenticated = {
                    navController.navigate(Routes.HOME) {
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

        composable(Routes.HOME) {
            CardHomeScreen(
                onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenCard = { card -> navController.navigate(Routes.card(card.id, card.name, card.isShared, card.role ?: "OWNER")) }
            )
        }

        composable(
            route = Routes.CARD,
            arguments = listOf(navArgument("cardId") { type = NavType.StringType },
                navArgument("cardName") { type = NavType.StringType },
                navArgument("isShared") { type = NavType.BoolType; defaultValue = false },
                navArgument("role") { type = NavType.StringType; defaultValue = "MEMBER" })
        ) { entry ->
            CardWorkspaceScreen(
                cardId = entry.arguments?.getString("cardId").orEmpty(),
                cardName = entry.arguments?.getString("cardName").orEmpty(),
                isShared = entry.arguments?.getBoolean("isShared") ?: false,
                callerRole = entry.arguments?.getString("role") ?: "MEMBER",
                onBack = { navController.popBackStack() },
                onOpenPdf = { pdfId, resourceId -> navController.navigate(Routes.reader(pdfId, resourceId, entry.arguments?.getString("cardId"))) }
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

        composable(Routes.NOTIFICATIONS) {
            NotificationsScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.READER,
            arguments = listOf(navArgument("pdfId") { type = NavType.LongType },
                navArgument("resourceId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("cardId") { type = NavType.StringType; nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val pdfId = backStackEntry.arguments?.getLong("pdfId") ?: return@composable
            ReaderScreen(
                pdfId = pdfId,
                onBack = { navController.popBackStack() },
                onAskAi = { highlightId, text ->
                    val resourceId = backStackEntry.arguments?.getString("resourceId")
                    val cardId = backStackEntry.arguments?.getString("cardId")
                    if (resourceId != null && cardId != null) {
                        navController.navigate(Routes.resourceSarah(pdfId, resourceId, cardId, highlightId)) {
                            launchSingleTop = true
                        }
                    } else {
                        Toast.makeText(context, "Add this PDF to a Card to ask Sarah about it.", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }

        composable(
            route = Routes.SARAH_RESOURCE,
            arguments = listOf(
                navArgument("pdfId") { type = NavType.LongType },
                navArgument("resourceId") { type = NavType.StringType },
                navArgument("cardId") { type = NavType.StringType },
                navArgument("highlightId") { type = NavType.LongType; defaultValue = -1L }
            )
        ) { backStackEntry ->
            ContextualSarahScreen(onBack = { navController.popBackStack() })
        }
    }
}
