// Run with Playwright installed: node tools/test_anki_tap.cjs
const { chromium } = require('playwright');
const { readFileSync } = require('node:fs');
const assert = require('node:assert/strict');
// A card as AnkiDroid shows it: the note type's CSS first (the app puts the code at its end, AnkiCss), then the
// card, then the template's loader block, which runs the code from the CSS.
const template = readFileSync('app/src/main/assets/anki-loader.html', 'utf8');
const code = readFileSync('app/src/main/assets/anki-tap.js');
const css = '.card{font-size:20px}\n/* 한자 바로보기 코드: */\n:root{--hjtap-code:"' + code.toString('base64') + '";}\n/* 한자 바로보기 코드 끝 */';
(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage({ userAgent: 'Android AnkiDroid' });
    const cdp = await page.context().newCDPSession(page);
    await cdp.send('Page.enable');
    let lastNav = null;
    cdp.on('Page.frameRequestedNavigation', ev => {
      if (ev.url.startsWith('intent://') || ev.url.startsWith('missing-user-action:')) lastNav = ev.url;
    });
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    let pageHtml = '', svg = '';
    await page.route('http://card.test/**', route => {
      const url = route.request().url();
      if (url.includes('.svg')) return route.fulfill({ contentType: 'image/svg+xml', body: svg });
      return route.fulfill({ contentType: 'text/html; charset=utf-8', body: pageHtml });
    });
    async function captureNav(fn) {
      lastNav = null;
      await fn();
      await page.waitForTimeout(30);
      if (!lastNav) return null;
      if (lastNav.startsWith('missing-user-action:')) return { missing: lastNav };
      const params = new URL(lastNav).searchParams;
      return { q: params.get('q'), s: params.get('s') };
    }
    async function card(html, wait = 100) {
      pageHtml = '<style>' + css + '</style>' + html + template;
      await page.goto('http://card.test/card.html');
      await page.waitForFunction(() => window.__hjtapRun);
      await page.waitForTimeout(wait);
    }
    async function tap(text, index = 0) {
      const res = await captureNav(() => page.evaluate(({text, index}) => {
        const w = [...document.querySelectorAll('.hjtap')].filter(w => w.textContent === text)[index];
        if (!w) throw new Error('missing word: ' + text);
        w.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
      }, {text, index}));
      if (!res) throw new Error('no lookup opened: ' + text);
      return res;
    }
    await card('<div id="qa"><p>今日は雨です。</p><p><ruby>学校<rt>がっこう</rt></ruby>へ行きます。</p><a>既存リンク</a><button>漢字</button></div>');
    assert.deepEqual(await tap('学校'), { q: '学校', s: '学校へ行きます。' });
    assert.equal(await page.locator('rt .hjtap, button .hjtap, a .hjtap, .hjtap .hjtap').count(), 0);
    // Words must not be links, or a long press opens the WebView link preview instead of selecting text.
    assert.equal(await page.locator('a.hjtap, .hjtap a, .hjtap[href]').count(), 0);

    await card('<div>' + '前の文です。'.repeat(60) + '所はここです。次の文。</div>');
    assert.deepEqual(await tap('所'), { q: '所', s: '所はここです。' });
    await card('<div>生が好き。生ビールを飲む。</div>');
    assert.equal((await tap('生', 1)).s, '生ビールを飲む。');
    await card('<div>' + 'あ'.repeat(500) + '𠀋です' + 'い'.repeat(500) + '</div>');
    const long = await tap('𠀋');
    assert.ok(long.s.includes('𠀋'));
    assert.ok(Array.from(long.s).length <= 300);
    assert.ok(!/[\uD800-\uDBFF](?![\uDC00-\uDFFF])/.test(long.s));

    // Anki replaces #qa in the same WebView; newly inserted text must work without duplicate links.
    await page.evaluate(() => { document.body.innerHTML = '<div id="qa">電気です。<ruby>明日<rt>あした</rt></ruby></div>'; });
    await page.waitForTimeout(100);
    assert.equal((await tap('電気')).s, '電気です。');
    await page.evaluate(() => window.__hjtapRun());
    await page.waitForTimeout(100);
    assert.equal(await page.locator('.hjtap .hjtap').count(), 0);
    const count = await page.locator('.hjtap').count();
    await page.evaluate(() => {
      window.flipCount = 0;
      document.body.addEventListener('click', () => window.flipCount++);
    });
    await tap('明日');
    assert.equal(await page.evaluate(() => window.flipCount), 0);
    assert.equal(await page.locator('.hjtap').count(), count);

    // Selection chip is recreated when Anki replaces the card, and furigana stays out of its query.
    async function selectAll() {
      await page.evaluate(() => {
        const range = document.createRange();
        range.selectNodeContents(document.querySelector('#qa'));
        const sel = window.getSelection();
        sel.removeAllRanges(); sel.addRange(range);
        document.dispatchEvent(new Event('selectionchange'));
      });
      await page.waitForTimeout(350);
      return page.locator('a.hjchip7').getAttribute('href');
    }
    assert.ok((await selectAll()).includes(encodeURIComponent('電気です。明日')));
    await page.evaluate(() => { document.body.innerHTML = '<div id="qa">案内です。</div>'; });
    await page.waitForTimeout(100);
    assert.ok((await selectAll()).includes(encodeURIComponent('案内です。')));
    assert.equal(await page.locator('a.hjchip7').count(), 1);
    // Stroke-order drawings have no text: tapping one looks up the kanji it draws, without flipping the card.
    async function tapDrawing(selector) {
      let flipped = 0;
      const res = await captureNav(async () => {
        flipped = await page.evaluate((selector) => {
          let f = 0;
          const onClick = () => f++;
          document.body.addEventListener('click', onClick);
          try { document.querySelector(selector).dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true })); }
          finally { document.body.removeEventListener('click', onClick); }
          return f;
        }, selector);
      });
      return { q: res ? res.q : null, flipped };
    }
    const box = 'width="200" height="200" viewBox="0 0 200 200"';
    // An unnamed drawing above the meaning line (the deck in the user's screenshot).
    await card('<div id="qa"><div>JLPT 기초한자</div><svg id="d" ' + box + '><path id="p" d="M10 10L190 190"/></svg><div>반(半) 반</div><button>획순 리플레이</button></div>');
    assert.deepEqual(await tapDrawing('#p'), { q: '半', flipped: 0 });
    // Several kanji on the card: the first one written after the drawing.
    await card('<div id="qa">例: 一半<svg id="d" ' + box + '><path id="p" d="M0 0L9 9"/></svg><div>半分 はんぶん</div></div>');
    assert.equal((await tapDrawing('#p')).q, '半');
    // KanjiVG and AnimCJK markup name the kanji themselves.
    await card('<div id="qa">学校<svg ' + box + ' xmlns="http://www.w3.org/2000/svg"><g id="kvg:StrokePaths_0534a"><path id="p" d="M0 0L9 9"/></g></svg></div>');
    assert.equal((await tapDrawing('#p')).q, '半');
    await card('<div id="qa">学校<svg ' + box + '><path id="z21322d1" d="M0 0L9 9"/></svg></div>');
    assert.equal((await tapDrawing('#z21322d1')).q, '半');
    await card('<div id="qa">学校<canvas id="c" width="200" height="200" data-char="半"></canvas></div>');
    assert.equal((await tapDrawing('#c')).q, '半');
    // A plain picture, a small icon and an icon inside a button are left alone.
    await card('<div id="qa">半<img id="i" width="200" height="200" src="data:image/gif;base64,R0lGODlhAQABAAAAACw="></div>');
    assert.deepEqual(await tapDrawing('#i'), { q: null, flipped: 1 });
    await card('<div id="qa">半<svg id="s" width="20" height="20"><path d="M0 0L9 9"/></svg><button><svg id="b" ' + box + '></svg></button></div>');
    assert.equal((await tapDrawing('#s')).q, null);
    assert.equal((await tapDrawing('#b')).q, null);

    // The 'JLPT 기초한자' deck: an <object> loads an AnimCJK SVG file (kanji_<decimal code>.svg) and a real tap lands
    // inside that file unless the template lets it through. Tap it with the mouse at its position, as a finger would.
    svg = '<svg id="z21322" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path id="z21322d1" d="M0 0H1024V1024H0Z"/></svg>';
    await card('<style>.stroke-svg{width:120px;height:120px;margin:25px 0}</style><div id="qa"><div class="kanji-theme">JLPT 기초한자</div>' +
      '<div class="stroke-container"><object class="stroke-svg" data="kanji_21322.svg?cache=1" type="image/svg+xml"></object></div>' +
      '<hr><div class="meaning">반 반</div><button class="replay-btn">획순 리플레이</button></div>', 300);
    const at = await page.locator('object').boundingBox();
    const objTap = await captureNav(() => page.mouse.click(at.x + at.width / 2, at.y + at.height / 2));
    assert.equal(objTap && objTap.q, '半');

    // Regression #1: Bluetooth keyboard Q/W on drawing-only card (no kanji text at all, like JLPT 기초한자 front).
    const drawingAction = await captureNav(() => cdp.send('Runtime.evaluate', {
      expression: 'javascript: window.userJs1();',
      userGesture: false,
    }));
    assert.deepEqual(drawingAction, { q: '半', s: null });
    await page.evaluate(() => window.userJs2());
    assert.equal(await page.locator('object.hjkey').count(), 1);

    // A deck's own script copies its markup (JLPT MAX 덱 draws its pitch-accent row from innerHTML): the copy
    // has none of the first word's listeners and must still open the lookup.
    await card('<div id="qa"><div id="src">カメラを買う。</div><div id="copy"></div></div>');
    await page.evaluate(() => { document.querySelector('#copy').innerHTML = document.querySelector('#src').innerHTML; });
    await page.waitForTimeout(100);
    assert.equal(await page.locator('#copy .hjtap').count(), 2);
    assert.equal(await page.locator('.hjtap .hjtap').count(), 0);
    assert.deepEqual(await tap('カメラ', 1), { q: 'カメラ', s: 'カメラを買う。' });
    // AnkiDroid's study screen skips its tap gestures (flip, rate) on elements marked 'tappable'.
    assert.equal(await page.locator('.hjtap:not(.tappable)').count(), 0);
    // A word copied into a button belongs to the button.
    await card('<div id="qa"><div id="src">学校</div><button id="b"></button></div>');
    await page.evaluate(() => { document.querySelector('#b').innerHTML = document.querySelector('#src').innerHTML; });
    const inButton = await captureNav(() => page.evaluate(() => {
      document.querySelector('#b .hjtap').dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    }));
    assert.equal(inButton, null);

    // A Bluetooth keyboard: AnkiDroid's 사용자 행동 1–3 call userJs1–3 (no tap, so no click event / no user gesture).
    async function action(n) {
      return captureNav(() => cdp.send('Runtime.evaluate', {
        expression: `window.userJs${n}()`,
        userGesture: false,
      }));
    }
    const picked = () => page.evaluate(() => [...document.querySelectorAll('.hjkey')].map(w => w.textContent || w.nodeName.toLowerCase()));
    await card('<div id="qa"><p>今日は雨です。</p><p style="display:none">隠れた</p><p>学校へ行きます。</p><button>漢字</button></div>');
    assert.deepEqual(await action(1), { q: '今日', s: '今日は雨です。' });
    assert.deepEqual(await picked(), []);
    await action(2);
    assert.deepEqual(await picked(), ['今日']);
    await action(2);
    assert.deepEqual(await picked(), ['雨']);
    assert.deepEqual(await action(1), { q: '雨', s: '今日は雨です。' });
    await action(2); await action(2);
    assert.deepEqual(await picked(), ['行']);
    await action(2);
    assert.deepEqual(await picked(), ['今日']);
    await action(3);
    assert.deepEqual(await picked(), ['行']);
    // Regression #2: mixed text + stroke SVG + plain img + small icon + button SVG cycles only real targets.
    await card('<div id="qa"><p>学校</p><svg id="d" ' + box + '><path id="z21322d1" d="M0 0L9 9"/></svg>' +
      '<img width="200" height="200" src="data:image/gif;base64,R0lGODlhAQABAAAAACw=">' +
      '<svg width="20" height="20"><path d="M0 0L9 9"/></svg><button><svg ' + box + '></svg></button></div>');
    await action(2);
    assert.deepEqual(await picked(), ['学校']);
    await action(2);
    assert.deepEqual(await picked(), ['svg']);
    assert.deepEqual(await action(1), { q: '半', s: null });
    await action(2);
    assert.deepEqual(await picked(), ['学校']);
    // Regression #3: immediate userJs1() right after card swap (0ms before 30ms scan timer).
    const immediate = await captureNav(() => page.evaluate(() => {
      document.body.innerHTML = '<div id="qa">電車に乗る。</div>';
      window.__hjtapRun();
      window.userJs1();
    }));
    assert.deepEqual(immediate, { q: '電車', s: '電車に乗る。' });
    assert.deepEqual(await picked(), []);
    // Regression #4: userJs1 restored on __hjtapRun() after being cleared, while a deck's own function is preserved.
    await page.evaluate(() => { window.userJs1 = null; window.__hjtapRun(); });
    assert.deepEqual(await action(1), { q: '電車', s: '電車に乗る。' });
    await card('<script>window.userJs1 = function () { window.deckAction = 1; };</script><div id="qa">電車</div>');
    await page.evaluate(() => window.userJs1());
    assert.equal(await page.evaluate(() => window.deckAction), 1);
    assert.equal(await page.evaluate(() => typeof window.userJs2), 'function');

    await card('<div id="qa">学校</div>');
    // The template runs again for the next card in the same page: the running code rescans, nothing is loaded again.
    await page.evaluate(() => {
      document.querySelector('#qa').innerHTML = '電話';
      const s = document.createElement('script');
      s.textContent = [...document.querySelectorAll('script:not([src])')].pop().textContent;
      document.body.appendChild(s);
    });
    await page.waitForTimeout(100);
    assert.equal(await page.locator('#qa .hjtap').count(), 1);
    assert.equal(await page.locator('#hjtap-style').count(), 1);

    // A large template: the user pastes the loader at the very top, an old full block may stay further down.
    pageHtml = '<style>' + css + '</style>' + template + '<div id="qa">学校へ行く。</div>' +
      '<script>(function () { if (window.__hjtap7) { window.__hjtap7(); return; } window.oldRan = 1; })();</script>';
    await page.goto('http://card.test/card.html');
    await page.waitForTimeout(150);
    assert.deepEqual(await tap('学校'), { q: '学校', s: '学校へ行く。' });
    assert.equal(await page.evaluate(() => window.oldRan), undefined);
    assert.equal(await page.locator('.hjtap .hjtap').count(), 0);

    // Regression #5: legacy v6 runs BEFORE v9 loader (bottom paste / SPA cross-card upgrade) — v9 must still register
    // userJs1..3 and .hjkey CSS without duplicating click listeners or hiding v6's .hjchip6.
    pageHtml = '<style>' + css + '</style><div id="qa"><span class="hjtap">天気</span></div>' +
      '<a class="hjchip hjchip6" style="display:block">한자</a>' +
      '<script>window.v6Clicks = 0; window.__hjtap6 = function () {}; ' +
      'document.addEventListener("click", function (e) { if (e.target.closest(".hjtap")) window.v6Clicks++; }, true);</script>' +
      template;
    await page.goto('http://card.test/card.html');
    await page.waitForTimeout(100);
    assert.deepEqual(await action(1), { q: '天気', s: '天気' });
    const chipDisplay = await page.evaluate(() => getComputedStyle(document.querySelector('.hjchip6')).display);
    assert.equal(chipDisplay, 'block');
    const dupNav = await captureNav(() => page.evaluate(() => {
      document.querySelector('.hjtap').dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    }));
    assert.equal(dupNav, null);
    assert.equal(await page.evaluate(() => window.v6Clicks), 1);

    // Regression #6: WebView keydown fallback (Korean IME key='ㅂ'/'ㅈ' with code='KeyQ'/'KeyW', ignoring inputs/modals).
    await card('<div id="qa"><p>今日は雨です。</p><input id="inp"><div role="dialog" id="dlg" hidden></div></div>');
    await page.evaluate(() => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ㅈ', code: 'KeyW', bubbles: true, cancelable: true })));
    assert.deepEqual(await picked(), ['今日']);
    await page.evaluate(() => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ㅈ', code: 'KeyW', bubbles: true, cancelable: true })));
    assert.deepEqual(await picked(), ['雨']);
    await page.evaluate(() => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ㅉ', code: 'KeyW', shiftKey: true, bubbles: true, cancelable: true })));
    assert.deepEqual(await picked(), ['今日']);
    const kdOpen = await captureNav(() => page.evaluate(() =>
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ㅂ', code: 'KeyQ', bubbles: true, cancelable: true }))
    ));
    assert.deepEqual(kdOpen, { q: '今日', s: '今日は雨です。' });
    // Ignored inside input or visible dialog.
    const inInput = await captureNav(() => page.evaluate(() =>
      document.querySelector('#inp').dispatchEvent(new KeyboardEvent('keydown', { key: 'q', code: 'KeyQ', bubbles: true, cancelable: true }))
    ));
    assert.equal(inInput, null);
    const inDialog = await captureNav(() => page.evaluate(() => {
      document.querySelector('#dlg').hidden = false;
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'q', code: 'KeyQ', bubbles: true, cancelable: true }));
    }));
    assert.equal(inDialog, null);

    // A note type whose CSS has no code: the loader does nothing and breaks nothing.
    pageHtml = '<div id="qa">学校</div>' + template;
    await page.goto('http://card.test/card.html');
    await page.waitForTimeout(100);
    assert.equal(await page.locator('.hjtap').count(), 0);

    assert.deepEqual(errors, []);
    console.log('PASS: copied words, furigana, long/repeated contexts, supplementary kanji, card replacement, idempotency, click isolation, selection chip, stroke drawings, keyboard actions (incl. drawings, 0ms scan, v6 upgrade, keydown fallback)');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
