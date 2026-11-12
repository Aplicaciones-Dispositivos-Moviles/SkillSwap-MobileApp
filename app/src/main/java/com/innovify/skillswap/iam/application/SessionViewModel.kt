package com.innovify.skillswap.iam.application

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewModelScope
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.innovify.skillswap.assessment.application.VerificationCaseViewModel
import com.innovify.skillswap.finaldemonstration.application.FinalDemonstrationViewModel
import com.innovify.skillswap.iam.domain.AuthUser
import com.innovify.skillswap.iam.domain.DeviceTokenRequest
import com.innovify.skillswap.iam.domain.SignInRequest
import com.innovify.skillswap.iam.domain.SignUpRequest
import com.innovify.skillswap.iam.infrastructure.BiometricVault
import com.innovify.skillswap.iam.infrastructure.iamApi
import com.innovify.skillswap.shared.application.SessionAwareViewModel
import com.innovify.skillswap.shared.application.SessionStore
import com.innovify.skillswap.shared.infrastructure.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// iam: sign-up, sign-in and sign-out, biometric access and push notifications. A tapped notification selects
// the case or the demonstration to open, so it receives those two ViewModels.
class SessionViewModel(
    session: SessionStore,
    private val verificationCases: VerificationCaseViewModel,
    private val demonstrations: FinalDemonstrationViewModel
) : SessionAwareViewModel(session) {

    // ------------------------------------------------------------------ SESIÓN
    val user: AuthUser? get() = session.user

    // Se llama al abrir la app (MainActivity): recupera el usuario y el token guardados, si hay.
    fun restoreSession(context: Context) {
        if (session.user != null) return // la app ya estaba abierta (por ejemplo, tras girar la pantalla)
        val raw = session.prefs(context).getString("user", null) ?: return
        try {
            val saved = Gson().fromJson(raw, AuthUser::class.java)
            if (saved != null && saved.token.isNotEmpty()) {
                session.user = saved
                RetrofitClient.token = saved.token
            }
        } catch (e: Exception) {
            session.prefs(context).edit().remove("user").apply()
        }
    }

    // Cierra la sesión: borra lo guardado y limpia lo que se ve en pantalla (ver SessionStore.signOut).
    fun signOut(context: Context) {
        session.signOut(context)
    }

    // suspend: la VISTA lo llama dentro de viewModelScope.launch(Dispatchers.IO).
    // Devuelve true si el login fue correcto.
    // Usuario recién registrado: el Login lo muestra ya escrito al volver del registro
    var registeredUsername: String by mutableStateOf("")

    // Registro de un Estudiante nuevo. No inicia sesión: primero debe verificar su correo.
    suspend fun signUp(username: String, email: String, password: String): Boolean {
        return try {
            val response = iamApi.signUp(SignUpRequest(username, email, password))
            if (response.isSuccessful) {
                registeredUsername = username
                true
            } else {
                errorMessage = when (response.code()) {
                    400 -> messageFrom(response, "Revisa los datos. El correo debe ser institucional (.edu.pe)")
                    409 -> messageFrom(response, "Ese usuario o correo ya está registrado")
                    else -> "No se pudo crear la cuenta (${response.code()})"
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    suspend fun signIn(username: String, password: String, context: Context): Boolean {
        credentialsRejected = false
        return try {
            val response = iamApi.signIn(SignInRequest(username, password))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                session.user = body
                RetrofitClient.token = body.token
                session.prefs(context).edit().putString("user", Gson().toJson(body)).apply()
                true
            } else {
                errorMessage = when (response.code()) {
                    401 -> {
                        credentialsRejected = true
                        "Usuario o contraseña incorrectos"
                    }
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

    // ------------------------------------------------------ ACCESO BIOMÉTRICO (US03)
    // true si el último signIn fue rechazado por usuario o contraseña (401)
    var credentialsRejected = false

    // true si este usuario tiene el acceso con huella o rostro activado en este teléfono
    var biometricEnabled: Boolean by mutableStateOf(false)

    fun refreshBiometric(context: Context) {
        biometricEnabled = BiometricVault.isEnabled(context, session.user?.username)
    }

    // Comprueba la contraseña del usuario con sesión iniciada, sin cambiar la sesión (al activar la biometría).
    suspend fun checkPassword(password: String): Boolean {
        val username = session.user?.username ?: return false
        return try {
            val response = iamApi.signIn(SignInRequest(username, password))
            if (!response.isSuccessful) {
                errorMessage = if (response.code() == 401) "La contraseña no es correcta"
                else "No se pudo verificar la contraseña (${response.code()})"
            }
            response.isSuccessful
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // ------------------------------------------------------ NOTIFICACIONES PUSH
    // Ruta a la que hay que ir porque el Estudiante tocó una notificación (NavSkillSwap la consume y la limpia)
    var pendingRoute: String? by mutableStateOf(null)

    // El permiso se pide una sola vez; si lo niegan, la app sigue funcionando sin avisos.
    fun shouldAskNotificationPermission(context: Context): Boolean =
        !session.prefs(context).getBoolean("notification_permission_asked", false)

    fun markNotificationPermissionAsked(context: Context) {
        session.prefs(context).edit().putBoolean("notification_permission_asked", true).apply()
    }

    // Con el permiso concedido envía el token de este teléfono al backend; sin permiso, lo borra.
    // Se llama tras iniciar sesión, al reabrir la app y al recuperar internet (el PUT se puede repetir).
    fun syncDeviceToken(context: Context) {
        if (session.user == null || !connectivity.isOnline) return
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            session.forgetDeviceToken()
            return
        }
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    iamApi.registerDeviceToken(DeviceTokenRequest(token))
                } catch (e: Exception) {
                    // sin red: se reintenta cuando vuelva internet
                }
            }
        }
    }

    // data.type de la notificación tocada -> pantalla que se abre
    fun openFromNotification(type: String, caseId: Int?, demonstrationId: Int? = null) {
        pendingRoute = when (type) {
            "MiniProjectEscalatedByAi", "VerificationCaseResolved" ->
                if (caseId != null) {
                    verificationCases.selectedCaseId = caseId
                    "CaseStatus"
                } else "Path"
            "MiniProjectApprovedByAi" -> "Path"
            "FinalDemonstrationApproved", "FinalDemonstrationRejected" -> {
                demonstrations.selectedDemonstrationId = demonstrationId
                "Demonstration"
            }
            else -> null
        }
    }
}
