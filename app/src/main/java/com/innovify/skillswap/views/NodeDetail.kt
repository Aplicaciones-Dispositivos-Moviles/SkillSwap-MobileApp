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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun NodeDetail(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    val node = viewModel.selectedNode

    // T48: busca si ya hay un certificado guardado en el teléfono para este nodo
    LaunchedEffect(node?.id) {
        val nodeId = node?.id ?: return@LaunchedEffect
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            viewModel.loadLocalCertificate(context, nodeId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 50.dp, horizontal = 20.dp)
    ) {
        OfflineBanner(viewModel)
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
            val local = viewModel.localCertificate
            if (local != null && local.nodeId == node.id) {
                Text(
                    text = "Copia en el teléfono: " + File(local.filePath).name +
                        if (local.status == "Pending") " (pendiente de envío)" else " (enviado)"
                )
            }
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

        // T50: el quiz se rinde cuando el nodo está disponible y ya tiene su certificado vinculado
        if (node != null && node.status == "Available" && node.linkedCertificateId != null) {
            Button(
                onClick = { nav.navigate("Quiz") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
            ) {
                Text(text = "Rendir quiz")
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
