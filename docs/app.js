(function () {
  "use strict";

  var STORAGE_KEY = "notepen-lang";

  var i18n = {
    ru: {
      pageTitle: "NotePen: заметки и рисунки поверх PDF",
      metaDescription: "NotePen, приложение для заметок и рисунков поверх PDF и документов на бесконечном холсте. Работает на Android и на десктопе (Windows, macOS, Linux).",

      navFeatures: "Возможности",
      navUsage: "Как пользоваться",
      navSync: "Синхронизация",
      navPlatforms: "Платформы",

      heroTitle: "Пишите и рисуйте поверх PDF",
      heroSub: "NotePen открывает PDF и документы и даёт писать прямо по странице пером. Вокруг страницы есть бесконечный холст, так что заметки и схемы не упираются в поля. Работает на Android и на компьютере.",
      heroCta: "Открыть на GitHub",
      heroCta2: "Как это работает",
      altAnnotate: "Перо и маркер поверх страницы PDF: обведённое слово, подчёркивание и жёлтая подсветка.",

      featuresTitle: "Что умеет NotePen",
      featuresLead: "Набор инструментов для чтения и разметки документов пером.",

      f1Title: "Бесконечный холст",
      f1Desc: "Размечайте PDF и документы на холсте, который продолжается за краями страницы. Заметки, наброски и схемы не ограничены полями документа.",
      f2Title: "Перо поверх PDF",
      f2Desc: "Откройте любой PDF и пишите по нему, как по бумаге. Поддерживается нажим пера для стилуса.",
      f3Title: "Маркер",
      f3Desc: "Отдельный инструмент-маркер, чтобы подсвечивать нужные места в документе.",
      f4Title: "Ластик",
      f4Desc: "Стирайте линии. Стирание тоже передаётся между устройствами при синхронизации.",
      f5Title: "Распознавание фигур",
      f5Desc: "Нарисуйте линию или фигуру от руки, затем задержите перо. NotePen выпрямит штрих в ровную линию или превратит набросок в выделение.",
      f6Title: "Ссылки от руки",
      f6Desc: "Обведите текст или нарисуйте символ, чтобы сделать ссылку на другую страницу документа или на внешний адрес.",
      f7Title: "Режим чтения с перевёрсткой",
      f7Desc: "Текст документа извлекается и перекомпоновывается в удобный для чтения вид. Есть готовые пресеты под комфорт чтения и свои настройки, а заметки остаются привязаны к тексту.",
      f8Title: "Просмотр по страницам",
      f8Desc: "Листайте документ по одной странице или разворотом из двух. Есть лупа для точного попадания пером.",
      f9Title: "Автосохранение",
      f9Desc: "Изменения сохраняются сами. Закройте документ и вернитесь позже, заметки будут на месте.",
      f10Title: "Синхронизация по локальной сети",
      f10Desc: "Заметки синхронизируются между устройствами по локальной сети через Ktor WebSocket и mDNS. Слияние по правилу «последний выигрывает» для каждого документа. Без облака.",
      f11Title: "Связь по QR-коду",
      f11Desc: "Соедините устройства, показав и отсканировав QR-код NotePen. Генерация через zxing, сканирование на Android через ML Kit и CameraX.",
      f12Title: "EPUB и FB2",
      f12Desc: "Встроенная конвертация превращает книги EPUB и FB2 в постраничные документы, по которым можно писать как по PDF.",

      usageTitle: "Как пользоваться",
      usageLead: "Несколько шагов от открытия документа до синхронизации заметок.",
      altLibrary: "Главный экран: недавние файлы и папки.",
      altReading: "Режим чтения с перевёрсткой и пресетами комфорта чтения внизу.",
      altSync: "Диалог связи устройств: QR-код и список подключённых клиентов.",

      u1Title: "Откройте документ",
      u1Desc: "Откройте PDF или импортируйте книгу EPUB или FB2. Книга превращается в постраничный документ. На главном экране лежат недавние файлы и папки.",
      u2Title: "Пишите по странице",
      u2Desc: "Пишите и рисуйте пером с поддержкой нажима. Переключайтесь на маркер или ластик, когда нужно. Задержите перо после штриха, чтобы выпрямить его в линию или превратить в выделение.",
      u3Title: "Читайте с перевёрсткой",
      u3Desc: "Переключитесь в режим чтения и выберите пресет под комфорт чтения. В просмотрщике можно включить одну страницу или разворот из двух. Заметки остаются привязаны к тексту.",
      u4Title: "Синхронизируйте устройства",
      u4Desc: "Подключите второе устройство, отсканировав QR-код. Заметки синхронизируются по локальной сети. Изменения сохраняются автоматически.",

      syncTitle: "Синхронизация без облака",
      syncDesc: "Устройства обмениваются заметками напрямую по локальной сети. NotePen находит соседние устройства через mDNS и держит соединение по Ktor WebSocket. Для каждого документа правки сливаются по правилу «последний выигрывает», стирание тоже передаётся. Ничего не уходит в облако.",
      syncDesc2: "Связать устройства можно по QR-коду: одно показывает код, второе сканирует.",

      platformsTitle: "Платформы",
      platformsLead: "Один общий код на Kotlin Multiplatform для всех целей.",
      plat1: "Android",
      plat2: "Windows",
      plat3: "macOS",
      plat4: "Linux",
      runTitle: "Запуск из исходников",
      runDesc: "Соберите проект Gradle и запустите. На десктопе нужна JetBrains Runtime (JBR) 25.",
      runDesktop: "запуск десктопной версии",
      runAndroid: "установка debug-сборки на Android",

      footerTag: "Заметки и рисунки поверх PDF на Android и десктопе.",
      footerRepo: "Репозиторий на GitHub",
      footerLicense: "Лицензия Apache 2.0"
    },

    en: {
      pageTitle: "NotePen: write and draw over PDFs",
      metaDescription: "NotePen is an app for writing and drawing over PDFs and documents on an infinite canvas. It runs on Android and desktop (Windows, macOS, Linux).",

      navFeatures: "Features",
      navUsage: "How to use it",
      navSync: "Sync",
      navPlatforms: "Platforms",

      heroTitle: "Write and draw over PDFs",
      heroSub: "NotePen opens PDFs and documents and lets you write right on the page with a pen. There is an infinite canvas around the page, so notes and diagrams are not stuck inside the margins. It runs on Android and on the desktop.",
      heroCta: "View on GitHub",
      heroCta2: "See how it works",
      altAnnotate: "Pen and highlighter over a PDF page: a circled word, an underline, and yellow highlights.",

      featuresTitle: "What NotePen does",
      featuresLead: "A set of tools for reading and marking up documents with a pen.",

      f1Title: "Infinite canvas",
      f1Desc: "Mark up PDFs and documents on a canvas that keeps going past the edge of the page. Notes, sketches, and diagrams are not limited by the document margins.",
      f2Title: "Pen over PDF",
      f2Desc: "Open any PDF and write over it like paper. Pen pressure is supported for stylus input.",
      f3Title: "Highlighter",
      f3Desc: "A separate marker tool for highlighting the parts of a document you care about.",
      f4Title: "Eraser",
      f4Desc: "Erase strokes. Erasing is also passed between devices during sync.",
      f5Title: "Shape recognition",
      f5Desc: "Draw a line or shape freehand, then hold the pen. NotePen straightens the stroke into a clean line or turns the sketch into a selection.",
      f6Title: "Handwritten links",
      f6Desc: "Circle text or draw a symbol to make a link to another page of the document or to an external address.",
      f7Title: "Reflow reading mode",
      f7Desc: "Document text is pulled out and re-laid into a comfortable reading view. There are built-in reading-comfort presets plus your own, and notes stay anchored to the text.",
      f8Title: "Page-by-page viewer",
      f8Desc: "Page through a document one page at a time or as a two-page spread. A magnifier helps with precise pen placement.",
      f9Title: "Autosave",
      f9Desc: "Changes save on their own. Close a document and come back later with your notes intact.",
      f10Title: "Sync over the local network",
      f10Desc: "Notes sync between devices over the local network using Ktor WebSocket and mDNS. Each document merges last-writer-wins. No cloud.",
      f11Title: "QR-code pairing",
      f11Desc: "Connect devices by showing and scanning a NotePen QR code. Generated with zxing, scanned on Android with ML Kit and CameraX.",
      f12Title: "EPUB and FB2",
      f12Desc: "Built-in conversion turns EPUB and FB2 books into paginated documents you can write over like a PDF.",

      usageTitle: "How to use it",
      usageLead: "A few steps from opening a document to syncing your notes.",
      altLibrary: "Home screen: recent files and folders.",
      altReading: "Reflow reading mode with reading-comfort presets along the bottom.",
      altSync: "Device pairing dialog: a QR code and a list of connected clients.",

      u1Title: "Open a document",
      u1Desc: "Open a PDF or import an EPUB or FB2 book. The book becomes a paginated document. The home screen holds your recent files and folders.",
      u2Title: "Write on the page",
      u2Desc: "Write and draw with a pressure-sensitive pen. Switch to the marker or eraser when you need to. Hold the pen after a stroke to straighten it into a line or turn it into a selection.",
      u3Title: "Read with reflow",
      u3Desc: "Switch to reading mode and pick a reading-comfort preset. In the viewer you can show a single page or a two-page spread. Notes stay anchored to the text.",
      u4Title: "Sync your devices",
      u4Desc: "Connect a second device by scanning a QR code. Notes sync over the local network. Changes save automatically.",

      syncTitle: "Sync without the cloud",
      syncDesc: "Devices share notes directly over the local network. NotePen finds nearby devices through mDNS and keeps a Ktor WebSocket connection open. For each document, edits merge last-writer-wins, and erasing is passed along too. Nothing goes to the cloud.",
      syncDesc2: "You pair devices with a QR code: one device shows the code, the other scans it.",

      platformsTitle: "Platforms",
      platformsLead: "One shared Kotlin Multiplatform codebase for every target.",
      plat1: "Android",
      plat2: "Windows",
      plat3: "macOS",
      plat4: "Linux",
      runTitle: "Run from source",
      runDesc: "Build the Gradle project and run it. The desktop build needs JetBrains Runtime (JBR) 25.",
      runDesktop: "run the desktop version",
      runAndroid: "install the debug build on Android",

      footerTag: "Write and draw over PDFs on Android and desktop.",
      footerRepo: "Repository on GitHub",
      footerLicense: "Apache 2.0 license"
    }
  };

  function applyLang(lang) {
    var dict = i18n[lang] || i18n.ru;

    document.documentElement.setAttribute("lang", lang);

    var nodes = document.querySelectorAll("[data-i18n]");
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      var key = el.getAttribute("data-i18n");
      if (!(key in dict)) { continue; }
      var attr = el.getAttribute("data-i18n-attr");
      if (attr) {
        el.setAttribute(attr, dict[key]);
      } else {
        el.textContent = dict[key];
      }
    }

    if (dict.pageTitle) { document.title = dict.pageTitle; }

    var buttons = document.querySelectorAll(".lang-btn");
    for (var j = 0; j < buttons.length; j++) {
      var b = buttons[j];
      b.classList.toggle("active", b.getAttribute("data-lang") === lang);
      b.setAttribute("aria-pressed", b.getAttribute("data-lang") === lang ? "true" : "false");
    }
  }

  function getInitialLang() {
    var saved;
    try { saved = localStorage.getItem(STORAGE_KEY); } catch (e) { saved = null; }
    if (saved === "ru" || saved === "en") { return saved; }
    return "ru";
  }

  function setLang(lang) {
    try { localStorage.setItem(STORAGE_KEY, lang); } catch (e) { /* ignore */ }
    applyLang(lang);
  }

  document.addEventListener("DOMContentLoaded", function () {
    applyLang(getInitialLang());

    var buttons = document.querySelectorAll(".lang-btn");
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].addEventListener("click", function () {
        setLang(this.getAttribute("data-lang"));
      });
    }
  });
})();
