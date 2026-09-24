# Catalog search frontend

Slices 12–13 provide standalone Angular search/list and item detail over the accepted REST API.
AI workflows remain deferred to later slices.

Use Node **22.22.3** and npm **10.9.8** (see `.nvmrc` and package.json engines).
Angular core/compiler is **22.2.0**, CLI/build **22.1.8**. Install from the committed
lockfile with `npm ci`; do not replace the approved toolchain with a global CLI.

```bash
cd frontend
npm ci
npm test
npm run build
npm start
```

The dev server binds `127.0.0.1:4200` and proxies `/api/**` to the existing backend at
`http://127.0.0.1:8080`. Start the backend/PostgreSQL using the root README instructions.
If the Compose frontend already occupies port 4200, first run `docker compose stop frontend`.
Stop `npm start` before restoring it with `docker compose up -d --wait frontend`.
The approved development proxy assumes the backend's default port 8080.

For the containerized UI, run `docker compose up --build --wait` from the repository
root, then open `http://127.0.0.1:4200`. nginx serves the production bundle and proxies
`/api/` to `mcp-catalog-server:8080`, keeping browser calls on one origin. `/mcp` is not
proxied. No backend URL or credentials are embedded in application assets.

## Tests

`npm test` runs 40 focused Vitest/Angular tests with Angular's HTTP test controller.
They cover all filters, request mapping, server-owned defaults/bounds, results,
pagination, loading/empty/errors, retries, request cancellation, detail routing and return navigation.

For real-browser validation, start the full stack and run:

```bash
# Use an installed Chrome executable, or install Playwright Chromium once.
CHROME_BIN=/usr/bin/google-chrome npm run smoke
# Alternative: npx playwright install chromium && npm run smoke
```

The smoke script uses real catalog seed data, not network mocks. It checks initial
search, page navigation, six active services <= 200, rendered results, empty and
validation states, detail links/refresh, inactive items, not-found, offline retry, back/forward,
restored searches, mobile layout and same-origin API requests. It also runs against
the dev server. Optional environment variables: `SMOKE_BASE_URL` (default
`http://127.0.0.1:4200`), `SMOKE_SCREENSHOT` and `SMOKE_MOBILE_SCREENSHOT` for local captures.

All catalog validation, filtering, defaults and result ordering remain server-owned.
Blank controls are omitted; decimal price text is preserved verbatim. Navigation uses
the server's current page, page size and total pages. A new search omits the page to
request the server default. Frontend prices display two decimals without implying a
currency that the REST contract does not provide.


## Item detail

Item names link to `/catalog/{id}`. Angular Router is pinned at 22.2.0, matching core.
The API client calls relative `/api/v1/catalog/{id}`; lookup validation remains server-owned.
Detail is read-only and includes inactive records. The back link always returns to the
catalog, including when a directly opened item is missing or the backend is unavailable.
Search filters/page and draft form edits are retained only in memory for navigation;
records are fetched again on return, and a full reload clears this navigation snapshot.
Optional `SMOKE_DETAIL_SCREENSHOT` / `SMOKE_DETAIL_MOBILE_SCREENSHOT` capture detail UI.
