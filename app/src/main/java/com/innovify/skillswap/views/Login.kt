package com.innovify.skillswap.views

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun Login(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 50.dp, horizontal = 20.dp)
    ) {
        Text(text = "SkillSwap", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Inicia sesión como Estudiante",
            modifier = Modifier.padding(bottom = 20.dp)
        )

        OutlinedTextField(
            value = username,
            label = { Text(text = "Usuario") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            onValueChange = { username = it }
        )

        OutlinedTextField(
            value = password,
            label = { Text(text = "Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            onValueChange = { password = it }
        )

        Button(
            enabled = !loading,
            onClick = {
                if (username.isBlank() || password.isBlank()) {
                    Toast.makeText(context, "Ingrese usuario y contraseña", Toast.LENGTH_SHORT).show()
                } else {
                    loading = true
                    // La red va en hilo IO (como en la plantilla del profe)
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        val ok = viewModel.signIn(username.trim(), password)
                        // Toast y navegación necesitan el hilo principal
                        withContext(Dispatchers.Main) {
                            loading = false
                            if (ok) {
                                nav.navigate("Path") {
                                    popUpTo("Login") { inclusive = true }
                                }
                            } else {
                                Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
        ) {
            Text(text = if (loading) "Ingresando… (puede tardar si el servidor estaba dormido)" else "Iniciar sesión")
        }
    }

}
