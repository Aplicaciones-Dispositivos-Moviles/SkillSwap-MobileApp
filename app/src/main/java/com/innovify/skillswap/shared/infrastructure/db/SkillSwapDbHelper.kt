package com.innovify.skillswap.shared.infrastructure.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

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

}
