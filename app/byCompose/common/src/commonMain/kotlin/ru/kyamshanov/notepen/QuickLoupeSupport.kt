package ru.kyamshanov.notepen

/**
 * `true` на платформах, где имеет смысл показывать плавающую кнопку «быстрая лупа» —
 * touch-устройства без клавиатуры, на которых hotkey `shortcutsSettings.loupeOpen`
 * недоступен. На десктопе всегда `false`: там drag-to-select запускается клавишным
 * биндингом, отдельная FAB только захламляла бы UI.
 */
expect val SupportsQuickLoupe: Boolean
