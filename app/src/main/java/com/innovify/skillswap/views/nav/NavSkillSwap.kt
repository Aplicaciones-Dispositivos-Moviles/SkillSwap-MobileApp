package com.innovify.skillswap.views.nav

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
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import com.innovify.skillswap.views.CaseStatus
import com.innovify.skillswap.views.Demonstration
import com.innovify.skillswap.views.DemonstrationVideo
import com.innovify.skillswap.views.Interests
import com.innovify.skillswap.views.MyPaths
import com.innovify.skillswap.views.MyPlan
import com.innovify.skillswap.views.Profile
import com.innovify.skillswap.views.CertificateCapture
import com.innovify.skillswap.views.GoalInput
import com.innovify.skillswap.views.Login
import com.innovify.skillswap.views.NodeDetail
import com.innovify.skillswap.views.PathList
import com.innovify.skillswap.views.ProjectDelivery
import com.innovify.skillswap.views.Quiz
import com.innovify.skillswap.views.QuizResult
import com.innovify.skillswap.views.Register
import com.innovify.skillswap.views.SkillConfirmation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NavSkillSwap(viewModel: SkillSwapViewModel, context: Context) {

    val recordarPantalla = rememberNavController()

    // T51: cada vez que vuelve internet (o tras iniciar sesión con internet) se reenvían los quiz pendientes
    val online = viewModel.connectivity.isOnline
    LaunchedEffect(online, viewModel.user) {
        if (online && viewModel.user != null) {
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                val sent = viewModel.resendPending(context)
                if (sent > 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Se enviaron $sent quiz pendientes", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // Si la sesión se cierra (botón Salir o token vencido), se vuelve al Login y se limpia el historial
    LaunchedEffect(viewModel.user) {
        val route = recordarPantalla.currentDestination?.route
        if (viewModel.user == null && route != null && route != "Login" && route != "Register") {
            recordarPantalla.navigate("Login") { popUpTo(0) }
        }
    }

    // Notificaciones push: se pide el permiso una sola vez tras iniciar sesión (Android 13+). Si lo niegan, la app
    // sigue igual; si lo conceden (o ya estaba concedido), el token de este teléfono se envía al backend.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.syncDeviceToken(context)
    }
    LaunchedEffect(viewModel.user, online) {
        if (viewModel.user == null) return@LaunchedEffect
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission && viewModel.shouldAskNotificationPermission(context)) {
            viewModel.markNotificationPermissionAsked(context)
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.syncDeviceToken(context)
        }
    }

    // Notificación tocada: abre la pantalla que corresponde (sin sesión se queda en el Login)
    LaunchedEffect(viewModel.pendingRoute, viewModel.user) {
        val route = viewModel.pendingRoute ?: return@LaunchedEffect
        viewModel.pendingRoute = null
        if (viewModel.user == null) return@LaunchedEffect
        if (route == "Path") {
            recordarPantalla.navigate("Path") { popUpTo("Path") { inclusive = true } }
        } else {
            recordarPantalla.navigate(route) { launchSingleTop = true }
        }
    }

    // Con una sesión guardada la app abre directo en la ruta
    val start = if (viewModel.user != null) "Path" else "Login"

    // Cada nav.navigate("X") debe tener su composable("X") aquí.
    NavHost(navController = recordarPantalla, startDestination = start) {

        composable("Login") { Login(viewModel, context, recordarPantalla) }
        composable("Register") { Register(viewModel, context, recordarPantalla) }
        composable("Path") { PathList(viewModel, context, recordarPantalla) }
        composable("Goal") { GoalInput(viewModel, context, recordarPantalla) }
        composable("SkillConfirmation") { SkillConfirmation(viewModel, context, recordarPantalla) }
        composable("Node") { NodeDetail(viewModel, context, recordarPantalla) }
        composable("Certificate") { CertificateCapture(viewModel, context, recordarPantalla) }
        composable("Quiz") { Quiz(viewModel, context, recordarPantalla) }
        composable("QuizResult") { QuizResult(viewModel, context, recordarPantalla) }
        composable("Project") { ProjectDelivery(viewModel, context, recordarPantalla) }
        composable("CaseStatus") { CaseStatus(viewModel, context, recordarPantalla) }
        composable("Profile") { Profile(viewModel, context, recordarPantalla) }
        composable("Demonstration") { Demonstration(viewModel, context, recordarPantalla) }
        composable("DemonstrationVideo") { DemonstrationVideo(viewModel, context, recordarPantalla) }
        composable("MyPlan") { MyPlan(viewModel, context, recordarPantalla) }
        composable("MyPaths") { MyPaths(viewModel, context, recordarPantalla) }
        composable("Interests") { Interests(viewModel, context, recordarPantalla) }

    }

}
