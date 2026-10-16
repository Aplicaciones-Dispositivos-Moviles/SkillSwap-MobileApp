package com.innovify.skillswap.model.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.innovify.skillswap.model.beans.AssessmentBlueprint
import com.innovify.skillswap.model.beans.LearningPath
import com.innovify.skillswap.model.beans.LocalCertificate
import com.innovify.skillswap.model.beans.MiniProject
import com.innovify.skillswap.model.beans.MiniProjectSubmission
import com.innovify.skillswap.model.beans.PathNode
import com.innovify.skillswap.model.beans.Question
import com.innovify.skillswap.model.beans.QuizProgress

// T48 + T49: base local SQLite.
//  - learning_path / path_node: copia de la última ruta descargada (se lee cuando no hay conexión).
//  - local_certificate: ruta del archivo del certificado guardado en el teléfono.
//  - quiz_blueprint / quiz_attempt (T50 + T51): el quiz descargado y las respuestas del Estudiante.
//  - mini_project (T55): el proyecto del nodo (su plazo corre aunque no haya internet) y su entrega.
class SkillSwapDbHelper(context: Context) : SQLiteOpenHelper(context, "skillswap.db", null, 3) {

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
        createQuizTables(db)
        createMiniProjectTable(db)
    }

    private fun createMiniProjectTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE mini_project (" +
                "node_id INTEGER PRIMARY KEY, mini_project_id INTEGER NOT NULL, project_json TEXT NOT NULL, " +
                "submission_json TEXT)"
        )
    }

    private fun createQuizTables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE quiz_blueprint (" +
                "node_id INTEGER PRIMARY KEY, blueprint_id INTEGER NOT NULL, skill_name TEXT NOT NULL, " +
                "skill_tag TEXT NOT NULL, questions_json TEXT NOT NULL, generated_at TEXT)"
        )
        db.execSQL(
            "CREATE TABLE quiz_attempt (" +
                "blueprint_id INTEGER PRIMARY KEY, node_id INTEGER NOT NULL, answers TEXT NOT NULL, " +
                "status TEXT NOT NULL, result_json TEXT, updated_at INTEGER NOT NULL)"
        )
    }

    // Migraciones: solo agregan tablas, así no se pierden la ruta ni los certificados ya guardados.
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createQuizTables(db)
        if (oldVersion < 3) createMiniProjectTable(db)
    }

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

    // ------------------------------------------------------------------ QUIZ (T50 + T51)
    // Guarda el quiz descargado y deja las respuestas en blanco. Reemplaza al quiz anterior del nodo.
    fun saveBlueprint(blueprint: AssessmentBlueprint, nodeId: Int) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "DELETE FROM quiz_attempt WHERE node_id = ?",
                arrayOf<Any>(nodeId)
            )
            db.delete("quiz_blueprint", "node_id = ?", arrayOf(nodeId.toString()))
            db.insert("quiz_blueprint", null, ContentValues().apply {
                put("node_id", nodeId)
                put("blueprint_id", blueprint.id)
                put("skill_name", blueprint.skillName)
                put("skill_tag", blueprint.skillTag)
                put("questions_json", Gson().toJson(blueprint.questions))
                put("generated_at", blueprint.generatedAt)
            })
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        saveProgress(blueprint.id, nodeId, List(blueprint.questions.size) { -1 }, "Draft")
    }

    // Quiz guardado para el nodo, o null si nunca se descargó.
    fun findBlueprint(nodeId: Int): AssessmentBlueprint? {
        return readableDatabase.rawQuery(
            "SELECT blueprint_id, skill_name, skill_tag, questions_json, generated_at " +
                "FROM quiz_blueprint WHERE node_id = ?",
            arrayOf(nodeId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return null
            val type = object : TypeToken<List<Question>>() {}.type
            AssessmentBlueprint(
                id = c.getInt(0),
                pathNodeId = nodeId,
                skillName = c.getString(1),
                skillTag = c.getString(2),
                questions = Gson().fromJson(c.getString(3), type),
                generatedAt = if (c.isNull(4)) null else c.getString(4)
            )
        }
    }

    // Guarda las respuestas (una por pregunta, -1 = sin responder) y su estado. Se llama en cada opción elegida.
    fun saveProgress(blueprintId: Int, nodeId: Int, answers: List<Int>, status: String) {
        writableDatabase.insertWithOnConflict(
            "quiz_attempt", null, ContentValues().apply {
                put("blueprint_id", blueprintId)
                put("node_id", nodeId)
                put("answers", answers.joinToString(","))
                put("status", status)
                putNull("result_json")
                put("updated_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun findProgress(blueprintId: Int): QuizProgress? {
        return readableDatabase.rawQuery(
            "SELECT blueprint_id, node_id, answers, status, result_json FROM quiz_attempt WHERE blueprint_id = ?",
            arrayOf(blueprintId.toString())
        ).use { c -> if (c.moveToFirst()) toProgress(c) else null }
    }

    // Intentos que el Estudiante ya envió pero el backend aún no calificó (T51 los reenvía).
    fun pendingAttempts(): List<QuizProgress> {
        val list = mutableListOf<QuizProgress>()
        readableDatabase.rawQuery(
            "SELECT blueprint_id, node_id, answers, status, result_json FROM quiz_attempt " +
                "WHERE status = 'Pending' ORDER BY updated_at",
            null
        ).use { c ->
            while (c.moveToNext()) list.add(toProgress(c))
        }
        return list
    }

    // El backend calificó el intento: se guarda su resultado para poder mostrarlo después.
    fun markAttemptSent(blueprintId: Int, resultJson: String) {
        writableDatabase.update(
            "quiz_attempt",
            ContentValues().apply {
                put("status", "Sent")
                put("result_json", resultJson)
            },
            "blueprint_id = ?", arrayOf(blueprintId.toString())
        )
    }

    // ------------------------------------------------------------ PROYECTO (T55)
    // Guarda el proyecto del nodo. Si es el mismo de antes conserva su entrega; si es otro, la descarta.
    fun saveMiniProject(project: MiniProject) {
        val previous = findMiniProject(project.pathNodeId)
        val keepSubmission = if (previous?.id == project.id) findSubmission(project.pathNodeId) else null
        writableDatabase.insertWithOnConflict(
            "mini_project", null, ContentValues().apply {
                put("node_id", project.pathNodeId)
                put("mini_project_id", project.id)
                put("project_json", Gson().toJson(project))
                if (keepSubmission != null) put("submission_json", Gson().toJson(keepSubmission))
                else putNull("submission_json")
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun findMiniProject(nodeId: Int): MiniProject? {
        return readableDatabase.rawQuery(
            "SELECT project_json FROM mini_project WHERE node_id = ?", arrayOf(nodeId.toString())
        ).use { c ->
            if (c.moveToFirst()) Gson().fromJson(c.getString(0), MiniProject::class.java) else null
        }
    }

    // Entrega ya hecha de ese nodo, o null.
    fun findSubmission(nodeId: Int): MiniProjectSubmission? {
        return readableDatabase.rawQuery(
            "SELECT submission_json FROM mini_project WHERE node_id = ?", arrayOf(nodeId.toString())
        ).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) {
                Gson().fromJson(c.getString(0), MiniProjectSubmission::class.java)
            } else null
        }
    }

    fun saveSubmission(nodeId: Int, submission: MiniProjectSubmission) {
        writableDatabase.update(
            "mini_project",
            ContentValues().apply { put("submission_json", Gson().toJson(submission)) },
            "node_id = ?", arrayOf(nodeId.toString())
        )
    }

    private fun toProgress(c: Cursor): QuizProgress {
        return QuizProgress(
            blueprintId = c.getInt(0),
            nodeId = c.getInt(1),
            answers = c.getString(2).split(",").map { it.trim().toIntOrNull() ?: -1 },
            status = c.getString(3),
            resultJson = if (c.isNull(4)) null else c.getString(4)
        )
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
