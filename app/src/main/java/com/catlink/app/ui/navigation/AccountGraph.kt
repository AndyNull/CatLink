package com.catlink.app.ui.navigation

import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.catlink.app.debug.DeveloperMode
import com.catlink.app.debug.DeveloperServerScreen
import com.catlink.app.ui.MainViewModel
import com.catlink.app.ui.screens.about.AboutScreen
import com.catlink.app.ui.screens.auth.DevicesScreen
import com.catlink.app.ui.screens.auth.ForgotPasswordScreen
import com.catlink.app.ui.screens.auth.LoginScreen
import com.catlink.app.ui.screens.auth.RegisterScreen
import com.catlink.app.ui.screens.auth.ResetPasswordScreen
import com.catlink.app.ui.screens.auth.VerifyEmailScreen
import com.catlink.app.ui.screens.backup.BackupScreen
import com.catlink.app.ui.screens.export.ExportScreen
import com.catlink.app.ui.screens.poster.PosterScreen
import com.catlink.app.ui.screens.sync.SyncScreen
import com.catlink.app.ui.screens.theme.ThemeScreen

fun NavGraphBuilder.accountGraph(navController: NavHostController, vm: MainViewModel) {
    composable(Routes.USER_CENTER) {
        SecondaryPage {
            com.catlink.app.ui.screens.profile.UserCenterScreen(
                onBack = { navController.popBackStack() },
                onOpenDevices = { navController.navigate(Routes.DEVICES) },
                onOpenResetPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
            )
        }
    }
    composable(Routes.POSTER) {
        SecondaryPage { PosterScreen(vm = vm, onBack = { navController.popBackStack() }) }
    }
    composable(Routes.BACKUP) {
        SecondaryPage { BackupScreen(onBack = { navController.popBackStack() }) }
    }
    composable(Routes.ABOUT) {
        SecondaryPage {
            AboutScreen(
                onBack = { navController.popBackStack() },
                onOpenDeveloper = {
                    if (DeveloperMode.enabled) navController.navigate(Routes.DEVELOPER_SERVER)
                },
            )
        }
    }
    composable(Routes.THEME) {
        SecondaryPage { ThemeScreen(onBack = { navController.popBackStack() }) }
    }
    composable(Routes.SYNC) {
        SecondaryPage { SyncScreen(onBack = { navController.popBackStack() }) }
    }
    composable(Routes.EXPORT) {
        SecondaryPage { ExportScreen(onBack = { navController.popBackStack() }) }
    }
    composable(Routes.LOGIN) {
        SecondaryPage {
            LoginScreen(
                onBack = { navController.popBackStack() },
                onGoRegister = { navController.navigate(Routes.REGISTER) },
                onGoForgot = { navController.navigate(Routes.FORGOT_PASSWORD) },
                onGoVerify = { navController.navigate(Routes.VERIFY_EMAIL) },
                onLoggedIn = { navController.popBackStack() },
            )
        }
    }
    composable(Routes.REGISTER) {
        SecondaryPage {
            RegisterScreen(
                onBack = { navController.popBackStack() },
                onGoLogin = { navController.popBackStack() },
                onRegistered = { email ->
                    navController.navigate(Routes.VERIFY_EMAIL) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }
    }
    composable(Routes.VERIFY_EMAIL) {
        SecondaryPage {
            val context = LocalContext.current
            val app = context.applicationContext as com.catlink.app.CatLinkApplication
            VerifyEmailScreen(
                email = app.container.authSession.pendingVerificationEmail()
                    ?: app.container.authSession.currentEmail()
                    ?: "",
                onBack = { navController.popBackStack() },
                onVerified = { navController.popBackStack() },
            )
        }
    }
    composable(Routes.FORGOT_PASSWORD) {
        SecondaryPage {
            ForgotPasswordScreen(
                onBack = { navController.popBackStack() },
                onCodeSent = { navController.navigate(Routes.RESET_PASSWORD) },
            )
        }
    }
    composable(Routes.RESET_PASSWORD) {
        SecondaryPage {
            ResetPasswordScreen(
                email = (LocalContext.current.applicationContext as com.catlink.app.CatLinkApplication).container.authSession.pendingPasswordResetEmail().orEmpty(),
                onBack = { navController.popBackStack() },
            )
        }
    }
    composable(Routes.DEVICES) {
        SecondaryPage {
            DevicesScreen(
                onBack = { navController.popBackStack() },
                onOpenLogin = { navController.navigate(Routes.LOGIN) },
            )
        }
    }
    composable(Routes.NOTIFICATIONS) {
        SecondaryPage {
            com.catlink.app.ui.screens.notifications.NotificationsScreen(
                onBack = { navController.popBackStack() },
                onOpenAdoptionApplications = { navController.navigate(Routes.ADOPTION_APPLICATIONS) },
            )
        }
    }
    composable(Routes.ADOPTION_APPLICATIONS) {
        SecondaryPage {
            com.catlink.app.ui.screens.profile.AdoptionApplicationsScreen(onBack = { navController.popBackStack() })
        }
    }
    if (DeveloperMode.enabled) {
        composable(Routes.DEVELOPER_SERVER) {
            val context = LocalContext.current
            SecondaryPage {
                DeveloperServerScreen(context = context, onBack = { navController.popBackStack() })
            }
        }
    }
}
