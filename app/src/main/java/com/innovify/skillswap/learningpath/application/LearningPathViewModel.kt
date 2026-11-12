package com.innovify.skillswap.learningpath.application

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.innovify.skillswap.learningpath.domain.DeclareGoalRequest
import com.innovify.skillswap.learningpath.domain.LearningPath
import com.innovify.skillswap.learningpath.domain.PathNode
import com.innovify.skillswap.learningpath.domain.SkillOption
import com.innovify.skillswap.learningpath.domain.SkillOptionsRequest
import com.innovify.skillswap.learningpath.infrastructure.LearningPathDao
import com.innovify.skillswap.learningpath.infrastructure.learningPathApi
import com.innovify.skillswap.shared.application.SessionAwareViewModel
import com.innovify.skillswap.shared.application.SessionStore
import com.innovify.skillswap.subscription.application.SubscriptionViewModel
import retrofit2.Response

// Next step of the path: the first available node in path order (null when none is available).
fun LearningPath.nextStep(): PathNode? =
    nodes.orEmpty().sortedBy { it.order }.firstOrNull { it.status == "Available" }

// Every node the Student can see: those of "Mis rutas" plus the path on screen (it may come from the cache).
fun knownNodes(path: LearningPath?, myPaths: List<LearningPath>): List<PathNode> =
    (myPaths.flatMap { it.nodes.orEmpty() } + path?.nodes.orEmpty()).distinctBy { it.id }

// learningpath: the path on screen and its selected node, the goal and its skill options, and "Mis rutas".
// Listing the paths also reads the plan, and a 409 on declareGoal is shown as the plan limit, so it receives
// the SubscriptionViewModel.
class LearningPathViewModel(
    session: SessionStore,
    private val subscription: SubscriptionViewModel
) : SessionAwareViewModel(session) {

    // ------------------------------------------------------------------- RUTA
    // Ruta que se muestra (la más reciente del Estudiante) y el nodo elegido.
    var path: LearningPath? by mutableStateOf(null)
    var selectedNode: PathNode? by mutableStateOf(null)

    // Primero la red; si no hay conexión o el servidor falla (5xx), se muestra la última ruta guardada en SQLite.
    // Devuelve true si hay algo que mostrar (aunque el Estudiante aún no tenga ruta: path = null).
    suspend fun loadPath(context: Context): Boolean {
        val studentId = session.user?.id ?: return false
        if (!connectivity.isOnline) {
            return loadPathFromCache(context, studentId, "Sin conexión y no hay una ruta guardada en el teléfono")
        }
        return try {
            val response = learningPathApi.getLearningPaths(studentId)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                myPaths = body
                // La ruta elegida en "Mis rutas"; si no hay, la activa; si no, la más reciente
                val latest = body.firstOrNull { it.id == selectedPathId }
                    ?: body.firstOrNull { it.status == "Active" }
                    ?: body.firstOrNull()
                path = latest
                session.showingCache = false
                // Guardar en SQLite es opcional: si falla, la ruta igual se muestra
                try {
                    if (latest != null) LearningPathDao(db(context)).savePath(latest) else LearningPathDao(db(context)).clearPath(studentId)
                } catch (e: Exception) {
                }
                true
            } else if (response.code() == 401) {
                // El token venció: se cierra la sesión y la app vuelve al Login
                session.signOut(context)
                errorMessage = "Tu sesión venció. Inicia sesión de nuevo"
                false
            } else if (response.code() >= 500) {
                loadPathFromCache(context, studentId, "No se pudo cargar la ruta (${response.code()})")
            } else {
                errorMessage = "No se pudo cargar la ruta (${response.code()})"
                false
            }
        } catch (e: Exception) {
            loadPathFromCache(context, studentId, "No se pudo conectar con el servidor")
        }
    }

    // Lee la última ruta guardada. Si no hay ninguna, errorMessage explica por qué no se puede mostrar nada.
    private fun loadPathFromCache(context: Context, studentId: Int, error: String): Boolean {
        val cached = try {
            LearningPathDao(db(context)).getPath(studentId)
        } catch (e: Exception) {
            null
        }
        return if (cached != null) {
            path = cached
            session.showingCache = true
            true
        } else {
            errorMessage = error
            false
        }
    }

    // ---------------------------------------------------- META Y HABILIDAD (T47)
    // Meta escrita por el Estudiante y las habilidades candidatas que propone el backend (T46).
    var goal: String by mutableStateOf("")
    var skillOptions: List<SkillOption> by mutableStateOf(emptyList())

    // suspend: la vista lo llama en Dispatchers.IO. true si hay opciones para elegir.
    // Si el backend aún no tiene el endpoint (404), skillOptions queda vacío y la vista construye la ruta directo.
    suspend fun loadSkillOptions(goalText: String): Boolean {
        goal = goalText
        skillOptions = emptyList()
        return try {
            val response = learningPathApi.getSkillOptions(SkillOptionsRequest(goalText))
            val body = response.body()
            if (response.isSuccessful && body != null && body.isNotEmpty()) {
                skillOptions = body.sortedByDescending { it.affinity }
                true
            } else if (response.isSuccessful) {
                errorMessage = "No encontramos habilidades para esta meta. Cuéntanos qué quieres construir o para qué trabajo"
                false
            } else {
                errorMessage = when (response.code()) {
                    400 -> "Escribe tu meta (máximo 500 caracteres)"
                    409 -> messageFrom(response, "Ya demostraste las habilidades de esta meta. Prueba con una meta más avanzada")
                    422 -> messageFrom(response, "No encontramos habilidades para esta meta. Cuéntanos qué quieres construir o para qué trabajo")
                    else -> messageFrom(response, "No se pudo interpretar tu meta (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // Construye la ruta con la habilidad confirmada (null = el backend interpreta la meta).
    // true si se creó; la nueva ruta queda en path y en SQLite.
    suspend fun declareGoal(context: Context, skillTag: String?): Boolean {
        subscription.planLimitMessage = null
        return try {
            val response = learningPathApi.declareGoal(DeclareGoalRequest(goal, skillTag))
            val body = response.body()
            if (response.isSuccessful && body != null) {
                path = body
                selectedPathId = body.id
                selectedNode = null
                session.showingCache = false
                try {
                    LearningPathDao(db(context)).savePath(body)
                } catch (e: Exception) {
                }
                true
            } else {
                errorMessage = when (response.code()) {
                    409 -> {
                        // 409 PlanLimitReached: la pantalla muestra una tarjeta con "Ver mi plan" en vez de un Toast
                        val message = messageFrom(response, "Alcanzaste el límite de rutas de tu plan")
                        subscription.planLimitMessage = message
                        message
                    }
                    422 -> messageFrom(response, "No encontramos habilidades para esta meta")
                    else -> messageFrom(response, "No se pudo crear la ruta (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // ------------------------------------------------------------- MIS RUTAS
    // Todas las rutas del Estudiante (la más reciente primero) y la que eligió abrir (null = la activa)
    var myPaths: List<LearningPath> by mutableStateOf(emptyList())
    var selectedPathId: Int? = null

    init {
        session.onSignOut {
            myPaths = emptyList()
            selectedPathId = null
            path = null
            selectedNode = null
        }
    }

    // Lee todas las rutas y el plan (para saber cuántas permite). true si llegaron ambos.
    suspend fun loadMyPaths(): Boolean {
        val studentId = session.user?.id
        if (studentId == null) {
            errorMessage = "Inicia sesión para ver tus rutas"
            return false
        }
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para ver tus rutas"
            return false
        }
        try {
            val response = learningPathApi.getLearningPaths(studentId)
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                errorMessage = messageFrom(response, "No se pudieron cargar tus rutas (${response.code()})")
                return false
            }
            myPaths = body
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            return false
        }
        return subscription.loadPlan()
    }

    // Deja esa ruta como la que muestra la pantalla principal
    fun openPath(chosen: LearningPath) {
        path = chosen
        selectedPathId = chosen.id
        selectedNode = null
        session.showingCache = false
    }

    suspend fun pausePath(pathId: Int): Boolean {
        return changePath("No se pudo pausar la ruta") { learningPathApi.pausePath(pathId) }
    }

    suspend fun resumePath(pathId: Int): Boolean {
        return changePath("No se pudo reanudar la ruta") { learningPathApi.resumePath(pathId) }
    }

    // Pausa o reanuda y deja al día la lista y la ruta que se está mostrando
    private suspend fun changePath(failure: String, call: suspend () -> Response<LearningPath>): Boolean {
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para cambiar tus rutas"
            return false
        }
        return try {
            val response = call()
            val body = response.body()
            if (response.isSuccessful && body != null) {
                if (path?.id == body.id) path = body
                myPaths = myPaths.map { if (it.id == body.id) body else it }
                true
            } else {
                errorMessage = when (response.code()) {
                    403 -> "Esta ruta no es tuya"
                    404 -> "No encontramos la ruta"
                    409 -> messageFrom(response, "Tu plan no permite este cambio")
                    else -> messageFrom(response, "$failure (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }
}
