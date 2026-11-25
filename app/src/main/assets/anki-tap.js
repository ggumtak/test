// 한자 바로보기 Anki 코드 v9: 한자 바로보기 앱이 노트 유형의 CSS 끝(--hjtap-code)에 넣고, 카드 템플릿의 로더가 읽어 실행합니다.
(window.__hjCode = window.__hjCode || {})[9] = function () {
  if (window.__hjtapVer >= 9 && window.__hjtapRun) { window.__hjtapRun(); return; }
  // An older version (v6 without keyboard actions, v7, or v8) may already hold the tap/observer listeners in this
  // WebView (large template pasted at the bottom, or an earlier card in the new study screen SPA): keep its tap
  // listeners so taps never fire twice, while still upgrading keyboard shortcuts and highlight styling.
  var prevRun = window.__hjtapRun || window.__hjtap7 || window.__hjtap6 || null;
  var KEY_CSS = '.hjkey{outline:2px solid #B84830;outline-offset:2px;border-radius:3px;} .hjtap.hjkey{outline-offset:1px;background:rgba(184,72,48,.14);}';
  var CSS = '.hjtap{color:inherit;} a.hjchip{position:fixed;z-index:2147483647;display:none;padding:9px 18px;border-radius:20px;background:#B84830; color:#fff!important;font:bold 16px sans-serif;text-decoration:none!important;box-shadow:0 2px 8px rgba(0,0,0,.3);} a.hjchip:not(.hjchip7){display:none!important;} ' + KEY_CSS + ' object[type="image/svg+xml"],object[data*=".svg"],embed[src*=".svg"]{pointer-events:none;}';
  function style() {
    var id = prevRun ? 'hjtap-key-style' : 'hjtap-style';
    var css = prevRun ? KEY_CSS : CSS;
    var s = document.getElementById(id);
    if (s) {
      if (s.textContent !== css) s.textContent = css;
      return;
    }
    s = document.createElement('style');
    s.id = id;
    s.textContent = css;
    (document.head || document.documentElement).appendChild(s);
  }
  var RUN = /[㐀-䶿一-鿿豈-﫿\u{20000}-\u{3134F}々]+|[ァ-ヺー]+/gu;
  var SKIP = {SCRIPT:1, STYLE:1, RT:1, RP:1, A:1, BUTTON:1, TEXTAREA:1, INPUT:1, SELECT:1, OPTION:1};
  var MAX_CONTEXT = 300;
  function url(q, s) {
    return 'intent://lookup?q=' + encodeURIComponent(q) + (s ? '&s=' + encodeURIComponent(s) : '') +
      '#Intent;scheme=hanja;package=com.ggumtak.hanja;end';
  }
  function skip(node) {
    for (var p = node.parentNode; p; p = p.parentNode) {
      if (SKIP[p.nodeName] || p.isContentEditable || p.hidden || (p.classList && p.classList.contains('hjtap'))) return true;
      if (p === document.body) break;
    }
    return false;
  }
  function textOf(node) {
    var copy = node.cloneNode(true);
    if (copy.querySelectorAll) copy.querySelectorAll('rt,rp,script,style,.hjchip').forEach(function (e) { e.remove(); });
    return copy.textContent.replace(/\s+/g, ' ').trim();
  }
  // Keep the tapped occurrence, even near the bottom of a long card or after repeated words.
  function sentence(node, selectedRange) {
    var box = (node.closest && node.closest('p,li,td,div')) || document.body;
    var before = document.createRange();
    before.selectNodeContents(box);
    if (selectedRange) before.setEnd(selectedRange.startContainer, selectedRange.startOffset);
    else before.setEndBefore(node);
    var prefix = textOf(before.cloneContents());
    var all = textOf(box);
    var q = selectedRange ? textOf(selectedRange.cloneContents()) : node.textContent;
    var at = all.indexOf(q, Math.max(0, prefix.length - 1));
    if (at < 0) at = all.indexOf(q);
    if (at < 0) return q;
    var left = at, right = at + q.length;
    while (left > 0 && !/[。！？!?]/.test(all[left - 1])) left--;
    while (right < all.length && !/[。！？!?]/.test(all[right])) right++;
    if (right < all.length) right++;
    var start = Array.from(all.slice(left, at));
    var end = Array.from(all.slice(at + q.length, right));
    var room = Math.max(0, MAX_CONTEXT - Array.from(q).length);
    var keepLeft = Math.min(start.length, Math.floor(room / 2));
    var keepRight = Math.min(end.length, room - keepLeft);
    keepLeft = Math.min(start.length, room - keepRight);
    return start.slice(start.length - keepLeft).join('') + q + end.slice(0, keepRight).join('');
  }
  // Matches AnkiDroid's own JS->native bridge (card.js / ankidroid-reviewer.js): avoids detached <a> click
  // deprecation and dispatches no synthetic click event to the card.
  function open(href) {
    window.location.href = href;
  }
  // 'tappable' is AnkiDroid's own mark for "a tap here is not a study gesture" (flip, rate, tap zones).
  function link(q) {
    var w = document.createElement('span');
    w.className = 'hjtap tappable';
    w.textContent = q;
    return w;
  }
  function wrap(root) {
    if (!root || !root.isConnected) return;
    var nodes = [];
    if (root.nodeType === 3) nodes.push(root);
    else {
      var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
      for (var n = walker.nextNode(); n; n = walker.nextNode()) nodes.push(n);
    }
    nodes.forEach(function (n) {
      RUN.lastIndex = 0;
      if (skip(n) || !RUN.test(n.nodeValue)) return;
      var text = n.nodeValue, frag = document.createDocumentFragment(), last = 0, m;
      RUN.lastIndex = 0;
      while ((m = RUN.exec(text))) {
        if (m.index > last) frag.appendChild(document.createTextNode(text.slice(last, m.index)));
        frag.appendChild(link(m[0]));
        last = m.index + m[0].length;
      }
      if (last < text.length) frag.appendChild(document.createTextNode(text.slice(last)));
      n.parentNode.replaceChild(frag, n);
    });
  }
  // A stroke-order drawing (SVG/canvas, as in decks with a 획순 replay) has no text to tap: find the kanji it draws.
  var ONE = /[㐀-䶿一-鿿豈-﫿\u{20000}-\u{3134F}]/u;
  function kanjiAt(code) {
    var ch = code > 0 && code <= 0x10FFFF ? String.fromCodePoint(code) : '';
    return ONE.test(ch) ? ch : '';
  }
  // Markup that names the drawn kanji: KanjiVG (kvg:element, kvg:0534a), AnimCJK (z21322d1), labels, file names.
  function named(e) {
    if (!e.attributes) return '';
    if (e.nodeName.toLowerCase() === 'title') { var t = e.textContent.match(ONE); if (t) return t[0]; }
    for (var i = 0; i < e.attributes.length; i++) {
      var name = e.attributes[i].name.toLowerCase(), v = e.attributes[i].value, m, k;
      if (/^(kvg:element|data-(char|kanji|character|hanzi)|aria-label|title|alt)$/.test(name)) {
        if ((m = v.match(ONE))) return m[0];
      } else if (name === 'id' || name === 'class') {
        if ((m = v.match(/kvg:(?:[A-Za-z]+_)?([0-9a-f]{5})(?![0-9a-f])/i)) && (k = kanjiAt(parseInt(m[1], 16)))) return k;
        if ((m = v.match(/(?:^|\s)z(\d{4,6})d\d/)) && (k = kanjiAt(+m[1]))) return k;
      } else if (/^(src|data|href|xlink:href)$/.test(name)) {
        try { v = decodeURIComponent(v); } catch (err) {}
        var file = v.split(/[?#]/)[0].split('/').pop();
        if ((m = file.match(ONE))) return m[0];
        // kanji_21322.svg (AnimCJK, decimal), 0534a.svg (KanjiVG, hex with a leading 0), u534a.svg.
        if ((m = file.match(/(?:^|[_-])([1-9]\d{3,5})(?=\.)/)) && (k = kanjiAt(+m[1]))) return k;
        if ((m = file.match(/(?:^|[_-])(?:u\+?)?([0-9a-f]{4,5})(?=[._-])/i)) && (k = kanjiAt(parseInt(m[1], 16)))) return k;
      }
    }
    return '';
  }
  function drawnKanji(el) {
    var k = '', p, i;
    for (p = el; p && p !== document.body && !k; p = p.parentNode) k = named(p);
    var inside = el.querySelectorAll ? el.querySelectorAll('*') : [];
    for (i = 0; i < inside.length && i < 400 && !k; i++) k = named(inside[i]);
    return k;
  }
  // Unnamed drawing: the card's only kanji, or else the first kanji written after the drawing (半 above 반(半) 반).
  function cardKanji(el) {
    var all = textOf(document.body).match(new RegExp(ONE.source, 'gu')) || [];
    var distinct = Array.from(new Set(all));
    if (distinct.length <= 1) return distinct[0] || '';
    var after = document.createRange();
    after.selectNodeContents(document.body);
    after.setStartAfter(el);
    var m = textOf(after.cloneContents()).match(ONE);
    return m ? m[0] : distinct[0];
  }
  function drawingKanji(el) {
    if (!el || (el.closest && el.closest('a,button,input,textarea,select,label,summary,.hjchip')) || skip(el)) return '';
    var box = el.getBoundingClientRect();
    if (box.width < 40 || box.height < 40) return '';
    var q = drawnKanji(el);
    if (!q && el.nodeName.toLowerCase() !== 'img') q = cardKanji(el);
    return q;
  }
  var pending = false, timer = null, roots = new Set(), watch = null;
  function scan() {
    pending = false;
    if (timer) { clearTimeout(timer); timer = null; }
    if (watch) watch.disconnect();
    var batch = Array.from(roots);
    roots.clear();
    batch.forEach(function (root) {
      if (!batch.some(function (other) { return other !== root && other.contains(root); })) wrap(root);
    });
    if (watch && document.body) watch.observe(document.body, {childList:true, subtree:true, characterData:true});
  }
  function schedule() {
    if (!pending) { pending = true; timer = setTimeout(scan, 30); }
  }
  function flushScan() {
    if (pending) scan();
    else if (document.body && !document.querySelector('.hjtap')) { roots.add(document.body); scan(); }
  }
  var chip = null, selTimer = null;
  function hideChip() { if (chip) chip.style.display = 'none'; }
  if (!prevRun) {
    // One listener for every word, not one per word: a deck's own script may rebuild its text by copying the
    // markup (JLPT MAX 덱's pitch-accent rows), and a copied word has no listener of its own.
    function wordTap(event) {
      var t = event.target, w = t && t.closest && t.closest('.hjtap');
      if (!w || w.closest('a,button,input,textarea,select,label,summary')) return false;
      var sel = window.getSelection();
      if (sel && !sel.isCollapsed) return true;
      // Opening the dictionary must not also flip or rate the Anki card.
      event.stopPropagation();
      open(url(w.textContent, sentence(w)));
      return true;
    }
    function drawingTap(event) {
      if (wordTap(event)) return;
      var t = event.target, el = null;
      for (var p = t; p && p !== document.body; p = p.parentNode) {
        var n = p.nodeName && p.nodeName.toLowerCase();
        if (n === 'svg' || n === 'canvas' || n === 'img' || n === 'object' || n === 'embed') el = p;
      }
      // An <object>/<embed> SVG lets taps through (see the style above): find it under the tap.
      if (!el) document.querySelectorAll('object,embed').forEach(function (o) {
        var r = o.getBoundingClientRect();
        if (!el && event.clientX >= r.left && event.clientX <= r.right && event.clientY >= r.top && event.clientY <= r.bottom) el = o;
      });
      if (!el) return;
      var sel = window.getSelection();
      if (sel && !sel.isCollapsed) return;
      var q = drawingKanji(el);
      if (!q) return;
      event.stopPropagation();
      open(url(q, ''));
    }
    document.addEventListener('click', drawingTap, true);
    watch = new MutationObserver(function (records) {
      records.forEach(function (r) {
        if (r.type === 'characterData') roots.add(r.target);
        else r.addedNodes.forEach(function (n) { roots.add(n); });
      });
      schedule();
    });
    function showChip() {
      var sel = window.getSelection();
      if (!sel || sel.isCollapsed || !sel.rangeCount) { hideChip(); return; }
      var range = sel.getRangeAt(0), q = Array.from(textOf(range.cloneContents())).slice(0, 100).join('');
      if (!/[\u3040-\u30FF㐀-䶿一-鿿豈-﫿\u{20000}-\u{3134F}]/u.test(q)) { hideChip(); return; }
      var node = range.startContainer.nodeType === 1 ? range.startContainer : range.startContainer.parentNode;
      if (node.closest('input,textarea,[contenteditable="true"]')) { hideChip(); return; }
      if (!chip || !chip.isConnected) {
        chip = document.createElement('a');
        chip.className = 'hjchip hjchip7';
        chip.textContent = '한자';
        chip.addEventListener('click', function (event) { event.stopPropagation(); setTimeout(hideChip, 300); });
        document.body.appendChild(chip);
      }
      chip.href = url(q, sentence(node, range));
      var rect = range.getBoundingClientRect();
      chip.style.display = 'block';
      var w = chip.offsetWidth || 72, h = chip.offsetHeight || 40;
      chip.style.left = Math.max(8, Math.min(window.innerWidth - w - 8, rect.left + rect.width / 2 - w / 2)) + 'px';
      var top = rect.bottom + 14;
      if (top + h > window.innerHeight - 8) top = Math.max(8, rect.top - h - 14);
      chip.style.top = top + 'px';
    }
    document.addEventListener('selectionchange', function () {
      clearTimeout(selTimer);
      selTimer = setTimeout(showChip, 250);
    });
    window.addEventListener('pagehide', hideChip);
    window.addEventListener('scroll', hideChip, true);
  }
  // A Bluetooth keyboard: AnkiDroid hands a key bound in 설정 > 컨트롤 > 사용자 행동 N to window.userJsN.
  // 1 opens the chosen word or stroke-order drawing (the first when none is chosen), 2 and 3 choose next/previous.
  var picked = null;
  function targets() {
    flushScan();
    var out = [];
    document.querySelectorAll('.hjtap,svg,canvas,img,object,embed').forEach(function (el) {
      if (el.classList && el.classList.contains('hjtap')) {
        if (!el.closest('a,button,input,textarea,select,label,summary') && el.getClientRects().length > 0) {
          out.push({el: el, word: true});
        }
        return;
      }
      if (el.parentElement && el.parentElement.closest('svg,canvas,img,object,embed')) return;
      var q = drawingKanji(el);
      if (q) out.push({el: el, q: q});
    });
    return out;
  }
  function pick(step) {
    var list = targets();
    if (!list.length) return;
    var i = picked ? list.findIndex(function (t) { return t.el === picked; }) : -1;
    i = i < 0 ? (step > 0 ? 0 : list.length - 1) : (i + step + list.length) % list.length;
    if (picked) picked.classList.remove('hjkey');
    picked = list[i].el;
    picked.classList.add('hjkey');
    if (picked.scrollIntoView) picked.scrollIntoView({block: 'nearest'});
  }
  function openPicked() {
    var list = targets();
    var t = (picked && list.find(function (x) { return x.el === picked; })) || list[0];
    if (!t) return;
    open(t.word ? url(t.el.textContent, sentence(t.el)) : url(t.q, ''));
  }
  function installKeys() {
    [openPicked, function () { pick(1); }, function () { pick(-1); }].forEach(function (f, i) {
      var name = 'userJs' + (i + 1);
      // A deck's own user action stays the deck's.
      if (typeof window[name] === 'function' && !window[name].__hj) return;
      f.__hj = true;
      window[name] = f;
    });
  }
  // Fallback when the WebView itself receives hardware key events (e.g. AnkiDroid 2.22 new study screen
  // webviewHasFocus after tapping a button, or legacy WebView focus): matches physical KeyQ / KeyW regardless
  // of Korean/English IME while staying out of inputs, modals, and modified shortcuts.
  if (!window.__hjtapKeydown) {
    window.__hjtapKeydown = true;
    document.addEventListener('keydown', function (e) {
      if (e.defaultPrevented || e.repeat || e.ctrlKey || e.altKey || e.metaKey) return;
      var t = e.target;
      if (t && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.nodeName))) return;
      if (document.querySelector('[role="dialog"]:not([hidden])')) return;
      var code = e.code, key = (e.key || '').toLowerCase();
      if ((code === 'KeyQ' || key === 'q') && !e.shiftKey) {
        if (typeof window.userJs1 === 'function' && window.userJs1.__hj) {
          e.preventDefault(); e.stopPropagation(); openPicked();
        }
      } else if (code === 'KeyW' || key === 'w') {
        var name = e.shiftKey ? 'userJs3' : 'userJs2';
        if (typeof window[name] === 'function' && window[name].__hj) {
          e.preventDefault(); e.stopPropagation(); pick(e.shiftKey ? -1 : 1);
        }
      }
    });
  }
  window.__hjtapVer = 10;
  window.__hjtapRun = function () {
    // Run before the page body exists (the loader at the very top of a template shown as a page): once it does.
    if (!document.body) { document.addEventListener('DOMContentLoaded', window.__hjtapRun, {once: true}); return; }
    style();
    installKeys();
    hideChip();
    if (picked && !picked.isConnected) picked = null;
    if (prevRun) { prevRun(); return; }
    roots.add(document.body);
    schedule();
  };
  // An older full code block still in the template (the loader pasted at the top of a large one, the old block
  // left further down) finds its own guard set and only rescans: one copy of the code, never two.
  if (!prevRun) window.__hjtap6 = window.__hjtap7 = function () { window.__hjtapRun(); };
  window.__hjtapRun();
};
