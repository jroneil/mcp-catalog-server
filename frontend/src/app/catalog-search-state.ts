import { Injectable } from '@angular/core';
import { CatalogSearch } from './catalog-api';

/** In-memory navigation state only; catalog records are always reloaded from REST. */
@Injectable({ providedIn: 'root' })
export class CatalogSearchState {
  snapshot: { filters: CatalogSearch; request: CatalogSearch; draft: Record<string, string> } | null = null;
}
