package com.nathanb.lock.nfc

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.R
import com.nathanb.lock.data.model.EndReason
import com.nathanb.lock.data.model.LatchAction
import com.nathanb.lock.data.model.ProfileType
import com.nathanb.lock.data.repository.LatchRuntime
import com.nathanb.lock.data.repository.LockRepository

/** Outcome of trying to write our routing data to a tag during pairing. */
enum class NdefWriteResult {
    SUCCESS,
    TRANSIENT_FAILURE,
    WRITE_PROTECTED,
}

sealed interface NfcResult {
    data class TagPaired(val uid: String, val writeResult: NdefWriteResult) : NfcResult
    data class Started(val profileId: Long, val tagName: String?, val isNoEscape: Boolean) : NfcResult
    data class Stopped(val tagName: String?) : NfcResult
    data class ModeLatched(val modeId: Long, val tagName: String?) : NfcResult
    data class ModeUnlatched(val tagName: String?) : NfcResult
    data class ModeActivationConflict(val tagName: String?) : NfcResult
    data class ModeActionIgnored(
        val activeModeName: String? = null,
        val requiredLatchNames: List<String> = emptyList(),
    ) : NfcResult
    data object IgnoredNoEscapeActive : NfcResult
    data object UnknownTag : NfcResult
    data class Error(@StringRes val messageRes: Int) : NfcResult
}

class NfcManager(
    private val repository: LockRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    companion object {
        private const val TAG = "NfcManager"
        private const val MIME_TYPE = "application/vnd.latch.toggle"
        private const val APP_PACKAGE = "com.tomcoley.latch"
        private const val PAIRING_GRACE_MS = 3_000L
        private const val TOGGLE_REFRACTORY_MS = 1_000L
        const val MAX_WRITE_RETRIES = 5
    }

    private var isPairingMode = false
    private var justPairedUid: String? = null
    private var justPairedAt: Long = 0L
    private var lastToggleUid: String? = null
    private var lastToggleAt: Long = 0L
    private var writeFailureCount = 0
    private var rewriteUid: String? = null

    fun consecutiveWriteFailures(): Int = writeFailureCount

    fun enablePairingMode() {
        isPairingMode = true
        writeFailureCount = 0
    }

    fun disablePairingMode() {
        isPairingMode = false
        writeFailureCount = 0
    }

    fun forcePairWithoutWrite() {
        isPairingMode = false
        writeFailureCount = 0
    }

    fun enableRewriteMode(uid: String) {
        rewriteUid = uid
    }

    fun disableRewriteMode() {
        rewriteUid = null
    }

    fun enableReaderMode(activity: ComponentActivity, onTagDiscovered: (Tag) -> Unit) {
        val adapter = NfcAdapter.getDefaultAdapter(activity)
        if (adapter == null) {
            if (BuildConfig.DEBUG) Log.w(TAG, "enableReaderMode — adapter is null!")
            return
        }
        try {
            adapter.enableReaderMode(
                activity,
                onTagDiscovered,
                NfcAdapter.FLAG_READER_NFC_A or
                    NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_V,
                null,
            )
            if (BuildConfig.DEBUG) Log.d(TAG, "enableReaderMode — OK")
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "enableReaderMode — FAILED: ${e.message}", e)
        }
    }

    fun disableReaderMode(activity: ComponentActivity) {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        adapter.disableReaderMode(activity)
    }

    suspend fun handleTag(tag: Tag): NfcResult? =
        handleScan(tag.id.toHexString()) { writeNdefToTag(tag) }

    internal suspend fun handleScan(uid: String, write: () -> NdefWriteResult): NfcResult? {
        if (BuildConfig.DEBUG) Log.d(TAG, "Tag scanned: $uid")

        rewriteUid?.let { target ->
            if (uid != target) return null
            val writeResult = write()
            if (writeResult != NdefWriteResult.TRANSIENT_FAILURE) rewriteUid = null
            if (BuildConfig.DEBUG) Log.d(TAG, "Tag rewritten: $uid ($writeResult)")
            return NfcResult.TagPaired(uid, writeResult)
        }

        if (isPairingMode) {
            val writeResult = write()
            if (writeResult == NdefWriteResult.TRANSIENT_FAILURE) {
                writeFailureCount++
            } else {
                writeFailureCount = 0
                isPairingMode = false
                justPairedUid = uid
                justPairedAt = clock()
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Tag paired: $uid ($writeResult, fails=$writeFailureCount)")
            return NfcResult.TagPaired(uid, writeResult)
        }

        val paired = justPairedUid
        if (paired != null) {
            if (uid == paired && clock() - justPairedAt < PAIRING_GRACE_MS) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Tap ignored — pairing grace period")
                return null
            }
            justPairedUid = null
        }

        if (uid == lastToggleUid && clock() - lastToggleAt < TOGGLE_REFRACTORY_MS) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Tap ignored — toggle refractory window")
            return null
        }

        val result = processKnownTag(uid)
        if (
            result is NfcResult.Started ||
            result is NfcResult.Stopped ||
            result is NfcResult.ModeLatched ||
            result is NfcResult.ModeUnlatched
        ) {
            lastToggleUid = uid
            lastToggleAt = clock()
        }
        return result
    }

    suspend fun handleIntent(intent: Intent): NfcResult? {
        val action = intent.action ?: return null
        if (action != NfcAdapter.ACTION_TAG_DISCOVERED &&
            action != NfcAdapter.ACTION_NDEF_DISCOVERED &&
            action != NfcAdapter.ACTION_TECH_DISCOVERED
        ) return null

        val tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java) ?: return null
        return handleTag(tag)
    }

    /**
     * Latch-owned tags are resolved before the inherited Lock model. Once a tag has been added to
     * Latch, it never falls through to old Profile behaviour. This prevents a physical Latch with
     * no valid action for the current Mode from accidentally toggling an inherited Lock session.
     */
    internal suspend fun processKnownTag(uid: String): NfcResult {
        val latchRepository = LatchRuntime.repositoryOrNull()
        val latchDevice = latchRepository?.getLatchDevice(uid)
        if (latchRepository != null && latchDevice != null) {
            val links = latchRepository.getActionsForLatch(uid)
            val activeModeId = latchRepository.activeModeState.value.activeModeId

            if (activeModeId != null) {
                val releaseLink = links.firstOrNull { link ->
                    link.modeId == activeModeId &&
                        (LatchAction.fromValue(link.action) == LatchAction.UNLATCH ||
                            LatchAction.fromValue(link.action) == LatchAction.TOGGLE)
                }
                return if (releaseLink != null) {
                    latchRepository.unlatch()
                    if (BuildConfig.DEBUG) Log.d(TAG, "Mode $activeModeId unlatched by ${latchDevice.name}")
                    NfcResult.ModeUnlatched(latchDevice.name)
                } else {
                    val activeModeName = latchRepository.getMode(activeModeId)?.name
                    val releaseLinks = latchRepository.getLatchActionsForMode(activeModeId)
                        .filter { link ->
                            val action = LatchAction.fromValue(link.action)
                            action == LatchAction.UNLATCH || action == LatchAction.TOGGLE
                        }
                    val requiredLatchNames = releaseLinks
                        .mapNotNull { link -> latchRepository.getLatchDevice(link.latchUid)?.name }
                        .distinct()
                    if (BuildConfig.DEBUG) {
                        Log.d(
                            TAG,
                            "Latch ${latchDevice.name} cannot release Mode $activeModeId; required=$requiredLatchNames",
                        )
                    }
                    NfcResult.ModeActionIgnored(
                        activeModeName = activeModeName,
                        requiredLatchNames = requiredLatchNames,
                    )
                }
            }

            val activationLinks = links.filter { link ->
                val action = LatchAction.fromValue(link.action)
                action == LatchAction.LATCH || action == LatchAction.TOGGLE
            }
            val activationModeIds = activationLinks.map { it.modeId }.distinct()
            if (activationModeIds.size > 1) {
                if (BuildConfig.DEBUG) {
                    Log.e(
                        TAG,
                        "Ambiguous activation blocked for ${latchDevice.name}: Modes $activationModeIds",
                    )
                }
                return NfcResult.ModeActivationConflict(latchDevice.name)
            }

            val activationModeId = activationModeIds.singleOrNull()
            return if (activationModeId != null && latchRepository.latch(activationModeId)) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Mode $activationModeId latched by ${latchDevice.name}")
                NfcResult.ModeLatched(activationModeId, latchDevice.name)
            } else {
                if (BuildConfig.DEBUG) Log.d(TAG, "Latch ${latchDevice.name} has no activation action")
                NfcResult.ModeActionIgnored()
            }
        }

        // Inherited Lock behaviour remains as a fallback for tags not yet migrated into Latch.
        val knownTag = repository.findNfcTag(uid)
        if (knownTag == null) {
            if (!repository.hasAnyNfcTag()) {
                return NfcResult.Error(R.string.toast_error_no_tag)
            }
            if (BuildConfig.DEBUG) Log.d(TAG, "Unknown tag: $uid")
            return NfcResult.UnknownTag
        }

        val state = repository.getLockState()
        return if (state.isLocked) {
            if (state.isNoEscape) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Tap ignored — no-escape session active")
                NfcResult.IgnoredNoEscapeActive
            } else {
                repository.endLockSession(EndReason.NFC.value)
                if (BuildConfig.DEBUG) Log.d(TAG, "Stopped via NFC (tag: ${knownTag.name})")
                NfcResult.Stopped(knownTag.name)
            }
        } else {
            val profileId = knownTag.profileId ?: repository.getDefaultProfile()?.id
            val profile = profileId?.let { repository.getProfile(it) }
            when {
                profile == null -> NfcResult.Error(R.string.toast_error_no_profile)
                profile.blockedPackages.isEmpty() -> NfcResult.Error(R.string.toast_error_no_apps)
                else -> {
                    repository.startLockSession(profile.id)
                    val isNoEscape = ProfileType.fromValue(profile.type) == ProfileType.NO_ESCAPE
                    if (BuildConfig.DEBUG) Log.d(TAG, "Started via NFC (profile=${profile.id}, SE=$isNoEscape)")
                    NfcResult.Started(profile.id, knownTag.name, isNoEscape)
                }
            }
        }
    }

    private fun writeNdefToTag(tag: Tag): NdefWriteResult {
        val mimeRecord = NdefRecord.createMime(MIME_TYPE, "latch".toByteArray())
        val aarRecord = NdefRecord.createApplicationRecord(APP_PACKAGE)
        val ndefMessage = NdefMessage(arrayOf(mimeRecord, aarRecord))

        return try {
            val ndef = Ndef.get(tag)
            if (ndef != null) {
                ndef.connect()
                if (!ndef.isWritable) {
                    ndef.close()
                    if (BuildConfig.DEBUG) Log.w(TAG, "Tag is read-only")
                    return NdefWriteResult.WRITE_PROTECTED
                }
                ndef.writeNdefMessage(ndefMessage)
                ndef.close()
                if (BuildConfig.DEBUG) Log.d(TAG, "NDEF written to formatted tag")
                NdefWriteResult.SUCCESS
            } else {
                val formatable = NdefFormatable.get(tag)
                    ?: run {
                        if (BuildConfig.DEBUG) Log.w(TAG, "Tag does not support NDEF")
                        return NdefWriteResult.WRITE_PROTECTED
                    }
                formatable.connect()
                formatable.format(ndefMessage)
                formatable.close()
                if (BuildConfig.DEBUG) Log.d(TAG, "NDEF written to newly formatted tag")
                NdefWriteResult.SUCCESS
            }
        } catch (e: android.nfc.FormatException) {
            if (BuildConfig.DEBUG) Log.e(TAG, "NDEF format rejected: ${e.message}", e)
            NdefWriteResult.WRITE_PROTECTED
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e(TAG, "NDEF write interrupted: ${e.message}", e)
            NdefWriteResult.TRANSIENT_FAILURE
        }
    }

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }
}
