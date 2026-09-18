package com.olivia.companion.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies that only Olivia-owned transient directories are cleaned after a simulated crash. */
@RunWith(AndroidJUnit4::class)
class LocalArtifactRecoveryInstrumentationTest {
    @Test
    fun orphanedPrivateImportsAndExportsAreRemovedOnNextInitialization() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val importDirectory = File(context.cacheDir, "olivia-recordings").apply { mkdirs() }
        val exportDirectory = File(context.filesDir, "olivia-review-exports").apply { mkdirs() }
        val partial = File(importDirectory, "interrupted.part").apply { writeText("synthetic") }
        val imported = File(importDirectory, "interrupted.mp4").apply { writeText("synthetic") }
        val review = File(exportDirectory, "interrupted.review.json").apply { writeText("synthetic") }
        val subtitles = File(exportDirectory, "interrupted.srt").apply { writeText("synthetic") }

        AndroidLocalRecordingSource(context)
        AndroidReviewExporter(context)

        assertFalse("Partial private import must not survive restart", partial.exists())
        assertFalse("Completed private import without owner must not survive restart", imported.exists())
        assertFalse("Private review export without owner must not survive restart", review.exists())
        assertFalse("Private subtitles without owner must not survive restart", subtitles.exists())
    }
}
