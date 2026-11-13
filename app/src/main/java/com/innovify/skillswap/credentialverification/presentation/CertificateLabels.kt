package com.innovify.skillswap.credentialverification.presentation

import com.innovify.skillswap.shared.presentation.design.StepStatus

// Spanish labels for the backend certificate enums (the UI never shows the raw value of a known one).

/** Certificate status ("Pending" | "Unverified" | "Suspicious" | "Verified" | "Rejected") in Spanish. */
fun certificateStatusLabel(status: String?): String = when (status) {
    null -> "—"
    "Pending" -> "Pendiente"
    "Unverified" -> "Sin verificar"
    "Suspicious" -> "Sospechoso"
    "Verified" -> "Verificado"
    "Rejected" -> "Rechazado"
    else -> status
}

/** Document risk level ("LowRisk" | "Review" | "HighRisk") in Spanish. */
fun riskLevelLabel(riskLevel: String?): String = when (riskLevel) {
    null -> "—"
    "LowRisk" -> "Riesgo bajo"
    "Review" -> "Requiere revisión"
    "HighRisk" -> "Riesgo alto"
    else -> riskLevel
}

/**
 * What the certificate screen knows so far.
 * [uploadFailed]: the send finished online without registering the certificate (offline sends stay pending).
 * [coversNode]: null until the link to the node was tried; false when it does not cover the node.
 */
data class CertificateProgress(
    val reading: Boolean,
    val extracted: Boolean,
    val sending: Boolean,
    val uploaded: Boolean,
    val uploadFailed: Boolean,
    val coversNode: Boolean?,
    val riskLevel: String?
)

/**
 * Status of the four checklist steps of the certificate screen (Figma 09):
 * data extracted, no duplicates (registered), compared with the node, document risk analysis.
 */
fun certificateSteps(p: CertificateProgress): List<StepStatus> {
    val extracted = when {
        p.reading -> StepStatus.Active
        p.extracted -> StepStatus.Done
        else -> StepStatus.Pending
    }
    val registered = when {
        p.uploaded -> StepStatus.Done
        p.uploadFailed -> StepStatus.Failed
        p.sending -> StepStatus.Active
        else -> StepStatus.Pending
    }
    val node = when {
        p.coversNode == true -> StepStatus.Done
        p.coversNode == false -> StepStatus.Failed
        p.sending && p.uploaded -> StepStatus.Active
        else -> StepStatus.Pending
    }
    val risk = when {
        !p.uploaded || p.riskLevel == null -> StepStatus.Pending
        p.riskLevel == "HighRisk" -> StepStatus.Failed
        else -> StepStatus.Done
    }
    return listOf(extracted, registered, node, risk)
}
