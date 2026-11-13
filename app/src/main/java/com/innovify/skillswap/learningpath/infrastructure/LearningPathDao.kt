package com.innovify.skillswap.learningpath.infrastructure

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.innovify.skillswap.learningpath.domain.LearningPath
import com.innovify.skillswap.learningpath.domain.PathNode
import com.innovify.skillswap.shared.infrastructure.db.SkillSwapDbHelper

// Local cache of the learningpath context, stored in the shared skillswap.db.
class LearningPathDao(private val helper: SkillSwapDbHelper) {

    // Reemplaza la ruta guardada del Estudiante por la recién descargada.
    fun savePath(path: LearningPath) {
        val db = helper.writableDatabase
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
        deletePath(helper.writableDatabase, studentId)
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
        val db = helper.readableDatabase
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
}
