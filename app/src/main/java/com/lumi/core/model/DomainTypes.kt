package com.lumi.core.model

/**
 * The capabilities the dispatcher can route to.
 *
 * This is the closed set. Adding a capability means adding a value here, which forces
 * every `when` over it to be updated — that is the point. A capability the audit log
 * cannot name is a capability the user cannot see.
 */
enum class CapabilityId {
    CHAT,
    RAG,
    ROUTINE,
    FILES,
    DEVICE,
    JOURNAL,
    MEMORY,
    SOS,
    TOOLS,
}

/** Who produced a chat turn. */
enum class MessageRole {
    USER,
    MODEL,
    TOOL,
    SYSTEM,
}

/** Whether the user wrote a memory entry or Lumi generated it. */
enum class MemoryKind {
    USER_AUTHORED,
    SYSTEM_AUTHORED,
}

/** Where a system-authored memory entry came from. Shown to the user as provenance. */
enum class MemorySource {
    UNSPECIFIED,
    MANUAL,
    CHAT,
    DOCUMENT,
    JOURNAL,
    ROUTINE,
}

/**
 * What starts a routine.
 *
 * Parsed once when the routine is created and then persisted. A firing routine reads its
 * stored trigger and never re-interprets the sentence the user originally typed.
 */
enum class TriggerType {
    TIME,
    LOCATION,
    BATTERY,
    WIFI,
    CALENDAR,
}

/** What a routine does when it fires. */
enum class ActionType {
    WIFI,
    BLUETOOTH,
    FLASHLIGHT,
    SILENT_MODE,
    OPEN_APP,
    NOTIFY,
    SPEAK,
    RUN_RAG_QUERY,
    SOS,
}

/**
 * How an audited action ended.
 *
 * [PARTIAL] exists because it is the honest answer more often than either extreme — an
 * SOS that flashed and sounded but could not send SMS did not succeed and did not fail.
 * The error UX requirement to say "whether the action partially completed" depends on
 * this value being recorded truthfully.
 */
enum class AuditOutcome {
    SUCCESS,
    FAILURE,
    PARTIAL,
    SKIPPED,
}
