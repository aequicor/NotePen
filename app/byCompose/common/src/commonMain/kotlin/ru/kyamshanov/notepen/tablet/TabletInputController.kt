package ru.kyamshanov.notepen.tablet

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Stylus / graphics tablet input port.
 *
 * Compose drives stroke geometry (x, y) via its normal pointer pipeline.
 * This controller supplies the **side-channel** the pointer pipeline can't
 * see — pen pressure, barrel-button state, the eraser-tip flag, tilt and
 * hover position — sourced from a platform-specific API (WinTab on Windows,
 * Cocoa NSEvent on macOS, the Android `MotionEvent` digitizer fields, etc).
 *
 * Consumers read the state-flows on every `onDrag` tick (or on hover ticks
 * for [hoverPosition]); the controller pushes updates from its own polling
 * thread or from the platform input pipeline. The read-side race is
 * intentional — losing a single packet at 200+ Hz is invisible.
 */
interface TabletInputController {
    /** Latest pressure sample in `[0..1]`. `1f` when no tablet is attached. */
    val latestPressure: StateFlow<Float>

    /** `true` while the pen's barrel (side) button is held down. */
    val barrelPressed: StateFlow<Boolean>

    /**
     * `true` while the pen is touching the screen with its eraser tip
     * (e.g. flipped S-Pen). UI uses this to override the active tool with
     * ERASER, mirroring the barrel-button hold-to-erase behaviour.
     */
    val eraserTipActive: StateFlow<Boolean>

    /**
     * Latest tilt sample in `[0..1]` where `0f` is perpendicular to the
     * screen and `1f` is parallel. `0f` when not supported.
     */
    val tilt: StateFlow<Float>

    /**
     * Latest hover position in **normalised** canvas coordinates `[0..1]`,
     * or `null` when the pen is not hovering over the drawing surface.
     * Used to draw the hover preview indicator.
     */
    val hoverPosition: StateFlow<Offset?>

    /**
     * `true` while a stylus / eraser-tip is considered "present" — flips on
     * the first stylus / eraser event and stays on until the platform
     * implementation detects that the pen has gone silent for long enough
     * to count as disconnected (typically: a finger event arrives after a
     * substantial idle period without any stylus event). Used by Pencil
     * Mode to auto-enable on first contact AND to auto-recover when the
     * pen driver hangs — without the recovery edge, a wedged S-Pen would
     * leave palm-rejection on with no way to draw with the finger short
     * of a device reboot.
     */
    val stylusEverSeen: StateFlow<Boolean>

    /**
     * Native pen event stream, bypasses Compose's pointer pipeline.
     *
     * Зачем существует: на Windows AWT синтезирует legacy WM_MOUSE из
     * WM_POINTER через Tablet Input Service, и этот шаг добавляет ~400мс
     * задержки на первое движение пера (press-and-hold gesture recognition,
     * который драйверы Huion не уважают через стандартный SetProp). Compose
     * получает MouseEvents только после этой задержки → "линия из центра".
     *
     * Если платформа умеет читать pen-события напрямую (Windows: WndProc
     * subclass + WM_POINTER + GetPointerPenInfo), она публикует их сюда
     * без задержки и с реальным давлением. Drawing-pipeline на десктопе
     * подписывается на этот flow и драйвит штрих в обход Compose pointerInput.
     *
     * Пустой (cold) flow на платформах, где fallback на стандартный Compose
     * pointer pipeline. UI-код проверяет emission, не наличие источника.
     */
    val penPointerEvents: SharedFlow<PenPointerEvent>
        get() = NoOpPenPointerEvents
}

/**
 * Тип события в [TabletInputController.penPointerEvents].
 */
enum class PenPointerEventType { DOWN, UPDATE, UP, CANCEL }

/**
 * Сырое событие пера от native API (Windows WM_POINTER, в будущем — другие).
 *
 * @property type фаза жеста.
 * @property position позиция в window-local координатах (px) — совпадает с
 *  системой координат, которой оперирует Compose pointerInput root'а окна.
 * @property pressure нормализованное давление [0..1], `1f` если устройство
 *  не сообщает давление.
 * @property tilt нормализованный наклон [0..1], `0f` если не сообщается.
 * @property timestamp системное время прибытия события в миллисекундах
 *  (для отладки rate'ов; consume-логика может игнорировать).
 */
data class PenPointerEvent(
    val type: PenPointerEventType,
    val position: Offset,
    val pressure: Float,
    val tilt: Float,
    val timestamp: Long,
)

private val NoOpPenPointerEvents: SharedFlow<PenPointerEvent> =
    MutableSharedFlow<PenPointerEvent>(extraBufferCapacity = 0).asSharedFlow()

/** Fallback controller for platforms without tablet support. */
object NoOpTabletInputController : TabletInputController {
    override val latestPressure: StateFlow<Float> = MutableStateFlow(1f)
    override val barrelPressed: StateFlow<Boolean> = MutableStateFlow(false)
    override val eraserTipActive: StateFlow<Boolean> = MutableStateFlow(false)
    override val tilt: StateFlow<Float> = MutableStateFlow(0f)
    override val hoverPosition: StateFlow<Offset?> = MutableStateFlow(null)
    override val stylusEverSeen: StateFlow<Boolean> = MutableStateFlow(false)
}

/**
 * Provides the active [TabletInputController] to the composition tree.
 * Defaults to [NoOpTabletInputController]; Desktop main wraps the app in a
 * `CompositionLocalProvider` supplying the WinTab-backed implementation,
 * and Android `MainActivity` does the same with an Android implementation.
 */
val LocalTabletInputController = staticCompositionLocalOf<TabletInputController> {
    NoOpTabletInputController
}
