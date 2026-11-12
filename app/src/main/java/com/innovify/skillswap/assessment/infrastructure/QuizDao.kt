package com.innovify.skillswap.assessment.infrastructure

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.innovify.skillswap.assessment.domain.AssessmentBlueprint
import com.innovify.skillswap.assessment.domain.Question
import com.innovify.skillswap.assessment.domain.QuizProgress
import com.innovify.skillswap.shared.infrastructure.db.SkillSwapDbHelper

// Local cache of the assessment context, stored in the shared skillswap.db.
class QuizDao(private val helper: SkillSwapDbHelper) {

    // Guarda el quiz descargado y deja las respuestas en blanco. Reemplaza al quiz anterior del nodo.
    fun saveBlueprint(blueprint: AssessmentBlueprint, nodeId: Int) {
        val db = helper.writableDatabase
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
        return helper.readableDatabase.rawQuery(
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
        helper.writableDatabase.insertWithOnConflict(
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
        return helper.readableDatabase.rawQuery(
            "SELECT blueprint_id, node_id, answers, status, result_json FROM quiz_attempt WHERE blueprint_id = ?",
            arrayOf(blueprintId.toString())
        ).use { c -> if (c.moveToFirst()) toProgress(c) else null }
    }

    // Intentos que el Estudiante ya envió pero el backend aún no calificó (T51 los reenvía).
    fun pendingAttempts(): List<QuizProgress> {
        val list = mutableListOf<QuizProgress>()
        helper.readableDatabase.rawQuery(
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
        helper.writableDatabase.update(
            "quiz_attempt",
            ContentValues().apply {
                put("status", "Sent")
                put("result_json", resultJson)
            },
            "blueprint_id = ?", arrayOf(blueprintId.toString())
        )
    }

    // Borra el quiz guardado de un nodo (el quiz y sus respuestas). Sirve para descartar un intento que el
    // servidor rechazó y pedir uno nuevo.
    fun deleteQuiz(nodeId: Int) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("quiz_attempt", "node_id = ?", arrayOf(nodeId.toString()))
            db.delete("quiz_blueprint", "node_id = ?", arrayOf(nodeId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
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
}
