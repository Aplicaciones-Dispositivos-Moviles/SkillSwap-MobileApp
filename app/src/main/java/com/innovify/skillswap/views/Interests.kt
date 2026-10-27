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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.model.beans.MAX_INTERESTS
import com.innovify.skillswap.model.beans.MAX_INTEREST_LENGTH
import com.innovify.skillswap.model.beans.cleanInterest
import com.innovify.skillswap.model.beans.interestError
import com.innovify.skillswap.ui.theme.Error
import com.innovify.skillswap.ui.theme.NeutralBorder
import com.innovify.skillswap.ui.theme.TextSecondary
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// US04 · Perfil de intereses: de 1 a 10 temas y una descripción opcional; ayudan a recomendar habilidades.
@Composable
fun Interests(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var topics by remember { mutableStateOf(viewModel.interests) }
    var input by rememberSaveable { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }
    var description by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        // La red va en hilo IO (como en la plantilla del profe)
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val ok = viewModel.loadInterests()
            withContext(Dispatchers.Main) {
                loading = false
                if (ok) topics = viewModel.interests
                else Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun add() {
        val error = interestError(topics, input)
        if (error != null) {
            inputError = error
        } else {
            topics = topics + cleanInterest(input)
            input = ""
            inputError = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp, bottom = 32.dp, start = 16.dp, end = 24.dp)
    ) {
        ScreenTopBar(title = "Mis intereses", onBack = { nav.popBackStack() })
        OfflineBanner(viewModel)

        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = "Cuéntanos qué quieres aprender. Usamos tus temas para recomendarte habilidades.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    label = { Text(text = "Tema (${topics.size}/$MAX_INTERESTS)") },
                    singleLine = true,
                    enabled = !saving && !loading,
                    isError = inputError != null,
                    supportingText = { Text(text = inputError ?: "Máximo $MAX_INTEREST_LENGTH caracteres") },
                    onValueChange = {
                        input = it
                        inputError = null
                    },
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    enabled = !saving && !loading,
                    onClick = { add() },
                    modifier = Modifier
                        .padding(start = 8.dp, top = 8.dp)
                        .height(52.dp)
                ) { Text(text = "Agregar") }
            }

            if (loading) {
                Text(text = "Cargando tus intereses…", color = TextSecondary, modifier = Modifier.padding(top = 8.dp))
            } else if (topics.isEmpty()) {
                Text(
                    text = "Agrega al menos un tema.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            topics.forEach { topic ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = topic, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    TextButton(enabled = !saving, onClick = { topics = topics - topic }) {
                        Text(text = "Quitar", color = Error)
                    }
                }
                HorizontalDivider(color = NeutralBorder)
            }

            OutlinedTextField(
                value = description,
                label = { Text(text = "Descripción (opcional)") },
                enabled = !saving && !loading,
                minLines = 3,
                onValueChange = { description = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            )

            Button(
                enabled = !saving && !loading && topics.isNotEmpty() && viewModel.connectivity.isOnline,
                onClick = {
                    saving = true
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        val ok = viewModel.saveInterests(topics, description)
                        withContext(Dispatchers.Main) {
                            saving = false
                            if (ok) {
                                Toast.makeText(context, "Intereses guardados", Toast.LENGTH_SHORT).show()
                                nav.popBackStack()
                            } else {
                                Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .height(52.dp)
            ) {
                Text(
                    text = when {
                        saving -> "Guardando…"
                        !viewModel.connectivity.isOnline -> "Necesitas internet para guardar"
                        else -> "Guardar intereses"
                    }
                )
            }
        }
    }

}
