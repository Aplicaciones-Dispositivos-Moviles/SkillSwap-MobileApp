package com.innovify.skillswap.views

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.fragment.app.FragmentActivity
import com.innovify.skillswap.viewmodel.BiometricVault
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.model.beans.CreditTransaction
import com.innovify.skillswap.ui.theme.Error
import com.innovify.skillswap.ui.theme.NeutralBorder
import com.innovify.skillswap.ui.theme.NeutralSurface
import com.innovify.skillswap.ui.theme.Success
import com.innovify.skillswap.ui.theme.TextSecondary
import com.innovify.skillswap.ui.theme.Warning
import com.innovify.skillswap.ui.theme.WarningContainer
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Perfil / Billetera (solo lectura): cuenta, saldo de SkillCredits y movimientos, todo del backend.
@Composable
fun Profile(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() {
        loading = true
        error = null
        // La red va en hilo IO (como en la plantilla del profe)
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val ok = viewModel.loadWallet()
            withContext(Dispatchers.Main) {
                loading = false
                error = if (ok) null else viewModel.errorMessage
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    // US03: activar o quitar el acceso con huella o rostro
    val biometricAvailable = remember { BiometricVault.isAvailable(context) }
    var askPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshBiometric(context) }

    if (askPassword) {
        AlertDialog(
            onDismissRequest = { if (!checking) askPassword = false },
            title = { Text(text = "Activar acceso biométrico") },
            text = {
                Column {
                    Text(
                        text = "Escribe tu contraseña una vez. Se guarda cifrada en este teléfono y solo se abre con tu huella o rostro.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = password,
                        singleLine = true,
                        enabled = !checking,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        onValueChange = { password = it }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !checking && password.isNotBlank(),
                    onClick = {
                        checking = true
                        // La red va en hilo IO (como en la plantilla del profe)
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            val ok = viewModel.checkPassword(password)
                            withContext(Dispatchers.Main) {
                                checking = false
                                val activity = context as? FragmentActivity
                                if (!ok) {
                                    Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
                                } else if (activity != null) {
                                    askPassword = false
                                    BiometricVault.enable(
                                        activity, viewModel.user?.username ?: "", password,
                                        onDone = {
                                            viewModel.biometricEnabled = true
                                            Toast.makeText(context, "Acceso biométrico activado", Toast.LENGTH_SHORT).show()
                                        },
                                        onError = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
                                    )
                                    password = ""
                                }
                            }
                        }
                    }
                ) { Text(text = if (checking) "Verificando…" else "Continuar") }
            },
            dismissButton = {
                TextButton(enabled = !checking, onClick = { askPassword = false }) { Text(text = "Cancelar") }
            }
        )
    }

    val wallet = viewModel.wallet
    val transactions = viewModel.transactions

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 40.dp, bottom = 32.dp, start = 16.dp, end = 24.dp)
    ) {
        item {
            ScreenTopBar(title = "Mi perfil", onBack = { nav.popBackStack() })
            OfflineBanner(viewModel)
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    text = viewModel.user?.username ?: "",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = viewModel.user?.email ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 24.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Entrar con huella o rostro", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (biometricAvailable) "Inicia sesión sin escribir tu contraseña."
                        else "Tu dispositivo no tiene huella ni rostro configurados. Sigues entrando con tu contraseña.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Switch(
                    checked = viewModel.biometricEnabled,
                    onCheckedChange = { wantsOn ->
                        if (!wantsOn) {
                            BiometricVault.disable(context)
                            viewModel.biometricEnabled = false
                            Toast.makeText(context, "Acceso biométrico desactivado", Toast.LENGTH_SHORT).show()
                        } else if (!biometricAvailable) {
                            Toast.makeText(
                                context, "El acceso biométrico no está disponible en este dispositivo", Toast.LENGTH_LONG
                            ).show()
                        } else {
                            password = ""
                            askPassword = true
                        }
                    }
                )
            }
        }

        if (loading) {
            item {
                Text(
                    text = "Cargando…",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp, top = 24.dp)
                )
            }
        } else if (error != null || wallet == null) {
            item {
                Column(modifier = Modifier.padding(start = 8.dp, top = 24.dp)) {
                    Text(
                        text = error ?: "No se pudo cargar tu billetera",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Error
                    )
                    Button(
                        enabled = viewModel.connectivity.isOnline,
                        onClick = { load() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .height(52.dp)
                    ) {
                        Text(text = if (viewModel.connectivity.isOnline) "Reintentar" else "Necesitas internet")
                    }
                }
            }
        } else {
            item {
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp, top = 24.dp)
                        .fillMaxWidth()
                        .background(WarningContainer, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(text = "Tu saldo", style = MaterialTheme.typography.labelLarge, color = Warning)
                    Text(
                        text = wallet.balance.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Warning
                    )
                    Text(text = "SkillCredits", style = MaterialTheme.typography.bodyMedium, color = Warning)
                }
                Text(
                    text = "Movimientos",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp)
                )
            }

            if (transactions.isEmpty()) {
                item {
                    Text(
                        text = "Todavía no tienes movimientos. Ganas SkillCredits al resolver casos como Verificador.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            } else {
                items(transactions, key = { it.id }) { transaction ->
                    TransactionRow(transaction)
                }
            }
        }
    }

}

// Un movimiento: descripción, tipo y fecha a la izquierda; monto con signo a la derecha
@Composable
private fun TransactionRow(transaction: CreditTransaction) {
    Column(modifier = Modifier.padding(start = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.description?.ifBlank { null } ?: transaction.typeLabel(),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = transaction.typeLabel() + " · " + localDate(transaction.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Text(
                text = transaction.signedAmount(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (transaction.isEarned()) Success else Error
            )
        }
        HorizontalDivider(color = NeutralBorder)
    }
}
