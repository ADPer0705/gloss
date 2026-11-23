(function () {
  var CACHE_KEY = "gloss-gh-meta-v1";
  var CACHE_TTL_MS = 5 * 60 * 1000;
  var REPO = "ADPer0705/gloss";

  var DEMO_PAIRS = {
    "en-es": {
      srcLabel: "English",
      srcCode: "eng_Latn",
      tgtLabel: "Spanish",
      tgtCode: "spa_Latn",
      scriptLabel: "Latin",
      srcWords: [
        { text: "Every", gloss: "cada", note: "Determiner · source token" },
        { text: "word", gloss: "palabra", note: "Noun · feminine singular" },
        { text: "stays", gloss: "permanece", note: "Verb · 3rd person singular" },
        { text: "private", gloss: "privada", note: "Adjective · agrees in gender" },
        { text: "on", gloss: "en", note: "Preposition" },
        { text: "your", gloss: "tu", note: "Possessive determiner" },
        { text: "phone.", punct: ".", gloss: "teléfono", note: "Noun · offline device" }
      ],
      tgtWords: [
        { text: "Cada", gloss: "every", note: "Determiner · invariable" },
        { text: "palabra", gloss: "word", note: "Feminine noun · default gloss" },
        { text: "permanece", gloss: "remains / stays", note: "3rd person present · permanecer" },
        { text: "privada", gloss: "private", note: "Feminine adjective matching palabra" },
        { text: "en", gloss: "on / in", note: "Preposition" },
        { text: "tu", gloss: "your", note: "Informal singular possessive" },
        { text: "teléfono.", punct: ".", gloss: "phone", note: "Masculine noun" }
      ],
      defaultSide: "tgt",
      defaultIndex: 1
    },
    "en-ja": {
      srcLabel: "English",
      srcCode: "eng_Latn",
      tgtLabel: "Japanese",
      tgtCode: "jpn_Jpan",
      scriptLabel: "Kanji / Kana",
      srcWords: [
        { text: "Translate", gloss: "翻訳する", note: "Verb · imperative action" },
        { text: "freely", gloss: "自由に", note: "Adverb · without cloud latency" },
        { text: "without", gloss: "なしで", note: "Preposition" },
        { text: "internet.", punct: ".", gloss: "インターネット", note: "Katakana loanword noun" }
      ],
      tgtWords: [
        { text: "インターネット", gloss: "internet", note: "Katakana loanword · WordSegmenter CJK unit" },
        { text: "なしで", gloss: "without", note: "Postpositional particle phrase" },
        { text: "自由に", gloss: "freely", note: "Na-adjective adverbial form" },
        { text: "翻訳", gloss: "translation", note: "Sino-Japanese compound noun" },
        { text: "できます。", punct: "。", gloss: "can do", note: "Polite potential verb · dekiru" }
      ],
      defaultSide: "tgt",
      defaultIndex: 3
    },
    "fr-en": {
      srcLabel: "French",
      srcCode: "fra_Latn",
      tgtLabel: "English",
      tgtCode: "eng_Latn",
      scriptLabel: "Latin",
      srcWords: [
        { text: "Apprenez", gloss: "learn", note: "2nd person plural · apprendre" },
        { text: "une", gloss: "a", note: "Feminine indefinite article" },
        { text: "nouvelle", gloss: "new", note: "Feminine adjective before langue" },
        { text: "langue", gloss: "language", note: "Feminine noun" },
        { text: "partout.", punct: ".", gloss: "everywhere", note: "Adverb of place" }
      ],
      tgtWords: [
        { text: "Learn", gloss: "apprenez", note: "Verb · base/imperative form" },
        { text: "a", gloss: "une", note: "Indefinite article" },
        { text: "new", gloss: "nouvelle", note: "Adjective" },
        { text: "language", gloss: "langue", note: "Noun · primary focus" },
        { text: "everywhere.", punct: ".", gloss: "partout", note: "Adverb" }
      ],
      defaultSide: "src",
      defaultIndex: 2
    }
  };

  function setupThemeToggle() {
    var btn = document.getElementById("theme-toggle");
    if (!btn) return;
    btn.addEventListener("click", function () {
      var root = document.documentElement;
      var next = root.getAttribute("data-theme") === "light" ? "dark" : "light";
      root.setAttribute("data-theme", next);
      try {
        localStorage.setItem("gloss-theme", next);
      } catch (e) {}
    });
  }

  function setupMobileMenu() {
    var burger = document.getElementById("burger-toggle");
    var drawer = document.getElementById("mobile-drawer");
    if (!burger || !drawer) return;

    burger.addEventListener("click", function () {
      var isOpen = !drawer.hasAttribute("hidden");
      if (isOpen) {
        drawer.setAttribute("hidden", "");
        burger.setAttribute("aria-expanded", "false");
      } else {
        drawer.removeAttribute("hidden");
        burger.setAttribute("aria-expanded", "true");
      }
    });

    drawer.querySelectorAll("a").forEach(function (link) {
      link.addEventListener("click", function () {
        drawer.setAttribute("hidden", "");
        burger.setAttribute("aria-expanded", "false");
      });
    });
  }

  function setupInteractiveDemo() {
    var activePairKey = "en-es";
    var selectedSide = DEMO_PAIRS[activePairKey].defaultSide;
    var selectedIndex = DEMO_PAIRS[activePairKey].defaultIndex;

    var srcLabel = document.getElementById("demo-src-label");
    var srcCode = document.getElementById("demo-src-code");
    var tgtLabel = document.getElementById("demo-tgt-label");
    var tgtCode = document.getElementById("demo-tgt-code");
    var pairBadge = document.getElementById("demo-pair-badge");
    var srcWordsEl = document.getElementById("demo-src-words");
    var tgtWordsEl = document.getElementById("demo-tgt-words");

    var directionEl = document.getElementById("gloss-direction");
    var scriptEl = document.getElementById("gloss-script");
    var selectedWordEl = document.getElementById("gloss-selected-word");
    var meaningEl = document.getElementById("gloss-meaning");
    var noteEl = document.getElementById("gloss-note");

    if (!srcWordsEl || !tgtWordsEl) return;

    function updatePopover(pair) {
      var list = selectedSide === "src" ? pair.srcWords : pair.tgtWords;
      var item = list[selectedIndex] || list[0];
      var cleanWord = item.punct ? item.text.slice(0, -item.punct.length) : item.text;

      if (selectedSide === "src") {
        directionEl.textContent = "SOURCE \u2192 TARGET GLOSS";
        scriptEl.textContent = pair.srcLabel + " \u00b7 " + pair.scriptLabel;
      } else {
        directionEl.textContent = "REVERSE WORD GLOSS";
        scriptEl.textContent = pair.tgtLabel + " \u00b7 " + pair.scriptLabel;
      }

      selectedWordEl.textContent = cleanWord;
      meaningEl.textContent = item.gloss;
      noteEl.textContent = item.note;
    }

    function renderFlow(container, words, side, pair) {
      container.innerHTML = "";
      words.forEach(function (entry, idx) {
        var clean = entry.punct ? entry.text.slice(0, -entry.punct.length) : entry.text;
        var btn = document.createElement("button");
        btn.type = "button";
        btn.className = "demo-word" + (selectedSide === side && selectedIndex === idx ? " is-selected" : "");
        btn.textContent = clean;
        btn.setAttribute("aria-pressed", String(selectedSide === side && selectedIndex === idx));
        btn.addEventListener("click", function () {
          selectedSide = side;
          selectedIndex = idx;
          render();
        });
        container.appendChild(btn);

        if (entry.punct) {
          var p = document.createElement("span");
          p.className = "demo-punct";
          p.textContent = entry.punct;
          container.appendChild(p);
        }
      });
      void pair;
    }

    function render() {
      var pair = DEMO_PAIRS[activePairKey];
      srcLabel.textContent = pair.srcLabel;
      srcCode.textContent = pair.srcCode;
      tgtLabel.textContent = pair.tgtLabel;
      tgtCode.textContent = pair.tgtCode;
      pairBadge.textContent = pair.srcCode + " \u21c4 " + pair.tgtCode;

      renderFlow(srcWordsEl, pair.srcWords, "src", pair);
      renderFlow(tgtWordsEl, pair.tgtWords, "tgt", pair);
      updatePopover(pair);
    }

    document.querySelectorAll(".pair-tab").forEach(function (tab) {
      tab.addEventListener("click", function () {
        var key = tab.getAttribute("data-pair");
        if (!DEMO_PAIRS[key]) return;
        activePairKey = key;
        selectedSide = DEMO_PAIRS[key].defaultSide;
        selectedIndex = DEMO_PAIRS[key].defaultIndex;

        document.querySelectorAll(".pair-tab").forEach(function (t) {
          var active = t === tab;
          t.classList.toggle("is-active", active);
          t.setAttribute("aria-selected", String(active));
        });
        render();
      });
    });

    render();
  }

  function applyGitHubStats(data) {
    if (!data) return;
    var downloadBtn = document.getElementById("download-btn");
    var navVer = document.getElementById("nav-version-label");
    var navVerLink = document.getElementById("nav-version");
    var starsLabel = document.getElementById("nav-stars-label");
    var totalDownloadsEl = document.getElementById("total-downloads");
    var installVerEl = document.getElementById("install-version");

    if (data.latestTag) {
      if (navVer) navVer.textContent = data.latestTag;
      if (installVerEl) installVerEl.textContent = data.latestTag;
      if (navVerLink && data.releaseUrl) navVerLink.href = data.releaseUrl;
      if (downloadBtn) {
        if (data.apkUrl) downloadBtn.href = data.apkUrl;
        var mb = data.apkSize ? " \u00b7 " + (data.apkSize / 1048576).toFixed(1) + " MB" : "";
        downloadBtn.textContent = "Download " + data.latestTag + " APK" + mb;
      }
    }

    if (typeof data.totalApkDownloads === "number" && totalDownloadsEl) {
      totalDownloadsEl.textContent = data.totalApkDownloads.toLocaleString() + " downloads";
    }

    if (typeof data.stars === "number" && starsLabel) {
      starsLabel.textContent = data.stars.toLocaleString() + " stars";
    }
  }

  function loadGitHubStats() {
    try {
      var cachedRaw = sessionStorage.getItem(CACHE_KEY);
      if (cachedRaw) {
        var cached = JSON.parse(cachedRaw);
        if (cached && Date.now() - cached.ts < CACHE_TTL_MS) {
          applyGitHubStats(cached.payload);
          return;
        }
      }
    } catch (e) {}

    Promise.all([
      fetch("https://api.github.com/repos/" + REPO + "/releases?per_page=20").then(function (r) {
        if (!r.ok) throw new Error("releases status " + r.status);
        return r.json();
      }),
      fetch("https://api.github.com/repos/" + REPO).then(function (r) {
        if (!r.ok) return null;
        return r.json();
      }).catch(function () {
        return null;
      })
    ])
      .then(function (results) {
        var releases = Array.isArray(results[0]) ? results[0] : [];
        var repoInfo = results[1];
        var published = releases.filter(function (rel) {
          return rel && !rel.draft;
        });

        var totalApkDownloads = 0;
        var latestWithApk = null;
        var latestApk = null;

        published.forEach(function (rel) {
          var assets = Array.isArray(rel.assets) ? rel.assets : [];
          var apk = assets.find(function (a) {
            return a && typeof a.name === "string" && a.name.toLowerCase().endsWith(".apk");
          });
          assets.forEach(function (a) {
            if (a && typeof a.name === "string" && a.name.toLowerCase().endsWith(".apk")) {
              totalApkDownloads += Number(a.download_count || 0);
            }
          });
          if (!latestWithApk && apk) {
            latestWithApk = rel;
            latestApk = apk;
          }
        });

        var payload = {
          latestTag: latestWithApk ? latestWithApk.tag_name : null,
          releaseUrl: latestWithApk ? latestWithApk.html_url : null,
          apkUrl: latestApk ? latestApk.browser_download_url : null,
          apkSize: latestApk ? latestApk.size : 0,
          totalApkDownloads: totalApkDownloads,
          stars: repoInfo && typeof repoInfo.stargazers_count === "number" ? repoInfo.stargazers_count : null
        };

        applyGitHubStats(payload);
        try {
          sessionStorage.setItem(CACHE_KEY, JSON.stringify({ ts: Date.now(), payload: payload }));
        } catch (e) {}
      })
      .catch(function () {
        // Keep clean static fallback copy on rate-limit or offline preview
      });
  }

  setupThemeToggle();
  setupMobileMenu();
  setupInteractiveDemo();
  loadGitHubStats();
})();
