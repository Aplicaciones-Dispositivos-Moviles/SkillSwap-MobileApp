package com.innovify.skillswap.views

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.ui.theme.SkillBlue
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Mock-up 03 · Inicio de sesión
@Composable
fun Login(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 80.dp, bottom = 32.dp, start = 24.dp, end = 24.dp)
    ) {
        // Marca: cuadrado azul con las flechas de intercambio
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .background(SkillBlue, RoundedCornerShape(16.dp))
        ) {
            Text(text = "⇄", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            text = "SkillSwap",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = "Valida tus habilidades con evidencia real",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 32.dp)
        )

        Text(
            text = "Inicia sesión",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        )

        Text(
            text = "Usuario",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = username,
            singleLine = true,
            leadingIcon = { Icon(imageVector = Icons.Filled.Person, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            onValueChange = { username = it }
        )

        Text(
            text = "Contraseña",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = password,
            singleLine = true,
            leadingIcon = { Icon(imageVector = Icons.Filled.Lock, contentDescription = null) },
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(text = if (showPassword) "Ocultar" else "Ver")
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            onValueChange = { password = it }
        )

        Spacer(modifier = Modifier.height(32.dp))
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
                .height(52.dp)
        ) {
            Text(
                text = if (loading) "Ingresando…" else "Iniciar sesión",
                style = MaterialTheme.typography.labelLarge
            )
        }
        if (loading) {
            Text(
                text = "Puede tardar si el servidor estaba dormido",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }

}
