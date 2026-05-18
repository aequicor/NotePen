package ru.kyamshanov.notepen.qrconnect.domain.port

import kotlinx.coroutines.flow.Flow

/**
 * Stream of raw decoded QR contents. Each emission is the textual payload of a
 * single recognised barcode; the consumer is responsible for filtering it
 * (e.g. through [ru.kyamshanov.notepen.qrconnect.domain.PairingUri.parse]).
 *
 * Implementations own the camera lifecycle — collecting the flow starts the
 * camera, cancelling the collector releases it.
 */
fun interface QrScanner {
    fun scans(): Flow<String>
}
