---
genre: reference
title: "ADR-001 — PDF rendering strategy per platform"
topic: architecture-decision
confidence: high
source: ai
updated: 2026-05-15T00:00:00Z
---

# ADR-001 — PDF rendering strategy per platform

**Status:** ACCEPTED
**Date:** 2026-05-15
**Scope:** M1 PDF infrastructure — `:shared` domain ports + platform implementations in `:app:byCompose:common`

---

## Context

NotePen needs to display PDF pages on Android and Desktop (JVM). The domain model lives in `:shared` (pure KMP, no platform deps). The UI lives in `:app:byCompose:common` (Compose Multiplatform). Both targets need to:

1. Open a PDF by file-system path and read its metadata (page count, dimensions).
2. Render individual pages to pixel buffers at a given resolution (for zoom support).

The key constraints are:

- `:shared` must not depend on Compose, Android SDK, or platform I/O — only `kotlin-stdlib` + `kotlinx-coroutines-core`.
- The rendering libraries on Android and JVM are different and cannot be unified without a heavyweight third-party dependency.
- Rendering is not thread-safe on either platform and must be externally synchronized.
- The code must be testable: dispatchers are injected, not hardcoded.

---

## Decision

### 1. Domain ports in `:shared`

Two interfaces are declared in `shared/src/commonMain/.../pdf/domain/port/`:

```
PdfDocumentLoader  — suspend fun load(path: String): PdfDocument
PdfPageRenderer    — suspend fun renderPage(document, pageIndex, widthPx, heightPx): PdfPageData
```

`PdfDocument` is a `Closeable`-style interface carrying `PdfDocumentInfo` (page count + `List<PdfPageInfo>`).

`PdfPageData(widthPx, heightPx, pixels: IntArray)` uses raw `IntArray` (ARGB) rather than `ImageBitmap` so `:shared` remains Compose-free.

### 2. Pixel conversion as expect/actual in `:app:byCompose:common`

`PdfPageData.toImageBitmap(): ImageBitmap` is declared as `expect` in `commonMain` and has two `actual` implementations:

- **jvmMain** — `BufferedImage.setRGB()` → `toComposeImageBitmap()` (Skia bridge via `org.jetbrains.skia`)
- **androidMain** — `Bitmap.createBitmap()` + `setPixels()` → `asImageBitmap()`

### 3. Platform-specific PDF libraries

| Platform | Library | Rationale |
|----------|---------|-----------|
| Android | `android.graphics.pdf.PdfRenderer` (API 21+) | Built-in — no extra deps; `PdfRenderer.Page.render()` returns ARGB Bitmap |
| Desktop/JVM | Apache PDFBox 3.0.5 | Mature open-source; `PDFRenderer.renderImageWithDPI()` returns `BufferedImage`; already in project |

iOS and Web are deferred to M1b; they will add their own `actual` implementations of the same ports.

### 4. Thread safety

Both `PdfRenderer` (Android) and `PDFRenderer` (PDFBox) are documented as not thread-safe. All page-render calls are serialized via `synchronized(document.renderer)` inside the infrastructure class. The `ioDispatcher` is injected via constructor so tests can substitute `UnconfinedTestDispatcher`.

### 5. Loading in `DetailsContent`

The old synchronous `PdfManager(path)` constructor that ran on the composition thread is replaced by `LaunchedEffect(filePath) { pdfDocument = loader.load(filePath) }`. `DisposableEffect` calls `pdfDocument?.close()` on disposal.

---

## Alternatives considered

### A — Single multiplatform library (e.g. PdfiumAndroid / PdfBox everywhere)

Rejected: PdfiumAndroid does not have a JVM/Desktop variant. Distributing PDFBox on Android adds ~10 MB and conflicts with Android's own PDF renderer. No mature single-library covers both targets at acceptable cost.

### B — Return `ImageBitmap` directly from the port

Rejected: `ImageBitmap` is a Compose type; adding Compose to `:shared` would break Clean Architecture (domain importing from UI framework) and would prevent future use of `:shared` from a non-Compose context (e.g. Ktor server in M5).

### C — `expect/actual` for the entire `PdfDocumentLoader`

Rejected: unnecessary; the platform variation is only in the byte-level rendering call. The domain interface + platform concrete class is simpler and avoids the `expect`/`actual` indirection for all methods.

---

## Consequences

- **Good:** `:shared` stays dependency-free; domain tests run on pure JVM without Android SDK.
- **Good:** Dispatchers are injectable → rendering infrastructure is unit-testable.
- **Good:** `PdfPageData.toImageBitmap()` expect/actual boundary is narrow (one function).
- **Risk:** `synchronized` limits concurrency on multi-page pre-fetch. Acceptable for M1; M3 will evaluate coroutine-mutex or single-threaded renderer executor.
- **Risk:** `PdfRenderer` (Android) requires API 21+. Project minSdk is 24, so this is a non-issue.
