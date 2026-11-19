package com.edgeedu.app.content

import android.content.res.AssetManager

/**
 * The origin a login-time download reads from. In this build the origin is the
 * APK's bundled assets — a local stand-in for the signed static host — so the
 * lifecycle (copy into per-profile storage, verify, delete on logout) is real
 * and demonstrable fully offline. Phase 2 swaps in an HTTP source against the
 * actual host; the [ContentProvisioner] above it is unchanged either way.
 */
interface ContentSource {
    /** Raw bytes of the signed manifest (data/manifest.json). */
    fun manifest(): ByteArray

    /** Raw bytes of one content file, named by its manifest-relative path. */
    fun open(path: String): ByteArray
}

/** Reads content from the APK's bundled assets (the offline origin stand-in). */
class AssetContentSource(private val assets: AssetManager) : ContentSource {
    override fun manifest(): ByteArray =
        assets.open("data/${ContentProvisioner.MANIFEST}").use { it.readBytes() }

    override fun open(path: String): ByteArray =
        assets.open(path).use { it.readBytes() }
}

/**
 * Tries [primary] (e.g. a GitHub release) first and falls back to [fallback]
 * (the APK's bundled assets) on any failure. This keeps the app truly
 * offline-first: if the content release is unpublished, unreachable, or the
 * device is offline, the identical bundled content is used instead of
 * hard-failing with a 404. The provisioner's SHA-256 check guards both origins
 * equally, so a fallback is never a security downgrade.
 *
 * The manifest source is latched: whichever origin serves the manifest also
 * serves every file, so a bundled manifest is never paired with remote files
 * (or vice-versa), which would spuriously fail the hash check.
 */
class FallbackContentSource(
    private val primary: ContentSource,
    private val fallback: ContentSource,
) : ContentSource {
    private var usingFallback = false

    override fun manifest(): ByteArray = try {
        primary.manifest().also { usingFallback = false }
    } catch (_: Exception) {
        usingFallback = true
        fallback.manifest()
    }

    override fun open(path: String): ByteArray =
        if (usingFallback) fallback.open(path)
        else try {
            primary.open(path)
        } catch (_: Exception) {
            usingFallback = true
            fallback.open(path)
        }
}
