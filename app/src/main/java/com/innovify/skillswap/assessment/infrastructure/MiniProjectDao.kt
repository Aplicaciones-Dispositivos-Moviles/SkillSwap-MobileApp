package com.innovify.skillswap.assessment.infrastructure

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.google.gson.Gson
import com.innovify.skillswap.assessment.domain.MiniProject
import com.innovify.skillswap.assessment.domain.MiniProjectSubmission
import com.innovify.skillswap.shared.infrastructure.db.SkillSwapDbHelper

// Local cache of the assessment context, stored in the shared skillswap.db.
class MiniProjectDao(private val helper: SkillSwapDbHelper) {

    // Guarda el proyecto del nodo. Si es el mismo de antes conserva su entrega; si es otro, la descarta.
    fun saveMiniProject(project: MiniProject) {
        val previous = findMiniProject(project.pathNodeId)
        val keepSubmission = if (previous?.id == project.id) findSubmission(project.pathNodeId) else null
        helper.writableDatabase.insertWithOnConflict(
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
        return helper.readableDatabase.rawQuery(
            "SELECT project_json FROM mini_project WHERE node_id = ?", arrayOf(nodeId.toString())
        ).use { c ->
            if (c.moveToFirst()) Gson().fromJson(c.getString(0), MiniProject::class.java) else null
        }
    }

    // Entrega ya hecha de ese nodo, o null.
    fun findSubmission(nodeId: Int): MiniProjectSubmission? {
        return helper.readableDatabase.rawQuery(
            "SELECT submission_json FROM mini_project WHERE node_id = ?", arrayOf(nodeId.toString())
        ).use { c ->
            if (c.moveToFirst() && !c.isNull(0)) {
                Gson().fromJson(c.getString(0), MiniProjectSubmission::class.java)
            } else null
        }
    }

    fun saveSubmission(nodeId: Int, submission: MiniProjectSubmission) {
        helper.writableDatabase.update(
            "mini_project",
            ContentValues().apply { put("submission_json", Gson().toJson(submission)) },
            "node_id = ?", arrayOf(nodeId.toString())
        )
    }
}
