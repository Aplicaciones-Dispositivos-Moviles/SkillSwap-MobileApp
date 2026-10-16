package com.innovify.skillswap.model.beans

// Certificado guardado en el teléfono (T48). status: "Pending" (aún no enviado) | "Sent" (ya subido al backend)
data class LocalCertificate(
    var id: Long,
    var nodeId: Int,
    var filePath: String, // ruta absoluta en filesDir/certificates
    var mime: String,
    var institution: String,
    var course: String,
    var issueDate: String,
    var ocrText: String,
    var status: String,
    var createdAt: Long
)
