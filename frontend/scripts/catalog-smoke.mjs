import assert from 'node:assert/strict';
import { chromium } from 'playwright';

// Run against the real Compose stack or Angular dev proxy; no mocked requests.
const baseURL = process.env['SMOKE_BASE_URL'] ?? 'http://127.0.0.1:4200';
const browser = await chromium.launch({
  executablePath: process.env['CHROME_BIN'] || undefined,
  headless: true,
});
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 1050 } });
  const errors = [];
  const apiURLs = [];
  page.on('pageerror', error => errors.push(error.message));
  page.on('request', request => {
    if (request.url().includes('/api/')) apiURLs.push(request.url());
  });
  await page.goto(baseURL);
  const cards = page.locator('article');
  await cards.first().waitFor();
  assert.equal(await cards.count(), 20);
  assert.match(await page.locator('.pagination').innerText(), /Page 1 of 2/);
  await page.getByRole('button', { name: 'Next', exact: true }).click();
  await page.getByText('Page 2 of 2', { exact: false }).waitFor();
  assert.equal(await cards.count(), 4);
  await page.getByRole('button', { name: 'Previous', exact: true }).click();
  await page.getByText('Page 1 of 2', { exact: false }).waitFor();

  await page.getByLabel('Item type').selectOption('SERVICE');
  await page.getByLabel('Availability').selectOption('true');
  await page.getByLabel('Maximum price').fill('200');
  await page.getByRole('button', { name: 'Search catalog' }).click();
  await page.getByText('Page 1 of 1', { exact: false }).waitFor();
  assert.equal(await cards.count(), 6);
  assert.deepEqual(await page.locator('.sku').allTextContents(), ['SVC-101', 'SVC-102', 'SVC-103', 'SVC-104', 'SVC-107', 'SVC-108']);
  const response = await page.request.get(`${baseURL}/api/v1/catalog?type=SERVICE&active=true&maxPrice=200`);
  assert.equal(response.status(), 200);
  const result = await response.json();
  assert.equal(result.totalItems, 6);
  assert.deepEqual(result.items.map(item => item.id), [13, 14, 15, 16, 19, 20]);
  console.log('Real catalog search: 6 active services <= 200; IDs 13,14,15,16,19,20.');

  // Slice 13: follow a real result, exercise browser history and return to the same search.
  await page.getByRole('link', { name: 'Network Health Assessment', exact: true }).click();
  await page.waitForURL('**/catalog/16');
  await page.getByRole('heading', { name: 'Network Health Assessment', exact: true }).waitFor();
  assert.match(await page.locator('.detail-card').innerText(), /199.00/);
  assert.deepEqual(await page.locator('time').evaluateAll(nodes => nodes.map(node => node.getAttribute('datetime'))),
    ['2026-01-15T09:00:00Z', '2026-01-15T09:00:00Z']);
  assert.ok(apiURLs.some(url => new URL(url).pathname === '/api/v1/catalog/16'));
  if (process.env['SMOKE_DETAIL_SCREENSHOT']) await page.screenshot({ path: process.env['SMOKE_DETAIL_SCREENSHOT'], fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  if (process.env['SMOKE_DETAIL_MOBILE_SCREENSHOT']) await page.screenshot({ path: process.env['SMOKE_DETAIL_MOBILE_SCREENSHOT'], fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1050 });
  await page.goBack();
  await page.getByText('Page 1 of 1', { exact: false }).waitFor();
  assert.equal(await cards.count(), 6);
  await page.goForward();
  await page.getByRole('heading', { name: 'Network Health Assessment', exact: true }).waitFor();
  await page.getByRole('link', { name: 'Back to catalog' }).click();
  await page.getByText('Page 1 of 1', { exact: false }).waitFor();
  assert.equal(await cards.count(), 6);
  assert.equal(await page.getByLabel('Item type').inputValue(), 'SERVICE');
  assert.equal(await page.getByLabel('Availability').inputValue(), 'true');
  assert.equal(await page.getByLabel('Maximum price').inputValue(), '200');

  if (process.env['SMOKE_SCREENSHOT']) await page.screenshot({ path: process.env['SMOKE_SCREENSHOT'], fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
  if (process.env['SMOKE_MOBILE_SCREENSHOT']) await page.screenshot({ path: process.env['SMOKE_MOBILE_SCREENSHOT'], fullPage: true });

  await page.getByLabel('Search catalog', { exact: true }).fill('no-matching-catalog-record');
  await page.getByRole('button', { name: 'Search catalog' }).click();
  await page.getByRole('heading', { name: 'No items found' }).waitFor();
  assert.equal(await cards.count(), 0);
  await page.getByLabel('Items per page').fill('101');
  await page.getByRole('button', { name: 'Search catalog' }).click();
  await page.getByRole('alert').waitFor();
  assert.match(await page.getByRole('alert').innerText(), /Page size must be between 1 and 100/);
  assert.equal(await cards.count(), 0);
  // Direct detail refresh uses the existing nginx SPA fallback, including inactive items.
  const beforeDetail = apiURLs.length;
  await page.goto(`${baseURL}/catalog/22`);
  await page.getByRole('heading', { name: 'Legacy Email Account Setup', exact: true }).waitFor();
  assert.match(await page.locator('.detail-card').innerText(), /Inactive/);
  await page.reload();
  await page.getByRole('heading', { name: 'Legacy Email Account Setup', exact: true }).waitFor();
  assert.ok(apiURLs.slice(beforeDetail).every(url => new URL(url).pathname === '/api/v1/catalog/22'));
  await page.goto(`${baseURL}/catalog/99999`);
  await page.getByRole('heading', { name: 'Item not found', exact: true }).waitFor();
  assert.equal(await page.locator('.detail-card').count(), 0);
  await page.getByRole('link', { name: 'Back to catalog' }).click();
  await cards.first().waitFor();
  assert.equal(await cards.count(), 20);

  // A real browser network failure, without modifying backend availability/security.
  await page.context().setOffline(true);
  await page.getByRole('link', { name: 'Network Health Assessment', exact: true }).click();
  await page.getByRole('alert').waitFor();
  assert.match(await page.getByRole('alert').innerText(), /catalog is unavailable/);
  assert.equal(await page.locator('.detail-card').count(), 0);
  await page.context().setOffline(false);
  await page.getByRole('button', { name: 'Try again', exact: true }).click();
  await page.getByRole('heading', { name: 'Network Health Assessment', exact: true }).waitFor();
  await page.getByRole('link', { name: 'Back to catalog' }).click();
  await cards.first().waitFor();
  assert.equal(await cards.count(), 20);
  console.log('PASS: detail links, all fields, inactive direct URL/refresh, 404, offline failure/retry, browser back/forward, restored search and return navigation.');
  assert.ok(apiURLs.length >= 6);
  assert.ok(apiURLs.every(url => new URL(url).origin === new URL(baseURL).origin));
  assert.deepEqual(errors, []);
  console.log('PASS: default search, page navigation, combined filters, rendering, empty state, validation, mobile layout, same-origin requests; no browser runtime errors.');
} finally {
  await browser.close();
}
