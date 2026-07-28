import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Book, BookRequest, CoverUploadResponse, Page } from '../models';

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

  create(body: BookRequest): Observable<Book> {
    return this.http.post<Book>(this.base, body);
  }

  update(id: number, body: BookRequest): Observable<Book> {
    return this.http.put<Book>(`${this.base}/${id}`, body);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }

  requestCoverUpload(id: number, contentType: string): Observable<CoverUploadResponse> {
    const params = new HttpParams().set('contentType', contentType);
    return this.http.post<CoverUploadResponse>(`${this.base}/${id}/cover`, null, { params });
  }
}
