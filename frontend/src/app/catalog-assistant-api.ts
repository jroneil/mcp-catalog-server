import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { CatalogPage } from './catalog-api';

export interface CatalogAssistantResult extends CatalogPage {
  answer: string;
  capability: string;
  arguments: Record<string, unknown>;
  provider: string;
  model: string;
}

@Injectable({ providedIn: 'root' })
export class CatalogAssistantApi {
  private readonly http = inject(HttpClient);

  ask(prompt: string) {
    return this.http.post<CatalogAssistantResult>('/api/v1/catalog/assistant', { prompt });
  }
}
