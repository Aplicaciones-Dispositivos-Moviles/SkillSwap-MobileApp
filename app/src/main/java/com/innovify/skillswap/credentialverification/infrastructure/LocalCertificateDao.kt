package com.innovify.skillswap.credentialverification.infrastructure

import android.content.ContentValues
import android.database.Cursor
import com.innovify.skillswap.credentialverification.domain.LocalCertificate
import com.innovify.skillswap.shared.infrastructure.db.SkillSwapDbHelper

// Local cache of the credentialverification context, stored in the shared skillswap.db.
class LocalCertificateDao(private val helper: SkillSwapDbHelper) {

    // Devuelve el id de la fila nueva.
    fun saveCertificate(certificate: LocalCertificate): Long {
        return helper.writableDatabase.insert("local_certificate", null, ContentValues().apply {
            put("node_id", certificate.nodeId)
            put("file_path", certificate.filePath)
            put("mime", certificate.mime)
            put("institution", certificate.institution)
            put("course", certificate.course)
            put("issue_date", certificate.issueDate)
            put("ocr_text", certificate.ocrText)
            put("status", certificate.status)
            put("created_at", certificate.createdAt)
        })
    }

    // Último certificado guardado para el nodo (pendiente o ya enviado), o null.
    fun findCertificate(nodeId: Int): LocalCertificate? {
        return helper.readableDatabase.rawQuery(
            "SELECT id, node_id, file_path, mime, institution, course, issue_date, ocr_text, status, created_at " +
                "FROM local_certificate WHERE node_id = ? ORDER BY id DESC LIMIT 1",
            arrayOf(nodeId.toString())
        ).use { c -> if (c.moveToFirst()) toCertificate(c) else null }
    }

    // Certificados que aún no se subieron al backend (los reenviará T51 al volver la conexión).
    fun pendingCertificates(): List<LocalCertificate> {
        val list = mutableListOf<LocalCertificate>()
        helper.readableDatabase.rawQuery(
            "SELECT id, node_id, file_path, mime, institution, course, issue_date, ocr_text, status, created_at " +
                "FROM local_certificate WHERE status = 'Pending' ORDER BY id",
            null
        ).use { c ->
            while (c.moveToNext()) list.add(toCertificate(c))
        }
        return list
    }

    fun markCertificateSent(id: Long) {
        helper.writableDatabase.update(
            "local_certificate", ContentValues().apply { put("status", "Sent") },
            "id = ?", arrayOf(id.toString())
        )
    }

    fun deleteCertificate(id: Long) {
        helper.writableDatabase.delete("local_certificate", "id = ?", arrayOf(id.toString()))
    }

    private fun toCertificate(c: Cursor): LocalCertificate {
        return LocalCertificate(
            id = c.getLong(0),
            nodeId = c.getInt(1),
            filePath = c.getString(2),
            mime = c.getString(3),
            institution = c.getString(4),
            course = c.getString(5),
            issueDate = c.getString(6),
            ocrText = c.getString(7),
            status = c.getString(8),
            createdAt = c.getLong(9)
        )
    }
}
