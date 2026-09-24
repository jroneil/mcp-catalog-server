import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';

export interface CatalogItem {
  id: number;
  sku: string;
  name: string;
  type: string;
  description: string;
  price: number;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CatalogPage {
  items: CatalogItem[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}

/** Optional wire inputs; validation/defaulting belongs to CatalogService. */
export interface CatalogSearch {
  text?: string;
  type?: string;
  active?: boolean;
  maxPrice?: string;
  page?: number;
  pageSize?: string | number;
}

@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);

  detail(id: string) {
    return this.http.get<CatalogItem>(`/api/v1/catalog/${encodeURIComponent(id)}`);
  }

  search(criteria: CatalogSearch = {}) {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(criteria)) {
      if (value !== undefined) params = params.set(key, String(value));
    }
    return this.http.get<CatalogPage>('/api/v1/catalog', { params });
  }
}
