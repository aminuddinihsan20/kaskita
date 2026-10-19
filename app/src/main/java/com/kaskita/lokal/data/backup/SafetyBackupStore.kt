package com.kaskita.lokal.data.backup

import java.io.File

/** The copy of the user's data that is written to the app's private storage right before every restore. */
data class SafetyBackupInfo(val location: String, val createdAt: Long)

object SafetyBackupStore {
    private const val PREFIX = "kaskita-safety-"
    private const val SUFFIX = ".json"
    const val MAX_KEPT = 5

    fun fileFor(dir: File, createdAt: Long): File = File(dir, "$PREFIX$createdAt$SUFFIX")

    private fun timestampOf(file: File): Long? =
        file.name.takeIf { it.startsWith(PREFIX) && it.endsWith(SUFFIX) }
            ?.removePrefix(PREFIX)?.removeSuffix(SUFFIX)?.toLongOrNull()

    private fun entries(dir: File): List<Pair<File, Long>> =
        dir.listFiles().orEmpty().mapNotNull { file -> timestampOf(file)?.let { file to it } }

    /** The newest safety copy, or null when there is none. */
    fun latest(dir: File): SafetyBackupInfo? =
        entries(dir).maxByOrNull { it.second }?.let { (file, createdAt) -> SafetyBackupInfo(file.toURI().toString(), createdAt) }

    /** Deletes everything except the [MAX_KEPT] newest copies. */
    fun prune(dir: File) {
        entries(dir).sortedByDescending { it.second }.drop(MAX_KEPT).forEach { it.first.delete() }
    }
}
