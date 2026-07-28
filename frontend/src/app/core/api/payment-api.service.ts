import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Payment, PaymentRequest } from '../models';

@Injectable({ providedIn: 'root' })
export class PaymentApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiBaseUrl}/api/payments`;

  pay(body: PaymentRequest): Observable<Payment> {
    return this.http.post<Payment>(this.base, body);
  }

  getByOrderId(orderId: number): Observable<Payment> {
    return this.http.get<Payment>(`${this.base}/${orderId}`);
  }
}
