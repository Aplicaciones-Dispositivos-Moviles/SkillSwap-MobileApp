package com.innovify.skillswap.model.beans

// Body de POST api/v1/learning-paths/skill-options: la meta en palabras del Estudiante (1 a 500 caracteres).
data class SkillOptionsRequest(
    var goal: String
)

// T46 (backend): una habilidad candidata del catálogo para la meta, ordenadas por afinidad (0 a 1).
// Contrato propuesto para T46; si cambia en el backend, se ajustan estos campos.
data class SkillOption(
    var skillTag: String,
    var skillName: String,
    var description: String?,
    var nodeCount: Int?,
    var estimatedWeeks: Int?,
    var affinity: Double
)

// Body de POST api/v1/learning-paths. skillTag = habilidad confirmada por el Estudiante (T47);
// null cuando el backend interpreta la meta por su cuenta.
data class DeclareGoalRequest(
    var goal: String,
    var skillTag: String?
)
