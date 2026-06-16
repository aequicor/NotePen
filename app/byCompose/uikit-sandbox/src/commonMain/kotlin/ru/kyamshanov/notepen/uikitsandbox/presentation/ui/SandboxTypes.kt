package ru.kyamshanov.notepen.uikitsandbox.presentation.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Поддерживаемые локали standalone sandbox.
 */
internal enum class SandboxLocale {
    /** Русская локаль интерфейса. */
    Ru,

    /** Английская локаль интерфейса. */
    En,
}

/**
 * Ориентация preview-рамки и, на Android, Activity.
 */
internal enum class SandboxPreviewOrientation {
    /** Портретная ориентация preview и Android Activity. */
    Portrait,

    /** Landscape-ориентация preview и Android Activity. */
    Landscape,
}

/**
 * Верхнеуровневые вкладки каталога UIKit sandbox.
 *
 * @property icon иконка вкладки в control bar.
 */
internal enum class SandboxTab(
    val icon: ImageVector,
) {
    /** Базовые UIKit-компоненты и инструменты. */
    Components(Icons.Default.Widgets),

    /** Фрагменты экранов основного приложения. */
    Project(Icons.Default.Folder),

    /** Диалоги, sheet, snackbar и уведомления. */
    Overlays(Icons.Default.Notifications),

    /** Переходы и пример component boundary. */
    Flow(Icons.Default.PlayArrow),
}

/**
 * Dialog/sheet-сценарии, доступные в overlay-вкладке.
 */
internal enum class SandboxDialog {
    /** Диалог создания папки. */
    CreateFolder,

    /** Диалог удаления папки. */
    DeleteFolder,

    /** Диалог объединения SAF-записей. */
    SafMerge,

    /** Диалог настроек шорткатов. */
    Shortcuts,

    /** Bottom sheet настройки фона документа. */
    BackgroundSheet,

    /** Liquid glass alert dialog. */
    Alert,
}

/**
 * Демо-маршруты для preview переходов.
 */
internal enum class SandboxRoute {
    /** Раздел библиотеки документов. */
    Library,

    /** Раздел редактора документа. */
    Editor,

    /** Раздел настроек приложения. */
    Settings,
}

/**
 * Набор локализованных строк standalone sandbox.
 *
 * @property appTitle заголовок приложения.
 * @property previewTitle заголовок preview-области.
 * @property light подпись светлой темы.
 * @property dark подпись тёмной темы.
 * @property foundationTitle заголовок секции базовых UIKit-компонентов.
 * @property toolsTitle заголовок секции инструментов.
 * @property projectTitle заголовок секции фрагментов проекта.
 * @property overlaysTitle заголовок секции overlay-сценариев.
 * @property flowTitle заголовок секции переходов.
 * @property primaryAction текст основного действия.
 * @property secondaryAction текст вторичного действия.
 * @property tooltipText текст tooltip.
 * @property glassButton подпись glass-кнопки.
 * @property statusReady локализованный статус готовности.
 * @property menu подпись меню.
 * @property menuOpen пункт открытия.
 * @property menuDelete пункт удаления.
 * @property pen название инструмента пера.
 * @property marker название инструмента маркера.
 * @property eraser название инструмента ластика.
 * @property createFolder подпись действия создания папки.
 * @property deleteFolder подпись действия удаления папки.
 * @property safMerge подпись SAF merge сценария.
 * @property shortcuts подпись настроек шорткатов.
 * @property backgroundSheet подпись sheet-а фона.
 * @property alert подпись alert dialog.
 * @property notificationTitle заголовок notification preview.
 * @property notificationDescription описание notification preview.
 * @property notificationAction content description действия уведомления.
 * @property showSnackbar текст кнопки snackbar.
 * @property routeLibrary название route библиотеки.
 * @property routeEditor название route редактора.
 * @property routeSettings название route настроек.
 * @property routeDescription описание preview перехода.
 * @property architectureSample заголовок embedded component boundary.
 * @property newFolderName пример имени новой папки.
 * @property folderName пример имени папки.
 * @property fileName пример имени файла.
 * @property peerName пример имени локального пира.
 * @property remoteFileName пример имени удалённого файла.
 * @property remoteFolderName пример имени удалённой папки.
 * @property alertTitle заголовок alert dialog.
 * @property alertText текст alert dialog.
 * @property confirm подпись подтверждения.
 * @property cancel подпись отмены.
 * @property primaryActionMessage сообщение основного действия.
 * @property secondaryActionMessage сообщение вторичного действия.
 * @property tooltipActionMessage сообщение tooltip-кнопки.
 * @property backMessage сообщение back-кнопки.
 * @property glassMessage сообщение glass-кнопки.
 * @property chipMessage сообщение выбора chip.
 * @property menuOpenMessage сообщение пункта открытия.
 * @property menuDeleteMessage сообщение пункта удаления.
 * @property openDocumentMessage сообщение открытия документа.
 * @property openFolderMessage сообщение открытия папки.
 * @property movedMessage сообщение перемещения в папку.
 * @property deletedMessage сообщение удаления.
 * @property peerMessage сообщение открытия пира.
 * @property remoteMessage сообщение открытия удалённого документа.
 * @property dropMessage сообщение обработки drop.
 * @property createdMessage сообщение создания папки.
 * @property mergedMessage сообщение SAF merge.
 * @property alertConfirmed сообщение подтверждения alert.
 * @property notificationMessage сообщение snackbar из notification preview.
 */
internal data class SandboxStrings(
    val appTitle: String,
    val previewTitle: String,
    val light: String,
    val dark: String,
    val foundationTitle: String,
    val toolsTitle: String,
    val projectTitle: String,
    val overlaysTitle: String,
    val flowTitle: String,
    val primaryAction: String,
    val secondaryAction: String,
    val tooltipText: String,
    val glassButton: String,
    val statusReady: String,
    val menu: String,
    val menuOpen: String,
    val menuDelete: String,
    val pen: String,
    val marker: String,
    val eraser: String,
    val createFolder: String,
    val deleteFolder: String,
    val safMerge: String,
    val shortcuts: String,
    val backgroundSheet: String,
    val alert: String,
    val notificationTitle: String,
    val notificationDescription: String,
    val notificationAction: String,
    val showSnackbar: String,
    val routeLibrary: String,
    val routeEditor: String,
    val routeSettings: String,
    val routeDescription: String,
    val architectureSample: String,
    val newFolderName: String,
    val folderName: String,
    val fileName: String,
    val peerName: String,
    val remoteFileName: String,
    val remoteFolderName: String,
    val alertTitle: String,
    val alertText: String,
    val confirm: String,
    val cancel: String,
    val primaryActionMessage: String,
    val secondaryActionMessage: String,
    val tooltipActionMessage: String,
    val backMessage: String,
    val glassMessage: String,
    val chipMessage: String,
    val menuOpenMessage: String,
    val menuDeleteMessage: String,
    val openDocumentMessage: String,
    val openFolderMessage: String,
    val movedMessage: String,
    val deletedMessage: String,
    val peerMessage: String,
    val remoteMessage: String,
    val dropMessage: String,
    val createdMessage: String,
    val mergedMessage: String,
    val alertConfirmed: String,
    val notificationMessage: String,
) {
    /**
     * Возвращает короткую подпись локали.
     *
     * @param locale локаль control bar.
     * @return строка `RU` или `EN`.
     */
    fun localeLabel(locale: SandboxLocale): String =
        when (locale) {
            SandboxLocale.Ru -> "RU"
            SandboxLocale.En -> "EN"
        }

    /**
     * Возвращает подпись ориентации для текущей локали.
     *
     * @param orientation ориентация preview.
     * @return локализованная подпись ориентации.
     */
    fun orientationLabel(orientation: SandboxPreviewOrientation): String =
        when (orientation) {
            SandboxPreviewOrientation.Portrait -> if (this == ruStrings) "Портрет" else "Portrait"
            SandboxPreviewOrientation.Landscape -> if (this == ruStrings) "Альбом" else "Landscape"
        }

    /**
     * Возвращает подпись вкладки для текущей локали.
     *
     * @param tab вкладка каталога.
     * @return локализованная подпись вкладки.
     */
    fun tabLabel(tab: SandboxTab): String =
        when (tab) {
            SandboxTab.Components -> if (this == ruStrings) "Компоненты" else "Components"
            SandboxTab.Project -> if (this == ruStrings) "Фрагменты" else "Fragments"
            SandboxTab.Overlays -> if (this == ruStrings) "Overlay" else "Overlays"
            SandboxTab.Flow -> if (this == ruStrings) "Переходы" else "Flow"
        }

    /**
     * Возвращает подпись preview-route.
     *
     * @param route route, выбранный в секции переходов.
     * @return локализованная подпись route.
     */
    fun routeLabel(route: SandboxRoute): String =
        when (route) {
            SandboxRoute.Library -> routeLibrary
            SandboxRoute.Editor -> routeEditor
            SandboxRoute.Settings -> routeSettings
        }

    /**
     * Собирает подзаголовок preview-области.
     *
     * @param tab выбранная вкладка каталога.
     * @param orientation выбранная ориентация preview.
     * @return локализованная строка с вкладкой, ориентацией и темой.
     */
    fun previewSubtitle(
        tab: SandboxTab,
        orientation: SandboxPreviewOrientation,
    ): String =
        if (this == ruStrings) {
            "${tabLabel(tab)} · ${orientationLabel(orientation)} · светлая/тёмная тема"
        } else {
            "${tabLabel(tab)} · ${orientationLabel(orientation)} · light/dark theme"
        }

    /**
     * Собирает сообщение о выбранном sandbox-виджете.
     *
     * @param id публичный идентификатор выбранного виджета.
     * @return локализованное snackbar-сообщение.
     */
    fun widgetSelected(id: String): String = if (this == ruStrings) "Выбран sandbox-виджет: $id" else "Sandbox widget selected: $id"

    /**
     * Фабрика строк для выбранной локали.
     */
    companion object {
        /**
         * Возвращает набор строк по локали.
         *
         * @param locale выбранная локаль.
         * @return [SandboxStrings] для этой локали.
         */
        fun forLocale(locale: SandboxLocale): SandboxStrings =
            when (locale) {
                SandboxLocale.Ru -> ruStrings
                SandboxLocale.En -> enStrings
            }
    }
}

/**
 * Русская локализация standalone sandbox.
 */
internal val ruStrings =
    SandboxStrings(
        appTitle = "NotePen UIKit Sandbox",
        previewTitle = "Каталог интерфейса",
        light = "Светлая",
        dark = "Тёмная",
        foundationTitle = "UIKit и базовые поверхности",
        toolsTitle = "Инструменты, wheel, пресеты",
        projectTitle = "Фрагменты главного экрана",
        overlaysTitle = "Диалоги, sheet, snackbar, уведомления",
        flowTitle = "Переходы и component boundary",
        primaryAction = "Выполнить",
        secondaryAction = "Назад",
        tooltipText = "Настройки демо",
        glassButton = "Стеклянная кнопка",
        statusReady = "Готово",
        menu = "Меню",
        menuOpen = "Открыть",
        menuDelete = "Удалить",
        pen = "Перо",
        marker = "Маркер",
        eraser = "Ластик",
        createFolder = "Новая папка",
        deleteFolder = "Удаление",
        safMerge = "SAF merge",
        shortcuts = "Шорткаты",
        backgroundSheet = "Фон",
        alert = "Alert",
        notificationTitle = "Синхронизация",
        notificationDescription = "Демонстрация уведомления о локальном статусе и прогрессе операции.",
        notificationAction = "Показать уведомление",
        showSnackbar = "Показать snackbar",
        routeLibrary = "Библиотека",
        routeEditor = "Редактор",
        routeSettings = "Настройки",
        routeDescription = "AnimatedContent имитирует переход между разделами приложения.",
        architectureSample = "Component boundary sandbox",
        newFolderName = "Учебные материалы",
        folderName = "Лекции",
        fileName = "Конспект.pdf",
        peerName = "Планшет аудитории",
        remoteFileName = "Расшаренный учебник.pdf",
        remoteFolderName = "Общий каталог",
        alertTitle = "Демо-подтверждение",
        alertText = "LiquidGlassAlertDialog поверх общей glass-сцены.",
        confirm = "Готово",
        cancel = "Отмена",
        primaryActionMessage = "Основное действие выполнено",
        secondaryActionMessage = "Возврат обработан",
        tooltipActionMessage = "Tooltip-кнопка нажата",
        backMessage = "Back button",
        glassMessage = "Glass icon button",
        chipMessage = "Статус выбран",
        menuOpenMessage = "Пункт открытия",
        menuDeleteMessage = "Пункт удаления",
        openDocumentMessage = "Открытие документа",
        openFolderMessage = "Открытие папки",
        movedMessage = "Перемещено в папку",
        deletedMessage = "Удалено",
        peerMessage = "Открыт каталог пира",
        remoteMessage = "Открыт удалённый документ",
        dropMessage = "Drop обработан",
        createdMessage = "Папка создана",
        mergedMessage = "SAF-записи объединены",
        alertConfirmed = "Подтверждено",
        notificationMessage = "Snackbar из sandbox-каталога",
    )

/**
 * Английская локализация standalone sandbox.
 */
internal val enStrings =
    SandboxStrings(
        appTitle = "NotePen UIKit Sandbox",
        previewTitle = "Interface catalog",
        light = "Light",
        dark = "Dark",
        foundationTitle = "UIKit and base surfaces",
        toolsTitle = "Tools, wheel, presets",
        projectTitle = "Main screen fragments",
        overlaysTitle = "Dialogs, sheet, snackbar, notifications",
        flowTitle = "Transitions and component boundary",
        primaryAction = "Run",
        secondaryAction = "Back",
        tooltipText = "Demo settings",
        glassButton = "Glass button",
        statusReady = "Ready",
        menu = "Menu",
        menuOpen = "Open",
        menuDelete = "Delete",
        pen = "Pen",
        marker = "Marker",
        eraser = "Eraser",
        createFolder = "New folder",
        deleteFolder = "Delete",
        safMerge = "SAF merge",
        shortcuts = "Shortcuts",
        backgroundSheet = "Background",
        alert = "Alert",
        notificationTitle = "Sync",
        notificationDescription = "A local operation status notification with progress.",
        notificationAction = "Show notification",
        showSnackbar = "Show snackbar",
        routeLibrary = "Library",
        routeEditor = "Editor",
        routeSettings = "Settings",
        routeDescription = "AnimatedContent previews application navigation transitions.",
        architectureSample = "Sandbox component boundary",
        newFolderName = "Study materials",
        folderName = "Lectures",
        fileName = "Notebook.pdf",
        peerName = "Classroom tablet",
        remoteFileName = "Shared textbook.pdf",
        remoteFolderName = "Shared catalog",
        alertTitle = "Demo confirmation",
        alertText = "LiquidGlassAlertDialog over the shared glass scene.",
        confirm = "Done",
        cancel = "Cancel",
        primaryActionMessage = "Primary action completed",
        secondaryActionMessage = "Back action handled",
        tooltipActionMessage = "Tooltip button clicked",
        backMessage = "Back button",
        glassMessage = "Glass icon button",
        chipMessage = "Status selected",
        menuOpenMessage = "Open menu item",
        menuDeleteMessage = "Delete menu item",
        openDocumentMessage = "Opening document",
        openFolderMessage = "Opening folder",
        movedMessage = "Moved to folder",
        deletedMessage = "Deleted",
        peerMessage = "Peer catalog opened",
        remoteMessage = "Remote document opened",
        dropMessage = "Drop handled",
        createdMessage = "Folder created",
        mergedMessage = "SAF records merged",
        alertConfirmed = "Confirmed",
        notificationMessage = "Snackbar from sandbox catalog",
    )

/**
 * Прозрачность панели управления sandbox-приложения.
 */
internal const val CONTROL_BAR_ALPHA = 0.76f

/**
 * Прозрачность карточек preview-секций.
 */
internal const val SURFACE_PANEL_ALPHA = 0.64f

/**
 * Соотношение сторон портретной preview-рамки.
 */
internal const val PORTRAIT_ASPECT = 0.62f

/**
 * Соотношение сторон landscape preview-рамки.
 */
internal const val LANDSCAPE_ASPECT = 1.62f
