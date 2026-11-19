package com.edgeedu.app.content

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** A saved local profile with its row id (for the who-is-studying picker). */
data class StoredProfile(val id: Long, val profile: Profile)

/**
 * Persists the device's local profiles (PRD §12.1, §12.3) — Netflix-style:
 * one device holds several profiles ("Who's studying?"), one of which is
 * active. Logging out clears the ACTIVE selection and the downloaded content,
 * but keeps the saved profiles so re-login is one tap. Kept in its own DB,
 * separate from bookmarks/notes, because their lifecycles differ (§12.4).
 */
class ProfileStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "edgeedu_profile.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        createV2(db)
    }

    private fun createV2(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                standard INTEGER NOT NULL,
                medium TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )"""
        )
        // Single-row pointer to the active profile (empty = logged out).
        db.execSQL(
            """CREATE TABLE active (
                id INTEGER PRIMARY KEY CHECK (id = 0),
                profile_id INTEGER NOT NULL
            )"""
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v1 held a single profile in `profile(id=0)`; migrate it to the
            // multi-profile tables and keep it active so the user stays
            // logged in across the update.
            createV2(db)
            db.rawQuery("SELECT name, standard, medium FROM profile WHERE id = 0", null).use { c ->
                if (c.moveToFirst()) {
                    val values = ContentValues().apply {
                        put("name", c.getString(0))
                        put("standard", c.getInt(1))
                        put("medium", c.getString(2))
                        put("created_at", System.currentTimeMillis())
                    }
                    val id = db.insert("profiles", null, values)
                    db.insert(
                        "active", null,
                        ContentValues().apply { put("id", 0); put("profile_id", id) },
                    )
                }
            }
            db.execSQL("DROP TABLE IF EXISTS profile")
        }
    }

    /** All saved profiles, oldest first — the "Who's studying?" list. */
    fun profiles(): List<StoredProfile> =
        readableDatabase.rawQuery(
            "SELECT id, name, standard, medium FROM profiles ORDER BY created_at", null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(StoredProfile(c.getLong(0), Profile(c.getString(1), c.getInt(2), c.getString(3))))
                }
            }
        }

    /** Adds a profile (or returns the id of an identical existing one). */
    fun add(profile: Profile): Long {
        val existing = readableDatabase.rawQuery(
            "SELECT id FROM profiles WHERE name = ? AND standard = ? AND medium = ?",
            arrayOf(profile.name, profile.standard.toString(), profile.medium),
        ).use { if (it.moveToFirst()) it.getLong(0) else null }
        if (existing != null) return existing
        return writableDatabase.insert(
            "profiles", null,
            ContentValues().apply {
                put("name", profile.name)
                put("standard", profile.standard)
                put("medium", profile.medium)
                put("created_at", System.currentTimeMillis())
            },
        )
    }

    fun setActive(profileId: Long) {
        writableDatabase.insertWithOnConflict(
            "active", null,
            ContentValues().apply { put("id", 0); put("profile_id", profileId) },
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    /** The active profile, or null when logged out (picker shown). */
    fun current(): Profile? =
        readableDatabase.rawQuery(
            """SELECT p.name, p.standard, p.medium FROM active a
               JOIN profiles p ON p.id = a.profile_id WHERE a.id = 0""",
            null,
        ).use { c ->
            if (c.moveToFirst()) Profile(c.getString(0), c.getInt(1), c.getString(2)) else null
        }

    fun remove(profileId: Long) {
        writableDatabase.run {
            delete("profiles", "id = ?", arrayOf(profileId.toString()))
            delete("active", "profile_id = ?", arrayOf(profileId.toString()))
        }
    }

    /** Logout: deselect the active profile but keep all saved profiles. */
    fun clear() {
        writableDatabase.delete("active", null, null)
    }
}
