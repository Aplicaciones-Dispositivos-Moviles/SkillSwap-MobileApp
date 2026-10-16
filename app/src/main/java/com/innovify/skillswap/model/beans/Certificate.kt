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

// Cuerpo de error del backend (ProblemDetail): title = código, detail = mensaje legible
data class ApiError(
    var title: String?,
    var detail: String?,
    var status: Int?
)
