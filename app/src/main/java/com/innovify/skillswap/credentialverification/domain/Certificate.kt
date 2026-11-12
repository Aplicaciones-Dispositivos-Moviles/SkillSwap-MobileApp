package com.innovify.skillswap.credentialverification.domain

import com.innovify.skillswap.learningpath.domain.PathNode

// CertificateResource del backend (solo los campos que usa la app).
// status: "Pending" | "Unverified" | "Suspicious" | "Verified" | "Rejected"
// riskLevel: "LowRisk" | "Review" | "HighRisk"
data class Certificate(
    var id: Int,
    var ownerId: Int,
    var institutionName: String?,
    var courseName: String?,
    var issueDate: String?,
    var status: String,
    var verificationMethod: String?,
    var riskLevel: String?,
    var fileUrl: String?,
    var holderNameMismatch: Boolean,
    // Registration time (UTC); only the GET list uses it, so it is optional
    var createdAt: String? = null
)

// What the Student sees of a certificate's status in "Mis certificados".
enum class CertificateStage { Validated, InProgress, Rejected }

// Verified -> validated; Rejected -> rejected; Pending, Unverified, Suspicious (and any new status) are
// still waiting for a decision.
fun Certificate.stage(): CertificateStage = when (status) {
    "Verified" -> CertificateStage.Validated
    "Rejected" -> CertificateStage.Rejected
    else -> CertificateStage.InProgress
}

// Filter chips of "Mis certificados" (null stage = every certificate).
enum class CertificateFilter(val stage: CertificateStage?) {
    All(null),
    Validated(CertificateStage.Validated),
    InProgress(CertificateStage.InProgress),
    Rejected(CertificateStage.Rejected)
}

fun List<Certificate>.filterBy(filter: CertificateFilter): List<Certificate> =
    if (filter.stage == null) this else filter { it.stage() == filter.stage }

// Body de POST api/v1/path-nodes/{nodeId}/certificate
data class LinkCertificateRequest(
    var certificateId: Int
)

// CertificateLinkResource: el certificado cubre la habilidad del nodo (affinity >= threshold)
data class CertificateLink(
    var pathNodeId: Int,
    var certificateId: Int,
    var affinity: Double,
    var threshold: Double,
    var assessmentEnabled: Boolean,
    var node: PathNode?
)

// Un nodo de la ruta que el certificado sí cubre (parte del 422 CertificateSkillMismatch)
data class SuggestedNode(
    var nodeId: Int,
    var skillTag: String,
    var affinity: Double
)

// 422 CertificateSkillMismatch: el certificado no cubre la habilidad del nodo. affinity y threshold son de este nodo;
// suggestedNodes son los nodos de la ruta que sí cubre. El backend los manda como campos del cuerpo de error;
// si vinieran dentro de "details" también se leen.
data class CertificateMismatch(
    var affinity: Double?,
    var threshold: Double?,
    var suggestedNodes: List<SuggestedNode>?
) {
    companion object {
        fun fromErrorBody(raw: String?): CertificateMismatch? {
            return try {
                val root = com.google.gson.JsonParser.parseString(raw).asJsonObject
                val source = if (root.has("suggestedNodes")) root else root.getAsJsonObject("details")
                com.google.gson.Gson().fromJson(source, CertificateMismatch::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}
