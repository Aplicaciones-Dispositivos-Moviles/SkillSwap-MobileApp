package com.innovify.skillswap.views

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.viewmodel.SignUpValidator
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// WF-01 · Registro de nuevo usuario (correo institucional .edu.pe)
@Composable
fun Register(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    var username by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp, bottom = 32.dp, start = 16.dp, end = 24.dp)
    ) {
        ScreenTopBar(title = "Crear cuenta", onBack = { nav.popBackStack() })
        OfflineBanner(viewModel)

        Text(
            text = "Regístrate con tu correo institucional",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 8.dp, top = 8.dp)
        )
        Text(
            text = "Te enviaremos un enlace para verificar tu cuenta.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 24.dp)
        )

        Text(
            text = "Usuario",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = username,
            singleLine = true,
            enabled = !loading,
            leadingIcon = { Icon(imageVector = Icons.Filled.Person, contentDescription = null) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp),
            onValueChange = { username = it }
        )

        Text(
            text = "Correo institucional",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = email,
            singleLine = true,
            enabled = !loading,
            placeholder = { Text(text = "tu.codigo@universidad.edu.pe") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Email, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp),
            onValueChange = { email = it }
        )

        Text(
            text = "Contraseña",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = password,
            singleLine = true,
            enabled = !loading,
            supportingText = { Text(text = "Mínimo ${SignUpValidator.MIN_PASSWORD} caracteres") },
            leadingIcon = { Icon(imageVector = Icons.Filled.Lock, contentDescription = null) },
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(text = if (showPassword) "Ocultar" else "Ver")
                }
            },
            visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp),
            onValueChange = { password = it }
        )

        Text(
            text = "Confirmar contraseña",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 8.dp)
        )
        OutlinedTextField(
            value = confirm,
            singleLine = true,
            enabled = !loading,
            leadingIcon = { Icon(imageVector = Icons.Filled.Lock, contentDescription = null) },
            visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp),
            onValueChange = { confirm = it }
        )

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            enabled = !loading,
            onClick = {
                val problem = SignUpValidator.validate(username, email, password, confirm)
                if (problem != null) {
                    Toast.makeText(context, problem, Toast.LENGTH_LONG).show()
                } else {
                    loading = true
                    // La red va en hilo IO (como en la plantilla del profe)
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        val ok = viewModel.signUp(username.trim(), email.trim().lowercase(), password)
                        withContext(Dispatchers.Main) {
                            loading = false
                            if (ok) {
                                Toast.makeText(
                                    context,
                                    "Cuenta creada. Revisa tu correo para verificarla e inicia sesión",
                                    Toast.LENGTH_LONG
                                ).show()
                                nav.popBackStack() // vuelve al Login, que muestra el usuario ya escrito
                            } else {
                                Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp)
                .height(52.dp)
        ) {
            Text(
                text = if (loading) "Creando cuenta…" else "Crear cuenta",
                style = MaterialTheme.typography.labelLarge
            )
        }
        if (loading) {
            Text(
                text = "Puede tardar si el servidor estaba dormido",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 12.dp)
            )
        }
    }

}
