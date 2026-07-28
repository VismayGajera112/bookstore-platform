import { Component, inject } from '@angular/core';
import { AsyncPipe, CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { CartService } from '../../core/cart/cart.service';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-cart',
  standalone: true,
  imports: [AsyncPipe, CurrencyPipe, FormsModule, RouterLink],
  templateUrl: './cart.component.html',
  styleUrl: './cart.component.css'
})
export class CartComponent {
  private readonly cart = inject(CartService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly items$ = this.cart.items$;

  get subtotal(): number {
    return this.cart.subtotal;
  }

  updateQty(bookId: number, quantity: number | string): void {
    this.cart.updateQuantity(bookId, Number(quantity));
  }

  remove(bookId: number): void {
    this.cart.remove(bookId);
  }

  checkout(): void {
    if (!this.auth.isLoggedIn) {
      this.router.navigate(['/login'], { queryParams: { returnUrl: '/checkout' } });
      return;
    }
    this.router.navigate(['/checkout']);
  }
}
