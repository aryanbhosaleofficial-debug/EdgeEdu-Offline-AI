package com.edgeedu.app.sync

/** The slice of the history store the sync loop needs (JVM-testable). */
interface HistorySource {
    fun unsynced(limit: Int): List<HistoryEntry>
    fun markSynced(clientIds: List<Long>)
    fun unsyncedCount(): Int
}

/** Outcome of one sync attempt, surfaced in Settings. */
sealed class SyncResult {
    /** Nothing to do: no account, sync unconfigured, or no pending rows. */
    data class Skipped(val reason: String) : SyncResult()
    data class Synced(val uploaded: Int) : SyncResult()
    data class Failed(val reason: String, val uploaded: Int) : SyncResult()
}

/**
 * Uploads pending local history to the account's database when the device is
 * online (PRD §17). Rows are pushed oldest-first in batches and marked synced
 * only after the server accepts them, so an interrupted sync just resumes on
 * the next attempt — nothing is lost and nothing uploads twice (the server
 * dedupes on account + client_id).
 */
class SyncManager(
    private val client: SyncClient,
    private val store: HistorySource,
    private val batchSize: Int = 50,
) {

    /** Pushes everything pending under [token]; safe to call from anywhere. */
    fun sync(token: String?): SyncResult {
        if (!client.isConfigured) return SyncResult.Skipped("sync not configured")
        if (token.isNullOrBlank()) return SyncResult.Skipped("not signed in")
        var uploaded = 0
        while (true) {
            val batch = store.unsynced(batchSize)
            if (batch.isEmpty()) break
            try {
                client.pushHistory(token, batch)
            } catch (e: SyncException) {
                return SyncResult.Failed(e.message ?: "sync failed", uploaded)
            }
            store.markSynced(batch.map { it.client_id })
            uploaded += batch.size
        }
        return if (uploaded > 0) SyncResult.Synced(uploaded) else SyncResult.Skipped("nothing to sync")
    }
}
