package com.innovify.skillswap.model.beans

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
    var holderNameMismatch: Boolean
)

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

// Cuerpo de error del backend (ProblemDetail): title = código, detail = mensaje legible
data class ApiError(
    var title: String?,
    var detail: String?,
    var status: Int?
)
