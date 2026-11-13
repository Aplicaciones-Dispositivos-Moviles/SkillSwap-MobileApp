package com.innovify.skillswap.shared.application

import android.content.Context
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.innovify.skillswap.shared.domain.ApiError
import com.innovify.skillswap.shared.infrastructure.connectivity.ConnectivityObserver
import com.innovify.skillswap.shared.infrastructure.db.SkillSwapDbHelper
import retrofit2.Response

// Base of the per-context ViewModels. The error message, the connection state and the database live in the
// shared SessionStore, so a screen reads the same errorMessage whichever ViewModel made the failing call.
abstract class SessionAwareViewModel(protected val session: SessionStore) : ViewModel() {

    val connectivity: ConnectivityObserver get() = session.connectivity

    val showingCache: Boolean get() = session.showingCache

    var errorMessage: String
        get() = session.errorMessage
        set(value) {
            session.errorMessage = value
        }

    protected fun db(context: Context): SkillSwapDbHelper = session.db(context)

    // Texto del error del backend (ProblemDetail.detail) o uno por defecto.
    protected fun messageFrom(response: Response<*>, fallback: String): String {
        return try {
            val raw = response.errorBody()?.string()
            val error = Gson().fromJson(raw, ApiError::class.java)
            error?.detail ?: error?.title ?: fallback
        } catch (e: Exception) {
            fallback
        }
    }
}
