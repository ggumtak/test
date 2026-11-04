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
    await page.evaluate(() => window.__hjtap4());
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
      return page.locator('a.hjchip4').getAttribute('href');
    }
    assert.ok((await selectAll()).includes(encodeURIComponent('電気です。明日')));
    await page.evaluate(() => { document.body.innerHTML = '<div id="qa">案内です。</div>'; });
    await page.waitForTimeout(100);
    assert.ok((await selectAll()).includes(encodeURIComponent('案内です。')));
    assert.equal(await page.locator('a.hjchip4').count(), 1);
    assert.deepEqual(errors, []);
    console.log('PASS: furigana, long/repeated contexts, supplementary kanji, card replacement, idempotency, click isolation, selection chip');
  } finally { await browser.close(); }
})().catch(error => { console.error(error); process.exitCode = 1; });
