// Drives the UI like a user and captures screenshots + a video into ../docs/demo.
//   1. npm start            (in another terminal)
//   2. npm run demo         (BASE_URL defaults to http://localhost:8100)
import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(HERE, '..', '..', 'docs', 'demo');
const REC = path.join(HERE, '..', '.recordings');
const BASE = process.env.BASE_URL ?? 'http://localhost:8100';
const SAMPLE = path.join(HERE, '..', 'sample', 'q3-research-report.pdf');
const SIZE = { width: 1280, height: 800 };
fs.mkdirSync(OUT, { recursive: true }); fs.rmSync(REC, { recursive: true, force: true });

const pause = (ms) => new Promise((r) => setTimeout(r, ms));
const shot = (page, name) => page.screenshot({ path: path.join(OUT, `${name}.png`) });

// Headless video has no mouse pointer; draw one so clicks are followable.
const CURSOR = () => {
  addEventListener('DOMContentLoaded', () => {
    const c = document.createElement('div');
    c.style.cssText = 'position:fixed;z-index:99999;width:18px;height:18px;margin:-3px 0 0 -3px;pointer-events:none;border-radius:50%;background:rgba(15,118,110,.85);box-shadow:0 0 0 3px rgba(255,255,255,.8),0 2px 8px rgba(0,0,0,.35);left:-40px;top:-40px;transition:transform .1s';
    document.body.append(c);
    addEventListener('mousemove', (e) => { c.style.left = e.clientX + 'px'; c.style.top = e.clientY + 'px'; }, true);
    addEventListener('mousedown', () => (c.style.transform = 'scale(.7)'), true);
    addEventListener('mouseup', () => (c.style.transform = ''), true);
  });
};
async function click(page, target) {
  const box = await target.boundingBox();
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps: 18 });
  await pause(150); await target.click();
}
const settled = (page) => page.waitForFunction(() => !document.getElementById('send').hidden, null, { timeout: 30000 });

const browser = await chromium.launch();

// ---------- 1) main walkthrough: screenshots + video ----------
{
  const ctx = await browser.newContext({ viewport: SIZE, deviceScaleFactor: 2, colorScheme: 'light',
    recordVideo: { dir: REC, size: SIZE } });
  const page = await ctx.newPage(); await page.addInitScript(CURSOR);
  await page.goto(BASE); await page.waitForSelector('.conv');
  await pause(1600); await shot(page, '01-home');

  // Upload the sample PDF through the real file input.
  await click(page, page.locator('#drop'));   // hover/click target for the video only (dialog is not shown headless)
  await page.setInputFiles('#fileInput', SAMPLE);
  await page.waitForSelector('.spinner'); await shot(page, '02-indexing');
  await page.waitForSelector('#chat:not([hidden])'); await pause(900);
  await shot(page, '03-ready');

  // Suggested question -> streamed answer
  await click(page, page.locator('.chip', { hasText: 'Give me a summary' }));
  await page.waitForSelector('.msg.bot .caret'); await pause(700);
  await shot(page, '04-streaming');
  await settled(page); await pause(1200);
  await shot(page, '05-answer');

  // Typed follow-up, with the author recommendation
  await click(page, page.locator('#input'));
  await page.locator('#input').pressSequentially('Who wrote this?', { delay: 55 });
  await pause(300); await page.keyboard.press('Enter');
  await settled(page); await pause(1400);
  await shot(page, '06-authors');

  // Out of scope -> honest "can't find it"
  await page.locator('#input').pressSequentially('What is the weather in Paris?', { delay: 45 });
  await page.keyboard.press('Enter'); await settled(page); await pause(1400);
  await shot(page, '07-not-found');

  // Reopen an older conversation from the history sidebar
  const old = page.locator('.conv', { hasText: 'main risks' });
  await click(page, old); await page.waitForSelector('.msg.bot .cite'); await pause(1500);
  await shot(page, '08-history');

  // Dark mode
  await click(page, page.locator('#toggleTheme')); await pause(1600);
  await shot(page, '09-dark');
  await click(page, page.locator('#toggleTheme')); await pause(800);

  await ctx.close();
  const vid = fs.readdirSync(REC).find((f) => f.endsWith('.webm'));
  fs.copyFileSync(path.join(REC, vid), path.join(OUT, 'demo.webm'));
}

// ---------- 2) extra states: auth error, settings, mobile ----------
{
  const ctx = await browser.newContext({ viewport: SIZE, deviceScaleFactor: 2, colorScheme: 'light' });
  const page = await ctx.newPage(); await page.goto(BASE); await page.waitForSelector('.conv');
  await page.click('#openSettings'); await page.fill('#setToken', 'invalid'); await pause(200);
  await shot(page, '10-connection');
  await page.click('button[value=save]'); await pause(400);
  await page.setInputFiles('#fileInput', SAMPLE); await page.waitForSelector('.status.err');
  await shot(page, '11-rejected-token');
  await ctx.close();

  const phone = await browser.newContext({ viewport: { width: 390, height: 780 }, deviceScaleFactor: 3, isMobile: true, hasTouch: true, colorScheme: 'light' });
  const m = await phone.newPage(); await m.goto(BASE); await m.waitForSelector('.conv', { state: 'attached' });
  await m.setInputFiles('#fileInput', SAMPLE); await m.waitForSelector('#chat:not([hidden])');
  await m.locator('.chip', { hasText: 'main risks' }).tap();
  await settled(m); await pause(400); await shot(m, '12-mobile');
  await m.tap('#openMenu'); await pause(400); await shot(m, '13-mobile-menu');
  await phone.close();
}
await browser.close();
console.log('captured to', OUT);
