package com.innovify.skillswap.views

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.innovify.skillswap.viewmodel.SkillSwapViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PathList(viewModel: SkillSwapViewModel, context: Context, nav: NavHostController) {

    var loading by remember { mutableStateOf(true) }

    // Carga la ruta UNA vez al entrar a la pantalla
    LaunchedEffect(Unit) {
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            val ok = viewModel.loadPath()
            withContext(Dispatchers.Main) {
                loading = false
                if (!ok) {
                    Toast.makeText(context, viewModel.errorMessage, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .padding(vertical = 30.dp)
    ) {
        Text(text = "MI RUTA", style = MaterialTheme.typography.headlineSmall)

        val path = viewModel.path
        if (loading) {
            Text(text = "Cargando…", modifier = Modifier.padding(top = 20.dp))
        } else if (path == null) {
            Text(
                text = "Aún no tienes una ruta de aprendizaje.",
                modifier = Modifier.padding(top = 20.dp)
            )
        } else {
            Text(text = "Meta: ${path.goal}", modifier = Modifier.padding(top = 10.dp))
            Text(text = "Estado: ${path.status}", modifier = Modifier.padding(bottom = 10.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items((path.nodes ?: emptyList()).sortedBy { it.order }) { node ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.selectedNode = node
                                nav.navigate("Node")
                            }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "${node.order}. ${node.skillName}",
                                fontWeight = FontWeight.Bold
                            )
                            Text(text = "Estado: ${node.status}")
                        }
                    }
                }
            }
        }
    }

}
