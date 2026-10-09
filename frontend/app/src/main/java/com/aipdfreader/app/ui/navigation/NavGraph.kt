package com.aipdfreader.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.aipdfreader.app.ui.account.AccountScreen
import com.aipdfreader.app.ui.auth.LoginScreen
import com.aipdfreader.app.ui.auth.ProfileSetupScreen
import com.aipdfreader.app.data.repository.LearnerProfileStore
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
    const val SARAH_RESOURCE = "sarah-resource/{pdfId}/{resourceId}/{cardId}?highlightId={highlightId}&pageIndex={pageIndex}"
    const val ACCOUNT = "account"
    const val PROFILE_SETUP = "profile-setup"
    const val NOTIFICATIONS = "notifications"
    const val CARD = "card/{cardId}/{cardName}?isShared={isShared}&role={role}&memberCardId={memberCardId}"

    fun reader(pdfId: Long, resourceId: String? = null, cardId: String? = null) =
        if (resourceId == null || cardId == null) "reader/$pdfId"
        else "reader/$pdfId?resourceId=$resourceId&cardId=$cardId"
    fun resourceSarah(pdfId: Long, resourceId: String, cardId: String,
                      highlightId: Long? = null, pageIndex: Int? = null) =
        "sarah-resource/$pdfId/$resourceId/$cardId?highlightId=${highlightId ?: -1L}&pageIndex=${pageIndex ?: -1}"
    fun card(cardId: String, cardName: String, isShared: Boolean, role: String?, memberCardId: String? = null) =
        "card/$cardId/${android.net.Uri.encode(cardName)}?isShared=$isShared&role=${role ?: "MEMBER"}&memberCardId=${memberCardId ?: cardId}"
}

/**
 * A0 opens on a local Card-first shell. Authentication and backend wiring are
 * intentionally left to A1; existing login, on-device files, reader, and
 * chat destinations remain available.
 */
@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController(),
    notificationOpenRequest: Int = 0
) {
    val context = LocalContext.current
    val currentUser = FirebaseAuth.getInstance().currentUser
    val startDestination = when {
        currentUser == null -> Routes.LOGIN
        LearnerProfileStore.hasProfile(context, currentUser.uid) -> Routes.HOME
        else -> Routes.PROFILE_SETUP
    }
    LaunchedEffect(notificationOpenRequest) {
        if (notificationOpenRequest > 0 && FirebaseAuth.getInstance().currentUser != null) {
            navController.navigate(Routes.NOTIFICATIONS) { launchSingleTop = true }
        }
    }
    NavHost(navController = navController, startDestination = startDestination) {

        composable(Routes.LOGIN) {
            LoginScreen(
                onAuthenticated = { needsSetup ->
                    navController.navigate(if (needsSetup) Routes.PROFILE_SETUP else Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PROFILE_SETUP) {
            ProfileSetupScreen(onComplete = {
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.PROFILE_SETUP) { inclusive = true }
                }
            })
        }

        composable(Routes.LIBRARY) {
            LibraryScreen(
                onBack = { navController.popBackStack() },
                onOpenPdf = { pdfId -> navController.navigate(Routes.reader(pdfId)) },
                onOpenAccount = { navController.navigate(Routes.ACCOUNT) }
            )
        }

        composable(Routes.HOME) {
            CardHomeScreen(
                onOpenLibrary = { navController.navigate(Routes.LIBRARY) },
                onOpenAccount = { navController.navigate(Routes.ACCOUNT) },
                onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                onOpenCard = { card -> navController.navigate(Routes.card(card.id, card.name, card.isShared, card.role ?: "OWNER", card.memberCardId)) }
            )
        }

        composable(
            route = Routes.CARD,
            arguments = listOf(navArgument("cardId") { type = NavType.StringType },
                navArgument("cardName") { type = NavType.StringType },
                navArgument("isShared") { type = NavType.BoolType; defaultValue = false },
                navArgument("role") { type = NavType.StringType; defaultValue = "MEMBER" },
                navArgument("memberCardId") { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            CardWorkspaceScreen(
                cardId = entry.arguments?.getString("cardId").orEmpty(),
                cardName = entry.arguments?.getString("cardName").orEmpty(),
                isShared = entry.arguments?.getBoolean("isShared") ?: false,
                callerRole = entry.arguments?.getString("role") ?: "MEMBER",
                memberCardId = entry.arguments?.getString("memberCardId")?.takeIf(String::isNotBlank),
                onBack = { navController.popBackStack() },
                onOpenPdf = { pdfId, resourceId ->
                    val workspaceCardId = entry.arguments?.getString("cardId").orEmpty()
                    if (workspaceCardId.startsWith("local:")) navController.navigate(Routes.reader(pdfId))
                    else navController.navigate(Routes.reader(pdfId, resourceId, workspaceCardId))
                }
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
                canAskSarahAboutResource = backStackEntry.arguments?.getString("resourceId") != null &&
                    backStackEntry.arguments?.getString("cardId") != null,
                onAskAi = { highlightId, _, pageIndex ->
                    val resourceId = backStackEntry.arguments?.getString("resourceId")
                    val cardId = backStackEntry.arguments?.getString("cardId")
                    if (resourceId != null && cardId != null) {
                        navController.navigate(Routes.resourceSarah(pdfId, resourceId, cardId, highlightId, pageIndex)) {
                            launchSingleTop = true
                        }
                    } else {
                        Toast.makeText(context, "Sarah needs an internet connection and an online Card to answer questions about this file.", Toast.LENGTH_LONG).show()
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
                navArgument("highlightId") { type = NavType.LongType; defaultValue = -1L },
                navArgument("pageIndex") { type = NavType.IntType; defaultValue = -1 }
            )
        ) { backStackEntry ->
            ContextualSarahScreen(onBack = { navController.popBackStack() })
        }
    }
}
