import { DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { distinctUntilChanged, map, Subscription } from 'rxjs';
import { CatalogApi, CatalogItem } from './catalog-api';

@Component({
  selector: 'app-catalog-detail',
  imports: [DatePipe, DecimalPipe, RouterLink],
  templateUrl: './catalog-detail.html',
  styleUrl: './catalog-detail.css',
})
export class CatalogDetailComponent {
  private readonly api = inject(CatalogApi);
  private request?: Subscription;
  private id = '';
  readonly loading = signal(false);
  readonly item = signal<CatalogItem | null>(null);
  readonly notFound = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    inject(DestroyRef).onDestroy(() => this.request?.unsubscribe());
    inject(ActivatedRoute).paramMap.pipe(
      map(params => params.get('id') ?? ''), distinctUntilChanged(), takeUntilDestroyed(),
    ).subscribe(id => { this.id = id; this.load(); });
  }

  retry() { this.load(); }

  private load() {
    this.request?.unsubscribe();
    this.item.set(null);
    this.notFound.set(false);
    this.error.set(null);
    this.loading.set(true);
    this.request = this.api.detail(this.id).subscribe({
      next: item => { this.item.set(item); this.loading.set(false); },
      error: (failure: HttpErrorResponse) => {
        if (failure.status === 404) {
          this.notFound.set(true);
        } else if (failure.status === 400 && failure.error?.status === 400 && typeof failure.error.message === 'string') {
          this.error.set(failure.error.message);
        } else if ([0, 502, 503, 504].includes(failure.status)) {
          this.error.set('The catalog is unavailable. Please try again.');
        } else {
          this.error.set('We could not load this item. Please try again.');
        }
        this.loading.set(false);
      },
    });
  }
}
