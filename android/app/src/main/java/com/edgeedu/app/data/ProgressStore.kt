package com.edgeedu.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Per-subject study progress for the home screen: a chunk counts as
 * "studied" when the student actually engaged with it — it was cited as a
 * source in a tutor answer, or bookmarked. Progress for a subject is
 * distinct studied chunks ÷ total chunks. User data: kept across logout.
 */
class ProgressStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "edgeedu_progress.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE studied (
                subject_key TEXT NOT NULL,
                chunk_key TEXT NOT NULL,
                studied_at INTEGER NOT NULL,
                PRIMARY KEY (subject_key, chunk_key)
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /** Marks a chunk studied. [subjectKey] is SubjectRef.key; idempotent. */
    fun markStudied(subjectKey: String, chunkKey: String) {
        writableDatabase.insertWithOnConflict(
            "studied", null,
            ContentValues().apply {
                put("subject_key", subjectKey)
                put("chunk_key", chunkKey)
                put("studied_at", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
    }

    /** Distinct studied-chunk count per subject key. */
    fun studiedCounts(): Map<String, Int> =
        readableDatabase.rawQuery(
            "SELECT subject_key, COUNT(*) FROM studied GROUP BY subject_key", null,
        ).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getInt(1)) }
        }
}
