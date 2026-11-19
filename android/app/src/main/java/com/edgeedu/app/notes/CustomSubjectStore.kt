package com.edgeedu.app.notes

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.edgeedu.app.data.Chunk
import com.edgeedu.app.data.ChunkSource
import com.edgeedu.app.data.IndexedChunk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A user-created subject and how much content it has. */
data class CustomSubject(
    val id: Long,
    val name: String,
    val standard: Int,
    val chunkCount: Int,
)

/**
 * Stores user-created subjects and their structured note-chunks (the offline
 * "add your own subject" feature). Kept across logout like other user data
 * (PRD §12.4), keyed by class. The chunks are produced by [NoteStructurer].
 */
class CustomSubjectStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "edgeedu_custom.db", null, 2) {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE custom_subjects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                standard INTEGER NOT NULL,
                created_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE custom_chunks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject_id INTEGER NOT NULL,
                idx INTEGER NOT NULL,
                heading TEXT NOT NULL,
                body TEXT NOT NULL,
                keywords TEXT NOT NULL,
                chapter_title TEXT NOT NULL DEFAULT '',
                chunk_json TEXT
            )"""
        )
        db.execSQL("CREATE INDEX idx_custom_chunks ON custom_chunks(subject_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v2: the full curriculum-shaped chunk is stored as JSON; legacy
            // rows keep working through the heading/body/keywords columns.
            db.execSQL("ALTER TABLE custom_chunks ADD COLUMN chapter_title TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE custom_chunks ADD COLUMN chunk_json TEXT")
        }
    }

    fun create(name: String, standard: Int): Long =
        writableDatabase.insert(
            "custom_subjects", null,
            ContentValues().apply {
                put("name", name)
                put("standard", standard)
                put("created_at", System.currentTimeMillis())
            },
        )

    fun subjects(standard: Int): List<CustomSubject> {
        val sql =
            """SELECT s.id, s.name, s.standard, COUNT(c.id)
               FROM custom_subjects s LEFT JOIN custom_chunks c ON c.subject_id = s.id
               WHERE s.standard = ?
               GROUP BY s.id ORDER BY s.created_at DESC"""
        return readableDatabase.rawQuery(sql, arrayOf(standard.toString())).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(CustomSubject(c.getLong(0), c.getString(1), c.getInt(2), c.getInt(3)))
                }
            }
        }
    }

    /** Appends structured [chapters] to a subject, continuing the chunk index. */
    fun appendChapters(subjectId: Long, chapters: List<StructuredChapter>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            var idx = nextIndex(db, subjectId)
            for (chapter in chapters) {
                for (chunk in chapter.chunks) {
                    db.insert(
                        "custom_chunks", null,
                        ContentValues().apply {
                            put("subject_id", subjectId)
                            put("idx", idx++)
                            put("heading", chunk.heading)
                            put("body", chunk.body)
                            put("keywords", chunk.keywords.joinToString("\n"))
                            put("chapter_title", chapter.title)
                            put("chunk_json", json.encodeToString(chunk))
                        },
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun nextIndex(db: SQLiteDatabase, subjectId: Long): Int =
        db.rawQuery(
            "SELECT COALESCE(MAX(idx), -1) + 1 FROM custom_chunks WHERE subject_id = ?",
            arrayOf(subjectId.toString()),
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** A stored row decoded to its full chunk (v2 JSON) or a legacy chunk. */
    private fun decodeChunk(chunkJson: String?, heading: String, body: String, keywords: String): Chunk =
        chunkJson?.let {
            try {
                json.decodeFromString<Chunk>(it)
            } catch (_: Exception) {
                null
            }
        } ?: Chunk(
            chunk_id = "",
            heading = heading,
            text = body,
            keywords = keywords.split("\n").filter { it.isNotBlank() },
        )

    /** The subject's chunks as retrievable [IndexedChunk]s, tagged as notes. */
    fun chunksFor(subjectId: Long, medium: String): List<IndexedChunk> {
        val sql =
            """SELECT s.name, s.standard, c.idx, c.heading, c.body, c.keywords, c.chunk_json
               FROM custom_chunks c JOIN custom_subjects s ON c.subject_id = s.id
               WHERE c.subject_id = ? ORDER BY c.idx"""
        return readableDatabase.rawQuery(sql, arrayOf(subjectId.toString())).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val name = c.getString(0)
                    val chunk = decodeChunk(c.getString(6), c.getString(3), c.getString(4), c.getString(5))
                    add(
                        IndexedChunk(
                            chunk = chunk.copy(chunk_id = "C$subjectId.${c.getInt(2)}"),
                            file = "custom:$subjectId",
                            standard = c.getInt(1),
                            subject = name,
                            language = medium,
                            source = ChunkSource.Notes,
                        )
                    )
                }
            }
        }
    }

    /**
     * The subject's chunks regrouped into chapters (consecutive rows sharing a
     * chapter title) — used to (re)generate the curriculum JSON file.
     */
    fun structuredChapters(subjectId: Long): List<StructuredChapter> =
        readableDatabase.rawQuery(
            "SELECT heading, body, keywords, chapter_title, chunk_json FROM custom_chunks " +
                "WHERE subject_id = ? ORDER BY idx",
            arrayOf(subjectId.toString()),
        ).use { c ->
            val chapters = mutableListOf<Pair<String, MutableList<Chunk>>>()
            while (c.moveToNext()) {
                val title = c.getString(3).ifBlank { "Notes" }
                val chunk = decodeChunk(c.getString(4), c.getString(0), c.getString(1), c.getString(2))
                if (chapters.lastOrNull()?.first != title) chapters += title to mutableListOf()
                chapters.last().second += chunk
            }
            chapters.map { StructuredChapter(it.first, it.second) }
        }

    fun delete(subjectId: Long) {
        writableDatabase.run {
            delete("custom_chunks", "subject_id = ?", arrayOf(subjectId.toString()))
            delete("custom_subjects", "id = ?", arrayOf(subjectId.toString()))
        }
    }
}
