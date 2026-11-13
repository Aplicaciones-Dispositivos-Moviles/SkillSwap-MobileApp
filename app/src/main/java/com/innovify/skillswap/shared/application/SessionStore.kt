package com.innovify.skillswap.shared.application

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.innovify.skillswap.iam.domain.AuthUser
import com.innovify.skillswap.iam.infrastructure.iamApi
import com.innovify.skillswap.shared.infrastructure.connectivity.ConnectivityObserver
import com.innovify.skillswap.shared.infrastructure.db.SkillSwapDbHelper
import com.innovify.skillswap.shared.infrastructure.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// State shared by every bounded context: the signed-in user, the last error, the connection state and the
// single local database. Activity-scoped (created in MainActivity) so it lives exactly as long as the
// per-context ViewModels that read it.
class SessionStore : ViewModel() {

    // ------------------------------------------------------------ OFFLINE (T49)
    // Estado de la conexión (se arranca en MainActivity) y base SQLite local.
    val connectivity = ConnectivityObserver()

    // true cuando la ruta que se ve viene de SQLite y no del servidor
    var showingCache by mutableStateOf(false)

    private var dbHelper: SkillSwapDbHelper? = null

    fun db(context: Context): SkillSwapDbHelper {
        return dbHelper ?: SkillSwapDbHelper(context.applicationContext).also { dbHelper = it }
    }

    override fun onCleared() {
        connectivity.stop()
    }

    // ------------------------------------------------------------------ SESIÓN
    var user: AuthUser? by mutableStateOf(null)

    // Último mensaje de error de red. OJO: aquí NO se puede mostrar Toast (hilo IO);
    // la vista lo muestra con withContext(Dispatchers.Main).
    var errorMessage: String = ""

    // Sesión guardada en el teléfono (SharedPreferences) para no pedir el login al reabrir la app.
    fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("skillswap_session", Context.MODE_PRIVATE)

    // Each context registers here the part of its state that a sign-out clears.
    private val signOutListeners = mutableListOf<() -> Unit>()

    fun onSignOut(reset: () -> Unit) {
        signOutListeners.add(reset)
    }

    // Cierra la sesión: borra lo guardado y limpia lo que se ve en pantalla.
    // La copia local de la ruta (SQLite) se conserva para cuando vuelva a entrar.
    fun signOut(context: Context) {
        forgetDeviceToken() // antes de limpiar el JWT: el siguiente usuario de este teléfono no debe recibir sus avisos
        prefs(context).edit().remove("user").apply()
        RetrofitClient.token = null
        user = null
        signOutListeners.forEach { it() }
    }

    fun forgetDeviceToken() {
        val jwt = RetrofitClient.token ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                iamApi.removeDeviceToken("Bearer $jwt")
            } catch (e: Exception) {
                // si falla, el backend olvida el token solo cuando FCM lo reporta inválido
            }
        }
    }
}
