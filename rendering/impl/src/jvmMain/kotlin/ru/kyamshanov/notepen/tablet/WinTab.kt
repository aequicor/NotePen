package ru.kyamshanov.notepen.tablet

import com.sun.jna.Library
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LONG
import com.sun.jna.platform.win32.WinDef.UINT

/**
 * Thin JNA binding for the Wintab32 ABI (`wintab32.dll`), installed by every
 * mainstream graphics-tablet vendor on Windows (Wacom, Huion, XP-Pen, Gaomon).
 *
 * Only the subset we actually need is bound:
 *
 *  - `WTInfoA` — read tablet capabilities, in particular the pressure axis range.
 *  - `WTOpenA` — create a context attached to our HWND that produces packets
 *    on a queue Wintab manages internally.
 *  - `WTPacketsGet` — drain pending packets in bulk (avoids the queue filling
 *    up when the polling thread sleeps).
 *  - `WTClose` — release the context on shutdown.
 *
 * The Wintab spec is openly published by Wacom; field offsets / sizes are
 * defined by the `LOGCONTEXTA` and `PACKET` structures below and **must** match
 * the DLL's layout, otherwise we'll read garbage. Sticking to a fixed packet
 * mask keeps the layout deterministic.
 */
@Suppress("FunctionName", "PropertyName", "VariableNaming")
internal interface WinTab : Library {
    fun WTInfoA(
        category: UINT,
        index: UINT,
        output: Pointer?,
    ): UINT

    fun WTOpenA(
        hwnd: HWND,
        logContext: LOGCONTEXTA,
        enable: Boolean,
    ): Pointer?

    fun WTClose(hctx: Pointer): Boolean

    fun WTPacketsGet(
        hctx: Pointer,
        maxPackets: Int,
        packetsOut: Pointer,
    ): Int

    companion object {
        /** Categories for `WTInfoA`. */
        const val WTI_DEFSYSCTX: Int = 4
        const val WTI_DEVICES: Int = 100

        /** Index inside `WTI_DEVICES` returning the pressure axis as `AXIS`. */
        const val DVC_NPRESSURE: Int = 15

        /** Packet field bitmask values we request. */
        const val PK_CONTEXT: Int = 0x0001
        const val PK_STATUS: Int = 0x0002
        const val PK_TIME: Int = 0x0004
        const val PK_CHANGED: Int = 0x0008
        const val PK_SERIAL_NUMBER: Int = 0x0010
        const val PK_BUTTONS: Int = 0x0040
        const val PK_X: Int = 0x0080
        const val PK_Y: Int = 0x0100
        const val PK_NORMAL_PRESSURE: Int = 0x0400

        /** Context option bits. */
        const val CXO_SYSTEM: Int = 0x0001
        const val CXO_MESSAGES: Int = 0x0004
    }
}

/**
 * Wintab `LOGCONTEXTA` (ANSI variant). Field order and types come straight
 * from `wintab.h`. Do **not** reorder.
 */
@Suppress("PropertyName", "VariableNaming")
internal open class LOGCONTEXTA : Structure() {
    @JvmField var lcName: ByteArray = ByteArray(LC_NAMELEN)

    @JvmField var lcOptions: Int = 0

    @JvmField var lcStatus: Int = 0

    @JvmField var lcLocks: Int = 0

    @JvmField var lcMsgBase: Int = 0

    @JvmField var lcDevice: Int = 0

    @JvmField var lcPktRate: Int = 0

    @JvmField var lcPktData: Int = 0

    @JvmField var lcPktMode: Int = 0

    @JvmField var lcMoveMask: Int = 0

    @JvmField var lcBtnDnMask: Int = 0

    @JvmField var lcBtnUpMask: Int = 0

    @JvmField var lcInOrgX: Int = 0

    @JvmField var lcInOrgY: Int = 0

    @JvmField var lcInOrgZ: Int = 0

    @JvmField var lcInExtX: Int = 0

    @JvmField var lcInExtY: Int = 0

    @JvmField var lcInExtZ: Int = 0

    @JvmField var lcOutOrgX: Int = 0

    @JvmField var lcOutOrgY: Int = 0

    @JvmField var lcOutOrgZ: Int = 0

    @JvmField var lcOutExtX: Int = 0

    @JvmField var lcOutExtY: Int = 0

    @JvmField var lcOutExtZ: Int = 0

    @JvmField var lcSensX: Int = 0

    @JvmField var lcSensY: Int = 0

    @JvmField var lcSensZ: Int = 0

    @JvmField var lcSysMode: Int = 0

    @JvmField var lcSysOrgX: Int = 0

    @JvmField var lcSysOrgY: Int = 0

    @JvmField var lcSysExtX: Int = 0

    @JvmField var lcSysExtY: Int = 0

    @JvmField var lcSysSensX: Int = 0

    @JvmField var lcSysSensY: Int = 0

    override fun getFieldOrder(): List<String> =
        listOf(
            "lcName",
            "lcOptions",
            "lcStatus",
            "lcLocks",
            "lcMsgBase",
            "lcDevice",
            "lcPktRate",
            "lcPktData",
            "lcPktMode",
            "lcMoveMask",
            "lcBtnDnMask",
            "lcBtnUpMask",
            "lcInOrgX",
            "lcInOrgY",
            "lcInOrgZ",
            "lcInExtX",
            "lcInExtY",
            "lcInExtZ",
            "lcOutOrgX",
            "lcOutOrgY",
            "lcOutOrgZ",
            "lcOutExtX",
            "lcOutExtY",
            "lcOutExtZ",
            "lcSensX",
            "lcSensY",
            "lcSensZ",
            "lcSysMode",
            "lcSysOrgX",
            "lcSysOrgY",
            "lcSysExtX",
            "lcSysExtY",
            "lcSysSensX",
            "lcSysSensY",
        )

    companion object {
        const val LC_NAMELEN: Int = 40
    }
}

/**
 * Layout of a single packet when the context's `lcPktData` is set to the
 * exact mask `PK_X | PK_Y | PK_BUTTONS | PK_NORMAL_PRESSURE | PK_TIME`.
 *
 * Wintab packs packets in the order of the bit indices of `lcPktData` (low to
 * high). For our mask that's: time(DWORD), buttons(DWORD), x(LONG), y(LONG),
 * pressure(UINT). Each field is 4 bytes on 32- and 64-bit Windows. Size = 20.
 */
internal const val WINTAB_PACKET_SIZE: Int = 20

internal data class WinTabPacket(
    val time: Int,
    val buttons: Int,
    val x: Int,
    val y: Int,
    val pressure: Int,
)

internal fun readPacket(
    buffer: Pointer,
    offset: Long,
): WinTabPacket =
    WinTabPacket(
        time = buffer.getInt(offset + 0),
        buttons = buffer.getInt(offset + 4),
        x = buffer.getInt(offset + 8),
        y = buffer.getInt(offset + 12),
        pressure = buffer.getInt(offset + 16),
    )

/**
 * Wintab `AXIS` structure — used to read pressure range from `WTI_DEVICES /
 * DVC_NPRESSURE`. We only need `axMax`.
 */
@Suppress("PropertyName", "VariableNaming")
internal open class AXIS : Structure() {
    @JvmField var axMin: Int = 0

    @JvmField var axMax: Int = 0

    @JvmField var axUnits: UINT = UINT(0)

    @JvmField var axResolution: LONG = LONG(0)

    override fun getFieldOrder(): List<String> = listOf("axMin", "axMax", "axUnits", "axResolution")
}
