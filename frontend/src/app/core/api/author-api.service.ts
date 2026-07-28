import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Author, AuthorRequest, Page } from '../models';

@Injectable({ providedIn: 'root' })
export class AuthorApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/authors`;

  list(page = 0, size = 100): Observable<Page<Author>> {
    const params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', 'name,asc');
    return this.http.get<Page<Author>>(this.base, { params });
  }

  create(body: AuthorRequest): Observable<Author> {
    return this.http.post<Author>(this.base, body);
  }
}
