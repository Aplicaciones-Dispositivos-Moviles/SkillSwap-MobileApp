package com.innovify.skillswap.shared.presentation.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.innovify.skillswap.assessment.application.MiniProjectViewModel
import com.innovify.skillswap.assessment.application.QuizViewModel
import com.innovify.skillswap.assessment.application.VerificationCaseViewModel
import com.innovify.skillswap.credentialverification.application.CertificateViewModel
import com.innovify.skillswap.finaldemonstration.application.FinalDemonstrationViewModel
import com.innovify.skillswap.iam.application.InterestsViewModel
import com.innovify.skillswap.iam.application.SessionViewModel
import com.innovify.skillswap.learningpath.application.LearningPathViewModel
import com.innovify.skillswap.reputation.application.WalletViewModel
import com.innovify.skillswap.subscription.application.SubscriptionViewModel
import com.innovify.skillswap.assessment.presentation.CaseStatus
import com.innovify.skillswap.finaldemonstration.presentation.Demonstration
import com.innovify.skillswap.finaldemonstration.presentation.DemonstrationVideo
import com.innovify.skillswap.iam.presentation.Interests
import com.innovify.skillswap.learningpath.presentation.MyPaths
import com.innovify.skillswap.subscription.presentation.MyPlan
import com.innovify.skillswap.iam.presentation.Profile
import com.innovify.skillswap.credentialverification.presentation.CertificateCapture
import com.innovify.skillswap.learningpath.presentation.GoalInput
import com.innovify.skillswap.iam.presentation.Login
import com.innovify.skillswap.learningpath.presentation.NodeDetail
import com.innovify.skillswap.learningpath.presentation.PathList
import com.innovify.skillswap.assessment.presentation.ProjectDelivery
import com.innovify.skillswap.assessment.presentation.Quiz
import com.innovify.skillswap.assessment.presentation.QuizResult
import com.innovify.skillswap.iam.presentation.Register
import com.innovify.skillswap.learningpath.presentation.SkillConfirmation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NavSkillSwap(
    sessionViewModel: SessionViewModel,
    interestsViewModel: InterestsViewModel,
    learningPathViewModel: LearningPathViewModel,
    certificateViewModel: CertificateViewModel,
    quizViewModel: QuizViewModel,
    miniProjectViewModel: MiniProjectViewModel,
    verificationCaseViewModel: VerificationCaseViewModel,
    demonstrationViewModel: FinalDemonstrationViewModel,
    subscriptionViewModel: SubscriptionViewModel,
    walletViewModel: WalletViewModel,
    context: Context
) {

    val recordarPantalla = rememberNavController()

    // T51: cada vez que vuelve internet (o tras iniciar sesión con internet) se reenvían los quiz pendientes
    val online = sessionViewModel.connectivity.isOnline
    LaunchedEffect(online, sessionViewModel.user) {
        if (online && sessionViewModel.user != null) {
            quizViewModel.viewModelScope.launch(Dispatchers.IO) {
                val sent = quizViewModel.resendPending(context)
                if (sent > 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Se enviaron $sent quiz pendientes", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Si la sesión se cierra (botón Salir o token vencido), se vuelve al Login y se limpia el historial
    LaunchedEffect(sessionViewModel.user) {
        val route = recordarPantalla.currentDestination?.route
        if (sessionViewModel.user == null && route != null && route != "Login" && route != "Register") {
            recordarPantalla.navigate("Login") { popUpTo(0) }
        }
    }

    // Notificaciones push: se pide el permiso una sola vez tras iniciar sesión (Android 13+). Si lo niegan, la app
    // sigue igual; si lo conceden (o ya estaba concedido), el token de este teléfono se envía al backend.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        sessionViewModel.syncDeviceToken(context)
    }
    LaunchedEffect(sessionViewModel.user, online) {
        if (sessionViewModel.user == null) return@LaunchedEffect
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission && sessionViewModel.shouldAskNotificationPermission(context)) {
            sessionViewModel.markNotificationPermissionAsked(context)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            sessionViewModel.syncDeviceToken(context)
        }
    }

    // Notificación tocada: abre la pantalla que corresponde (sin sesión se queda en el Login)
    LaunchedEffect(sessionViewModel.pendingRoute, sessionViewModel.user) {
        val route = sessionViewModel.pendingRoute ?: return@LaunchedEffect
        sessionViewModel.pendingRoute = null
        if (sessionViewModel.user == null) return@LaunchedEffect
        if (route == "Path") {
            recordarPantalla.navigate("Path") { popUpTo("Path") { inclusive = true } }
        } else {
            recordarPantalla.navigate(route) { launchSingleTop = true }
        }
    }

    // Con una sesión guardada la app abre directo en la ruta
    val start = if (sessionViewModel.user != null) "Path" else "Login"

    // Cada nav.navigate("X") debe tener su composable("X") aquí.
    NavHost(navController = recordarPantalla, startDestination = start) {

        composable("Login") { Login(sessionViewModel, context, recordarPantalla) }
        composable("Register") { Register(sessionViewModel, context, recordarPantalla) }
        composable("Path") { PathList(learningPathViewModel, sessionViewModel, context, recordarPantalla) }
        composable("Goal") { GoalInput(learningPathViewModel, context, recordarPantalla) }
        composable("SkillConfirmation") { SkillConfirmation(learningPathViewModel, subscriptionViewModel, context, recordarPantalla) }
        composable("Node") {
            NodeDetail(learningPathViewModel, quizViewModel, certificateViewModel, verificationCaseViewModel, context, recordarPantalla)
        }
        composable("Certificate") { CertificateCapture(certificateViewModel, learningPathViewModel, context, recordarPantalla) }
        composable("Quiz") { Quiz(quizViewModel, learningPathViewModel, context, recordarPantalla) }
        composable("QuizResult") { QuizResult(quizViewModel, verificationCaseViewModel, context, recordarPantalla) }
        composable("Project") {
            ProjectDelivery(miniProjectViewModel, learningPathViewModel, verificationCaseViewModel, context, recordarPantalla)
        }
        composable("CaseStatus") { CaseStatus(verificationCaseViewModel, context, recordarPantalla) }
        composable("Profile") { Profile(sessionViewModel, walletViewModel, context, recordarPantalla) }
        composable("Demonstration") { Demonstration(demonstrationViewModel, context, recordarPantalla) }
        composable("DemonstrationVideo") { DemonstrationVideo(demonstrationViewModel, context, recordarPantalla) }
        composable("MyPlan") { MyPlan(subscriptionViewModel, context, recordarPantalla) }
        composable("MyPaths") { MyPaths(learningPathViewModel, subscriptionViewModel, context, recordarPantalla) }
        composable("Interests") { Interests(interestsViewModel, context, recordarPantalla) }

    }

}
