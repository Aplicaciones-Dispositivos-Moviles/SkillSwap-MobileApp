package com.innovify.skillswap.model.beans

// Reglas de rutas del plan (informe: plan gratuito = 1 ruta activa a la vez y hasta 3 en total; plan mensual =
// hasta 3 activas y sin tope). Una ruta avanzada canjeada con SkillCredits no cuenta en ninguno de los dos límites.
const val NEW_PATH_ALLOWED = "Allowed"
const val NEW_PATH_NEEDS_PAUSE = "NeedsPause" // el máximo de rutas activas se alcanzó: hay que pausar una
const val NEW_PATH_TOTAL_LIMIT = "TotalLimit" // el máximo de rutas en total se alcanzó

private fun counted(paths: List<LearningPath>): List<LearningPath> = paths.filter { !it.advanced }

fun activePathCount(paths: List<LearningPath>): Int = counted(paths).count { it.status == "Active" }

fun totalPathCount(paths: List<LearningPath>): Int = counted(paths).size

// Qué pasa si el Estudiante quiere crear otra ruta con los límites de su plan
fun newPathDecision(limits: PlanLimits, paths: List<LearningPath>): String {
    val max = limits.maxTotalRoutes
    return when {
        max != null && totalPathCount(paths) >= max -> NEW_PATH_TOTAL_LIMIT
        activePathCount(paths) >= limits.maxActiveRoutes -> NEW_PATH_NEEDS_PAUSE
        else -> NEW_PATH_ALLOWED
    }
}

// Una ruta pausada se puede reanudar si el plan admite otra ruta activa (la avanzada nunca ocupa un cupo)
fun canResumePath(limits: PlanLimits, paths: List<LearningPath>, path: LearningPath): Boolean {
    return path.advanced || activePathCount(paths) < limits.maxActiveRoutes
}

fun pathStatusLabel(status: String): String = when (status) {
    "Active" -> "Activa"
    "Paused" -> "Pausada"
    "Completed" -> "Completada"
    else -> status
}

fun LearningPath.completedNodeCount(): Int = (nodes ?: emptyList()).count { it.status == "Completed" }

fun LearningPath.nodeCount(): Int = (nodes ?: emptyList()).size
