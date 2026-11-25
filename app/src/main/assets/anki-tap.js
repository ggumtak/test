// 한자 바로보기 Anki 코드 v8: 한자 바로보기 앱이 Anki 미디어에 _hanja-tap-8.js로 넣고, 카드 템플릿의 로더가 불러옵니다.
// 고칠 때는 번호를 올립니다(AnkiCode.VERSION, 파일 안의 [8]): Anki는 같은 이름에 다른 내용을 넣으면 이름을 바꿔 저장합니다.
(window.__hjCode = window.__hjCode || {})[8] = function () {
  if (window.__hjtapRun) { window.__hjtapRun(); return; }
  // A full code block of an earlier version already runs in this WebView (the template was just changed): it keeps it.
  if (window.__hjtap7) { window.__hjtap7(); return; }
  if (window.__hjtap6) { window.__hjtap6(); return; }
  var CSS = '.hjtap{color:inherit;} a.hjchip{position:fixed;z-index:2147483647;display:none;padding:9px 18px;border-radius:20px;background:#B84830; color:#fff!important;font:bold 16px sans-serif;text-decoration:none!important;box-shadow:0 2px 8px rgba(0,0,0,.3);} a.hjchip:not(.hjchip7){display:none!important;} .hjtap.hjkey{outline:2px solid #B84830;outline-offset:1px;border-radius:3px;background:rgba(184,72,48,.14);} object[type="image/svg+xml"],object[data*=".svg"],embed[src*=".svg"]{pointer-events:none;}';
  function style() {
    if (document.getElementById('hjtap-style')) return;
    var s = document.createElement('style');
    s.id = 'hjtap-style';
    s.textContent = CSS;
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
  // A detached link opens the intent the same way a tapped link does, without its click reaching the card.
  function open(href) {
    var a = document.createElement('a');
    a.href = href;
    a.click();
  }
  // 'tappable' is AnkiDroid's own mark for "a tap here is not a study gesture" (flip, rate, tap zones).
  function link(q) {
    var w = document.createElement('span');
    w.className = 'hjtap tappable';
    w.textContent = q;
    return w;
  }
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
  function wrap(root) {
    if (!root.isConnected) return;
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
    if (!el || (el.closest && el.closest('a,button,input,textarea,select,label,.hjchip')) || skip(el)) return;
    var sel = window.getSelection();
    if (sel && !sel.isCollapsed) return;
    var box = el.getBoundingClientRect();
    if (box.width < 40 || box.height < 40) return;
    var q = drawnKanji(el);
    if (!q && el.nodeName.toLowerCase() !== 'img') q = cardKanji(el);
    if (!q) return;
    event.stopPropagation();
    open(url(q, ''));
  }
  document.addEventListener('click', drawingTap, true);
  var pending = false, roots = new Set();
  var watch = new MutationObserver(function (records) {
    records.forEach(function (r) {
      if (r.type === 'characterData') roots.add(r.target);
      else r.addedNodes.forEach(function (n) { roots.add(n); });
    });
    schedule();
  });
  function scan() {
    pending = false;
    watch.disconnect();
    var batch = Array.from(roots);
    roots.clear();
    batch.forEach(function (root) {
      if (!batch.some(function (other) { return other !== root && other.contains(root); })) wrap(root);
    });
    if (document.body) watch.observe(document.body, {childList:true, subtree:true, characterData:true});
  }
  function schedule() {
    if (!pending) { pending = true; setTimeout(scan, 30); }
  }
  var chip = null, selTimer = null;
  function hideChip() { if (chip) chip.style.display = 'none'; }
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
  // A Bluetooth keyboard: AnkiDroid hands a key bound in 설정 > 컨트롤 > 사용자 행동 N to window.userJsN.
  // 1 opens the chosen word (the first word when none is chosen), 2 and 3 choose the next or previous word.
  var picked = null;
  function words() {
    return Array.from(document.querySelectorAll('.hjtap')).filter(function (w) {
      return !w.closest('a,button,input,textarea,select,label,summary') && w.getClientRects().length > 0;
    });
  }
  function pick(step) {
    var list = words();
    if (!list.length) return;
    var i = picked ? list.indexOf(picked) : -1;
    i = i < 0 ? (step > 0 ? 0 : list.length - 1) : (i + step + list.length) % list.length;
    if (picked) picked.classList.remove('hjkey');
    picked = list[i];
    picked.classList.add('hjkey');
    if (picked.scrollIntoView) picked.scrollIntoView({block: 'nearest'});
  }
  function openPicked() {
    var list = words(), w = picked && list.indexOf(picked) >= 0 ? picked : list[0];
    if (w) open(url(w.textContent, sentence(w)));
  }
  [openPicked, function () { pick(1); }, function () { pick(-1); }].forEach(function (f, i) {
    var name = 'userJs' + (i + 1);
    // A deck's own user action stays the deck's.
    if (typeof window[name] === 'function' && !window[name].__hj) return;
    f.__hj = true;
    window[name] = f;
  });
  window.__hjtapRun = function () {
    style();
    hideChip();
    if (picked && !picked.isConnected) picked = null;
    roots.add(document.body);
    schedule();
  };
  window.__hjtapRun();
};
