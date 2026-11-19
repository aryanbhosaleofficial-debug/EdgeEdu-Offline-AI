package com.edgeedu.app

import com.edgeedu.app.content.GithubContentSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The mapping from a signed-manifest path to its GitHub Release asset name must
 * match exactly how GitHub stores uploaded files (basename, each space -> '.').
 * If these drift, downloads 404 — so pin it. Verified against the live release.
 */
class GithubContentSourceTest {

    @Test
    fun mapsPathToGithubAssetName() {
        // Subfolders are dropped (releases are a flat bucket); a space-free name is
        // stored verbatim.
        assertEquals(
            "9th_Geography_English.pdf",
            GithubContentSource.assetName("data/9th/English/9th_Geography_English.pdf"),
        )
        // GitHub rewrites each space in the name to a dot.
        assertEquals(
            "9th_Maths.1_English.pdf",
            GithubContentSource.assetName("data/9th/English/9th_Maths 1_English.pdf"),
        )
        assertEquals(
            "9th_History.and.Political.Science_Marathi.pdf",
            GithubContentSource.assetName("data/9th/Marathi/9th_History and Political Science_Marathi.pdf"),
        )
        assertEquals(
            "10th_Science.1_Hindi.json",
            GithubContentSource.assetName("data/10th/Hindi/10th_Science 1_Hindi.json"),
        )
        // The manifest is uploaded at the bucket root (no spaces, unchanged).
        assertEquals("manifest.json", GithubContentSource.assetName("data/manifest.json"))
    }
}
