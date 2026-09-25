import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';

// Real local-only acceptance. No intercepted requests or provider calls from the browser.
const baseURL = process.env['SMOKE_BASE_URL'] ?? 'http://127.0.0.1:4200';
const prompt = 'Show me active service items under $200.';
const expectedIDs = [13, 14, 15, 16, 19, 20];
const browser = await chromium.launch({ executablePath: process.env['CHROME_BIN'] || undefined, headless: true });
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1050 } });
  const requests = [];
  const errors = [];
  page.on('request', request => requests.push(request.url()));
  page.on('pageerror', error => errors.push(error.message));
  await page.goto(baseURL);
  await page.locator('.catalog-card').first().waitFor();
  const panel = page.locator('app-catalog-assistant');
  await panel.getByLabel('Catalog request').fill(prompt);
  const pending = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/catalog/assistant', { timeout: 90000 });
  await panel.getByRole('button', { name: 'Find catalog items' }).click();
  const response = await pending;
  assert.equal(response.status(), 200);
  assert.deepEqual(response.request().postDataJSON(), { prompt });
  const result = await response.json();
  assert.equal(result.provider, 'ollama');
  assert.equal(result.model, 'qwen3-coder-next:latest');
  assert.equal(result.capability, 'search_catalog');
  assert.deepEqual(result.items.map(item => item.id), expectedIDs);
  assert.deepEqual(result.items.map(item => item.sku), ['SVC-101', 'SVC-102', 'SVC-103', 'SVC-104', 'SVC-107', 'SVC-108']);
  assert.equal(result.totalItems, 6);
  await panel.locator('article').first().waitFor();
  assert.deepEqual(await panel.locator('article').evaluateAll(nodes => nodes.map(node => Number(node.dataset.itemId))), expectedIDs);
  assert.ok((await panel.locator('.answer').innerText()).includes(result.answer));
  for (const item of result.items) assert.ok((await panel.innerText()).includes(item.sku));
  if (process.env['SMOKE_ASSISTANT_SCREENSHOT']) await panel.screenshot({ path: process.env['SMOKE_ASSISTANT_SCREENSHOT'] });
  await page.setViewportSize({ width: 390, height: 844 });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  if (process.env['SMOKE_ASSISTANT_MOBILE_SCREENSHOT']) await panel.screenshot({ path: process.env['SMOKE_ASSISTANT_MOBILE_SCREENSHOT'] });
  await panel.getByRole('link', { name: 'Network Health Assessment', exact: true }).click();
  await page.getByRole('heading', { name: 'Network Health Assessment', exact: true }).waitFor();
  await page.getByRole('link', { name: 'Back to catalog' }).click();
  await page.locator('.catalog-card').first().waitFor();
  assert.equal(await page.locator('.catalog-card').count(), 20);
  assert.ok(requests.every(url => new URL(url).origin === new URL(baseURL).origin));
  assert.ok(requests.every(url => !new URL(url).pathname.startsWith('/mcp')));
  assert.deepEqual(errors, []);
  const evidence = { prompt, status: response.status(), ...result };
  if (process.env['SMOKE_ASSISTANT_EVIDENCE']) await writeFile(process.env['SMOKE_ASSISTANT_EVIDENCE'], JSON.stringify(evidence, null, 2));
  console.log(JSON.stringify(evidence, null, 2));
  console.log('PASS: real local Ollama assistant, persisted items, same-origin browser requests, mobile layout, detail and return navigation.');
} finally {
  await browser.close();
}
