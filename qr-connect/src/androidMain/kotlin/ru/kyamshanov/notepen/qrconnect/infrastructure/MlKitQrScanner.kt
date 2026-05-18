package ru.kyamshanov.notepen.qrconnect.infrastructure

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.kyamshanov.notepen.qrconnect.domain.port.QrScanner
import kotlin.coroutines.resume

private val logger = KotlinLogging.logger {}

/**
 * CameraX + ML Kit (bundled, no Google Play Services dependency) implementation
 * of [QrScanner].
 *
 * The scanner is owned by a single screen — pass the screen's [lifecycleOwner]
 * and the [PreviewView] that hosts the camera preview. Collecting [scans]
 * binds the camera use cases; cancelling the collector releases them.
 *
 * Only the rear camera is bound; only QR codes are analysed.
 */
class MlKitQrScanner(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
) : QrScanner {

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun scans(): Flow<String> = callbackFlow {
        val cameraProvider = awaitCameraProvider(context)
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        val mlScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
        val mainExecutor = ContextCompat.getMainExecutor(context)
        analysis.setAnalyzer(mainExecutor) { proxy: ImageProxy ->
            val mediaImage = proxy.image
            if (mediaImage == null) {
                proxy.close()
                return@setAnalyzer
            }
            val input = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
            mlScanner.process(input)
                .addOnSuccessListener { barcodes ->
                    barcodes.firstNotNullOfOrNull { it.rawValue }?.let { value ->
                        trySend(value)
                    }
                }
                .addOnFailureListener { e ->
                    logger.debug { "ML Kit decode failed: ${e.message}" }
                }
                .addOnCompleteListener { proxy.close() }
        }
        runCatching {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        }.onFailure { e ->
            logger.warn { "Camera bind failed: ${e.message}" }
            close(e)
        }
        awaitClose {
            cameraProvider.unbindAll()
            mlScanner.close()
        }
    }
}

private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            { cont.resume(future.get()) },
            ContextCompat.getMainExecutor(context),
        )
    }
