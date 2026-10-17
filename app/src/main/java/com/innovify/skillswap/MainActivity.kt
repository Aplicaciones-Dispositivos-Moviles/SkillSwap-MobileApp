package com.innovify.skillswap

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.innovify.skillswap.ui.theme.SkillSwapTheme
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import com.innovify.skillswap.views.nav.NavSkillSwap

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel by viewModels<SkillSwapViewModel>()
        viewModel.restoreSession(applicationContext) // recupera la sesión guardada, si hay
        viewModel.connectivity.start(applicationContext) // T49: observa la conexión mientras la app vive

        setContent {
            SkillSwapTheme {
                NavSkillSwap(viewModel, this)
            }
        }
    }
}
