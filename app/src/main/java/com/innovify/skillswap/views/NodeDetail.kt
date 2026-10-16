package com.innovify.skillswap.views

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.innovify.skillswap.viewmodel.SkillSwapViewModel

@Composable
fun NodeDetail(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    val node = viewModel.selectedNode

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 50.dp, horizontal = 20.dp)
    ) {
        if (node == null) {
            Text(text = "Selecciona un nodo desde la ruta.")
        } else {
            Text(text = node.skillName, style = MaterialTheme.typography.headlineSmall)
            Text(text = "Habilidad: ${node.skillTag}", modifier = Modifier.padding(top = 10.dp))
            Text(text = "Orden en la ruta: ${node.order}")
            Text(text = "Estado: ${node.status}")
            Text(
                text = "Requisitos: " + (node.prerequisiteSkillTags?.joinToString(", ")
                    ?.ifBlank { null } ?: "ninguno")
            )
            Text(
                text = "Certificado: " + (node.linkedCertificateId?.let { "#$it" } ?: "sin certificado")
            )
        }

        // T44: solo un nodo disponible y sin certificado puede recibir uno
        if (node != null && node.status == "Available" && node.linkedCertificateId == null) {
            Button(
                onClick = { nav.navigate("Certificate") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            ) {
                Text(text = "Subir certificado")
            }
        }

        OutlinedButton(
            onClick = { nav.popBackStack() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
        ) {
            Text(text = "Volver a la ruta")
        }
    }

}
