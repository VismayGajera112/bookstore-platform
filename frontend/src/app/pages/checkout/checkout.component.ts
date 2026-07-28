import { Component, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CartService } from '../../core/cart/cart.service';
import { OrderApiService } from '../../core/api/order-api.service';
import { PaymentApiService } from '../../core/api/payment-api.service';
import { apiErrorMessage } from '../../shared/api-error';

@Component({
  selector: 'app-checkout',
  standalone: true,
  imports: [CurrencyPipe, FormsModule, RouterLink],
  templateUrl: './checkout.component.html',
  styleUrl: './checkout.component.css'
})
export class CheckoutComponent {
  private readonly cart = inject(CartService);
  private readonly ordersApi = inject(OrderApiService);
  private readonly paymentsApi = inject(PaymentApiService);
  private readonly router = inject(Router);

  cardLast4 = '4242';
  loading = false;
  error = '';

  get items() {
    return this.cart.items;
  }

  get subtotal(): number {
    return this.cart.subtotal;
  }

  placeOrderAndPay(): void {
    if (this.items.length === 0) {
      this.error = 'Cart is empty';
      return;
    }
    if (!/^\d{4}$/.test(this.cardLast4)) {
      this.error = 'Card last 4 must be exactly 4 digits';
      return;
    }

    this.loading = true;
    this.error = '';

    const body = {
      items: this.items.map((i) => ({ bookId: i.bookId, quantity: i.quantity }))
    };

    this.ordersApi.place(body).subscribe({
      next: (order) => {
        this.paymentsApi
          .pay({ orderId: order.id, cardLast4: this.cardLast4, simulateFailure: false })
          .subscribe({
            next: () => {
              this.cart.clear();
              this.loading = false;
              this.router.navigate(['/orders', order.id]);
            },
            error: (err) => {
              this.error =
                apiErrorMessage(err, 'Payment failed') +
                ` (order #${order.id} was created — check status on the order page)`;
              this.loading = false;
              this.cart.clear();
              this.router.navigate(['/orders', order.id], {
                queryParams: { payError: '1' }
              });
            }
          });
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Could not place order');
        this.loading = false;
      }
    });
  }
}
