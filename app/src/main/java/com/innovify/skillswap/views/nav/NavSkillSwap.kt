package com.innovify.skillswap.views.nav

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import com.innovify.skillswap.views.CertificateCapture
import com.innovify.skillswap.views.Login
import com.innovify.skillswap.views.NodeDetail
import com.innovify.skillswap.views.PathList
import com.innovify.skillswap.views.ProjectDelivery
import com.innovify.skillswap.views.Quiz
import com.innovify.skillswap.views.QuizResult
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

    // Cada nav.navigate("X") debe tener su composable("X") aquí.
    NavHost(navController = recordarPantalla, startDestination = "Login") {

        composable("Login") { Login(viewModel, context, recordarPantalla) }
        composable("Path") { PathList(viewModel, context, recordarPantalla) }
        composable("Node") { NodeDetail(viewModel, context, recordarPantalla) }
        composable("Certificate") { CertificateCapture(viewModel, context, recordarPantalla) }
        composable("Quiz") { Quiz(viewModel, context, recordarPantalla) }
        composable("QuizResult") { QuizResult(viewModel, context, recordarPantalla) }
        composable("Project") { ProjectDelivery(viewModel, context, recordarPantalla) }

    }

}
