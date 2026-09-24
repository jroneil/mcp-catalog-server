import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NavigationEnd, provideRouter, Router } from '@angular/router';
import { filter, firstValueFrom } from 'rxjs';
import { App } from './app';
import { routes } from './app.routes';
import { CatalogItem } from './catalog-api';

const item: CatalogItem = {
  id: 16, sku: 'SVC-104', name: 'Network Health Assessment', type: 'SERVICE',
  description: 'Review office network configuration and provide a prioritized findings report.',
  price: 199, active: true, createdAt: '2026-01-15T09:00:00Z', updatedAt: '2026-02-01T12:00:00Z',
};
const page = { items: [item], page: 0, pageSize: 1, totalItems: 2, totalPages: 2 };

describe('Catalog detail routing', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;
  let router: Router;
  let root: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App], providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(App);
    root = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });
  afterEach(() => { http.verify(); fixture.destroy(); });

  async function go(path: string) {
    await router.navigateByUrl(path);
    fixture.detectChanges();
  }
  async function follow(selector: string) {
    const completed = firstValueFrom(router.events.pipe(filter(event => event instanceof NavigationEnd)));
    (root.querySelector(selector) as HTMLElement).click();
    await completed;
    fixture.detectChanges();
  }
  function detail(record = item) {
    http.expectOne('/api/v1/catalog/' + record.id).flush(record);
    fixture.detectChanges();
  }

  it('navigates from a search result, shows loading, then displays all item fields', async () => {
    await go('/');
    http.expectOne('/api/v1/catalog').flush(page); fixture.detectChanges();
    await follow('article h3 a');
    expect(router.url).toBe('/catalog/16');
    expect(root.textContent).toContain('Loading item');
    expect(root.querySelector('article')).toBeNull();
    detail();
    expect(root.querySelector('h1')?.textContent).toBe(item.name);
    expect(root.textContent).toContain(item.description);
    expect(root.textContent).toContain('199.00');
    expect(root.textContent).toContain('SVC-104');
    expect(root.textContent).toContain('Service');
    expect(root.textContent).toContain('Active');
    expect(root.querySelector('dd')?.textContent).toBe('16');
    expect(Array.from(root.querySelectorAll('time')).map(time => time.dateTime)).toEqual([item.createdAt, item.updatedAt]);
    expect(root.textContent).toContain('Created (UTC)');
    expect(root.querySelector('[aria-busy]')?.getAttribute('aria-busy')).toBe('false');
    http.expectNone('/api/v1/catalog');
  });

  it('supports a direct detail URL and inactive products without filtering them out', async () => {
    await go('/catalog/22');
    detail({ ...item, id: 22, active: false, type: 'PRODUCT' });
    expect(root.textContent).toContain('Inactive');
    expect(root.textContent).toContain('Product');
    http.expectNone('/api/v1/catalog');
  });

  it('shows not found without fabricated data and can return to the catalog', async () => {
    await go('/catalog/99999');
    http.expectOne('/api/v1/catalog/99999').flush({ status: 404 }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();
    expect(root.textContent).toContain('Item not found');
    expect(root.querySelector('article')).toBeNull();
    await follow('.back-link');
    expect(router.url).toBe('/');
    http.expectOne('/api/v1/catalog').flush(page); fixture.detectChanges();
    expect(root.textContent).toContain(item.name);
  });

  it.each([500, 502, 503])('presents a safe error for status %s and permits retry', async status => {
    await go('/catalog/16');
    http.expectOne('/api/v1/catalog/16').flush('SQL secret stack trace', { status, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')).not.toBeNull();
    expect(root.textContent).not.toContain('SQL secret');
    expect(root.querySelector('article')).toBeNull();
    (root.querySelector('button') as HTMLButtonElement).click(); fixture.detectChanges();
    expect(root.textContent).toContain('Loading item');
    detail();
    expect(root.querySelector('[role="alert"]')).toBeNull();
  });

  it('presents a network failure and keeps return navigation available', async () => {
    await go('/catalog/16');
    http.expectOne('/api/v1/catalog/16').error(new ProgressEvent('error')); fixture.detectChanges();
    expect(root.textContent).toContain('The catalog is unavailable');
    await follow('.back-link');
    http.expectOne('/api/v1/catalog').flush(page);
  });

  it('delegates malformed identifiers to REST and displays its safe validation message', async () => {
    await go('/catalog/not-a-number');
    http.expectOne('/api/v1/catalog/not-a-number').flush({ status: 400, message: 'Malformed request parameter' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Malformed request parameter');
  });

  it('does not expose an unexpected validation error body', async () => {
    await go('/catalog/0');
    http.expectOne('/api/v1/catalog/0').flush({ message: 'internal diagnostics' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(root.textContent).toContain('We could not load this item');
    expect(root.textContent).not.toContain('internal diagnostics');
  });

  it('clears stale detail and cancels requests when the route identifier changes', async () => {
    await go('/catalog/16'); detail();
    await go('/catalog/22');
    expect(root.querySelector('article')).toBeNull();
    const cancelled = http.expectOne('/api/v1/catalog/22');
    await go('/catalog/1');
    expect(cancelled.cancelled).toBe(true);
    detail({ ...item, id: 1, name: 'Ergonomic Wireless Mouse', type: 'PRODUCT' });
    expect(root.textContent).not.toContain(item.name);
  });

  it('cancels a pending detail lookup when returning to the catalog', async () => {
    await go('/catalog/16');
    const cancelled = http.expectOne('/api/v1/catalog/16');
    await follow('.back-link');
    expect(cancelled.cancelled).toBe(true);
    http.expectOne('/api/v1/catalog').flush(page);
  });

  it('restores submitted filters, page and unsubmitted form edits on return', async () => {
    await go('/'); http.expectOne('/api/v1/catalog').flush(page); fixture.detectChanges();
    const set = (name: string, value: string) => { (root.querySelector(`[name="${name}"]`) as HTMLInputElement).value = value; };
    set('text', 'network'); set('type', 'SERVICE'); set('active', 'false'); set('maxPrice', '200.00'); set('pageSize', '1');
    root.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    http.expectOne(req => req.params.get('text') === 'network').flush(page); fixture.detectChanges();
    Array.from(root.querySelectorAll('button')).find(button => button.textContent === 'Next')!.click();
    http.expectOne(req => req.params.get('page') === '1').flush({ ...page, page: 1 }); fixture.detectChanges();
    set('text', 'unsubmitted draft');
    await follow('article h3 a'); detail();
    await follow('.back-link');
    const request = http.expectOne(req => req.url === '/api/v1/catalog');
    expect(request.request.params.get('text')).toBe('network');
    expect(request.request.params.get('type')).toBe('SERVICE');
    expect(request.request.params.get('active')).toBe('false');
    expect(request.request.params.get('maxPrice')).toBe('200.00');
    expect(request.request.params.get('pageSize')).toBe('1');
    expect(request.request.params.get('page')).toBe('1');
    request.flush({ ...page, page: 1 }); fixture.detectChanges();
    expect((root.querySelector('[name="text"]') as HTMLInputElement).value).toBe('unsubmitted draft');
    expect((root.querySelector('[name="active"]') as HTMLSelectElement).value).toBe('false');
    expect(root.textContent).toContain('Page 2 of 2');
  });
});
