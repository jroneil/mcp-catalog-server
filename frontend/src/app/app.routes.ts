import { Routes } from '@angular/router';
import { CatalogSearchComponent } from './catalog-search';
import { CatalogDetailComponent } from './catalog-detail';

export const routes: Routes = [
  { path: '', pathMatch: 'full', component: CatalogSearchComponent, title: 'Catalog | Products & Services' },
  { path: 'catalog/:id', component: CatalogDetailComponent, title: 'Catalog item | Products & Services' },
  { path: '**', redirectTo: '' },
];
