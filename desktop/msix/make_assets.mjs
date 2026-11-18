// Draws the Store package images (msix/Assets) from harfix_desktop/ui/icon.svg.
// Run once after the logo changes:  node desktop/msix/make_assets.mjs   (needs Playwright)
// build_msix.py indexes them into resources.pri, so Windows picks the size for each screen.
import { createRequire } from "node:module";
import { readFileSync, mkdirSync, readdirSync, unlinkSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const { chromium } = require("playwright");
const here = dirname(fileURLToPath(import.meta.url));
const svg = readFileSync(join(here, "..", "harfix_desktop", "ui", "icon.svg"), "utf8");
const out = join(here, "Assets");
mkdirSync(out, { recursive: true });
for (const f of readdirSync(out)) if (f.endsWith(".png")) unlinkSync(join(out, f));

const scales = [100, 125, 150, 200, 400];
// [file, width, height at scale 100, icon size as a share of the height]
const tiles = [
  ["Square44x44Logo", 44, 44, 1],
  ["StoreLogo", 50, 50, 1],
  ["SmallTile", 71, 71, 0.6],
  ["Square150x150Logo", 150, 150, 0.56],
  ["Wide310x150Logo", 310, 150, 0.56],
  ["LargeTile", 310, 310, 0.5],
];
const jobs = [];
for (const [name, w, h, share] of tiles)
  for (const s of scales)
    jobs.push([`${name}.scale-${s}.png`, Math.round((w * s) / 100), Math.round((h * s) / 100), share]);
// Taskbar, Start list, Explorer, Alt+Tab: exact pixel sizes, drawn without the accent plate.
for (const t of [16, 20, 24, 30, 32, 36, 40, 48, 60, 64, 72, 80, 96, 256]) {
  jobs.push([`Square44x44Logo.targetsize-${t}.png`, t, t, 1]);
  jobs.push([`Square44x44Logo.targetsize-${t}_altform-unplated.png`, t, t, 1]);
  jobs.push([`Square44x44Logo.targetsize-${t}_altform-lightunplated.png`, t, t, 1]);
}

const browser = await chromium.launch();
const page = await browser.newPage({ deviceScaleFactor: 1 });
for (const [file, w, h, share] of jobs) {
  const size = Math.round(h * share);
  await page.setViewportSize({ width: w, height: h });
  await page.setContent(
    `<html><body style="margin:0;width:${w}px;height:${h}px;display:flex;align-items:center;` +
      `justify-content:center;background:transparent">` +
      svg.replace("<svg ", `<svg width="${size}" height="${size}" `) +
      `</body></html>`
  );
  await page.screenshot({ path: join(out, file), omitBackground: true });
}
await browser.close();
console.log(`${jobs.length} images in ${out}`);
