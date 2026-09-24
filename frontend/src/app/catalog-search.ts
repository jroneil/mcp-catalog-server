import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, inject, OnDestroy, OnInit, signal, viewChild } from '@angular/core';
import { Subscription } from 'rxjs';
import { RouterLink } from '@angular/router';
import { CatalogSearchState } from './catalog-search-state';
import { CatalogApi, CatalogPage, CatalogSearch } from './catalog-api';

@Component({
  selector: 'app-catalog-search',
  imports: [DecimalPipe, RouterLink],
  templateUrl: './catalog-search.html',
  styleUrl: './catalog-search.css',
})
export class CatalogSearchComponent implements OnInit, OnDestroy {
  private readonly api = inject(CatalogApi);
  private request?: Subscription;
  private filters: CatalogSearch = {};
  private lastRequest: CatalogSearch = {};
  readonly loading = signal(false);
  readonly result = signal<CatalogPage | null>(null);
  readonly error = signal<string | null>(null);

  private readonly state = inject(CatalogSearchState);
  private readonly form = viewChild<ElementRef<HTMLFormElement>>('searchForm');
  readonly draft = this.state.snapshot?.draft ?? {};

  ngOnInit() {
    this.filters = this.state.snapshot?.filters ?? {};
    this.load(this.state.snapshot?.request ?? {});
  }

  ngOnDestroy() {
    this.request?.unsubscribe();
    const form = this.form()?.nativeElement;
    this.state.snapshot = {
      filters: { ...this.filters }, request: { ...this.lastRequest },
      draft: form ? Object.fromEntries(new FormData(form).entries()) as Record<string, string> : {},
    };
  }

  search(event: Event) {
    event.preventDefault();
    const form = new FormData(event.target as HTMLFormElement);
    const value = (name: string) => String(form.get(name) ?? '');
    this.filters = {};
    for (const name of ['text', 'type', 'maxPrice', 'pageSize'] as const) {
      if (value(name) !== '') this.filters[name] = value(name);
    }
    if (value('active') !== '') this.filters.active = value('active') === 'true';
    // Omit page on a new search; the server owns the first-page default.
    this.load(this.filters);
  }

  navigate(direction: -1 | 1) {
    const current = this.result();
    if (!current || this.loading()) return;
    const page = current.page + direction;
    if (page < 0 || page >= current.totalPages) return;
    this.load({ ...this.filters, page, pageSize: current.pageSize });
  }

  retry() { this.load(this.lastRequest); }

  private load(criteria: CatalogSearch) {
    this.request?.unsubscribe();
    this.lastRequest = criteria;
    this.loading.set(true);
    this.result.set(null);
    this.error.set(null);
    this.request = this.api.search(criteria).subscribe({
      next: (page) => {
        this.result.set(page);
        this.loading.set(false);
      },
      error: (failure: HttpErrorResponse) => {
        const body = failure.error;
        if (failure.status === 400 && body?.status === 400 && typeof body.message === 'string') {
          this.error.set(body.message);
        } else if ([0, 502, 503, 504].includes(failure.status)) {
          this.error.set('The catalog is unavailable. Please try again.');
        } else {
          this.error.set('We could not load the catalog. Please try again.');
        }
        this.loading.set(false);
      },
    });
  }
}
