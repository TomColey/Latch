package com.nathanb.lock.ui.screens.modes

/**
 * Short-lived in-process draft storage used when Mode creation/editing is interrupted to resolve
 * a physical Latch activation conflict in another Mode. It deliberately does not persist to disk:
 * this is navigation assistance, not saved product state.
 */
internal data class ModeEditorDraft(
    val editorModeId: Long?,
    val name: String,
    val allowedPackages: Set<String>,
    val maxLatchDurationMs: Long?,
    val latchUid: String?,
    val unlatchUid: String?,
    val step: Int,
)

internal object ModeEditorDraftStore {
    private var draft: ModeEditorDraft? = null

    fun save(value: ModeEditorDraft) {
        draft = value
    }

    fun take(editorModeId: Long?): ModeEditorDraft? {
        val value = draft?.takeIf { it.editorModeId == editorModeId }
        if (value != null) draft = null
        return value
    }

    fun clear(editorModeId: Long?) {
        if (draft?.editorModeId == editorModeId) draft = null
    }
}
