package ru.kyamshanov.notepen.rendering.api

/**
 * Active tool on the details screen.
 *
 * Enum guarantees mutual exclusivity — one value at a time.
 * [NONE] means no tool is active (canvas gestures are interpreted as pan/zoom).
 */
public enum class ToolMode { NONE, PEN, MARKER, ERASER }
