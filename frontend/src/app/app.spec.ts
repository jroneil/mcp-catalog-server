import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { App } from './app';
import { CatalogItem, CatalogPage } from './catalog-api';

const item: CatalogItem = {
  id: 16, sku: 'SVC-104', name: 'Network Health Assessment', type: 'SERVICE',
  description: 'Review office network configuration.', price: 199, active: true,
  createdAt: '2026-01-15T09:00:00Z', updatedAt: '2026-01-15T09:00:00Z',
};
const page: CatalogPage = { items: [item], page: 0, pageSize: 1, totalItems: 2, totalPages: 2 };

describe('Catalog search screen', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;
  let root: HTMLElement;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [App], providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    fixture = TestBed.createComponent(App);
    root = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });
  afterEach(() => { http.verify(); fixture.destroy(); });

  function respond(result = page) {
    http.expectOne(req => req.url === '/api/v1/catalog').flush(result);
    fixture.detectChanges();
  }
  function set(name: string, value: string) {
    (root.querySelector(`[name="${name}"]`) as HTMLInputElement).value = value;
  }
  function submit() {
    root.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
  }
  function button(label: string) {
    return Array.from(root.querySelectorAll('button')).find(el => el.textContent?.trim() === label)!;
  }

  it('shows loading during the initial default search', () => {
    expect(root.querySelector('[role="status"]')?.textContent).toContain('Loading catalog');
    const request = http.expectOne('/api/v1/catalog');
    expect(request.request.params.keys()).toEqual([]);
    request.flush(page);
    fixture.detectChanges();
    expect(root.querySelector('[aria-busy]')?.getAttribute('aria-busy')).toBe('false');
  });

  it('renders readable returned records, server metadata and inactive status', () => {
    respond({ ...page, items: [item, { ...item, id: 22, sku: 'SVC-110', active: false }] });
    expect(root.querySelectorAll('article')).toHaveLength(2);
    expect(root.textContent).toContain(item.name);
    expect(root.textContent).toContain(item.description);
    expect(root.textContent).toContain('SVC-104');
    expect(root.textContent).toContain('199.00');
    expect(root.textContent).toContain('Inactive');
    expect(root.textContent).toContain('Page 1 of 2');
    expect(root.querySelector('article a')).toBeNull(); // Detail is Slice 13.
  });

  it('shows an empty state and disables page navigation', () => {
    respond({ items: [], page: 0, pageSize: 20, totalItems: 0, totalPages: 0 });
    expect(root.textContent).toContain('No items found');
    expect(root.querySelectorAll('article')).toHaveLength(0);
    expect(button('Previous').disabled).toBe(true);
    expect(button('Next').disabled).toBe(true);
  });

  it('submits all filter controls, preserving decimal text and active=false', () => {
    respond();
    set('text', '  network  '); set('type', 'SERVICE'); set('active', 'false');
    set('maxPrice', '1.000'); set('pageSize', '7'); submit();
    const request = http.expectOne(req => req.url === '/api/v1/catalog');
    expect(request.request.params.get('text')).toBe('  network  ');
    expect(request.request.params.get('type')).toBe('SERVICE');
    expect(request.request.params.get('active')).toBe('false');
    expect(request.request.params.get('maxPrice')).toBe('1.000');
    expect(request.request.params.get('pageSize')).toBe('7');
    expect(request.request.params.has('page')).toBe(false);
    request.flush(page);
  });

  it('omits blank filters and page size instead of inventing defaults', () => {
    respond(); submit();
    const request = http.expectOne('/api/v1/catalog');
    expect(request.request.params.keys()).toEqual([]);
    request.flush({ ...page, pageSize: 13 }); fixture.detectChanges();
    expect(root.textContent).toContain('13 items per page');
  });

  it('uses server page metadata and retains submitted filters when navigating', () => {
    respond(); set('text', 'network'); set('type', 'SERVICE'); submit(); respond();
    // Editing an unsubmitted draft does not change the current result's filters.
    set('text', 'unsubmitted');
    button('Next').click(); fixture.detectChanges();
    const next = http.expectOne(req => req.params.get('page') === '1');
    expect(next.request.params.get('text')).toBe('network');
    expect(next.request.params.get('type')).toBe('SERVICE');
    expect(next.request.params.get('pageSize')).toBe('1');
    next.flush({ ...page, page: 1 }); fixture.detectChanges();
    expect(button('Next').disabled).toBe(true);
    button('Previous').click(); fixture.detectChanges();
    http.expectOne(req => req.params.get('page') === '0').flush(page); fixture.detectChanges();
    expect(button('Previous').disabled).toBe(true);
  });

  it('starts a new search with server defaults rather than retaining the previous page', () => {
    respond({ ...page, page: 1 }); set('text', 'mouse'); submit();
    const request = http.expectOne(req => req.params.get('text') === 'mouse');
    expect(request.request.params.has('page')).toBe(false);
    request.flush(page);
  });

  it('does not clamp or locally reject server-owned bounds', () => {
    respond(); set('pageSize', '101'); set('maxPrice', '-1'); set('text', 'x'.repeat(201)); submit();
    const request = http.expectOne(req => req.url === '/api/v1/catalog');
    expect(request.request.params.get('pageSize')).toBe('101');
    expect(request.request.params.get('maxPrice')).toBe('-1');
    expect(request.request.params.get('text')).toHaveLength(201);
    request.flush({ status: 400, error: 'Bad Request', message: 'Page size must be between 1 and 100', path: '/api/v1/catalog' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Page size must be between 1 and 100');
    expect(root.querySelectorAll('article')).toHaveLength(0);
  });

  it.each(['Malformed request parameter', 'Type must be PRODUCT or SERVICE'])('presents safe REST error: %s', message => {
    http.expectOne('/api/v1/catalog').flush({ status: 400, message }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')?.textContent).toContain(message);
    expect(root.textContent).not.toContain('No items found');
  });

  it.each([500, 502, 503])('handles backend %s without displaying internal error bodies', status => {
    http.expectOne('/api/v1/catalog').flush('SQL secret exception', { status, statusText: 'Server Error' });
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')).not.toBeNull();
    expect(root.textContent).not.toContain('SQL secret');
    expect(root.textContent).not.toContain('Loading catalog');
  });

  it('handles network unavailability and retries the same request', () => {
    http.expectOne('/api/v1/catalog').error(new ProgressEvent('error'));
    fixture.detectChanges();
    expect(root.textContent).toContain('The catalog is unavailable');
    button('Try again').click(); fixture.detectChanges(); respond();
    expect(root.querySelector('[role="alert"]')).toBeNull();
    expect(root.textContent).toContain(item.name);
  });

  it('uses a generic message for a malformed error envelope', () => {
    http.expectOne('/api/v1/catalog').flush({ message: 'internal details' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(root.textContent).toContain('We could not load the catalog');
    expect(root.textContent).not.toContain('internal details');
  });

  it('cancels stale requests when a new search is submitted', () => {
    const old = http.expectOne('/api/v1/catalog');
    set('text', 'mouse'); submit();
    expect(old.cancelled).toBe(true);
    respond();
    expect(root.textContent).toContain(item.name);
  });
});
