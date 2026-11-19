package com.edgeedu.app.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.edgeedu.app.sync.HistoryEntry
import com.edgeedu.app.sync.HistorySource

/**
 * Local history of every question the student asks and the tutor's answer
 * (PRD §17). Written on-device for every exchange — fully offline — with a
 * `synced` flag so the [com.edgeedu.app.sync.SyncManager] can upload pending
 * rows when the device comes online. User data: kept across logout.
 */
class ChatHistoryStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "edgeedu_history.db", null, 1), HistorySource {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE chat_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                asked_at INTEGER NOT NULL,
                standard INTEGER NOT NULL,
                medium TEXT NOT NULL,
                subject TEXT NOT NULL,
                question TEXT NOT NULL,
                answer TEXT NOT NULL,
                engine TEXT NOT NULL DEFAULT '',
                synced INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL("CREATE INDEX idx_history_synced ON chat_history(synced)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun add(entry: HistoryEntry): Long =
        writableDatabase.insert(
            "chat_history", null,
            ContentValues().apply {
                put("asked_at", entry.asked_at)
                put("standard", entry.standard)
                put("medium", entry.medium)
                put("subject", entry.subject)
                put("question", entry.question)
                put("answer", entry.answer)
                put("engine", entry.engine)
            },
        )

    private fun rowToEntry(c: android.database.Cursor) = HistoryEntry(
        client_id = c.getLong(0),
        asked_at = c.getLong(1),
        standard = c.getInt(2),
        medium = c.getString(3),
        subject = c.getString(4),
        question = c.getString(5),
        answer = c.getString(6),
        engine = c.getString(7),
    )

    /** Oldest-first pending rows for upload. */
    override fun unsynced(limit: Int): List<HistoryEntry> =
        readableDatabase.rawQuery(
            """SELECT id, asked_at, standard, medium, subject, question, answer, engine
               FROM chat_history WHERE synced = 0 ORDER BY id LIMIT ?""",
            arrayOf(limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(rowToEntry(c)) } }

    override fun markSynced(clientIds: List<Long>) {
        if (clientIds.isEmpty()) return
        val marks = clientIds.joinToString(",") { "?" }
        writableDatabase.execSQL(
            "UPDATE chat_history SET synced = 1 WHERE id IN ($marks)",
            clientIds.map { it.toString() }.toTypedArray(),
        )
    }

    override fun unsyncedCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM chat_history WHERE synced = 0", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    fun totalCount(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM chat_history", null)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** Newest-first recent history, e.g. for a history list in the UI. */
    fun recent(limit: Int = 50): List<HistoryEntry> =
        readableDatabase.rawQuery(
            """SELECT id, asked_at, standard, medium, subject, question, answer, engine
               FROM chat_history ORDER BY id DESC LIMIT ?""",
            arrayOf(limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(rowToEntry(c)) } }
}
