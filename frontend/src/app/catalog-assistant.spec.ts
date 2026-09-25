import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { CatalogAssistantComponent } from './catalog-assistant';
import { CatalogAssistantResult } from './catalog-assistant-api';

const result: CatalogAssistantResult = {
  answer: 'The catalog contains this matching service.', capability: 'search_catalog',
  arguments: { type: 'SERVICE', active: true, maxPrice: 200 },
  items: [{ id: 16, sku: 'SVC-104', name: 'Network Health Assessment', type: 'SERVICE',
    description: 'Review your office network.', price: 199, active: true,
    createdAt: '2026-01-15T09:00:00Z', updatedAt: '2026-01-15T09:00:00Z' }],
  page: 0, pageSize: 20, totalItems: 1, totalPages: 1,
  provider: 'ollama', model: 'qwen3-coder-next:latest',
};
const url = '/api/v1/catalog/assistant';

describe('Catalog assistant', () => {
  let fixture: ComponentFixture<CatalogAssistantComponent>;
  let http: HttpTestingController;
  let root: HTMLElement;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [CatalogAssistantComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    fixture = TestBed.createComponent(CatalogAssistantComponent);
    root = fixture.nativeElement;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });
  afterEach(() => { http.verify(); fixture.destroy(); });
  function submit(prompt = 'Show me active service items under $200.') {
    root.querySelector('textarea')!.value = prompt;
    root.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
    return http.expectOne(url);
  }
  function flushSuccess() { const request = submit(); request.flush(result); fixture.detectChanges(); }

  it('waits for submission and posts only the unchanged prompt to the relative backend endpoint', () => {
    http.expectNone(() => true);
    const request = submit('  active services  ');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ prompt: '  active services  ' });
    expect(request.request.params.keys()).toEqual([]);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(result);
  });
  it('shows loading without fabricating a result', () => {
    const request = submit();
    expect(root.querySelector('[role="status"]')?.textContent).toContain('Finding matching');
    expect(root.querySelector('[aria-busy]')?.getAttribute('aria-busy')).toBe('true');
    expect(root.querySelector('article')).toBeNull();
    request.flush(result); fixture.detectChanges();
    expect(root.querySelector('[aria-busy]')?.getAttribute('aria-busy')).toBe('false');
  });
  it('renders the authoritative answer, returned item, metadata and detail link', () => {
    flushSuccess();
    expect(root.textContent).toContain(result.answer);
    for (const text of ['Network Health Assessment', 'Review your office network.', 'SVC-104', '199.00', 'Active', 'Service', 'Showing 1 of 1']) {
      expect(root.textContent).toContain(text);
    }
    expect(root.querySelector('a')?.getAttribute('href')).toBe('/catalog/16');
    expect(root.textContent).not.toContain(result.model);
    expect(root.textContent).not.toContain(result.provider);
  });
  it('renders answer text safely rather than executing markup', () => {
    submit().flush({ ...result, answer: '<img src="https://provider.invalid/secret" onerror="alert(1)">' });
    fixture.detectChanges();
    expect(root.querySelector('img')).toBeNull();
    expect(root.querySelector('.answer')?.textContent).toContain('<img');
  });
  it('preserves returned ordering and inactive items without applying catalog rules', () => {
    submit().flush({ ...result, items: [{ ...result.items[0], id: 22, active: false }, result.items[0]], totalItems: 12 });
    fixture.detectChanges();
    expect([...root.querySelectorAll('article')].map(el => el.getAttribute('data-item-id'))).toEqual(['22', '16']);
    expect(root.textContent).toContain('Inactive');
    expect(root.textContent).toContain('Showing 2 of 12');
    http.expectNone(() => true);
  });
  it('renders an empty authoritative result', () => {
    submit().flush({ ...result, answer: 'No items matched.', items: [], totalItems: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(root.textContent).toContain('No items matched.');
    expect(root.textContent).toContain('No matching items were returned');
    expect(root.querySelector('article')).toBeNull();
  });
  it.each(['', '   ', 'x'.repeat(1001)])('leaves blank and length validation to the backend (%s)', prompt => {
    const request = submit(prompt);
    expect(request.request.body).toEqual({ prompt });
    request.flush({ status: 400, message: 'Prompt must be non-blank and at most 1000 characters.' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')?.textContent).toContain('Prompt must be');
    expect(root.textContent).not.toContain('Retry catalog request');
  });
  it('presents unsupported intent and lets the user submit a revised request', () => {
    submit('Write a poem').flush({ status: 400, message: 'This endpoint only supports catalog search requests.' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(root.textContent).toContain('only supports catalog search');
    submit().flush(result); fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')).toBeNull();
    expect(root.textContent).toContain(result.answer);
  });
  it('does not expose malformed validation diagnostics', () => {
    submit().flush({ message: 'internal secret' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();
    expect(root.textContent).toContain('Please revise');
    expect(root.textContent).not.toContain('internal secret');
  });
  it.each([[503, 'unavailable'], [504, 'too long'], [500, 'could not complete'], [502, 'unavailable']])('sanitizes status %s without leaking provider URLs or credentials', (status, message) => {
    submit().flush({ message: 'https://provider.invalid key=secret SQL stack trace' }, { status: Number(status), statusText: 'Error' });
    fixture.detectChanges();
    expect(root.querySelector('[role="alert"]')?.textContent).toContain(message);
    expect(root.textContent).not.toMatch(/provider.invalid|key=secret|SQL|stack trace/);
    expect(root.querySelector('article')).toBeNull();
    http.expectNone(() => true); // No automatic model/request retries.
  });
  it('handles browser network failure and retries the last submitted prompt explicitly', () => {
    submit('active services').error(new ProgressEvent('error')); fixture.detectChanges();
    expect(root.textContent).toContain('unavailable');
    root.querySelector('textarea')!.value = 'unsubmitted edit';
    [...root.querySelectorAll('button')].find(el => el.textContent === 'Retry catalog request')!.click();
    fixture.detectChanges();
    const retry = http.expectOne(url);
    expect(retry.request.body).toEqual({ prompt: 'active services' });
    expect(root.querySelector('[role="alert"]')).toBeNull();
    retry.flush(result); fixture.detectChanges();
    expect(root.textContent).toContain(result.answer);
  });
  it('cancels stale requests and renders only the latest submission', () => {
    const first = submit('products');
    const second = submit('services');
    expect(first.cancelled).toBe(true);
    second.flush(result); fixture.detectChanges();
    expect(root.querySelectorAll('article').length).toBe(1);
    expect(root.textContent).toContain(result.answer);
  });
  it('clears the previous result on repeat submission without conversation history', () => {
    flushSuccess();
    const next = submit('products');
    expect(root.querySelector('article')).toBeNull();
    expect(root.textContent).not.toContain(result.answer);
    expect(next.request.body).toEqual({ prompt: 'products' });
    next.flush(result);
  });
  it('cancels a pending request when leaving the component', () => {
    const request = submit(); fixture.destroy();
    expect(request.cancelled).toBe(true);
  });
});
