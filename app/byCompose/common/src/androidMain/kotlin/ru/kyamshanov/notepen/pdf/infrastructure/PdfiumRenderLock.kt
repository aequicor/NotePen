package ru.kyamshanov.notepen.pdf.infrastructure

/**
 * Process-global lock serialising ALL [android.graphics.pdf.PdfRenderer]
 * open / render / close operations across the whole app.
 *
 * **Why a single global lock and not a per-renderer one.**
 * [android.graphics.pdf.PdfRenderer] is backed by pdfium, which keeps a
 * *process-global, non-thread-safe* font module — the shared FreeType
 * `FT_Library` plus `CFX_GlyphCache` / `CPDF_FontGlobals`. That state is
 * mutated whenever a page is rendered and, critically, when a page (and its
 * fonts) is destroyed. When two *different* `PdfRenderer` instances render or
 * close pages concurrently on parallel coroutine-dispatcher threads, they
 * corrupt that shared font state, producing a native SIGSEGV in the
 * page/font destructor (`FT_Done_Face` inside `CPDF_Page::~CPDF_Page`).
 *
 * A per-instance `synchronized(renderer)` guard only serialises one renderer
 * against itself; it does nothing to stop a *second* renderer (thumbnails,
 * loader, exporter, editor) from touching the shared font module at the same
 * time. This single monitor forces every pdfium open/render/close in the
 * process to run one at a time.
 *
 * **Usage contract.** Acquire [lock] via a plain `synchronized(...)` block
 * around each page's `openPage` → `render` → `close` (and renderer open /
 * close). Keep the critical section tight and blocking: do **not** suspend
 * (`withContext` / `await` / `suspend` calls) while holding it, and do not
 * hold it across a whole multi-page loop — acquire it per page so long
 * exports don't serialise the entire app. The monitor is reentrant on the
 * same thread, so nested acquisition within one operation is safe.
 */
internal object PdfiumRenderLock {
    /** Monitor guarding all pdfium [android.graphics.pdf.PdfRenderer] calls. */
    val lock: Any = Any()
}
