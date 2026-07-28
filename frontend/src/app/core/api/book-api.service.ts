import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Book, Page } from '../models';

@Injectable({ providedIn: 'root' })
export class BookApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/books`;

  list(keyword = '', page = 0, size = 20): Observable<Page<Book>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', 'id,asc');
    if (keyword.trim()) {
      params = params.set('keyword', keyword.trim());
    }
    return this.http.get<Page<Book>>(this.base, { params });
  }

  getById(id: number): Observable<Book> {
    return this.http.get<Book>(`${this.base}/${id}`);
  }
}
