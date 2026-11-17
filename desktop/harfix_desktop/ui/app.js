(function () {
  "use strict";

  var H = window.Harfix;
  // UI texts are written in the current Latin alphabet and shown in the new one.
  var L = function (s) { return H.convert(s); };
  function $(id) { return document.getElementById(id); }
  function esc(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  var toastTimer;
  function toast(text) {
    var el = $("toast");
    el.textContent = text;
    el.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.classList.remove("show"); }, 2200);
  }

  // --- Python bridge (pywebview); a stand-in when the page is opened in a browser ---
  // Inside Harfix (WebView2) we always wait for the real bridge, however slow the start.
  var inApp = !!(window.chrome && window.chrome.webview);
  var api = null;
  var apiReady = new Promise(function (resolve) {
    function done() { if (!api) { api = window.pywebview.api; resolve(api); } }
    if (window.pywebview && window.pywebview.api && window.pywebview.api.state) { done(); return; }
    window.addEventListener("pywebviewready", done);
    if (!inApp) setTimeout(function () { if (!api) { api = browserStandIn(); resolve(api); } }, 800);
  });
  function browserStandIn() {
    var s = { target: "new", realtime: true, autostart: false, close_to_tray: true, stats: true, out_mode: "beside",
      out_dir: "", exclude: ["code.exe", "cmd.exe", "keepass.exe", "powershell.exe", "windowsterminal.exe"], quiz_best: 0 };
    var state = function () {
      return Promise.resolve({ version: "dev", settings: s, tools: { pdf: true, legacy: false },
        hotkeys: { "1": true, "2": true }, engine: false, frozen: false, busy: false });
    };
    return {
      state: state,
      set: function (k, v) { s[k] = v; return state(); },
      pick_files: function () {
        return Promise.resolve([
          { path: "C:\\Hujjatlar\\Hisobot.docx", name: "Hisobot.docx", kind: "file", size: 48213, root: "" },
          { path: "C:\\Hujjatlar\\Shartnoma.pdf", name: "Shartnoma.pdf", kind: "pdf", size: 1203321, root: "" },
          { path: "C:\\Hujjatlar\\eski.doc", name: "eski.doc", kind: "legacy", size: 30211, root: "" }]);
      },
      pick_folder: function () { return Promise.resolve([]); },
      pick_out_dir: function () { return Promise.resolve(""); },
      start: function () { return Promise.resolve(false); },
      cancel: function () { return Promise.resolve(); },
      open: function () { return Promise.resolve(); },
      reveal: function () { return Promise.resolve(); },
      save_quiz: function (n) { s.quiz_best = Math.max(n, s.quiz_best); return Promise.resolve(s.quiz_best); },
      online: function () {
        return Promise.resolve({ config: null, fresh: false, platform: "windows", version: "dev", install_days: 0, dismissed: [] });
      },
      dismiss: function () { return Promise.resolve(); },
      open_url: function (u) { window.open(u, "_blank", "noopener"); return Promise.resolve(); },
      hide: function () { return Promise.resolve(); },
      quit: function () { return Promise.resolve(); }
    };
  }
  function call(name) {
    var args = Array.prototype.slice.call(arguments, 1);
    return apiReady.then(function (a) { return a[name].apply(a, args); });
  }

  var state = { settings: { target: "new", realtime: true, exclude: [] }, tools: {}, hotkeys: {} };

  // --- static texts ------------------------------------------------------------------
  document.querySelectorAll("[data-t]").forEach(function (el) { el.textContent = L(el.dataset.t); });
  $("src").placeholder = L("Matnni shu yerga yozing yoki qoʻying (lotin yoki kirill)…");

  // --- navigation ----------------------------------------------------------------------
  function showTab(name) {
    document.querySelectorAll(".nav-item").forEach(function (b) { b.classList.toggle("active", b.dataset.tab === name); });
    document.querySelectorAll(".panel").forEach(function (p) { p.classList.toggle("active", p.id === "tab-" + name); });
    if (name === "quiz" && !quiz.started) startQuiz();
    if (name === "convert") $("src").focus();
  }
  document.querySelectorAll(".nav-item").forEach(function (b) {
    b.addEventListener("click", function () { showTab(b.dataset.tab); });
  });

  // --- alphabet choice (shared by text and files) ---------------------------------------
  var NAMES = { "new": L("Yangi lotin"), "old": L("Eski lotin"), "cyr": "Кирилл" };
  function target() { return state.settings.target || "new"; }
  function paintTargets() {
    document.querySelectorAll(".targets button").forEach(function (b) {
      b.classList.toggle("on", b.dataset.target === target());
      b.setAttribute("aria-checked", b.dataset.target === target() ? "true" : "false");
    });
  }
  document.querySelectorAll(".targets button").forEach(function (b) {
    b.addEventListener("click", function () {
      state.settings.target = b.dataset.target;
      paintTargets();
      render();
      call("set", "target", b.dataset.target);
    });
  });

  // --- text --------------------------------------------------------------------------
  var src = $("src"), out = $("out"), result = "";
  function render() {
    var text = src.value;
    result = text ? H.convertTo(text, target()) : "";
    out.textContent = result || L("Natija shu yerda chiqadi");
    out.classList.toggle("empty", !result);
    $("count").textContent = text.length ? L(text.length + " ta belgi") : "";
    $("badge").textContent = !text ? NAMES[target()] : (result === text ? L("✓ oʻzgarish yoʻq") : NAMES[target()]);
    $("copy").disabled = !result;
    $("use").disabled = !result || result === text;
  }
  src.addEventListener("input", render);
  $("clear").addEventListener("click", function () { src.value = ""; render(); src.focus(); });
  $("use").addEventListener("click", function () { src.value = result; render(); src.focus(); });
  $("copy").addEventListener("click", function () {
    if (!result) return;
    navigator.clipboard.writeText(result).then(function () { toast(L("Nusxalandi ✓")); },
      function () { toast(L("Nusxalab boʻlmadi — matnni belgilab nusxalang")); });
  });

  // --- real-time typing switch ------------------------------------------------------------
  function paintLive() {
    var on = !!state.settings.realtime;
    $("live-toggle").checked = on;
    $("live").classList.toggle("off", !on);
    $("live-state").textContent = on ? L("Yoqilgan — istalgan dasturda") : L("Oʻchiq");
    document.querySelectorAll("[data-set=realtime]").forEach(function (el) { el.checked = on; });
  }
  $("live-toggle").addEventListener("change", function () { setSetting("realtime", this.checked); });

  function setSetting(key, value) {
    state.settings[key] = value;
    paintAll();
    return call("set", key, value).then(function (s) { if (s) { state = s; paintAll(); } });
  }

  // --- files ------------------------------------------------------------------------------
  var KIND = { file: L("Office / txt"), pdf: "PDF", legacy: L("Eski format") };
  var ICON = { file: "📄", pdf: "📕", legacy: "🗂" };
  var HOW = { done: L("Tayyor"), legacy: L("Tayyor: yangi formatda") };
  var ERR = {
    unsupported: L("Fayl ochilmadi: turi qoʻllab-quvvatlanmaydi yoki fayl buzilgan"),
    pdf_off: L("PDF bilan ishlash moduli yoʻq"),
    pdf_scan: L("Skaner qilingan PDF: ichida matn yoʻq"),
    legacy_off: L("Eski formatlar uchun LibreOffice kerak"),
    legacy_failed: L("LibreOffice faylni ocha olmadi"),
    pdf_encrypted: L("PDF parol bilan himoyalangan"),
    pdf_too_many: L("PDF juda katta (300 betdan koʻp)"),
    locked: L("Fayl band yoki ruxsat yoʻq"),
    io: L("Faylni oʻqib yoki yozib boʻlmadi"),
    failed: L("Oʻgirib boʻlmadi")
  };
  var queue = [], running = false;

  function size(n) {
    if (n >= 1048576) return (n / 1048576).toFixed(1) + " MB";
    return Math.max(1, Math.round(n / 1024)) + " KB";
  }
  function addItems(items) {
    var have = {};
    queue.forEach(function (q) { have[q.path.toLowerCase()] = 1; });
    var added = 0;
    (items || []).forEach(function (it) {
      if (have[it.path.toLowerCase()]) return;
      it.status = "wait";
      queue.push(it);
      added++;
    });
    if (!added && items && !items.length) toast(L("Mos fayl topilmadi"));
    paintQueue();
  }
  function paintQueue() {
    $("queue-card").hidden = !queue.length;
    $("files-opts").hidden = !queue.length;
    var done = queue.filter(function (q) { return q.status === "done"; }).length;
    $("queue-title").textContent = L(queue.length + " ta fayl") + (done ? " · " + L(done + " ta tayyor") : "");
    $("queue").innerHTML = queue.map(function (q, i) {
      var st = q.status === "working" ? '<span class="spin">⏳</span> ' + L("Oʻgirilmoqda…")
        : q.status === "done" ? "✓ " + (HOW[q.how] || HOW.done)
        : q.status === "error" ? (ERR[q.error] || ERR.failed) : L("Navbatda");
      var cls = q.status === "done" ? "ok" : q.status === "error" ? "bad" : "";
      var acts = q.status === "done"
        ? '<button class="link" data-open="' + i + '">' + L("Ochish") + '</button><button class="link" data-reveal="' + i + '">' + L("Papka") + "</button>"
        : (!running && q.status !== "working" ? '<button class="x" title="' + L("Olib tashlash") + '" data-remove="' + i + '">×</button>' : "");
      return '<div class="qrow"><span>' + (ICON[q.kind] || "📄") + '</span><span class="name" title="' + esc(q.path) + '">' + esc(q.name) +
        '</span><span class="hint">' + (KIND[q.kind] || "") + '</span><span class="hint size">' + size(q.size) +
        '</span><span class="st ' + cls + '">' + st + '</span><span class="acts">' + acts + "</span></div>";
    }).join("");
    $("start").disabled = running || !queue.some(function (q) { return q.status !== "done"; });
    $("cancel").hidden = !running;
    $("queue-clear").hidden = running;
  }
  $("queue").addEventListener("click", function (e) {
    var b = e.target.closest("button");
    if (!b) return;
    if (b.dataset.open) call("open", queue[+b.dataset.open].out);
    if (b.dataset.reveal) call("reveal", queue[+b.dataset.reveal].out);
    if (b.dataset.remove) { queue.splice(+b.dataset.remove, 1); paintQueue(); }
  });
  $("pick-files").addEventListener("click", function () { call("pick_files").then(addItems); });
  $("pick-folder").addEventListener("click", function () { call("pick_folder").then(addItems); });
  $("queue-clear").addEventListener("click", function () { queue = []; paintQueue(); });
  $("start").addEventListener("click", function () {
    var todo = [];
    queue.forEach(function (q, i) { if (q.status !== "done") { q.status = "wait"; q.at = i; todo.push(q); } });
    if (!todo.length) return;
    batchMap = todo;  // before the call: progress events may arrive before it returns
    running = true;
    paintQueue();
    call("start", todo.map(function (q) { return { path: q.path, name: q.name, kind: q.kind, size: q.size, root: q.root }; }))
      .then(function (ok) {
        if (ok) return;
        running = false;
        paintQueue();
        toast(L("Hozir boshqa fayllar oʻgirilmoqda yoki bu oynada fayl bilan ishlab boʻlmaydi"));
      });
  });
  $("cancel").addEventListener("click", function () { call("cancel"); });
  document.querySelectorAll("input[name=out]").forEach(function (r) {
    r.addEventListener("change", function () {
      if (this.value === "folder" && !state.settings.out_dir) { pickOut(); return; }
      setSetting("out_mode", this.value);
    });
  });
  function pickOut() {
    call("pick_out_dir").then(function (dir) {
      if (dir) { state.settings.out_dir = dir; state.settings.out_mode = "folder"; }
      paintAll();
    });
  }
  $("pick-out").addEventListener("click", function (e) { e.preventDefault(); pickOut(); });

  var batchMap = [];
  // Drag and drop: Python reads the full paths, the page only shows the highlight.
  var drop = $("drop"), depth = 0;
  document.addEventListener("dragenter", function (e) { e.preventDefault(); depth++; drop.classList.add("over"); showTab("files"); });
  document.addEventListener("dragleave", function () { depth = Math.max(0, depth - 1); if (!depth) drop.classList.remove("over"); });
  document.addEventListener("dragover", function (e) { e.preventDefault(); });
  document.addEventListener("drop", function (e) { e.preventDefault(); depth = 0; drop.classList.remove("over"); });

  function paintTools() {
    var t = state.tools || {}, warn = [];
    if (!t.legacy) warn.push(L("Eski .doc/.xls/.ppt fayllar uchun LibreOffice oʻrnating (bepul): libreoffice.org"));
    $("tool-warn").hidden = !warn.length;
    $("tool-warn").textContent = "⚠️ " + warn.join(" ");
  }

  // Events from Python
  window.harfixEvent = function (ev) {
    if (ev.kind === "dropped") { addItems(ev.items); return; }
    if (ev.kind === "file") {
      var q = batchMap[ev.index];
      if (!q) return;
      q.status = ev.status;
      if (ev.out) q.out = ev.out;
      if (ev.how) q.how = ev.how;
      if (ev.error) q.error = ev.error;
      paintQueue();
      return;
    }
    if (ev.kind === "finished") {
      running = false;
      paintQueue();
      var msg = ev.cancelled ? L("Toʻxtatildi.") : L("Tayyor!");
      msg += " " + L(ev.ok + " ta oʻgirildi") + (ev.failed ? ", " + L(ev.failed + " ta xato") : "");
      toast(msg);
      return;
    }
    if (ev.kind === "realtime") { state.settings.realtime = ev.on; paintLive(); return; }
    if (ev.kind === "engine") { state.hotkeys = ev.hotkeys || {}; paintSettings(); }
    if (ev.kind === "online") loadOnline();
  };

  // --- online: announcement, new version note, sponsor card (online.js) -----------------
  // Python loads the config and sends the daily count; without internet nothing shows here.
  function loadOnline() {
    call("online").then(function (o) {
      if (!o || !window.HarfixOnline) return;
      var p = HarfixOnline.plan(o.config, { platform: o.platform, version: o.version, installDays: o.install_days,
        fresh: o.fresh, dismissed: o.dismissed });
      HarfixOnline.render(p, { top: $("online-top"), ad: $("online-ad"), L: L,
        open: function (url) { call("open_url", url); },
        dismiss: function (id) { call("dismiss", id); } });
    });
  }

  // --- settings ----------------------------------------------------------------------
  document.querySelectorAll("[data-set]").forEach(function (el) {
    el.addEventListener("change", function () { setSetting(el.dataset.set, el.checked); });
  });
  function paintSettings() {
    var s = state.settings;
    document.querySelectorAll("[data-set]").forEach(function (el) { el.checked = !!s[el.dataset.set]; });
    $("exclude").innerHTML = (s.exclude || []).map(function (x, i) {
      return '<span class="chip">' + esc(x) + '<button title="' + L("Olib tashlash") + '" data-i="' + i + '">×</button></span>';
    }).join("");
    var hk = state.hotkeys || {};
    function hkRow(id, keys, what) {
      var st = hk[id] === false ? '<span class="bad-text">' + L("boshqa dastur band qilgan") + "</span>"
        : hk[id] ? '<span class="ok-text">✓</span>' : "";
      return '<div class="row"><span><kbd>' + keys.join("</kbd>+<kbd>") + '</kbd></span><span class="what">' + what + "</span>" + st + "</div>";
    }
    $("hotkeys").innerHTML =
      hkRow("1", ["Ctrl", "Shift", "Y"], L("Yozayotganda oʻgirishni yoqish / oʻchirish")) +
      hkRow("2", ["Ctrl", "Shift", "H"], L("Belgilangan matnni tanlangan alifboga oʻgirish (istalgan dasturda)")) +
      '<div class="row"><span><kbd>Backspace</kbd></span><span class="what">' + L("Almashgan harfdan keyin darhol bosilsa — yozganingiz qaytadi") + "</span></div>";
    var t = state.tools || {};
    function yes(v) { return v ? "✅" : "—"; }
    $("about").innerHTML = "<p>Harfix " + esc(state.version || "") + "</p><p>PDF " + yes(t.pdf) +
      " · LibreOffice " + yes(t.legacy) + "</p>";
    var outDir = s.out_dir || "";
    document.querySelectorAll("input[name=out]").forEach(function (r) { r.checked = r.value === (s.out_mode || "beside"); });
    $("out-dir").textContent = outDir;
  }
  $("exclude").addEventListener("click", function (e) {
    var b = e.target.closest("button");
    if (!b) return;
    var list = state.settings.exclude.slice();
    list.splice(+b.dataset.i, 1);
    setSetting("exclude", list);
  });
  $("exclude-form").addEventListener("submit", function (e) {
    e.preventDefault();
    var v = $("exclude-input").value.trim().toLowerCase();
    if (!v) return;
    if (!/\.exe$/.test(v)) v += ".exe";
    var list = state.settings.exclude.slice();
    if (list.indexOf(v) < 0) list.push(v);
    $("exclude-input").value = "";
    setSetting("exclude", list.sort());
  });
  $("quit").addEventListener("click", function () { call("quit"); });

  function paintAll() {
    paintTargets();
    paintLive();
    paintSettings();
    paintTools();
    render();
  }

  // --- learn ------------------------------------------------------------------------------
  var LETTERS = [
    { big: "Ö ö", from: "oʻ · ў", ex: "Oʻzbekiston" },
    { big: "Ğ ğ", from: "gʻ · ғ", ex: "togʻ" },
    { big: "Ş ş", from: "sh · ш", ex: "shahar" },
    { big: "Ç ç", from: "ch · ч", ex: "choʻl" }
  ];
  $("letters").innerHTML = LETTERS.map(function (l) {
    return '<div class="letter"><div class="big">' + l.big + '</div><div class="from">' + l.from + '</div><div class="ex">' +
      esc(l.ex) + " → <b>" + esc(H.convert(l.ex)) + "</b></div></div>";
  }).join("");
  function ex(words) {
    return words.map(function (w) { return esc(w) + " → <b>" + esc(H.convert(w)) + "</b>"; }).join(", ");
  }
  var RULES = [
    [L("Katta harflar"), ex(["SHAHAR", "Shahar", "OʻZBEKISTON"])],
    [L("Tutuq belgisi (ʼ) saqlanadi"), ex(["maʼno", "sheʼr"])],
    [L("s va h alohida tovush boʻlsa, oraga tutuq qoʻyiladi"), ex(["Isʼhoq"])],
    [L("ng oʻzgarmaydi"), ex(["tong", "ming"])],
    [L("Kirilldan"), ex(["Тошкент", "Ўзбекистон"])]
  ];
  $("rules").innerHTML = RULES.map(function (r) { return "<li>" + r[0] + ": " + r[1] + "</li>"; }).join("");
  $("typing").innerHTML = [["o + ʼ", "ö"], ["g + ʼ", "ğ"], ["s + h", "ş"], ["c + h", "ç"]].map(function (p) {
    return "<li><kbd>" + p[0].split(" + ").join("</kbd> + <kbd>") + "</kbd> → <b>" + p[1] + "</b></li>";
  }).join("");

  // --- quiz -------------------------------------------------------------------------------
  var WORDS = ["Oʻzbekiston", "Toshkent", "shahar", "choʻl", "togʻ", "bogʻ", "qoʻshiq", "oʻqituvchi",
    "gʻisht", "choy", "chiroyli", "shamol", "quyosh", "ishchi", "oʻyin", "sogʻliq", "yashil", "tushlik",
    "choʻntak", "qishloq", "oʻrik", "gʻoya", "ochiq", "kecha", "kuchli", "oʻtmish", "sharbat", "chaqmoq",
    "oʻgʻil", "qizgʻaldoq", "ogʻir", "choʻqqi", "shirin", "ishonch", "Fargʻona", "Shahrisabz", "Chirchiq",
    "Qoʻqon", "Margʻilon", "Urganch", "Toʻrtkoʻl", "Gʻijduvon", "goʻsht", "toʻgʻri", "chegara", "koʻcha"];
  var LOOK = { "ö": "üõô", "ğ": "ǧĝg", "ş": "šśs", "ç": "čćc", "Ö": "ÜÕÔ", "Ğ": "ǦĜG", "Ş": "ŠŚS", "Ç": "ČĆC" };
  var QUIZ_LEN = 10;
  var quiz = { started: false };
  function shuffle(a) {
    for (var i = a.length - 1; i > 0; i--) {
      var j = Math.floor(Math.random() * (i + 1));
      var t = a[i]; a[i] = a[j]; a[j] = t;
    }
    return a;
  }
  function wrongs(word, right) {
    var c = {};
    c[word.replace(/ʻ/g, "'")] = 1;
    c[right.replace(/[öğşçÖĞŞÇ]/g, function (ch) { return "ogscOGSC"["öğşçÖĞŞÇ".indexOf(ch)]; })] = 1;
    for (var k = 0; k < 12; k++) {
      var chars = right.split(""), idx = [];
      chars.forEach(function (ch, i) { if (LOOK[ch]) idx.push(i); });
      if (!idx.length) break;
      shuffle(idx).slice(0, 1 + Math.floor(Math.random() * idx.length)).forEach(function (i) {
        var opts = LOOK[chars[i]];
        chars[i] = opts[Math.floor(Math.random() * opts.length)];
      });
      c[chars.join("")] = 1;
    }
    delete c[right];
    return shuffle(Object.keys(c)).slice(0, 3);
  }
  function makeQuiz() {
    var qs = [];
    shuffle(WORDS.slice()).forEach(function (w) {
      if (qs.length >= QUIZ_LEN) return;
      var right = H.convert(w), bad = wrongs(w, right);
      if (bad.length < 3) return;
      var opts = shuffle([right].concat(bad));
      var text = qs.length % 2 ? L("«" + w + "» yangi alifboda qanday yoziladi?") : L("Qaysi soʻz yangi alifboda toʻgʻri yozilgan?");
      if (qs.length % 2) text = text.replace("«" + H.convert(w) + "»", "«" + w + "»");
      qs.push({ text: text, opts: opts, right: opts.indexOf(right), expl: w + " → " + right });
    });
    return qs;
  }
  function startQuiz() { quiz = { started: true, qs: makeQuiz(), i: 0, score: 0 }; showQuestion(); }
  function showQuestion() {
    var q = quiz.qs[quiz.i];
    $("qnum").textContent = L("Savol " + (quiz.i + 1) + " / " + quiz.qs.length);
    $("qscore").textContent = "✓ " + quiz.score;
    $("qbar").style.width = (quiz.i / quiz.qs.length * 100) + "%";
    $("qtext").textContent = q.text;
    $("qexpl").textContent = "";
    $("qnext").hidden = true;
    $("qopts").innerHTML = "";
    q.opts.forEach(function (o, i) {
      var b = document.createElement("button");
      b.className = "opt";
      b.textContent = o;
      b.addEventListener("click", function () { answer(i); });
      $("qopts").appendChild(b);
    });
  }
  function answer(i) {
    var q = quiz.qs[quiz.i];
    $("qopts").querySelectorAll(".opt").forEach(function (b, k) {
      b.disabled = true;
      if (k === q.right) b.classList.add("ok"); else if (k === i) b.classList.add("bad");
    });
    if (i === q.right) quiz.score++;
    $("qscore").textContent = "✓ " + quiz.score;
    $("qexpl").textContent = L("Toʻgʻri javob: ") + q.expl;
    $("qnext").textContent = quiz.i + 1 < quiz.qs.length ? L("Keyingi savol") : L("Natijani koʻrish");
    $("qnext").hidden = false;
  }
  $("qnext").addEventListener("click", function () {
    if (quiz.finished) { startQuiz(); return; }
    quiz.i++;
    if (quiz.i < quiz.qs.length) { showQuestion(); return; }
    quiz.finished = true;
    var k = quiz.score / quiz.qs.length;
    $("qbar").style.width = "100%";
    $("qnum").textContent = L("Test tugadi");
    $("qtext").innerHTML = '<div class="final">' + (k === 1 ? "🏆" : k >= 0.8 ? "🥇" : k >= 0.6 ? "🥈" : "📚") + "</div>";
    $("qopts").innerHTML = "";
    $("qnext").textContent = L("Qaytadan boshlash");
    call("save_quiz", quiz.score).then(function (best) {
      $("qexpl").textContent = L("Natija: " + quiz.score + " / " + quiz.qs.length + " · eng yaxshi: " + best);
    });
  });

  // --- start ----------------------------------------------------------------------------------
  paintAll();
  apiReady.then(function () { return call("state"); }).then(function (s) {
    state = s;
    $("version").textContent = L("Yangi alifbo yordamchisi") + " · " + s.version;
    paintAll();
    loadOnline();
  });
  src.focus();
})();
