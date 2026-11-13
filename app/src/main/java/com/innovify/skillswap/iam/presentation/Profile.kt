package com.innovify.skillswap.iam.presentation

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.credentialverification.application.CertificateViewModel
import com.innovify.skillswap.credentialverification.domain.CertificateStage
import com.innovify.skillswap.credentialverification.domain.stage
import com.innovify.skillswap.iam.application.SessionViewModel
import com.innovify.skillswap.iam.infrastructure.BiometricVault
import com.innovify.skillswap.learningpath.application.LearningPathViewModel
import com.innovify.skillswap.learningpath.application.knownNodes
import com.innovify.skillswap.reputation.application.WalletViewModel
import com.innovify.skillswap.reputation.domain.CreditTransaction
import com.innovify.skillswap.shared.presentation.components.OfflineBanner
import com.innovify.skillswap.shared.presentation.components.localDate
import com.innovify.skillswap.shared.presentation.design.CardBorder
import com.innovify.skillswap.shared.presentation.design.EvenRow
import com.innovify.skillswap.shared.presentation.design.InitialsAvatar
import com.innovify.skillswap.shared.presentation.design.ListRow
import com.innovify.skillswap.shared.presentation.design.ScreenTitle
import com.innovify.skillswap.shared.presentation.design.SectionHeader
import com.innovify.skillswap.shared.presentation.design.StatePill
import com.innovify.skillswap.shared.presentation.design.StatTile
import com.innovify.skillswap.shared.presentation.design.StudentTab
import com.innovify.skillswap.shared.presentation.design.StudentTabScaffold
import com.innovify.skillswap.shared.presentation.design.planSummary
import com.innovify.skillswap.shared.presentation.design.tabContentPadding
import com.innovify.skillswap.shared.presentation.theme.Error
import com.innovify.skillswap.shared.presentation.theme.NeutralBorder
import com.innovify.skillswap.shared.presentation.theme.SkillBlueContainer
import com.innovify.skillswap.shared.presentation.theme.SkillBlueDark
import com.innovify.skillswap.shared.presentation.theme.Success
import com.innovify.skillswap.shared.presentation.theme.SuccessContainer
import com.innovify.skillswap.shared.presentation.theme.TextPrimary
import com.innovify.skillswap.shared.presentation.theme.TextSecondary
import com.innovify.skillswap.shared.presentation.theme.Warning
import com.innovify.skillswap.shared.presentation.theme.WarningContainer
import com.innovify.skillswap.subscription.application.SubscriptionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Figma 16 · Perfil: identity, progress numbers, validated skills, interests and the account options
// (plan, interests, paths, SkillCredits wallet, biometric access and sign-out).
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun Profile(
    viewModel: SessionViewModel,
    walletViewModel: WalletViewModel,
    learningPathViewModel: LearningPathViewModel,
    certificateViewModel: CertificateViewModel,
    subscriptionViewModel: SubscriptionViewModel,
    context: Context,
    nav: NavHostController
) {

    var walletLoading by remember { mutableStateOf(true) }
    var walletError by remember { mutableStateOf<String?>(null) }
    var certificatesLoaded by remember { mutableStateOf(certificateViewModel.certificates.isNotEmpty()) }
    var showWallet by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }

    fun loadWallet() {
        walletLoading = true
        walletError = null
        // La red va en hilo IO (como en la plantilla del profe)
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val ok = walletViewModel.loadWallet()
            val error = if (ok) null else viewModel.errorMessage
            withContext(Dispatchers.Main) {
                walletLoading = false
                walletError = error
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            // One after another: the error message is shared, so the wallet's is read before the next call
            val walletOk = walletViewModel.loadWallet()
            val error = if (walletOk) null else viewModel.errorMessage
            withContext(Dispatchers.Main) {
                walletLoading = false
                walletError = error
            }
            // Optional data of the header, numbers and interests: each one falls back to what is known
            viewModel.loadProfile(context)
            val certificates = certificateViewModel.loadCertificates()
            if (subscriptionViewModel.plan == null) subscriptionViewModel.loadPlan()
            withContext(Dispatchers.Main) { if (certificates) certificatesLoaded = true }
        }
    }

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
                        label = { Text("Contraseña") },
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

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Tu ruta guardada en el teléfono se conserva para cuando vuelvas a entrar.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut(context)
                }) { Text("Cerrar sesión", color = Error) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar") } }
        )
    }

    if (showWallet) {
        ModalBottomSheet(onDismissRequest = { showWallet = false }, containerColor = Color.White) {
            WalletSheet(
                loading = walletLoading,
                error = walletError,
                balance = walletViewModel.wallet?.balance,
                transactions = walletViewModel.transactions,
                online = viewModel.connectivity.isOnline,
                onRetry = { loadWallet() }
            )
        }
    }

    val user = viewModel.user
    val displayName = viewModel.displayName(context)
    val profile = viewModel.profile
    val pathNodes = learningPathViewModel.path?.nodes.orEmpty()
    val completedNodes = pathNodes.count { it.status == "Completed" }
    val validatedSkills = knownNodes(learningPathViewModel.path, learningPathViewModel.myPaths)
        .filter { it.status == "Completed" }
        .distinctBy { it.skillTag }
        .map { it.skillName }
    val validatedCertificates = certificateViewModel.certificates.count { it.stage() == CertificateStage.Validated }
    val plan = planSummary(subscriptionViewModel.plan)

    StudentTabScaffold(selected = StudentTab.Profile, nav = nav) { inner ->
        LazyColumn(
            contentPadding = tabContentPadding(inner),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                OfflineBanner(viewModel.connectivity, false)
                ScreenTitle("Perfil")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                    InitialsAvatar(displayName, size = 72.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(displayName, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                        if (user != null && displayName != user.username) {
                            Text("@${user.username}", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Text(user?.email ?: "", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        if (user?.isVerified == true) {
                            Spacer(Modifier.size(6.dp))
                            StatePill(
                                if (user.email.endsWith(".edu.pe", ignoreCase = true)) "Correo .edu.pe verificado"
                                else "Correo verificado",
                                Success, SuccessContainer, Icons.Filled.CheckCircle
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.size(12.dp))
                EvenRow {
                    StatTile(
                        if (pathNodes.isEmpty()) "0" else "$completedNodes de ${pathNodes.size}",
                        "Nodos aprobados",
                        Modifier.weight(1f)
                    )
                    StatTile(
                        if (certificatesLoaded) "$validatedCertificates" else "—",
                        "Certificados validados",
                        Modifier.weight(1f)
                    )
                    StatTile(
                        walletViewModel.wallet?.balance?.toString() ?: "—",
                        "SkillCredits",
                        Modifier.weight(1f)
                    )
                }
            }

            item {
                SectionHeader("Habilidades validadas")
                if (validatedSkills.isEmpty()) {
                    Text(
                        "Aún no validas habilidades. Completa un nodo de tu ruta para verla aquí.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        validatedSkills.forEach { SkillChip(it) }
                    }
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                ) {
                    Text(
                        "Tu descripción e intereses",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { nav.navigate("Interests") }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Editar mis intereses", tint = SkillBlueDark)
                    }
                }
                val bio = profile?.bio?.ifBlank { null }
                val interests = profile?.interests.orEmpty()
                Text(
                    when {
                        bio != null -> bio
                        profile == null && !viewModel.connectivity.isOnline -> "Necesitas internet para ver tu descripción e intereses."
                        profile == null -> "Cargando…"
                        interests.isEmpty() -> "Cuéntanos qué quieres lograr y tus temas de interés para recomendarte mejores rutas."
                        else -> "Aún no escribes una descripción."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (bio != null) TextPrimary else TextSecondary
                )
                if (interests.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        interests.forEach { InterestChip(it) }
                    }
                }
            }

            item {
                SectionHeader("Cuenta")
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, CardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListRow(Icons.Filled.Star, "Mi plan", subtitle = plan, onClick = { nav.navigate("MyPlan") })
                    HorizontalDivider(color = CardBorder, modifier = Modifier.padding(start = 56.dp))
                    ListRow(Icons.Filled.Favorite, "Mis intereses", onClick = { nav.navigate("Interests") })
                    HorizontalDivider(color = CardBorder, modifier = Modifier.padding(start = 56.dp))
                    ListRow(Icons.AutoMirrored.Filled.List, "Mis rutas", onClick = { nav.navigate("MyPaths") })
                    HorizontalDivider(color = CardBorder, modifier = Modifier.padding(start = 56.dp))
                    ListRow(
                        Icons.Filled.AccountBox,
                        "Billetera de SkillCredits",
                        subtitle = walletViewModel.wallet?.let { "Saldo: ${it.balance} SkillCredits" } ?: "Saldo y movimientos",
                        onClick = { showWallet = true }
                    )
                    HorizontalDivider(color = CardBorder, modifier = Modifier.padding(start = 56.dp))
                    ListRow(
                        Icons.Filled.Lock,
                        "Entrar con huella o rostro",
                        subtitle = if (biometricAvailable) "Inicia sesión sin escribir tu contraseña"
                        else "Tu dispositivo no tiene huella ni rostro configurados",
                        trailing = {
                            Switch(
                                checked = viewModel.biometricEnabled,
                                colors = SwitchDefaults.colors(checkedTrackColor = SkillBlueDark),
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
                    )
                    HorizontalDivider(color = CardBorder, modifier = Modifier.padding(start = 56.dp))
                    ListRow(
                        Icons.AutoMirrored.Filled.ExitToApp,
                        "Cerrar sesión",
                        tint = Error,
                        onClick = { confirmSignOut = true },
                        trailing = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillChip(name: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color.White, RoundedCornerShape(50))
            .border(1.dp, NeutralBorder, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
    }
}

@Composable
private fun InterestChip(topic: String) {
    Text(
        topic,
        style = MaterialTheme.typography.labelLarge,
        color = SkillBlueDark,
        modifier = Modifier
            .background(SkillBlueContainer, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

// Saldo y movimientos de SkillCredits (solo lectura, del backend)
@Composable
private fun WalletSheet(
    loading: Boolean,
    error: String?,
    balance: Int?,
    transactions: List<CreditTransaction>,
    online: Boolean,
    onRetry: () -> Unit
) {
    LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
        item {
            Text("Billetera de SkillCredits", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
        when {
            loading -> item {
                Text(
                    "Cargando…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }

            error != null || balance == null -> item {
                Text(
                    error ?: "No se pudo cargar tu billetera",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Error,
                    modifier = Modifier.padding(top = 16.dp)
                )
                Button(
                    enabled = online,
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 52.dp)
                ) { Text(if (online) "Reintentar" else "Necesitas internet") }
            }

            else -> {
                item {
                    Column(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth()
                            .background(WarningContainer, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text("Tu saldo", style = MaterialTheme.typography.labelLarge, color = Warning)
                        Text(
                            balance.toString(),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = Warning
                        )
                        Text("SkillCredits", style = MaterialTheme.typography.bodyMedium, color = Warning)
                    }
                    Text(
                        "Movimientos",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                    )
                }
                if (transactions.isEmpty()) {
                    item {
                        Text(
                            "Todavía no tienes movimientos. Ganas SkillCredits al resolver casos como Verificador.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                } else {
                    items(transactions, key = { it.id }) { TransactionRow(it) }
                }
            }
        }
    }
}

// Un movimiento: descripción, tipo y fecha a la izquierda; monto con signo a la derecha
@Composable
private fun TransactionRow(transaction: CreditTransaction) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.title(),
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
