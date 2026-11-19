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
    toastTimer = setTimeout(function () { el.classList.remove("show"); }, 2000);
  }

  // --- the phone (Kotlin bridge); a stand-in when the page is opened in a browser ---
  var A = window.HarfixAndroid || (function () {
    var s = { target: "new", live: true, sound: false, vibrate: true, lang_ru: true, lang_uz_cyrl: true, words: true, words_live: true,
      stats: true, notify: true, quiz_best: 0 };
    return {
      ready: function () {}, copy: function () {}, shareText: function () {}, keyboardSettings: function () {}, appSettings: function () {}, agreeWords: function () {},
      keyboardPicker: function () {}, accessibilitySettings: function () {}, open: function () {}, share: function () {},
      state: function () { return JSON.stringify({ version: "dev", settings: s, keyboard: { enabled: true, selected: false } }); },
      set: function (k, v) { s[k] = k === "target" ? v : v === "true"; },
      saveQuiz: function (n) { s.quiz_best = Math.max(n, s.quiz_best); return s.quiz_best; },
      pickFiles: function () {
        setTimeout(function () {
          window.harfixEvent({ kind: "files", items: [
            { uri: "content://1", name: "Hisobot.docx", size: 48213, supported: true },
            { uri: "content://2", name: "Jadval.xlsx", size: 20111, supported: true },
            { uri: "content://3", name: "rasm.jpg", size: 512000, supported: false }] });
        }, 100);
      },
      convert: function () {},
      online: function () {
        return JSON.stringify({ config: null, fresh: false, platform: "android", version: "dev", install_days: 0, dismissed: [] });
      },
      dismiss: function () {}, openUrl: function (u) { window.open(u, "_blank"); },
      showBanner: function () {}, hideBanner: function () {}
    };
  })();

  var state = JSON.parse(A.state());
  function refresh() { state = JSON.parse(A.state()); paintAll(); }

  document.querySelectorAll("[data-t]").forEach(function (el) { el.textContent = L(el.dataset.t); });
  $("src").placeholder = L("Matnni shu yerga yozing yoki qoʻying (lotin yoki kirill)…");
  $("try").placeholder = L("Masalan:") + " Toshkent shahri, oʻzbek tili…"; // the example stays as typed

  // The privacy policy (the same page as the store listings); Google Play wants it reachable in the app.
  var PRIVACY_URL = "https://harfix.uz/maxfiylik/";
  $("privacy").addEventListener("click", function () { A.openUrl(PRIVACY_URL); });

  // --- tabs ----------------------------------------------------------------------
  var currentTab = "convert";
  function showTab(name) {
    currentTab = name;
    document.querySelectorAll(".tab").forEach(function (b) { b.classList.toggle("active", b.dataset.tab === name); });
    document.querySelectorAll(".panel").forEach(function (p) { p.classList.toggle("active", p.id === "tab-" + name); });
    if (name === "quiz" && !quiz.started) startQuiz();
    if (name === "keyboard") refresh();
    window.scrollTo(0, 0);
  }
  document.querySelectorAll(".tab").forEach(function (b) {
    b.addEventListener("click", function () { showTab(b.dataset.tab); });
  });
  // While typing, the tab bar would sit on top of the keyboard: hide it.
  document.addEventListener("focusin", function (e) {
    if (e.target.matches("textarea, input")) document.body.classList.add("typing");
  });
  document.addEventListener("focusout", function () { document.body.classList.remove("typing"); });

  // Android Back: first back to the first tab, then the app closes
  window.harfixBack = function () {
    if (currentTab !== "convert") { showTab("convert"); return true; }
    return false;
  };

  // --- alphabet ---------------------------------------------------------------------
  var NAMES = { "new": L("Yangi lotin"), "old": L("Eski lotin"), "cyr": "Кирилл" };
  function target() { return state.settings.target || "new"; }
  document.querySelectorAll(".targets button").forEach(function (b) {
    b.addEventListener("click", function () {
      state.settings.target = b.dataset.target;
      A.set("target", b.dataset.target);
      paintAll();
    });
  });

  // --- text ----------------------------------------------------------------------------
  var src = $("src"), out = $("out"), result = "";
  function render() {
    var text = src.value;
    result = text ? H.convertTo(text, target()) : "";
    out.textContent = result || L("Natija shu yerda chiqadi");
    out.classList.toggle("empty", !result);
    $("count").textContent = text.length ? L(text.length + " ta belgi") : "";
    $("badge").textContent = !text ? NAMES[target()] : (result === text ? L("✓ oʻzgarish yoʻq") : NAMES[target()]);
    $("copy").disabled = $("share").disabled = !result;
  }
  src.addEventListener("input", render);
  $("clear").addEventListener("click", function () { src.value = ""; render(); src.focus(); });
  $("copy").addEventListener("click", function () { if (result) { A.copy(result); toast(L("Nusxalandi ✓")); } });
  $("share").addEventListener("click", function () { if (result) A.shareText(result); });

  // --- files ------------------------------------------------------------------------------
  var ERR = {
    unsupported: L("Bu fayl turi qoʻllab-quvvatlanmaydi yoki fayl buzilgan"),
    too_big: L("Fayl juda katta"),
    io: L("Faylni oʻqib yoki saqlab boʻlmadi"),
    failed: L("Oʻgirib boʻlmadi")
  };
  var queue = [], running = false;
  function size(n) {
    if (n < 0) return "";
    return n >= 1048576 ? (n / 1048576).toFixed(1) + " MB" : Math.max(1, Math.round(n / 1024)) + " KB";
  }
  function addItems(items) {
    var have = {};
    queue.forEach(function (q) { have[q.uri] = 1; });
    (items || []).forEach(function (it) {
      if (have[it.uri]) return;
      it.status = it.supported ? "wait" : "error";
      if (!it.supported) it.error = "unsupported";
      queue.push(it);
    });
    showTab("files");
    paintQueue();
  }
  function paintQueue() {
    $("queue-card").hidden = !queue.length;
    var done = queue.filter(function (q) { return q.status === "done"; }).length;
    $("queue-title").textContent = L(queue.length + " ta fayl") + (done ? " · " + L(done + " ta tayyor") : "");
    $("queue").innerHTML = queue.map(function (q, i) {
      var st = q.status === "working" ? L("Oʻgirilmoqda…")
        : q.status === "done" ? "✓ " + esc(q.outName || "")
        : q.status === "error" ? (ERR[q.error] || ERR.failed) : (size(q.size) || L("Navbatda"));
      var cls = q.status === "done" ? "ok" : q.status === "error" ? "bad" : "";
      var acts = q.status === "done"
        ? '<button class="link" data-open="' + i + '">' + L("Ochish") + '</button><button class="link" data-share="' + i + '">' + L("Ulashish") + "</button>"
        : (!running ? '<button class="x" data-remove="' + i + '">×</button>' : "");
      return '<div class="q"><span>' + (q.status === "done" ? "✅" : "📄") + '</span><div class="grow"><div class="name">' +
        esc(q.name) + '</div><div class="st ' + cls + '">' + st + '</div></div><div class="acts">' + acts + "</div></div>";
    }).join("");
    $("start").disabled = running || !queue.some(function (q) { return q.status === "wait"; });
    $("start").textContent = running ? L("Oʻgirilmoqda…") : L("Oʻgirish");
    $("queue-clear").hidden = running;
  }
  $("queue").addEventListener("click", function (e) {
    var b = e.target.closest("button");
    if (!b) return;
    var q = queue[+(b.dataset.open || b.dataset.share || b.dataset.remove)];
    if (b.dataset.open) A.open(q.out, q.outName);
    if (b.dataset.share) A.share(q.out, q.outName);
    if (b.dataset.remove) { queue.splice(+b.dataset.remove, 1); paintQueue(); }
  });
  $("pick").addEventListener("click", function () { A.pickFiles(); });
  $("queue-clear").addEventListener("click", function () { queue = []; paintQueue(); });
  var batch = [];
  $("start").addEventListener("click", function () {
    batch = queue.filter(function (q) { return q.status === "wait"; });
    if (!batch.length) return;
    running = true;
    paintQueue();
    A.convert(JSON.stringify(batch.map(function (q) { return { uri: q.uri, name: q.name, size: q.size }; })), target());
  });

  // --- events from the phone --------------------------------------------------------------
  window.harfixEvent = function (ev) {
    if (ev.kind === "state") { refresh(); return; }
    if (ev.kind === "online") { loadOnline(); return; }
    if (ev.kind === "open") { showTab(ev.tab || "convert"); refresh(); return; }
    if (ev.kind === "text") { src.value = ev.text; render(); showTab("convert"); return; }
    if (ev.kind === "files") { addItems(ev.items); return; }
    if (ev.kind === "file") {
      var q = batch[ev.index];
      if (!q) return;
      q.status = ev.status;
      if (ev.uri) { q.out = ev.uri; q.outName = ev.name; }
      if (ev.error) q.error = ev.error;
      paintQueue();
      return;
    }
    if (ev.kind === "finished") {
      running = false;
      paintQueue();
      toast(L("Tayyor! " + ev.ok + " ta fayl «Download/Harfix» papkasida") + (ev.failed ? " · " + L(ev.failed + " ta xato") : ""));
    }
  };

  // --- keyboard -------------------------------------------------------------------------------
  // Before Accessibility settings, say what the service does (once; Google Play asks for this)
  function openWords() {
    if (!state.words_ok) { $("words-dialog").hidden = false; return; }
    if (state.restricted) { $("words-guide").hidden = false; return; } // not from Google Play: 3 steps
    A.accessibilitySettings();
  }
  $("guide-try").addEventListener("click", function () { A.accessibilitySettings(); });
  $("guide-on").addEventListener("click", function () { A.accessibilitySettings(); });
  $("guide-app").addEventListener("click", function () { A.appSettings(); });
  $("guide-close").addEventListener("click", function () { $("words-guide").hidden = true; });
  $("words-on").addEventListener("click", openWords);
  $("words-again").addEventListener("click", openWords);
  $("words-cancel").addEventListener("click", function () { $("words-dialog").hidden = true; });
  $("words-agree").addEventListener("click", function () {
    $("words-dialog").hidden = true;
    state.words_ok = true;
    A.agreeWords();
    openWords();
  });
  $("app-settings").addEventListener("click", function () { A.appSettings(); });
  $("setup-go").addEventListener("click", function () { // explanation, then straight to Harfix's switch
    if (state.words_available === false) showTab("keyboard"); else openWords();
  });
  $("enable").addEventListener("click", function () { A.keyboardSettings(); });
  $("choose").addEventListener("click", function () { A.keyboardPicker(); });
  document.querySelectorAll("[data-set]").forEach(function (el) {
    el.addEventListener("change", function () {
      state.settings[el.dataset.set] = el.checked;
      A.set(el.dataset.set, String(el.checked));
    });
  });
  $("kbd-tips").innerHTML = [
    "<kbd>s</kbd> + <kbd>h</kbd> → <b>ş</b>, <kbd>c</kbd> + <kbd>h</kbd> → <b>ç</b> — " + esc(L("odatdagidek yozing")),
    "<kbd>o</kbd> + <kbd>'</kbd> → <b>ö</b> — " + esc(L("tutuq belgisi ?123 boʻlimida, yoki «l» ni bosib turing")),
    esc(L("Tepadagi qatorda ö ğ ş ç tayyor turadi")),
    esc(L("Harfni bosib turing — raqam va boshqa variantlar chiqadi")),
    "<kbd>Рус</kbd> / <kbd>Ўзб</kbd> / <kbd>Lot</kbd> — " + esc(L("tillar (Sozlamalarda yoqiladi)")),
    "🌐 — " + esc(L("boshqa klaviaturaga oʻtish; boʻsh joyni bosib turing — roʻyxat")),
    esc(L("Istalgan ilovada matnni belgilab, menyudan «Harfix»ni tanlang — oʻsha joyda oʻgiriladi"))
  ].map(function (t) { return "<li>" + t + "</li>"; }).join("");

  function paintKeyboard() {
    var k = state.keyboard || {};
    // First run: nothing on yet. Later: warn if Android switched the word service off.
    var words = state.words_available !== false; // the website APK has no word service
    $("step-words").closest(".card").hidden = !words;
    $("setup-card").hidden = !!(state.words || k.selected || state.words_seen);
    $("words-off").hidden = !(state.words_seen && !state.words);
    if (state.words) $("words-guide").hidden = true; // switched on: the guide is done
    $("step-words").classList.toggle("done", !!state.words);
    $("words-on").textContent = state.words ? L("✓ Yoqilgan") : L("Yoqish");
    $("words-on").classList.toggle("done", !!state.words);
    $("step1").classList.toggle("done", !!k.enabled);
    $("step2").classList.toggle("done", !!k.selected);
    $("enable").textContent = k.enabled ? L("✓ Yoqilgan") : L("Yoqish");
    $("enable").classList.toggle("done", !!k.enabled);
    $("choose").textContent = k.selected ? L("✓ Tanlangan") : L("Tanlash");
    $("choose").classList.toggle("done", !!k.selected);
    $("choose").disabled = !k.enabled;
    document.querySelectorAll("[data-set]").forEach(function (el) { el.checked = !!state.settings[el.dataset.set]; });
    $("app-version").textContent = "Harfix " + (state.version || "");
  }

  // --- online: announcement, new version note, sponsor card, ad banner (online.js) ----------
  // The phone loads the config and sends the daily count; without internet nothing shows here.
  function loadOnline() {
    var o;
    try { o = JSON.parse(A.online()); } catch (e) { return; }
    if (!window.HarfixOnline) return;
    var p = HarfixOnline.plan(o.config, { platform: o.platform, version: o.version, installDays: o.install_days,
      fresh: o.fresh, dismissed: o.dismissed });
    HarfixOnline.render(p, { top: $("online-top"), ad: $("online-ad"), L: L,
      open: function (url) { A.openUrl(url); },
      dismiss: function (id) { A.dismiss(id); } });
    if (p.banner) A.showBanner(p.banner.provider, p.banner.id); else A.hideBanner();
  }

  function paintAll() {
    document.querySelectorAll(".targets button").forEach(function (b) {
      b.classList.toggle("on", b.dataset.target === target());
    });
    render();
    paintKeyboard();
  }

  // --- learn ------------------------------------------------------------------------------------
  var LETTERS = [
    { big: "Ö ö", from: "oʻ · ў", ex: "Oʻzbekiston" }, { big: "Ğ ğ", from: "gʻ · ғ", ex: "togʻ" },
    { big: "Ş ş", from: "sh · ш", ex: "shahar" }, { big: "Ç ç", from: "ch · ч", ex: "choʻl" }
  ];
  $("letters").innerHTML = LETTERS.map(function (l) {
    return '<div class="letter"><div class="big">' + l.big + '</div><div class="from">' + l.from +
      '</div><div class="ex">' + esc(l.ex) + " → <b>" + esc(H.convert(l.ex)) + "</b></div></div>";
  }).join("");
  function ex(words) {
    return words.map(function (w) { return esc(w) + " → <b>" + esc(H.convert(w)) + "</b>"; }).join(", ");
  }
  $("rules").innerHTML = [
    [L("Katta harflar"), ex(["SHAHAR", "Shahar", "OʻZBEKISTON"])],
    [L("Tutuq belgisi (ʼ) saqlanadi"), ex(["maʼno", "sheʼr"])],
    [L("s va h alohida tovush boʻlsa, oraga tutuq qoʻyiladi"), ex(["Isʼhoq"])],
    [L("ng oʻzgarmaydi"), ex(["tong", "ming"])],
    [L("Kirilldan"), ex(["Тошкент", "Ўзбекистон"])]
  ].map(function (r) { return "<li>" + r[0] + ": " + r[1] + "</li>"; }).join("");

  // --- quiz ---------------------------------------------------------------------------------------
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
    var best = A.saveQuiz(quiz.score);
    $("qbar").style.width = "100%";
    $("qnum").textContent = L("Test tugadi");
    $("qtext").innerHTML = '<div class="final">' + (k === 1 ? "🏆" : k >= 0.8 ? "🥇" : k >= 0.6 ? "🥈" : "📚") + "</div>";
    $("qopts").innerHTML = "";
    $("qexpl").textContent = L("Natija: " + quiz.score + " / " + quiz.qs.length + " · eng yaxshi: " + best);
    $("qnext").textContent = L("Qaytadan boshlash");
  });

  // --- invite to the Harfix keyboard, keyboard background, personal dictionary, feedback --------
  var onboardLater = false; // "Keyinroq": not again until the app is opened next time
  function paintExtras() {
    var k = state.keyboard || {};
    $("kbd-onboard").hidden = onboardLater || !!k.selected;
    $("ob-step1").classList.toggle("done", !!k.enabled);
    $("ob-enable").textContent = k.enabled ? L("✓ Yoqilgan") : L("Yoqish");
    $("ob-choose").disabled = !k.enabled;
    $("bg-clear").hidden = !state.keyboard_bg;
    paintDict();
  }
  var basePaintAll = paintAll;
  paintAll = function () { basePaintAll(); paintExtras(); };
  $("ob-enable").addEventListener("click", function () { A.keyboardSettings(); });
  $("ob-choose").addEventListener("click", function () { A.keyboardPicker(); });
  $("ob-later").addEventListener("click", function () { onboardLater = true; $("kbd-onboard").hidden = true; });
  $("bg-pick").addEventListener("click", function () { if (A.pickKeyboardBackground) A.pickKeyboardBackground(); });
  $("bg-clear").addEventListener("click", function () {
    if (A.clearKeyboardBackground) A.clearKeyboardBackground();
    refresh();
  });
  $("feedback").addEventListener("click", function () { A.openUrl("https://t.me/a33208"); });

  function paintDict() {
    var words = [];
    try { words = JSON.parse(A.userDict ? A.userDict() : "[]"); } catch (e) { words = []; }
    var box = $("dict-list");
    box.innerHTML = "";
    words.forEach(function (w) {
      var b = document.createElement("button");
      b.type = "button";
      b.className = "chip";
      b.textContent = w + "  ✕";
      b.addEventListener("click", function () { A.removeUserWord(w); paintDict(); });
      box.appendChild(b);
    });
    if (!words.length) box.innerHTML = '<span class="hint">' + esc(L("Hozircha boʻsh")) + "</span>";
  }
  $("dict-form").addEventListener("submit", function (e) {
    e.preventDefault();
    var v = $("dict-input").value.trim();
    if (!v || !A.addUserWord) return;
    if (A.addUserWord(v)) {
      $("dict-input").value = "";
      paintDict();
      toast(L("Lugʻatga qoʻshildi"));
    } else {
      toast(L("Bu soʻz emas: faqat harflar (2–30 ta)"));
    }
  });

  paintAll();
  loadOnline();
  A.ready();
})();
