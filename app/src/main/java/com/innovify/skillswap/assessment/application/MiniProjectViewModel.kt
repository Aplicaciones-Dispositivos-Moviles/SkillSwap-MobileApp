package com.innovify.skillswap.assessment.application

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.innovify.skillswap.assessment.domain.MiniProject
import com.innovify.skillswap.assessment.domain.MiniProjectSubmission
import com.innovify.skillswap.assessment.domain.SubmitMiniProjectRequest
import com.innovify.skillswap.assessment.infrastructure.MiniProjectDao
import com.innovify.skillswap.assessment.infrastructure.assessmentApi
import com.innovify.skillswap.learningpath.application.LearningPathViewModel
import com.innovify.skillswap.shared.application.SessionAwareViewModel
import com.innovify.skillswap.shared.application.SessionStore
import kotlinx.coroutines.delay

// assessment: the node mini-project, its delivery and the AI evaluation. An approved delivery completes the node,
// so it receives the LearningPathViewModel to reload the path.
class MiniProjectViewModel(
    session: SessionStore,
    private val learningPath: LearningPathViewModel
) : SessionAwareViewModel(session) {

    // ------------------------------------------------------------ PROYECTO (T55)
    // Proyecto abierto del nodo y su entrega (null mientras no se haya entregado)
    var project: MiniProject? by mutableStateOf(null)
    var projectSubmission: MiniProjectSubmission? by mutableStateOf(null)

    init {
        session.onSignOut {
            project = null
            projectSubmission = null
        }
    }

    private fun showProject(value: MiniProject, submission: MiniProjectSubmission?) {
        project = value
        projectSubmission = submission
    }

    // Abre el proyecto del nodo. Con internet pregunta al backend (y genera uno con IA si todavía no hay, o
    // si forceNew=true porque el anterior venció); sin internet muestra el último guardado en SQLite.
    suspend fun openProject(context: Context, nodeId: Int, forceNew: Boolean = false): Boolean {
        val db = db(context)
        if (!connectivity.isOnline) {
            val cached = try {
                MiniProjectDao(db).findMiniProject(nodeId)
            } catch (e: Exception) {
                null
            }
            return if (cached != null) {
                showProject(cached, try { MiniProjectDao(db).findSubmission(nodeId) } catch (e: Exception) { null })
                true
            } else {
                errorMessage = "Necesitas internet para ver el proyecto la primera vez"
                false
            }
        }
        return try {
            val latest = if (forceNew) null else assessmentApi.getMiniProject(nodeId)
            val response = if (latest == null || latest.code() == 404) {
                assessmentApi.generateMiniProject(nodeId, false)
            } else {
                latest
            }
            val body = response.body()
            if (response.isSuccessful && body != null) {
                try {
                    MiniProjectDao(db).saveMiniProject(body)
                } catch (e: Exception) {
                }
                showProject(body, try { MiniProjectDao(db).findSubmission(nodeId) } catch (e: Exception) { null })
                true
            } else {
                errorMessage = when (response.code()) {
                    503 -> "El servicio de IA no pudo generar el proyecto. Intenta de nuevo en un momento"
                    else -> messageFrom(response, "No se pudo abrir el proyecto (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo abrir el proyecto. Revisa tu conexión"
            false
        }
    }

    // Entrega el enlace del repositorio. Solo se entrega una vez y requiere internet. true si el backend lo recibió.
    suspend fun submitProject(context: Context, repositoryUrl: String): Boolean {
        val current = project ?: return false
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para entregar el proyecto"
            return false
        }
        return try {
            val response = assessmentApi.submitMiniProject(
                current.id, SubmitMiniProjectRequest(repositoryUrl)
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                try {
                    MiniProjectDao(db(context)).saveSubmission(current.pathNodeId, body)
                } catch (e: Exception) {
                }
                projectSubmission = body
                true
            } else {
                errorMessage = when (response.code()) {
                    400 -> messageFrom(response, "El enlace del repositorio no es válido")
                    else -> messageFrom(response, "No se pudo entregar el proyecto (${response.code()})")
                }
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo entregar el proyecto. Revisa tu conexión"
            false
        }
    }

    // ------------------------------------------------ EVALUACIÓN DE LA ENTREGA (T56)
    // La IA evalúa la entrega en segundo plano: se consulta cada 3 s, máximo 30 veces (≈ 90 s).
    private val evaluationPollAttempts = 30
    private val evaluationPollMillis = 3000L

    // Vuelve a pedir la entrega al backend (con su evaluación preliminar) y la guarda. true si la leyó.
    suspend fun refreshSubmission(context: Context): Boolean {
        val current = projectSubmission ?: return false
        if (!connectivity.isOnline) {
            errorMessage = "Necesitas internet para ver el resultado de la evaluación"
            return false
        }
        return try {
            val response = assessmentApi.getMiniProjectSubmission(current.miniProjectId, current.id)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                try {
                    MiniProjectDao(db(context)).saveSubmission(current.pathNodeId, body)
                } catch (e: Exception) {
                }
                projectSubmission = body
                true
            } else {
                errorMessage = messageFrom(response, "No se pudo leer el resultado de la evaluación (${response.code()})")
                false
            }
        } catch (e: Exception) {
            errorMessage = "No se pudo conectar con el servidor"
            false
        }
    }

    // Espera el resultado de la evaluación. true si ya lo hay; false si se acabó el tiempo (el Estudiante
    // puede volver a pedirlo). Si la IA aprobó, el nodo quedó completado en el backend: se recarga la ruta.
    suspend fun waitForEvaluation(context: Context): Boolean {
        for (attempt in 1..evaluationPollAttempts) {
            if (refreshSubmission(context) && projectSubmission?.isEvaluating() == false) {
                if (projectSubmission?.evaluationStatus == "Approved") {
                    val nodeId = learningPath.selectedNode?.id
                    if (learningPath.loadPath(context)) {
                        learningPath.selectedNode =
                            learningPath.path?.nodes?.firstOrNull { it.id == nodeId } ?: learningPath.selectedNode
                    }
                }
                return true
            }
            if (attempt < evaluationPollAttempts) delay(evaluationPollMillis)
        }
        return false
    }
}
