package com.innovify.skillswap.model.beans

// RubricCriterionResource: un criterio con el que se evalúa el proyecto.
data class RubricCriterion(
    var name: String,
    var description: String,
    var weight: Int
)

// MiniProjectResource: el proyecto generado con IA para un nodo. Fechas en UTC (ISO-8601).
data class MiniProject(
    var id: Int,
    var pathNodeId: Int,
    var skillTag: String,
    var skillName: String,
    var title: String,
    var statement: String,
    var rubric: List<RubricCriterion>,
    var passingScore: Int, // mínimo sobre 100 para aprobar
    var createdAt: String?,
    var dueAt: String?
)

// SubmitMiniProjectResource: enlace http(s) del repositorio, hasta 500 caracteres.
data class SubmitMiniProjectRequest(
    var repositoryUrl: String
)

// MiniProjectSubmissionResource: la entrega recibida (la evaluación se hace después).
data class MiniProjectSubmission(
    var id: Int,
    var miniProjectId: Int,
    var pathNodeId: Int,
    var skillTag: String,
    var repositoryUrl: String,
    var submittedAt: String?
)
