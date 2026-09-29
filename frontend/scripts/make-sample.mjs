// Renders sample/q3-research-report.pdf (fictional content) with Chromium's PDF printer.
import { chromium } from 'playwright';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const out = path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'sample', 'q3-research-report.pdf');
const html = `<!doctype html><meta charset="utf-8"><style>
  body{font:12pt/1.55 Georgia,serif;color:#1c1b19;margin:0}
  h1{font-size:26pt;margin:0 0 4pt} h2{font-size:15pt;margin:22pt 0 6pt;color:#0f766e}
  .by{color:#6b6a65;margin-bottom:18pt} table{border-collapse:collapse;width:100%}
  td,th{border-bottom:1px solid #ddd;padding:5pt 8pt;text-align:left}
</style>
<h1>Northwind Analytics: Q3 2026 Research Report</h1>
<div class="by">Dr. Maya Chen (lead author) and Luis Ortega &middot; Fictional sample document for the FileChat demo</div>
<h2>Executive summary</h2>
<p>Revenue for the third quarter reached $4.2M, an increase of 18% over Q2. About two thirds of the growth came from existing customers expanding their plans. Monthly churn fell from 6.1% to 4.7% after the onboarding redesign.</p>
<h2>Key metrics</h2>
<table><tr><th>Metric</th><th>Q2</th><th>Q3</th></tr>
<tr><td>Revenue</td><td>$3.56M</td><td>$4.2M</td></tr>
<tr><td>Monthly churn</td><td>6.1%</td><td>4.7%</td></tr></table>
<h2>Risks</h2>
<p>Supply chain delays for hardware add-ons, currency exposure ahead of the EU launch, and key-person dependency on the analytics team.</p>
<h2>Roadmap</h2>
<p>The EU region launches in Q4 2026, followed by SSO and audit logging for enterprise plans in early 2027.</p>`;
const browser = await chromium.launch();
const page = await browser.newPage();
await page.setContent(html);
await page.pdf({ path: out, format: 'A4', margin: { top: '22mm', bottom: '22mm', left: '22mm', right: '22mm' } });
await browser.close();
console.log('wrote', out);
