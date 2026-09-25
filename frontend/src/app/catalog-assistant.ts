import { DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, OnDestroy, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { CatalogAssistantApi, CatalogAssistantResult } from './catalog-assistant-api';

@Component({
  selector: 'app-catalog-assistant',
  imports: [DecimalPipe, RouterLink],
  templateUrl: './catalog-assistant.html',
  styleUrl: './catalog-assistant.css',
})
export class CatalogAssistantComponent implements OnDestroy {
  private readonly api = inject(CatalogAssistantApi);
  private request?: Subscription;
  private lastPrompt = '';
  readonly loading = signal(false);
  readonly result = signal<CatalogAssistantResult | null>(null);
  readonly error = signal<string | null>(null);
  readonly retryable = signal(false);

  submit(event: Event) {
    event.preventDefault();
    const prompt = String(new FormData(event.target as HTMLFormElement).get('prompt') ?? '');
    this.load(prompt);
  }

  retry() { this.load(this.lastPrompt); }
  ngOnDestroy() { this.request?.unsubscribe(); }

  private load(prompt: string) {
    this.request?.unsubscribe();
    this.lastPrompt = prompt;
    this.loading.set(true);
    this.result.set(null);
    this.error.set(null);
    this.retryable.set(false);
    // The backend owns prompt validation, interpretation and catalog bounds.
    this.request = this.api.ask(prompt).subscribe({
      next: result => {
        this.result.set(result);
        this.loading.set(false);
      },
      error: (failure: HttpErrorResponse) => {
        if (failure.status === 400) {
          const body = failure.error;
          this.error.set(body?.status === 400 && typeof body.message === 'string'
            ? body.message : 'Please revise your catalog request and try again.');
        } else {
          this.retryable.set(true);
          this.error.set(failure.status === 504
            ? 'The catalog assistant took too long to respond. Please try again.'
            : [0, 502, 503].includes(failure.status)
              ? 'The catalog assistant is unavailable. Please try again.'
              : 'We could not complete your catalog request. Please try again.');
        }
        this.loading.set(false);
      },
    });
  }
}
