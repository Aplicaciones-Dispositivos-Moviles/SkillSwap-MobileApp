package com.innovify.skillswap.model.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.innovify.skillswap.model.beans.LearningPath
import com.innovify.skillswap.model.beans.LocalCertificate
import com.innovify.skillswap.model.beans.PathNode

// T48 + T49: base local SQLite.
//  - learning_path / path_node: copia de la última ruta descargada (se lee cuando no hay conexión).
//  - local_certificate: ruta del archivo del certificado guardado en el teléfono.
class SkillSwapDbHelper(context: Context) : SQLiteOpenHelper(context, "skillswap.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE learning_path (" +
                "id INTEGER PRIMARY KEY, student_id INTEGER NOT NULL, goal TEXT NOT NULL, " +
                "goal_skill_tags TEXT NOT NULL, status TEXT NOT NULL, advanced INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE path_node (" +
                "id INTEGER PRIMARY KEY, path_id INTEGER NOT NULL, skill_tag TEXT NOT NULL, " +
                "skill_name TEXT NOT NULL, node_order INTEGER NOT NULL, status TEXT NOT NULL, " +
                "prerequisites TEXT NOT NULL, linked_certificate_id INTEGER, " +
                "assessment_blueprint_id INTEGER, completed_by_certificate INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE local_certificate (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, node_id INTEGER NOT NULL, file_path TEXT NOT NULL, " +
                "mime TEXT NOT NULL, institution TEXT NOT NULL, course TEXT NOT NULL, " +
                "issue_date TEXT NOT NULL, ocr_text TEXT NOT NULL, status TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL)"
        )
    }

    // Versión 1: aún no hay migraciones. Si se cambia el esquema, subir la versión y migrar aquí.
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    // ------------------------------------------------------------------ RUTA (caché)
    // Reemplaza la ruta guardada del Estudiante por la recién descargada.
    fun savePath(path: LearningPath) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            deletePath(db, path.studentId)
            db.insert("learning_path", null, ContentValues().apply {
                put("id", path.id)
                put("student_id", path.studentId)
                put("goal", path.goal)
                put("goal_skill_tags", (path.goalSkillTags ?: emptyList()).joinToString(","))
                put("status", path.status)
                put("advanced", if (path.advanced) 1 else 0)
            })
            for (node in path.nodes ?: emptyList()) {
                db.insert("path_node", null, ContentValues().apply {
                    put("id", node.id)
                    put("path_id", path.id)
                    put("skill_tag", node.skillTag)
                    put("skill_name", node.skillName)
                    put("node_order", node.order)
                    put("status", node.status)
                    put("prerequisites", (node.prerequisiteSkillTags ?: emptyList()).joinToString(","))
                    put("linked_certificate_id", node.linkedCertificateId)
                    put("assessment_blueprint_id", node.assessmentBlueprintId)
                    put("completed_by_certificate", if (node.completedByCertificate) 1 else 0)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun clearPath(studentId: Int) {
        deletePath(writableDatabase, studentId)
    }

    private fun deletePath(db: SQLiteDatabase, studentId: Int) {
        db.execSQL(
            "DELETE FROM path_node WHERE path_id IN (SELECT id FROM learning_path WHERE student_id = ?)",
            arrayOf<Any>(studentId)
        )
        db.delete("learning_path", "student_id = ?", arrayOf(studentId.toString()))
    }

    // Última ruta guardada del Estudiante, o null si nunca se descargó una.
    fun getPath(studentId: Int): LearningPath? {
        val db = readableDatabase
        return db.rawQuery(
            "SELECT id, goal, goal_skill_tags, status, advanced FROM learning_path WHERE student_id = ? LIMIT 1",
            arrayOf(studentId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return null
            val pathId = c.getInt(0)
            LearningPath(
                id = pathId,
                studentId = studentId,
                goal = c.getString(1),
                goalSkillTags = splitTags(c.getString(2)),
                status = c.getString(3),
                nodes = getNodes(db, pathId),
                advanced = c.getInt(4) == 1
            )
        }
    }

    private fun getNodes(db: SQLiteDatabase, pathId: Int): List<PathNode> {
        val nodes = mutableListOf<PathNode>()
        db.rawQuery(
            "SELECT id, skill_tag, skill_name, node_order, status, prerequisites, " +
                "linked_certificate_id, assessment_blueprint_id, completed_by_certificate " +
                "FROM path_node WHERE path_id = ? ORDER BY node_order",
            arrayOf(pathId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                nodes.add(
                    PathNode(
                        id = c.getInt(0),
                        skillTag = c.getString(1),
                        skillName = c.getString(2),
                        order = c.getInt(3),
                        status = c.getString(4),
                        prerequisiteSkillTags = splitTags(c.getString(5)),
                        linkedCertificateId = if (c.isNull(6)) null else c.getInt(6),
                        assessmentBlueprintId = if (c.isNull(7)) null else c.getInt(7),
                        completedByCertificate = c.getInt(8) == 1
                    )
                )
            }
        }
        return nodes
    }

    private fun splitTags(value: String): List<String> {
        return if (value.isBlank()) emptyList() else value.split(",")
    }

    // ------------------------------------------------------------ CERTIFICADO LOCAL (T48)
    // Devuelve el id de la fila nueva.
    fun saveCertificate(certificate: LocalCertificate): Long {
        return writableDatabase.insert("local_certificate", null, ContentValues().apply {
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
        return readableDatabase.rawQuery(
            "SELECT id, node_id, file_path, mime, institution, course, issue_date, ocr_text, status, created_at " +
                "FROM local_certificate WHERE node_id = ? ORDER BY id DESC LIMIT 1",
            arrayOf(nodeId.toString())
        ).use { c -> if (c.moveToFirst()) toCertificate(c) else null }
    }

    // Certificados que aún no se subieron al backend (los reenviará T51 al volver la conexión).
    fun pendingCertificates(): List<LocalCertificate> {
        val list = mutableListOf<LocalCertificate>()
        readableDatabase.rawQuery(
            "SELECT id, node_id, file_path, mime, institution, course, issue_date, ocr_text, status, created_at " +
                "FROM local_certificate WHERE status = 'Pending' ORDER BY id",
            null
        ).use { c ->
            while (c.moveToNext()) list.add(toCertificate(c))
        }
        return list
    }

    fun markCertificateSent(id: Long) {
        writableDatabase.update(
            "local_certificate", ContentValues().apply { put("status", "Sent") },
            "id = ?", arrayOf(id.toString())
        )
    }

    fun deleteCertificate(id: Long) {
        writableDatabase.delete("local_certificate", "id = ?", arrayOf(id.toString()))
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
