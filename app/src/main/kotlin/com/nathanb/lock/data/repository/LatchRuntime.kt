package com.nathanb.lock.data.repository

/**
 * Temporary application-level bridge used while inherited Lock services are migrated to Latch.
 *
 * Android creates the existing NFC/Accessibility plumbing through framework-owned components,
 * so this keeps the new LatchRepository reachable without forcing a large dependency rewrite in
 * the middle of the staged migration. Remove this once those components are fully Latch-native.
 */
object LatchRuntime {
    @Volatile
    private var repository: LatchRepository? = null

    fun install(repository: LatchRepository) {
        this.repository = repository
    }

    fun repositoryOrNull(): LatchRepository? = repository
}
