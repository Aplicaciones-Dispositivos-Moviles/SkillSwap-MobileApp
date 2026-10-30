package com.innovify.skillswap.views

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.model.beans.StudentPlan
import com.innovify.skillswap.model.beans.canCancel
import com.innovify.skillswap.model.beans.isCancelled
import com.innovify.skillswap.model.beans.isPremium
import com.innovify.skillswap.model.beans.label
import com.innovify.skillswap.model.beans.totalRoutesLabel
import com.innovify.skillswap.ui.theme.Error
import com.innovify.skillswap.ui.theme.NeutralSurface
import com.innovify.skillswap.ui.theme.SkillBlue
import com.innovify.skillswap.ui.theme.SkillBlueContainer
import com.innovify.skillswap.ui.theme.Success
import com.innovify.skillswap.ui.theme.SuccessContainer
import com.innovify.skillswap.ui.theme.TextSecondary
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// US05 · Mi plan: plan vigente (Free o Premium), sus límites, y activar o cancelar Premium (cobro simulado).
@Composable
fun MyPlan(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var confirmSubscribe by remember { mutableStateOf(false) }
    var confirmCancel by remember { mutableStateOf(false) }

    fun load() {
        loading = true
        error = null
        // La red va en hilo IO (como en la plantilla del profe)
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val ok = viewModel.loadPlan()
            withContext(Dispatchers.Main) {
                loading = false
                error = if (ok) null else viewModel.errorMessage
            }
        }
    }

    // Ejecuta una acción de red y avisa el resultado
    fun act(success: String, action: suspend () -> Boolean) {
        working = true
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val ok = action()
            withContext(Dispatchers.Main) {
                working = false
                Toast.makeText(context, if (ok) success else viewModel.errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    val plan = viewModel.plan

    if (confirmSubscribe) {
        AlertDialog(
            onDismissRequest = { confirmSubscribe = false },
            title = { Text(text = "Activar Premium") },
            text = {
                Text(
                    text = "El cobro es simulado en esta versión: no se te cobra dinero real. " +
                        "Tu plan Premium se activa de inmediato."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSubscribe = false
                    act("Premium activado. Ya puedes tener más rutas activas") { viewModel.subscribe() }
                }) { Text(text = "Activar") }
            },
            dismissButton = { TextButton(onClick = { confirmSubscribe = false }) { Text(text = "Cancelar") } }
        )
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(text = "Cancelar suscripción") },
            text = { Text(text = "Dejará de renovarse, pero mantienes Premium hasta el fin del periodo ya pagado.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmCancel = false
                    act("Suscripción cancelada") { viewModel.cancelSubscription() }
                }) { Text(text = "Sí, cancelar", color = Error) }
            },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text(text = "Volver") } }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp, bottom = 32.dp, start = 16.dp, end = 24.dp)
    ) {
        ScreenTopBar(title = "Mi plan", onBack = { nav.popBackStack() })
        OfflineBanner(viewModel)

        Column(modifier = Modifier.padding(start = 8.dp)) {
            if (loading && plan == null) {
                Text(text = "Cargando…", style = MaterialTheme.typography.bodyLarge)
            } else if (plan == null) {
                Text(text = error ?: "No se pudo cargar tu plan", color = Error)
                Button(
                    enabled = viewModel.connectivity.isOnline,
                    onClick = { load() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .height(52.dp)
                ) { Text(text = if (viewModel.connectivity.isOnline) "Reintentar" else "Necesitas internet") }
            } else {
                PlanContent(plan)
                val subscription = plan.subscription

                if (!plan.isPremium()) {
                    Button(
                        enabled = !working && viewModel.connectivity.isOnline,
                        onClick = { confirmSubscribe = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                            .height(52.dp)
                    ) {
                        Text(
                            text = when {
                                working -> "Activando…"
                                !viewModel.connectivity.isOnline -> "Necesitas internet"
                                else -> "Activar Premium"
                            }
                        )
                    }
                } else if (subscription != null && subscription.canCancel()) {
                    OutlinedButton(
                        enabled = !working && viewModel.connectivity.isOnline,
                        onClick = { confirmCancel = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                            .height(52.dp)
                    ) { Text(text = if (working) "Cancelando…" else "Cancelar suscripción") }
                }

                OutlinedButton(
                    onClick = { nav.navigate("MyPaths") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(52.dp)
                ) { Text(text = "Ver mis rutas") }

                TextButton(
                    enabled = !loading && !working && viewModel.connectivity.isOnline,
                    onClick = { load() },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text(text = if (loading) "Actualizando…" else "Actualizar") }
            }
        }
    }

}

@Composable
private fun PlanContent(plan: StudentPlan) {
    val premium = plan.isPremium()
    StatusChip(
        text = if (premium) "Plan Premium" else "Plan Free",
        color = if (premium) Success else SkillBlue,
        container = if (premium) SuccessContainer else SkillBlueContainer
    )

    val subscription = plan.subscription
    if (premium && subscription != null) {
        val end = localDate(subscription.currentPeriodEnd)
        Text(
            text = if (subscription.isCancelled()) "Cancelada: mantienes Premium hasta el $end."
            else "Se renueva el $end.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 12.dp)
        )
        if (subscription.price != null) {
            Text(
                text = "${subscription.price} ${subscription.currency ?: ""} por periodo (cobro simulado)",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    } else if (!premium) {
        Text(
            text = "Con Premium tienes más rutas y escalamientos, y plazos de revisión más cortos.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(top = 12.dp)
        )
    }

    Column(modifier = Modifier.padding(top = 24.dp)) {
        PlanQuotaCard(
            label = "LÍMITES DE TU PLAN",
            title = null,
            items = listOf(
                QuotaItem("Rutas activas a la vez", plan.limits.maxActiveRoutes.toString()),
                QuotaItem("Rutas en total", plan.limits.totalRoutesLabel()),
                QuotaItem("Escalamientos al mes", plan.limits.monthlyEscalations.toString()),
                QuotaItem("Plazo de revisión", plan.limits.reviewDeadline?.label() ?: "—")
            )
        )
    }
}

