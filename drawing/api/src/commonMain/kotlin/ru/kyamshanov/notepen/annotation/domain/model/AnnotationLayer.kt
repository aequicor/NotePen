package ru.kyamshanov.notepen.annotation.domain.model

/**
 * One contributor's strokes for a single document, kept separate so that notes
 * from different sources (this device, peers received over sync, the host's own
 * baseline) never blend irreversibly and can be filtered or recombined.
 *
 * @property ownerDeviceId device that authored these strokes. The reserved value
 *   [HOST] holds strokes loaded from a legacy flat sidecar that predates
 *   per-author provenance — their original author is unknown.
 * @property pages strokes by zero-based page index.
 */
data class AnnotationLayer(
    val ownerDeviceId: String,
    val pages: Map<Int, List<DrawingPath>>,
) {
    companion object {
        /** Layer id for strokes whose author is unknown (legacy on-disk data). */
        const val HOST = "host"
    }
}
