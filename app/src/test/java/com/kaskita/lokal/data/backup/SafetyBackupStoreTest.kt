package com.kaskita.lokal.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SafetyBackupStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun file(dir: File, name: String) = File(dir, name).apply { writeText("{}") }

    @Test
    fun missingOrEmptyDirectoryHasNoSafetyBackup() {
        assertNull(SafetyBackupStore.latest(File(folder.root, "does-not-exist")))
        assertNull(SafetyBackupStore.latest(folder.newFolder("empty")))
    }

    @Test
    fun latestIsTheNewestByTimestampAndCarriesItsCreationTime() {
        val dir = folder.newFolder("safety")
        file(dir, "kaskita-safety-1700000000000.json")
        val newest = file(dir, "kaskita-safety-1700000999000.json")
        file(dir, "kaskita-safety-1600000000000.json")

        val info = SafetyBackupStore.latest(dir)!!
        assertEquals(1_700_000_999_000L, info.createdAt)
        assertEquals(newest.toURI().toString(), info.location)
    }

    @Test
    fun unrelatedAndMalformedFilesAreIgnored() {
        val dir = folder.newFolder("safety")
        file(dir, "notes.txt")
        file(dir, "kaskita-safety-abc.json")
        file(dir, "kaskita-safety-1700000000000.json.tmp")
        assertNull(SafetyBackupStore.latest(dir))
    }

    @Test
    fun pruneKeepsOnlyTheNewestFive() {
        val dir = folder.newFolder("safety")
        (1L..8L).forEach { file(dir, "kaskita-safety-${1_700_000_000_000 + it}.json") }
        SafetyBackupStore.prune(dir)
        assertEquals(
            (4L..8L).map { "kaskita-safety-${1_700_000_000_000 + it}.json" },
            dir.listFiles().orEmpty().map { it.name }.sorted(),
        )
    }
}
