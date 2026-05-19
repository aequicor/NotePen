package ru.kyamshanov.notepen.magnifier

/**
 * `true` на touch-платформах, где имеет смысл pinch/two-finger pan
 * внутри content-области панели лупы для изменения масштаба и позиции
 * увеличенной области (`targetOnPage`). На desktop'е те же операции
 * доступны через drag/resize рамки на странице мышью.
 */
expect val SupportsPanelGestureZoom: Boolean
