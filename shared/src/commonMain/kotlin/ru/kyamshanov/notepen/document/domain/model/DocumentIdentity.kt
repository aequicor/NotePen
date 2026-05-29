package ru.kyamshanov.notepen.document.domain.model

/**
 * Number of leading hex chars of the canonical `sha256` digest carried in the
 * sync wire id. 16 hex chars = 64 bits — collision-resistant enough to
 * disambiguate same-basename documents while keeping the wire id short.
 */
const val WIRE_ID_HASH_PREFIX_LENGTH: Int = 16

/**
 * The full identity of a document used by sync.
 *
 * Bundles the [canonicalId] (content-addressed, full digest) with the
 * [wireId] actually carried on the network. The wire id intentionally keeps
 * the legacy `<basename>#<hash>` *shape* — only the hash source changed (from
 * FNV-1a-of-path to a `sha256`-of-content prefix), so [NetworkMessage]
 * structure and `documentId` semantics on the wire are unchanged.
 *
 * @property canonicalId the full content-addressed identity.
 * @property wireId the `<basename>#<16-hex-sha256-prefix>` string used as the
 *   sync `documentId`. Build it via [wireIdOf] so the formula stays in one place.
 */
data class DocumentIdentity(
    val canonicalId: CanonicalBookId,
    val wireId: String,
)

/**
 * Builds the sync wire id from a [basename] and a full `sha256` hex digest.
 *
 * Wire form: `<basename>#<first 16 hex of sha256>`. The hash prefix
 * disambiguates files that share a basename; the basename keeps the id
 * human-recognisable in logs and cache filenames.
 *
 * Pure and deterministic — same inputs always yield the same id, which is the
 * property sync relies on for cross-device addressing.
 */
fun wireIdOf(
    basename: String,
    sha256Hex: String,
): String = "$basename#${sha256Hex.take(WIRE_ID_HASH_PREFIX_LENGTH)}"

/**
 * Extracts the basename from a file path (last path component), handling both
 * `/` and `\` separators. Used to build the [DocumentIdentity.wireId].
 */
fun basenameOf(filePath: String): String = filePath.substringAfterLast('/').substringAfterLast('\\')
