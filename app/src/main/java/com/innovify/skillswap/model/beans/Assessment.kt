package com.innovify.skillswap.model.beans

// QuestionResource del backend: la respuesta correcta nunca viaja al teléfono.
data class Question(
    var question: String,
    var answers: List<String>
)

// AssessmentBlueprintResource: el quiz generado con IA para un nodo.
data class AssessmentBlueprint(
    var id: Int,
    var pathNodeId: Int,
    var skillTag: String,
    var skillName: String,
    var questions: List<Question>,
    var generatedAt: String?
)

// SubmitAssessmentAttemptResource: un índice de opción (0 a 3) por pregunta, en el orden del quiz.
data class SubmitAttemptRequest(
    var blueprintId: Int,
    var selectedAnswers: List<Int>
)

// PlanLimitReachedResource: el intento falló pero el plan del Estudiante ya agotó sus escalaciones del mes.
data class PlanLimitReached(
    var limit: String,
    var plan: String,
    var max: Int,
    var current: Int,
    var upgradeAvailable: Boolean
)

// FailedQuestionResource: pregunta fallada y lo que eligió el Estudiante. Hoy el backend solo la entrega al
// Verificador; el campo failedQuestions del intento llega null hasta que el backend lo exponga al Estudiante.
data class FailedQuestion(
    var position: Int,
    var question: String,
    var answers: List<String>,
    var selectedAnswer: Int
)

// AssessmentAttemptResource: resultado calificado del intento.
data class AssessmentAttempt(
    var id: Int,
    var blueprintId: Int,
    var studentId: Int,
    var score: Int,
    var totalQuestions: Int,
    var passed: Boolean,
    var completedAt: String?,
    var verificationCaseId: Int?,
    var verificationCaseStatus: String?,
    var planLimitReached: PlanLimitReached?,
    var weakSubTopic: String?,
    var failedQuestions: List<FailedQuestion>?
)

// T50: respuestas guardadas en el teléfono. answers[i] = opción elegida en la pregunta i (-1 = sin responder).
// status: "Draft" (respondiendo) | "Pending" (enviado pero sin confirmar, se reenvía) | "Sent" (calificado)
data class QuizProgress(
    var blueprintId: Int,
    var nodeId: Int,
    var answers: List<Int>,
    var status: String,
    var resultJson: String?
)
