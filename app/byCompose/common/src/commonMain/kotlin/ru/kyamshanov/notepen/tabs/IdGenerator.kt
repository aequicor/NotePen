package ru.kyamshanov.notepen.tabs

/**
 * Source of session-unique [DocumentId]s for opening new tabs. Injected
 * into [OpenDocuments] (and thus into [TabSession]) so tests can supply
 * a deterministic counter.
 *
 * Implementations are expected to be called from the UI thread; the
 * default [SequentialIdGenerator] is intentionally non-atomic because
 * tabs are opened in response to user input on the main thread.
 */
interface IdGenerator {
    /** Returns the next id. Successive calls must return distinct values. */
    fun next(): DocumentId
}

/**
 * Default [IdGenerator] backed by a monotonically increasing `Long`. Not
 * thread-safe; intended for UI-thread use.
 */
class SequentialIdGenerator(
    initial: Long = 0L,
) : IdGenerator {
    private var counter: Long = initial

    override fun next(): DocumentId {
        counter += 1L
        return DocumentId(counter)
    }
}

/**
 * Source of fallback display names for tabs that open a file whose
 * filesystem name cannot be recovered (rare on JVM; common when the
 * picker returned an opaque content-URI on Android).
 *
 * Counts monotonically across the entire app session, never reused
 * — matches the "Document N" semantics in the feature spec.
 */
class FallbackNameCounter(
    initial: Int = 0,
) {
    private var counter: Int = initial

    /** Returns the next "Document N" name. */
    fun next(): String {
        counter += 1
        return "Document $counter"
    }
}
