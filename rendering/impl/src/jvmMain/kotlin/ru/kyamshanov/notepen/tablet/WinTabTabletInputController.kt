package ru.kyamshanov.notepen.tablet

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.UINT
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private val logger = KotlinLogging.logger {}

/**
 * Bit index inside `PACKET.buttons` reported as the pen's barrel (side)
 * button. Wintab numbers buttons starting from the tip; bit 1 is the
 * standard convention for the side button on Wacom / Huion drivers.
 */
private const val BARREL_BUTTON_BIT: Int = 1

/**
 * Сколько младших бит маски `PACKET.buttons` сканируется на состояние
 * кнопок пера. У Huion / Wacom-перьев максимум 2-3 кнопки; запас в 8 бит
 * с лихвой покрывает все реальные случаи.
 */
private const val MAX_PEN_BUTTON_BITS: Int = 8

/** How many packets to drain per polling tick. The context queue size is matched. */
private const val PACKET_BATCH: Int = 64

/**
 * Polling interval. 250 Hz comfortably tracks the ~200 Hz report rate of
 * mid-range Huion / Wacom drivers and stays well under the budget for a
 * background daemon thread. We intentionally do not down-shift when idle —
 * the per-tick cost when no packet is available is a single native call.
 */
private const val POLL_INTERVAL_MS: Long = 4

/**
 * [TabletInputController] backed by Wintab32 on Windows.
 *
 * Lifecycle:
 *  1. [attach] — load `wintab32.dll`, query pressure range, open a context for
 *     the given HWND, start the polling thread. Safe to call once the Compose
 *     window is `isDisplayable`.
 *  2. While attached — a single-thread scheduler drains packets and updates
 *     [latestPressure] / [barrelPressed] state-flows. Compose reads these
 *     synchronously from `onDrag` callbacks.
 *  3. [stop] — shut the scheduler down and close the context. Idempotent.
 *
 * On non-Windows platforms or when `wintab32.dll` is missing (Microsoft
 * Surface Pen without a vendor driver), [attach] logs once and the controller
 * stays in the no-op state — `latestPressure` reports `1f`, `barrelPressed`
 * reports `false`. The drawing UI degrades gracefully to mouse-only behaviour.
 */
class WinTabTabletInputController : TabletInputController {
    private val pressureFlow = MutableStateFlow(1f)
    private val buttonFlow = MutableStateFlow(false)
    private val penButtonsFlow = MutableStateFlow<Set<Int>>(emptySet())

    @Volatile private var winTabButtonBits: Set<Int> = emptySet()

    @Volatile private var hookButtonBits: Set<Int> = emptySet()

    private fun mergePenButtons() {
        val merged = winTabButtonBits + hookButtonBits
        if (merged != penButtonsFlow.value) penButtonsFlow.value = merged
    }

    override val latestPressure: StateFlow<Float> = pressureFlow.asStateFlow()
    override val barrelPressed: StateFlow<Boolean> = buttonFlow.asStateFlow()
    override val penButtons: StateFlow<Set<Int>> = penButtonsFlow.asStateFlow()
    override val eraserTipActive: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
    override val tilt: StateFlow<Float> = MutableStateFlow(0f).asStateFlow()
    override val hoverPosition: StateFlow<androidx.compose.ui.geometry.Offset?> =
        MutableStateFlow<androidx.compose.ui.geometry.Offset?>(null).asStateFlow()
    override val stylusEverSeen: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    // Pen-стрим от WM_POINTER напрямую: bypass AWT'шной 400мс задержки
    // на синтезации legacy WM_MOUSE из WM_POINTER (см. KDoc у
    // [WindowsPointerHook]). Drawing-pipeline подписывается на этот flow
    // через [TabletInputController.penPointerEvents] и драйвит штрих сам,
    // минуя Compose pointerInput для pen-устройств.
    override val penPointerEvents: kotlinx.coroutines.flow.SharedFlow<PenPointerEvent>
        get() = WindowsPointerHook.pointerEvents

    private val wintab = AtomicReference<WinTab?>(null)
    private val context = AtomicReference<com.sun.jna.Pointer?>(null)
    private var maxPressure: Float = 1f
    private var scheduler: ScheduledExecutorService? = null

    /** Open a Wintab context for [hwnd] and start polling. No-op on non-Windows. */
    fun attach(hwnd: HWND) {
        if (!Platform.isWindows()) {
            logger.info { "TabletInput: non-Windows platform, staying in no-op mode" }
            return
        }
        // Подписка на кнопки пера из WM_POINTER-стрима — независимо от WinTab.
        // Это покрывает планшеты без WinTab-драйвера (Microsoft Pen) и
        // планшеты, где barrel выведен только через POINTER_FLAG_SECONDBUTTON.
        WindowsPointerHook.penButtonsListener = { bits ->
            hookButtonBits = bits
            mergePenButtons()
        }
        val lib =
            try {
                Native.load("Wintab32", WinTab::class.java)
            } catch (e: UnsatisfiedLinkError) {
                logger.info { "TabletInput: wintab32.dll not available (${e.message}); pen pressure disabled" }
                return
            } catch (e: NoClassDefFoundError) {
                logger.warn { "TabletInput: JNA classes missing (${e.message}); pen pressure disabled" }
                return
            }
        wintab.set(lib)
        maxPressure = readMaxPressure(lib).coerceAtLeast(1f)

        val ctx = openContext(lib, hwnd)
        if (ctx == null) {
            logger.warn { "TabletInput: WTOpen failed; pen pressure disabled" }
            wintab.set(null)
            return
        }
        context.set(ctx)
        startPolling(lib, ctx)
        logger.info { "TabletInput: attached (maxPressure=$maxPressure)" }
    }

    /** Stop polling and release the Wintab context. Safe to call repeatedly. */
    fun stop() {
        WindowsPointerHook.penButtonsListener = null
        hookButtonBits = emptySet()
        winTabButtonBits = emptySet()
        mergePenButtons()
        scheduler?.shutdownNow()
        scheduler = null
        val ctx = context.getAndSet(null) ?: return
        wintab.get()?.WTClose(ctx)
        wintab.set(null)
    }

    private fun readMaxPressure(lib: WinTab): Float {
        val axis = AXIS()
        val size =
            lib.WTInfoA(
                UINT(WinTab.WTI_DEVICES.toLong()),
                UINT(WinTab.DVC_NPRESSURE.toLong()),
                axis.pointer,
            )
        if (size.toInt() == 0) return 1f
        axis.read()
        return axis.axMax.toFloat()
    }

    private fun openContext(
        lib: WinTab,
        hwnd: HWND,
    ): com.sun.jna.Pointer? {
        // Start from the default system context — driver fills in sensible
        // defaults for the active device — then override only what we need.
        val ctx = LOGCONTEXTA()
        val size =
            lib.WTInfoA(
                UINT(WinTab.WTI_DEFSYSCTX.toLong()),
                UINT(0),
                ctx.pointer,
            )
        if (size.toInt() == 0) return null
        ctx.read()

        ctx.lcOptions = ctx.lcOptions or WinTab.CXO_SYSTEM
        ctx.lcPktData = WinTab.PK_X or WinTab.PK_Y or WinTab.PK_BUTTONS or
            WinTab.PK_NORMAL_PRESSURE or WinTab.PK_TIME
        ctx.lcPktMode = 0 // absolute mode for all fields
        ctx.lcMoveMask = WinTab.PK_X or WinTab.PK_Y or WinTab.PK_NORMAL_PRESSURE
        ctx.lcBtnUpMask = ctx.lcBtnDnMask
        ctx.write()

        return lib.WTOpenA(hwnd, ctx, true)
    }

    private fun startPolling(
        lib: WinTab,
        ctx: com.sun.jna.Pointer,
    ) {
        val exec =
            Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "WinTab-poll").apply { isDaemon = true }
            }
        scheduler = exec

        val buffer = Memory((PACKET_BATCH * WINTAB_PACKET_SIZE).toLong())

        val task =
            Runnable {
                try {
                    val count = lib.WTPacketsGet(ctx, PACKET_BATCH, buffer)
                    if (count <= 0) return@Runnable
                    // Use only the latest packet for pressure — older samples are
                    // already obsolete by the time we read them.
                    val last = readPacket(buffer, ((count - 1).toLong()) * WINTAB_PACKET_SIZE)
                    pressureFlow.value = (last.pressure / maxPressure).coerceIn(0f, 1f)
                    val pressed = (last.buttons shr BARREL_BUTTON_BIT) and 1 == 1
                    if (pressed != buttonFlow.value) buttonFlow.value = pressed
                    // Биты кнопок пера для биндингов. Бит 0 — это «pen tip down»
                    // (касание тиром, а не физическая кнопка), его в шорткаты
                    // не пропускаем — иначе любое касание во время записи
                    // сохраняется как «Pen0». Сканируем биты [1..MAX_PEN_BUTTON_BITS).
                    val bits = last.buttons
                    val newSet =
                        buildSet {
                            for (bit in 1 until MAX_PEN_BUTTON_BITS) {
                                if ((bits shr bit) and 1 == 1) add(bit)
                            }
                        }
                    if (newSet != winTabButtonBits) {
                        winTabButtonBits = newSet
                        mergePenButtons()
                    }
                } catch (t: Throwable) {
                    logger.warn(t) { "TabletInput: poll failed" }
                }
            }

        exec.scheduleWithFixedDelay(task, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }
}
