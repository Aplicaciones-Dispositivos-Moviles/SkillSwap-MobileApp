package com.innovify.skillswap.model.beans

// VerificationCaseResource: un caso de verificación. caseType = "Quiz" | "MiniProject".
// attemptId solo viene en casos de quiz; submissionId solo en casos de miniproyecto.
// status = "Pending" | "Assigned" | "Resolved"; decision = "Approved" | "Rejected" (null hasta resolverse).
data class VerificationCase(
    var id: Int,
    var attemptId: Int?,
    var submissionId: Int?,
    var studentId: Int,
    var verifierUserId: Int?,
    var pathNodeId: Int,
    var skillTag: String,
    var caseType: String,
    var status: String,
    var decision: String?,
    var rubricNotes: String?,
    var evidenceUrl: String?,
    var appealCount: Int,
    var openedAt: String?,
    var reviewDueAt: String?,
    var assignedAt: String?,
    var resolvedAt: String?
) {
    // El Estudiante solo puede apelar un caso rechazado, y una sola vez (el backend responde 409 si no).
    fun canAppeal(): Boolean {
        return status == "Resolved" && decision == "Rejected" && appealCount < MAX_APPEALS
    }

    companion object {
        const val MAX_APPEALS = 1
    }
}

// VerificationCaseDetailResource: el caso con su intento y las preguntas falladas.
// En un caso de miniproyecto attempt es null y failedQuestions viene vacío (la evidencia es evidenceUrl).
data class VerificationCaseDetail(
    var verificationCase: VerificationCase,
    var attempt: AssessmentAttempt?,
    var failedQuestions: List<FailedQuestion>?
)
