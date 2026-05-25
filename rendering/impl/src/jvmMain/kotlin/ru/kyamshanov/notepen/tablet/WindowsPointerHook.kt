package ru.kyamshanov.notepen.tablet

import androidx.compose.ui.geometry.Offset
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser.WNDENUMPROC
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

private val logger = KotlinLogging.logger {}

/** GWLP_WNDPROC — индекс адреса оконной процедуры в окно-данных. */
private const val GWLP_WNDPROC: Int = -4

// WM_POINTER* идентификаторы из winuser.h.
private const val WM_POINTERUPDATE: Int = 0x0245
private const val WM_POINTERDOWN: Int = 0x0246
private const val WM_POINTERUP: Int = 0x0247
private const val WM_POINTERENTER: Int = 0x0249
private const val WM_POINTERLEAVE: Int = 0x024A
private const val WM_POINTERCAPTURECHANGED: Int = 0x024C

// POINTER_INPUT_TYPE значения.
private const val PT_PEN: Int = 3

// PEN_MASK биты — какие поля POINTER_PEN_INFO заполнены.
private const val PEN_MASK_PRESSURE: Int = 0x00000001

/** Custom JNA-биндинг (JNA's стандартный User32 не имеет нужных GetPointer*-функций). */
@Suppress("FunctionName")
private interface User32WndProc : StdCallLibrary {
    fun SetWindowLongPtrA(
        hWnd: HWND,
        nIndex: Int,
        dwNewLong: PenWndProc,
    ): Pointer?

    fun SetWindowLongPtrA(
        hWnd: HWND,
        nIndex: Int,
        dwNewLong: Pointer,
    ): Pointer?

    fun CallWindowProcA(
        lpPrevWndFunc: Pointer,
        hWnd: HWND,
        Msg: Int,
        wParam: WPARAM,
        lParam: LPARAM,
    ): LRESULT

    fun GetPointerType(
        pointerId: Int,
        pointerType: IntByReference,
    ): Boolean

    fun GetPointerPenInfo(
        pointerId: Int,
        penInfo: PointerPenInfo,
    ): Boolean

    /**
     * Возвращает массив POINTER_PEN_INFO с историческими сэмплами для
     * указанного pointer'а — оснасткой "newest first, oldest last". MSDN.
     *
     * `entriesCount` — IN: capacity массива, OUT: фактически записано.
     * Если IN < доступного, заполняет ровно IN последних сэмплов.
     */
    fun GetPointerPenInfoHistory(
        pointerId: Int,
        entriesCount: IntByReference,
        penInfo: Array<PointerPenInfo>,
    ): Boolean

    fun ScreenToClient(
        hWnd: HWND,
        lpPoint: POINT,
    ): Boolean
}

/**
 * Верхний предел сэмплов в одном WM_POINTERUPDATE-батче. Huion при ~250Гц
 * device rate и WM_POINTER notify rate ~20Гц = ~12.5 сэмплов / батч. 64 —
 * с большим запасом, на случай fastpath'ов или нестандартных драйверов.
 */
private const val MAX_PEN_HISTORY: Int = 64

/** WNDPROC, который JNA marshall'ит в нативный __stdcall function pointer. */
internal interface PenWndProc : StdCallLibrary.StdCallCallback {
    fun callback(
        hwnd: HWND,
        uMsg: Int,
        wParam: WPARAM,
        lParam: LPARAM,
    ): LRESULT
}

/**
 * POINTER_INFO (winuser.h) — встроена в POINTER_PEN_INFO. Только нужные поля
 * объявлены явно; остальные представлены `byte[]` padding, чтобы offset'ы
 * полей в POINTER_PEN_INFO совпали с native layout.
 */
@Structure.FieldOrder(
    "pointerType", "pointerId", "frameId", "pointerFlags",
    "sourceDevice", "hwndTarget",
    "ptPixelLocationX", "ptPixelLocationY",
    "ptHimetricLocationX", "ptHimetricLocationY",
    "ptPixelLocationRawX", "ptPixelLocationRawY",
    "ptHimetricLocationRawX", "ptHimetricLocationRawY",
    "dwTime", "historyCount", "inputData", "dwKeyStates",
    "performanceCount", "buttonChangeType",
)
internal open class PointerInfo : Structure() {
    @JvmField var pointerType: Int = 0

    @JvmField var pointerId: Int = 0

    @JvmField var frameId: Int = 0

    @JvmField var pointerFlags: Int = 0

    @JvmField var sourceDevice: Pointer? = null

    @JvmField var hwndTarget: Pointer? = null

    @JvmField var ptPixelLocationX: Int = 0

    @JvmField var ptPixelLocationY: Int = 0

    @JvmField var ptHimetricLocationX: Int = 0

    @JvmField var ptHimetricLocationY: Int = 0

    @JvmField var ptPixelLocationRawX: Int = 0

    @JvmField var ptPixelLocationRawY: Int = 0

    @JvmField var ptHimetricLocationRawX: Int = 0

    @JvmField var ptHimetricLocationRawY: Int = 0

    @JvmField var dwTime: Int = 0

    @JvmField var historyCount: Int = 0

    @JvmField var inputData: Int = 0

    @JvmField var dwKeyStates: Int = 0

    @JvmField var performanceCount: Long = 0

    @JvmField var buttonChangeType: Int = 0
}

/**
 * POINTER_PEN_INFO (winuser.h). [pointerInfo] inlined — JNA читает его поля
 * как первые байты структуры, что соответствует C layout'у.
 */
@Structure.FieldOrder("pointerInfo", "penFlags", "penMask", "pressure", "rotation", "tiltX", "tiltY")
internal open class PointerPenInfo : Structure() {
    @JvmField var pointerInfo: PointerInfo = PointerInfo()

    @JvmField var penFlags: Int = 0

    @JvmField var penMask: Int = 0

    @JvmField var pressure: Int = 0 // 0..1024 (нормированное native API)

    @JvmField var rotation: Int = 0 // 0..359 (degrees)

    @JvmField var tiltX: Int = 0 // -90..+90

    @JvmField var tiltY: Int = 0 // -90..+90
}

/**
 * Максимальное native-значение давления, возвращаемое `GetPointerPenInfo`.
 * MSDN говорит: "ranges from 0 to 1024". Используем для нормализации в [0..1].
 */
private const val PEN_PRESSURE_MAX: Float = 1024f

/**
 * Subclass'ит оконную процедуру AWT/Skiko-окон, чтобы перехватить WM_POINTER
 * сообщения и опубликовать pen-события в [pointerEvents] в обход AWT.
 *
 * Зачем: Windows синтезирует legacy WM_MOUSE из WM_POINTER через Tablet Input
 * Service. На Huion-планшетах эта синтезация добавляет ~400мс задержки перед
 * первым WM_MOUSEMOVE (press-and-hold gesture recognition, который Huion-
 * драйверы не уважают через стандартный SetProp). WM_POINTER же приходит
 * мгновенно с богатой информацией (pressure, tilt). Hook читает их напрямую
 * и публикует в flow, который drawing-pipeline собирает coroutine'ой,
 * минуя Compose pointerInput полностью для pen-устройств.
 *
 * Чтобы не было двойной отрисовки (наш flow + AWT mouse через 400мс),
 * hook **консумит** pen-WM_POINTER (возвращает 0, не форвардит в default
 * proc) — Windows тогда не синтезирует WM_MOUSE для них. Mouse-WM_POINTER
 * (для обычной мыши) форвардятся как обычно, чтобы Compose продолжал
 * получать обычные mouse-события.
 */
object WindowsPointerHook {
    private val user32: User32WndProc? by lazy {
        if (!Platform.isWindows()) {
            null
        } else {
            runCatching { Native.load("user32", User32WndProc::class.java) }
                .onFailure { logger.warn(it) { "WindowsPointerHook: cannot load user32.dll" } }
                .getOrNull()
        }
    }

    private data class Hook(val hwnd: HWND, val original: Pointer, val callback: PenWndProc)

    private val hooks = mutableMapOf<HWND, Hook>()

    private val penEventsFlow =
        MutableSharedFlow<PenPointerEvent>(
            replay = 0,
            // 1024 ≈ 4 кадра при 250Гц native sample-rate. С DROP_OLDEST tryEmit
            // никогда не блокирует AWT EDT (где живёт WndProc) и не дропает
            // самые свежие сэмплы; теоретически возможны крошечные пропуски
            // в середине жеста при затыке drawing-pipeline'а — Catmull-Rom
            // в renderer'е сгладит.
            extraBufferCapacity = 1024,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    private val penButtonsFlow = MutableStateFlow<Set<Int>>(emptySet())

    /**
     * Слушатель изменений состояния кнопок пера. Регистрируется
     * [WinTabTabletInputController] для слияния WM_POINTER-битов с
     * WinTab-битами в единый StateFlow. Один слушатель за раз — больше
     * сейчас не нужно.
     */
    internal var penButtonsListener: ((Set<Int>) -> Unit)? = null

    /**
     * Текущее состояние кнопок пера, прочитанное из WM_POINTER (POINTER_INFO
     * `pointerFlags` + `PointerPenInfo.penFlags`). Биты соответствуют
     * физическим кнопкам стилуса: 1 — barrel, 2..4 — дополнительные кнопки.
     * Тип пера (FIRSTBUTTON / бит 0) намеренно НЕ включается — это касание,
     * не кнопка.
     *
     * Обновляется на каждом pen-событии (включая hover): шорткаты должны
     * срабатывать без касания планшета. На платформах с WinTab набор
     * сливается с WinTab-кнопками в [WinTabTabletInputController].
     */
    val penButtons: StateFlow<Set<Int>> = penButtonsFlow.asStateFlow()

    /** Поток pen-событий от Windows Ink, для drawing-pipeline'а. */
    val pointerEvents: SharedFlow<PenPointerEvent> = penEventsFlow.asSharedFlow()

    /**
     * Устанавливает subclass на [root] и все его дочерние окна.
     */
    fun install(root: HWND) {
        val lib = user32 ?: return
        installOn(lib, root)
        try {
            User32.INSTANCE.EnumChildWindows(
                root,
                WNDENUMPROC { child, _ ->
                    installOn(lib, child)
                    true
                },
                Pointer.NULL,
            )
        } catch (t: Throwable) {
            logger.warn(t) { "WindowsPointerHook: EnumChildWindows threw" }
        }
        logger.info { "WindowsPointerHook: subclassed ${hooks.size} window(s)" }
    }

    /** Снимает все ранее установленные subclass'ы. Идемпотентно. */
    fun uninstall() {
        val lib = user32 ?: return
        val snapshot = hooks.toMap()
        hooks.clear()
        for ((hwnd, hook) in snapshot) {
            runCatching { lib.SetWindowLongPtrA(hwnd, GWLP_WNDPROC, hook.original) }
        }
    }

    private fun installOn(
        lib: User32WndProc,
        hwnd: HWND,
    ) {
        if (hooks.containsKey(hwnd)) return
        val callback =
            object : PenWndProc {
                override fun callback(
                    hwnd: HWND,
                    uMsg: Int,
                    wParam: WPARAM,
                    lParam: LPARAM,
                ): LRESULT {
                    // Forward по умолчанию через локальную lambda, чтобы каждый
                    // ранний return в обработке мог однострочно делегировать.
                    val forward: () -> LRESULT = {
                        val h = hooks[hwnd]
                        if (h != null) {
                            lib.CallWindowProcA(h.original, hwnd, uMsg, wParam, lParam)
                        } else {
                            LRESULT(0)
                        }
                    }
                    if (uMsg !in WM_POINTERUPDATE..WM_POINTERCAPTURECHANGED) return forward()

                    val pointerId = (wParam.toLong() and 0xFFFF).toInt()
                    val typeRef = IntByReference()
                    if (!lib.GetPointerType(pointerId, typeRef)) return forward()
                    if (typeRef.value != PT_PEN) return forward() // touch/mouse — пусть AWT обрабатывает

                    // Pen-сообщение: парсим, публикуем, **консумим** (return 0)
                    // чтобы Windows не синтезировал legacy WM_MOUSE с задержкой.
                    handlePenMessage(lib, hwnd, uMsg, pointerId)
                    return LRESULT(0)
                }
            }
        val original =
            try {
                lib.SetWindowLongPtrA(hwnd, GWLP_WNDPROC, callback)
            } catch (t: Throwable) {
                logger.warn(t) { "WindowsPointerHook: SetWindowLongPtrA threw for $hwnd" }
                return
            }
        if (original == null || original == Pointer.NULL) {
            logger.warn { "WindowsPointerHook: SetWindowLongPtrA returned null for $hwnd" }
            return
        }
        hooks[hwnd] = Hook(hwnd, original, callback)
    }

    private fun handlePenMessage(
        lib: User32WndProc,
        hwnd: HWND,
        uMsg: Int,
        pointerId: Int,
    ) {
        when (uMsg) {
            WM_POINTERDOWN -> emitFromInfo(lib, hwnd, pointerId, PenPointerEventType.DOWN)
            WM_POINTERUP -> emitFromInfo(lib, hwnd, pointerId, PenPointerEventType.UP)
            WM_POINTERUPDATE -> emitHistoryAsUpdates(lib, hwnd, pointerId)
            // ENTER / LEAVE / CAPCHG нас интересуют только как hover-индикатор;
            // drawing-pipeline их не требует — пропускаем (Compose hoverPosition
            // обновляется отдельным каналом, если потребуется).
            else -> Unit
        }
    }

    /**
     * Достаёт исторический батч сэмплов пера для одного WM_POINTERUPDATE
     * и эмитит их в хронологическом порядке (oldest → newest). Windows
     * шлёт UPDATE-нотификации ~20Гц, но между ними устройство семплит на
     * ~200-300Гц; без `GetPointerPenInfoHistory` мы бы дропали ~12 сэмплов
     * на каждый видимый UPDATE → штрих смотрится ступенчатым.
     */
    private fun emitHistoryAsUpdates(
        lib: User32WndProc,
        hwnd: HWND,
        pointerId: Int,
    ) {
        @Suppress("UNCHECKED_CAST")
        val historyArr = PointerPenInfo().toArray(MAX_PEN_HISTORY) as Array<PointerPenInfo>
        val countRef = IntByReference(MAX_PEN_HISTORY)
        val ok =
            try {
                lib.GetPointerPenInfoHistory(pointerId, countRef, historyArr)
            } catch (t: Throwable) {
                logger.warn(t) { "WindowsPointerHook: GetPointerPenInfoHistory threw" }
                // Fallback: одиночный сэмпл.
                emitFromInfo(lib, hwnd, pointerId, PenPointerEventType.UPDATE)
                return
            }
        if (!ok || countRef.value <= 0) {
            emitFromInfo(lib, hwnd, pointerId, PenPointerEventType.UPDATE)
            return
        }
        // MSDN: массив заполнен newest-first. Эмитим в обратном порядке —
        // drawing-pipeline ожидает хронологию.
        val n = countRef.value
        for (i in (n - 1) downTo 0) {
            historyArr[i].read()
            emitFromParsedInfo(lib, hwnd, historyArr[i], PenPointerEventType.UPDATE)
        }
    }

    /** Однократный запрос `GetPointerPenInfo` + эмит как [phase]. */
    private fun emitFromInfo(
        lib: User32WndProc,
        hwnd: HWND,
        pointerId: Int,
        phase: PenPointerEventType,
    ) {
        val info = PointerPenInfo()
        if (!lib.GetPointerPenInfo(pointerId, info)) return
        info.read()
        emitFromParsedInfo(lib, hwnd, info, phase)
    }

    /**
     * Парсит уже прочитанный [info] и публикует событие. Для UPDATE-фазы
     * фильтрует hover'ы (нет [POINTER_FLAG_INCONTACT]) — drawing-pipeline
     * их не использует.
     */
    private fun emitFromParsedInfo(
        lib: User32WndProc,
        hwnd: HWND,
        info: PointerPenInfo,
        phase: PenPointerEventType,
    ) {
        // Обновляем состояние кнопок пера ДО hover-фильтра — barrel может
        // быть нажат, пока перо просто висит над планшетом, и шорткаты
        // должны это видеть. На POINTERUP — если pen ушёл из proximity —
        // POINTER_FLAG_*BUTTON соответственно очистятся в следующем событии.
        updatePenButtons(info)
        if (phase == PenPointerEventType.UPDATE &&
            (info.pointerInfo.pointerFlags and POINTER_FLAG_INCONTACT) == 0
        ) {
            return
        }

        // Screen → client coords (relative to hooked HWND). Для root-окна это
        // даст координаты от верхнего-левого угла client area JFrame'а; для
        // Skiko child'а — от его верхнего-левого. Compose pointerInput модификатор
        // viewer'а сидит внутри Skiko-канваса, и его местные координаты
        // совпадают с client-coords Skiko HWND'а — это та же система, что у
        // Compose pointer-input'а из AWT mouse events.
        val pt = POINT(info.pointerInfo.ptPixelLocationX, info.pointerInfo.ptPixelLocationY)
        if (!lib.ScreenToClient(hwnd, pt)) return

        val pressure =
            if ((info.penMask and PEN_MASK_PRESSURE) != 0) {
                (info.pressure.toFloat() / PEN_PRESSURE_MAX).coerceIn(0f, 1f)
            } else {
                1f
            }
        val tilt =
            run {
                val maxAbs = maxOf(kotlin.math.abs(info.tiltX), kotlin.math.abs(info.tiltY))
                (maxAbs.toFloat() / 90f).coerceIn(0f, 1f)
            }

        penEventsFlow.tryEmit(
            PenPointerEvent(
                type = phase,
                position = Offset(pt.x.toFloat(), pt.y.toFloat()),
                pressure = pressure,
                tilt = tilt,
                timestamp = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Извлекает состояние физических кнопок пера из [POINTER_INFO.pointerFlags]
     * и [PointerPenInfo.penFlags], кладёт в [penButtonsFlow] как Set<Int>:
     * - бит 1 = barrel (SECONDBUTTON ∨ PEN_FLAG_BARREL),
     * - биты 2..4 = дополнительные кнопки.
     * Бит 0 (касание тиром) намеренно НЕ включается.
     */
    private fun updatePenButtons(info: PointerPenInfo) {
        val pf = info.pointerInfo.pointerFlags
        val barrel =
            (pf and POINTER_FLAG_SECONDBUTTON) != 0 ||
                (info.penFlags and PEN_FLAG_BARREL) != 0
        val newSet =
            buildSet {
                if (barrel) add(1)
                if ((pf and POINTER_FLAG_THIRDBUTTON) != 0) add(2)
                if ((pf and POINTER_FLAG_FOURTHBUTTON) != 0) add(3)
                if ((pf and POINTER_FLAG_FIFTHBUTTON) != 0) add(4)
            }
        if (newSet != penButtonsFlow.value) {
            penButtonsFlow.value = newSet
            penButtonsListener?.invoke(newSet)
        }
    }
}

/** [POINTER_INFO.pointerFlags] бит — перо/палец касается экрана. */
private const val POINTER_FLAG_INCONTACT: Int = 0x00000004

// Биты состояния кнопок указателя — у пера соответствуют физическим кнопкам.
// FIRSTBUTTON = тип пера (касание); SECONDBUTTON = barrel; далее
// дополнительные кнопки многокнопочных перьев.
private const val POINTER_FLAG_FIRSTBUTTON: Int = 0x00000010
private const val POINTER_FLAG_SECONDBUTTON: Int = 0x00000020
private const val POINTER_FLAG_THIRDBUTTON: Int = 0x00000040
private const val POINTER_FLAG_FOURTHBUTTON: Int = 0x00000080
private const val POINTER_FLAG_FIFTHBUTTON: Int = 0x00000100

/** [PointerPenInfo.penFlags] — barrel-кнопка зажата (дублирует SECONDBUTTON). */
private const val PEN_FLAG_BARREL: Int = 0x00000001
