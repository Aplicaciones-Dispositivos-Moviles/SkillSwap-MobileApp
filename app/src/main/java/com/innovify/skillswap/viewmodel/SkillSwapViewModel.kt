package com.innovify.skillswap.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.innovify.skillswap.model.beans.AuthUser
import com.innovify.skillswap.model.beans.LearningPath
import com.innovify.skillswap.model.beans.PathNode
import com.innovify.skillswap.model.beans.SignInRequest
import com.innovify.skillswap.model.client.RetrofitClient

class SkillSwapViewModel : ViewModel() {

    // ------------------------------------------------------------------ SESIÓN
    var user: AuthUser? by mutableStateOf(null)

    // Último mensaje de error de red. OJO: aquí NO se puede mostrar Toast (hilo IO);
    // la vista lo muestra con withContext(Dispatchers.Main).
    var errorMessage: String = ""

    // suspend: la VISTA lo llama dentro de viewModelScope.launch(Dispatchers.IO).
    // Devuelve true si el login fue correcto.
    suspend fun signIn(username: String, password: String): Boolean {
        return try {
            val response = RetrofitClient.webService.signIn(SignInRequest(username, password))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                user = body
                RetrofitClient.token = body.token
                true
            } else {
                errorMessage = when (response.code()) {
                    401 -> "Usuario o contraseña incorrectos"
                    403 -> "Verifica tu correo institucional antes de ingresar"
                    else -> "No se pudo iniciar sesión (${response.code()})"
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // ------------------------------------------------------------------- RUTA
    // Ruta que se muestra (la más reciente del Estudiante) y el nodo elegido.
    var path: LearningPath? by mutableStateOf(null)
    var selectedNode: PathNode? by mutableStateOf(null)

    // Devuelve true si la consulta fue correcta (aunque el Estudiante aún no tenga ruta: path = null).
    suspend fun loadPath(): Boolean {
        val studentId = user?.id ?: return false
        return try {
            val response = RetrofitClient.webService.getLearningPaths(studentId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                path = body.firstOrNull()
                true
            } else {
                errorMessage = "No se pudo cargar la ruta (${response.code()})"
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

}
