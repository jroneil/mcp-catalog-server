import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CatalogApi, CatalogSearch } from './catalog-api';

describe('CatalogApi', () => {
  let api: CatalogApi;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(CatalogApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('uses a relative GET and leaves all defaults to the server', () => {
    api.search().subscribe();
    const request = http.expectOne('/api/v1/catalog');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    request.flush({ items: [], page: 0, pageSize: 20, totalItems: 0, totalPages: 0 });
  });

  it.each([
    ['text', ' Network & support '], ['type', 'SERVICE'], ['active', false],
    ['maxPrice', '1.000'], ['page', 2], ['pageSize', 5],
  ])('maps %s without coercing or dropping values', (key, value) => {
    api.search({ [key]: value } as CatalogSearch).subscribe();
    const request = http.expectOne(req => req.url === '/api/v1/catalog');
    expect(request.request.params.keys()).toEqual([key]);
    expect(request.request.params.get(key as string)).toBe(String(value));
    request.flush({});
  });

  it('maps combined filters and pagination', () => {
    const criteria: CatalogSearch = { text: 'network', type: 'PRODUCT', active: true, maxPrice: '200', page: 1, pageSize: 3 };
    api.search(criteria).subscribe();
    const request = http.expectOne(req => req.url === '/api/v1/catalog');
    expect(request.request.params.keys()).toHaveLength(6);
    for (const [key, value] of Object.entries(criteria)) expect(request.request.params.get(key)).toBe(String(value));
    request.flush({});
  });

  it('passes invalid criteria to server validation and omits only undefined fields', () => {
    api.search({ page: -1, pageSize: '101', maxPrice: '-1', text: 'a'.repeat(201), type: undefined }).subscribe();
    const request = http.expectOne(req => req.url === '/api/v1/catalog');
    expect(request.request.params.get('page')).toBe('-1');
    expect(request.request.params.get('pageSize')).toBe('101');
    expect(request.request.params.get('maxPrice')).toBe('-1');
    expect(request.request.params.get('text')).toHaveLength(201);
    expect(request.request.params.has('type')).toBe(false);
    request.flush({});
  });

  it('retrieves detail using the relative GET endpoint without search filters', () => {
    api.detail('22').subscribe(item => expect(item.active).toBe(false));
    const request = http.expectOne('/api/v1/catalog/22');
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([]);
    request.flush({ id: 22, active: false });
  });

  it('preserves large identifier strings rather than rounding them in JavaScript', () => {
    api.detail('9223372036854775807').subscribe();
    http.expectOne('/api/v1/catalog/9223372036854775807').flush({});
  });

  it('encodes identifiers as one path segment and leaves validation to REST', () => {
    api.detail('invalid/id?x=1').subscribe({ error: failure => expect(failure.status).toBe(400) });
    http.expectOne('/api/v1/catalog/invalid%2Fid%3Fx%3D1')
      .flush({ status: 400 }, { status: 400, statusText: 'Bad Request' });
  });

});
