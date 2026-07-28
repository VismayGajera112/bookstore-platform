import { Component, OnInit, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { OrderApiService } from '../../core/api/order-api.service';
import { PaymentApiService } from '../../core/api/payment-api.service';
import { Order, Payment } from '../../core/models';
import { apiErrorMessage } from '../../shared/api-error';

@Component({
  selector: 'app-order-result',
  standalone: true,
  imports: [CurrencyPipe, RouterLink],
  templateUrl: './order-result.component.html',
  styleUrl: './order-result.component.css'
})
export class OrderResultComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly ordersApi = inject(OrderApiService);
  private readonly paymentsApi = inject(PaymentApiService);

  order: Order | null = null;
  payment: Payment | null = null;
  error = '';
  payHint = false;
  loading = true;

  ngOnInit(): void {
    this.payHint = this.route.snapshot.queryParamMap.get('payError') === '1';
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      this.error = 'Invalid order id';
      this.loading = false;
      return;
    }

    this.ordersApi.getById(id).subscribe({
      next: (order) => {
        this.order = order;
        this.paymentsApi.getByOrderId(id).subscribe({
          next: (payment) => {
            this.payment = payment;
            this.loading = false;
          },
          error: () => {
            this.loading = false;
          }
        });
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Could not load order');
        this.loading = false;
      }
    });
  }
}
