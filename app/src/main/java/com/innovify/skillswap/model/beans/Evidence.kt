package com.innovify.skillswap.model.beans

// US26 · Body de PUT api/v1/verification-cases/{id}/evidence: enlace a un repositorio o portafolio
data class AttachEvidenceRequest(
    var evidenceUrl: String
)

// El backend solo acepta enlaces http o https
fun isHttpUrl(raw: String): Boolean {
    val url = raw.trim().lowercase()
    return (url.startsWith("http://") || url.startsWith("https://")) && !url.contains(' ') && url.length > 10
}
