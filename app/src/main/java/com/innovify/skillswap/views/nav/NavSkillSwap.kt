package com.innovify.skillswap.views.nav

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import com.innovify.skillswap.views.CertificateCapture
import com.innovify.skillswap.views.Login
import com.innovify.skillswap.views.NodeDetail
import com.innovify.skillswap.views.PathList

@Composable
fun NavSkillSwap(viewModel: SkillSwapViewModel, context: Context) {

    val recordarPantalla = rememberNavController()

    // Cada nav.navigate("X") debe tener su composable("X") aquí.
    NavHost(navController = recordarPantalla, startDestination = "Login") {

        composable("Login") { Login(viewModel, context, recordarPantalla) }
        composable("Path") { PathList(viewModel, context, recordarPantalla) }
        composable("Node") { NodeDetail(viewModel, context, recordarPantalla) }
        composable("Certificate") { CertificateCapture(viewModel, context, recordarPantalla) }

    }

}
