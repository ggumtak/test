// Run with Playwright installed: node tools/test_anki_tap.cjs
const { chromium } = require('playwright');
const { readFileSync } = require('node:fs');
const assert = require('node:assert/strict');
const template = readFileSync('app/src/main/assets/anki-tap.html', 'utf8');
(async () => {
  const browser = await chromium.launch({ headless: true, args: ['--no-sandbox'] });
  try {
    const page = await browser.newPage({ userAgent: 'Android AnkiDroid' });
    const errors = [];
    page.on('pageerror', e => errors.push(e.message));
    async function card(html) {
      await page.goto('about:blank');
      await page.setContent(html + template);
      await page.waitForTimeout(100);
    }
    async function tap(text, index = 0) {
      return page.evaluate(({text, index}) => {
        const w = [...document.querySelectorAll('.hjtap')].filter(w => w.textContent === text)[index];
        if (!w) throw new Error('missing word: ' + text);
        let opened = null;
        const click = HTMLAnchorElement.prototype.click;
        HTMLAnchorElement.prototype.click = function () { opened = this.href; };
        try { w.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true })); }
        finally { HTMLAnchorElement.prototype.click = click; }
        if (!opened) throw new Error('no lookup opened: ' + text);
        const params = new URL(opened).searchParams;
        return { q: params.get('q'), s: params.get('s') };
      }, {text, index});
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
    await page.evaluate(() => window.__hjtap6());
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
      return page.locator('a.hjchip6').getAttribute('href');
    }
    assert.ok((await selectAll()).includes(encodeURIComponent('電気です。明日')));
    await page.evaluate(() => { document.body.innerHTML = '<div id="qa">案内です。</div>'; });
    await page.waitForTimeout(100);
    assert.ok((await selectAll()).includes(encodeURIComponent('案内です。')));
    assert.equal(await page.locator('a.hjchip6').count(), 1);
    // Stroke-order drawings have no text: tapping one looks up the kanji it draws, without flipping the card.
    async function tapDrawing(selector) {
      return page.evaluate((selector) => {
        let opened = null, flipped = 0;
        document.body.addEventListener('click', () => flipped++);
        const click = HTMLAnchorElement.prototype.click;
        HTMLAnchorElement.prototype.click = function () { opened = this.href; };
        try { document.querySelector(selector).dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true })); }
        finally { HTMLAnchorElement.prototype.click = click; }
        return { q: opened && new URL(opened).searchParams.get('q'), flipped };
      }, selector);
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
    await page.route('http://card.test/**', route => {
      const svg = '<svg id="z21322" viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg"><path id="z21322d1" d="M0 0H1024V1024H0Z"/></svg>';
      if (route.request().url().includes('.svg')) return route.fulfill({ contentType: 'image/svg+xml', body: svg });
      return route.fulfill({ contentType: 'text/html; charset=utf-8', body:
        '<style>.stroke-svg{width:120px;height:120px;margin:25px 0}</style><div id="qa"><div class="kanji-theme">JLPT 기초한자</div>' +
        '<div class="stroke-container"><object class="stroke-svg" data="kanji_21322.svg?cache=1" type="image/svg+xml"></object></div>' +
        '<hr><div class="meaning">반 반</div><button class="replay-btn">획순 리플레이</button></div>' + template });
    });
    await page.goto('http://card.test/card.html');
    await page.waitForTimeout(300);
    const at = await page.locator('object').boundingBox();
    await page.evaluate(() => {
      window.__opened = null;
      HTMLAnchorElement.prototype.click = function () { window.__opened = this.href; };
    });
    await page.mouse.click(at.x + at.width / 2, at.y + at.height / 2);
    await page.waitForTimeout(100);
    assert.equal(new URL(await page.evaluate(() => window.__opened)).searchParams.get('q'), '半');
    await page.unroute('http://card.test/**');

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
    const inButton = await page.evaluate(() => {
      let opened = null;
      const click = HTMLAnchorElement.prototype.click;
      HTMLAnchorElement.prototype.click = function () { opened = this.href; };
      try { document.querySelector('#b .hjtap').dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true })); }
      finally { HTMLAnchorElement.prototype.click = click; }
      return opened;
    });
    assert.equal(inButton, null);

    assert.deepEqual(errors, []);
    console.log('PASS: copied words, furigana, long/repeated contexts, supplementary kanji, card replacement, idempotency, click isolation, selection chip, stroke drawings');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
