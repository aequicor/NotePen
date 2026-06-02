package ru.kyamshanov.notepen.mainscreen.platform

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.COM.COMException
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid.CLSID
import com.sun.jna.platform.win32.Guid.IID
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.W32Errors
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.EventQueue
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual class FilePicker {
    actual suspend fun pickDocument(): String? =
        withContext(Dispatchers.IO) {
            if (isWindows()) {
                runCatching { WindowsFileOpenDialog.pickDocument() }
                    .getOrElse { pickDocumentWithAwtDialog() }
            } else {
                pickDocumentWithAwtDialog()
            }
        }
}

private fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)

private fun pickDocumentWithAwtDialog(): String? {
    var resultDir: String? = null
    var resultFile: String? = null
    EventQueue.invokeAndWait {
        val dialog = FileDialog(null as Frame?, "Open document", FileDialog.LOAD)
        dialog.isVisible = true
        resultDir = dialog.directory
        resultFile = dialog.file
    }
    val dir = resultDir ?: return null
    val file = resultFile ?: return null
    return File(dir, file).canonicalPath
}

private object WindowsFileOpenDialog {
    private const val CLSCTX_INPROC_SERVER = 0x1
    private const val COINIT_APARTMENTTHREADED = 0x2
    private const val COINIT_DISABLE_OLE1DDE = 0x4
    private const val ERROR_CANCELLED_HRESULT = -2147023673
    private const val SIGDN_FILESYSPATH = -2147123200

    private val fileOpenDialogClassId = CLSID("{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}")
    private val fileOpenDialogInterfaceId = IID("{D57C7288-D4AD-4768-BE02-9D969532D960}")

    fun pickDocument(): String? {
        val initResult =
            Ole32.INSTANCE.CoInitializeEx(
                Pointer.NULL,
                COINIT_APARTMENTTHREADED or COINIT_DISABLE_OLE1DDE,
            )
        val shouldUninitialize = W32Errors.SUCCEEDED(initResult)

        return try {
            val dialogRef = PointerByReference()
            checkResult(
                Ole32.INSTANCE.CoCreateInstance(
                    fileOpenDialogClassId,
                    Pointer.NULL,
                    CLSCTX_INPROC_SERVER,
                    fileOpenDialogInterfaceId,
                    dialogRef,
                ),
            )

            val dialog = FileOpenDialog(dialogRef.value)
            try {
                dialog.setFileTypes(documentFilters())
                dialog.setDefaultExtension("pdf")

                val showResult = dialog.show(Pointer.NULL)
                if (showResult.toInt() == ERROR_CANCELLED_HRESULT) {
                    return null
                }
                checkResult(showResult)

                val item = dialog.getResult()
                try {
                    item.fileSystemPath()
                } finally {
                    item.Release()
                }
            } finally {
                dialog.Release()
            }
        } finally {
            if (shouldUninitialize) {
                Ole32.INSTANCE.CoUninitialize()
            }
        }
    }

    private fun documentFilters(): Array<FilterSpec> =
        arrayOf(
            FilterSpec("Supported documents", "*.pdf;*.png;*.jpg;*.jpeg"),
            FilterSpec("PDF documents", "*.pdf"),
            FilterSpec("Images", "*.png;*.jpg;*.jpeg"),
            FilterSpec("All files", "*.*"),
        )

    private fun checkResult(result: WinNT.HRESULT) {
        if (W32Errors.FAILED(result)) {
            throw COMException("Windows file dialog failed", result)
        }
    }

    private class FileOpenDialog(
        pointer: Pointer,
    ) : Unknown(pointer) {
        fun show(owner: Pointer): WinNT.HRESULT =
            _invokeNativeObject(3, arrayOf(pointer, owner), WinNT.HRESULT::class.java) as WinNT.HRESULT

        fun setFileTypes(filters: Array<FilterSpec>) {
            val first = NativeFilterSpec()
            val nativeFilters = first.toArray(filters.size).map { it as NativeFilterSpec }
            filters.zip(nativeFilters).forEach { (filter, nativeFilter) ->
                nativeFilter.set(filter)
                nativeFilter.write()
            }
            checkResult(
                _invokeNativeObject(
                    4,
                    arrayOf(pointer, filters.size, first.pointer),
                    WinNT.HRESULT::class.java,
                ) as WinNT.HRESULT,
            )
        }

        fun setDefaultExtension(extension: String) {
            val value = wideString(extension)
            checkResult(
                _invokeNativeObject(
                    22,
                    arrayOf(pointer, value),
                    WinNT.HRESULT::class.java,
                ) as WinNT.HRESULT,
            )
        }

        fun getResult(): ShellItem {
            val itemRef = PointerByReference()
            checkResult(
                _invokeNativeObject(
                    20,
                    arrayOf(pointer, itemRef),
                    WinNT.HRESULT::class.java,
                ) as WinNT.HRESULT,
            )
            return ShellItem(itemRef.value)
        }
    }

    private class ShellItem(
        pointer: Pointer,
    ) : Unknown(pointer) {
        fun fileSystemPath(): String {
            val pathRef = PointerByReference()
            checkResult(
                _invokeNativeObject(
                    5,
                    arrayOf(pointer, SIGDN_FILESYSPATH, pathRef),
                    WinNT.HRESULT::class.java,
                ) as WinNT.HRESULT,
            )
            val pathPointer = pathRef.value
            return try {
                pathPointer.getWideString(0)
            } finally {
                Ole32.INSTANCE.CoTaskMemFree(pathPointer)
            }
        }
    }

    private data class FilterSpec(
        val name: String,
        val pattern: String,
    )

    @Suppress("MemberVisibilityCanBePrivate")
    private class NativeFilterSpec : Structure() {
        @JvmField
        var pszName: Pointer? = null

        @JvmField
        var pszSpec: Pointer? = null

        private var nameMemory: Memory? = null
        private var specMemory: Memory? = null

        fun set(filter: FilterSpec) {
            nameMemory = wideString(filter.name)
            specMemory = wideString(filter.pattern)
            pszName = nameMemory
            pszSpec = specMemory
        }

        override fun getFieldOrder(): List<String> = listOf("pszName", "pszSpec")
    }

    private fun wideString(value: String): Memory =
        Memory(((value.length + 1) * Native.WCHAR_SIZE).toLong()).apply {
            setWideString(0, value)
        }
}
