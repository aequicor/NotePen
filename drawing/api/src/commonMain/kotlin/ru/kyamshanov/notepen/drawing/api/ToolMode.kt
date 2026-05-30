package ru.kyamshanov.notepen.drawing.api

/**
 * Active tool on the details screen.
 *
 * Enum guarantees mutual exclusivity — one value at a time.
 * [NONE] means no tool is active (canvas gestures are interpreted as pan/zoom).
 * [NOTE] is a reading-mode-only tool: a text selection becomes a `PageNote`
 * instead of a freehand stroke or sticky highlight (it draws nothing on the
 * raster canvas, so the edit-mode gesture pipeline treats it like [NONE]).
 */
public enum class ToolMode { NONE, PEN, MARKER, ERASER, NOTE }
