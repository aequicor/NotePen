(function () {
  "use strict";

  var STORAGE_KEY = "notepen-lang";

  var i18n = {
    ru: {
      pageTitle: "NotePen: заметки и рисунки поверх PDF",
      metaDescription: "NotePen, приложение для заметок и рисунков поверх PDF и документов на бесконечном холсте. Работает на Android и на десктопе (Windows, macOS, Linux).",

      navDownload: "Скачать",
      navFeatures: "Возможности",
      navUsage: "Как пользоваться",
      navSync: "Синхронизация",
      navPlatforms: "Платформы",

      heroTitle: "Пишите и рисуйте поверх PDF",
      heroSub: "NotePen открывает PDF и документы и даёт писать прямо по странице пером. Вокруг страницы есть бесконечный холст, так что заметки и схемы не упираются в поля. Работает на Android и на компьютере.",
      heroCta: "Скачать",
      heroCta2: "GitHub",

      downloadTitle: "Скачать NotePen",
      downloadLead: "Готовые сборки для десктопа и Android. Установка не нужна для портативной версии Windows.",
      downloadVersionUnknown: "Последняя версия с GitHub",
      downloadVersionPrefix: "Последняя версия:",
      downloadBtn: "Скачать",
      downloadAllReleases: "Все сборки на странице релизов",
      dlWinInstallerLabel: "Windows",
      dlWinInstallerSub: "Установщик (.exe)",
      dlWinInstallerAria: "Скачать установщик NotePen для Windows",
      dlWinPortableLabel: "Windows",
      dlWinPortableSub: "Портативная, без установки (.zip)",
      dlWinPortableAria: "Скачать портативную версию NotePen для Windows",
      dlMacLabel: "macOS",
      dlMacSub: "Образ диска (.dmg)",
      dlMacAria: "Скачать NotePen для macOS",
      dlLinuxLabel: "Linux",
      dlLinuxSub: "Debian / Ubuntu (.deb)",
      dlLinuxAria: "Скачать NotePen для Linux",
      dlAndroidLabel: "Android",
      dlAndroidSub: "APK (.apk)",
      dlAndroidAria: "Скачать NotePen для Android",
      altAnnotate: "Перо и маркер поверх страницы PDF: обведённое слово, подчёркивание и жёлтая подсветка.",

      featuresTitle: "Что умеет NotePen",
      featuresLead: "Набор инструментов для чтения и разметки документов пером.",

      f1Title: "Бесконечный холст",
      f1Desc: "Размечайте PDF и документы на холсте, который продолжается за краями страницы. Заметки, наброски и схемы не ограничены полями документа.",
      f2Title: "Перо поверх PDF",
      f2Desc: "Откройте любой PDF и пишите по нему, как по бумаге. Поддерживается нажим пера для стилуса.",
      fTabletTitle: "Графический планшет и стилус",
      fTabletDesc: "Перьевой ввод с нажимом и наклоном, кнопкой на пере и ластиком-наконечником. На Windows работает через WinTab (планшеты Wacom и совместимые), на macOS — через события планшета Cocoa, на Android — встроенный стилус.",
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
      contribTitle: "Сборка из исходников",
      contribDesc: "Это для тех, кто хочет участвовать в разработке. Если вам нужно просто пользоваться приложением, берите готовую сборку выше.",
      contribLink: "Как собрать и внести вклад",

      footerTag: "Заметки и рисунки поверх PDF на Android и десктопе.",
      footerRepo: "Репозиторий на GitHub",
      footerLicense: "Лицензия Apache 2.0"
    },

    en: {
      pageTitle: "NotePen: write and draw over PDFs",
      metaDescription: "NotePen is an app for writing and drawing over PDFs and documents on an infinite canvas. It runs on Android and desktop (Windows, macOS, Linux).",

      navDownload: "Download",
      navFeatures: "Features",
      navUsage: "How to use it",
      navSync: "Sync",
      navPlatforms: "Platforms",

      heroTitle: "Write and draw over PDFs",
      heroSub: "NotePen opens PDFs and documents and lets you write right on the page with a pen. There is an infinite canvas around the page, so notes and diagrams are not stuck inside the margins. It runs on Android and on the desktop.",
      heroCta: "Download",
      heroCta2: "GitHub",

      downloadTitle: "Download NotePen",
      downloadLead: "Ready builds for desktop and Android. The Windows portable build needs no install.",
      downloadVersionUnknown: "Latest release from GitHub",
      downloadVersionPrefix: "Latest version:",
      downloadBtn: "Download",
      downloadAllReleases: "All builds on the releases page",
      dlWinInstallerLabel: "Windows",
      dlWinInstallerSub: "Installer (.exe)",
      dlWinInstallerAria: "Download the NotePen installer for Windows",
      dlWinPortableLabel: "Windows",
      dlWinPortableSub: "Portable, no install (.zip)",
      dlWinPortableAria: "Download the portable NotePen build for Windows",
      dlMacLabel: "macOS",
      dlMacSub: "Disk image (.dmg)",
      dlMacAria: "Download NotePen for macOS",
      dlLinuxLabel: "Linux",
      dlLinuxSub: "Debian / Ubuntu (.deb)",
      dlLinuxAria: "Download NotePen for Linux",
      dlAndroidLabel: "Android",
      dlAndroidSub: "APK (.apk)",
      dlAndroidAria: "Download NotePen for Android",
      altAnnotate: "Pen and highlighter over a PDF page: a circled word, an underline, and yellow highlights.",

      featuresTitle: "What NotePen does",
      featuresLead: "A set of tools for reading and marking up documents with a pen.",

      f1Title: "Infinite canvas",
      f1Desc: "Mark up PDFs and documents on a canvas that keeps going past the edge of the page. Notes, sketches, and diagrams are not limited by the document margins.",
      f2Title: "Pen over PDF",
      f2Desc: "Open any PDF and write over it like paper. Pen pressure is supported for stylus input.",
      fTabletTitle: "Graphics tablet & stylus",
      fTabletDesc: "Pen input with pressure and tilt, the barrel button, and the eraser tip. Windows works through WinTab (Wacom and compatible tablets), macOS through Cocoa tablet events, Android through the built-in stylus.",
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
      contribTitle: "Build from source",
      contribDesc: "This is for people who want to work on NotePen. If you just want to use the app, grab a ready build above.",
      contribLink: "How to build and contribute",

      footerTag: "Write and draw over PDFs on Android and desktop.",
      footerRepo: "Repository on GitHub",
      footerLicense: "Apache 2.0 license"
    }
  };

  var currentLang = "ru";

  var RELEASES_PAGE = "https://github.com/aequicor/NotePen/releases/latest";
  var RELEASE_API = "https://api.github.com/repos/aequicor/NotePen/releases/latest";
  var RELEASE_CACHE_KEY = "notepen-release";

  // Latest release version, once known (e.g. "v1.2.0"); null until/unless the fetch succeeds.
  var latestTag = null;

  // How each download button is matched against a release asset's file name.
  var DOWNLOAD_TARGETS = [
    { id: "dl-win-installer", match: function (n) { return /\.exe$/.test(n); } },
    { id: "dl-win-portable", match: function (n) { return /portable.*\.zip$/.test(n); } },
    { id: "dl-mac", match: function (n) { return /\.dmg$/.test(n); } },
    { id: "dl-linux", match: function (n) { return /\.deb$/.test(n); } },
    { id: "dl-android", match: function (n) { return /\.apk$/.test(n); } }
  ];

  function renderVersionLine(dict) {
    var line = document.getElementById("download-version");
    if (!line) { return; }
    if (latestTag) {
      line.textContent = dict.downloadVersionPrefix + " " + latestTag;
    } else if ("downloadVersionUnknown" in dict) {
      line.textContent = dict.downloadVersionUnknown;
    }
  }

  // Point each platform button at its matching asset; fall back to the releases page.
  function applyReleaseAssets(assets) {
    var list = Array.isArray(assets) ? assets : [];
    for (var i = 0; i < DOWNLOAD_TARGETS.length; i++) {
      var target = DOWNLOAD_TARGETS[i];
      var btn = document.getElementById(target.id);
      if (!btn) { continue; }
      var url = RELEASES_PAGE;
      for (var j = 0; j < list.length; j++) {
        var asset = list[j];
        var name = (asset && asset.name ? String(asset.name) : "").toLowerCase();
        if (name && asset.browser_download_url && target.match(name)) {
          url = asset.browser_download_url;
          break;
        }
      }
      btn.setAttribute("href", url);
    }
  }

  function useReleaseData(data) {
    if (data && typeof data.tag_name === "string" && data.tag_name) {
      latestTag = data.tag_name;
    }
    applyReleaseAssets(data && data.assets);
    renderVersionLine(i18n[currentLang] || i18n.ru);
  }

  function loadRelease() {
    var cached = null;
    try { cached = sessionStorage.getItem(RELEASE_CACHE_KEY); } catch (e) { cached = null; }
    if (cached) {
      try { useReleaseData(JSON.parse(cached)); return; } catch (e2) { /* refetch below */ }
    }
    if (typeof fetch !== "function") { return; }
    fetch(RELEASE_API, { headers: { Accept: "application/vnd.github+json" } })
      .then(function (resp) {
        if (!resp.ok) { throw new Error("release request failed: " + resp.status); }
        return resp.json();
      })
      .then(function (data) {
        try { sessionStorage.setItem(RELEASE_CACHE_KEY, JSON.stringify(data)); } catch (e) { /* ignore */ }
        useReleaseData(data);
      })
      .catch(function () {
        // Offline or rate-limited: buttons already default to the releases page, so nothing to do.
      });
  }

  function applyLang(lang) {
    var dict = i18n[lang] || i18n.ru;
    currentLang = (lang in i18n) ? lang : "ru";

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

    var ariaNodes = document.querySelectorAll("[data-i18n-aria]");
    for (var a = 0; a < ariaNodes.length; a++) {
      var aEl = ariaNodes[a];
      var aKey = aEl.getAttribute("data-i18n-aria");
      if (aKey in dict) { aEl.setAttribute("aria-label", dict[aKey]); }
    }

    if (dict.pageTitle) { document.title = dict.pageTitle; }

    renderVersionLine(dict);

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
    loadRelease();

    var buttons = document.querySelectorAll(".lang-btn");
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].addEventListener("click", function () {
        setLang(this.getAttribute("data-lang"));
      });
    }
  });
})();
