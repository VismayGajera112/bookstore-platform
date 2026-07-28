import { Component, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { CartService } from '../../core/cart/cart.service';
import { map } from 'rxjs';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, AsyncPipe],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.css'
})
export class NavbarComponent {
  private readonly auth = inject(AuthService);
  private readonly cart = inject(CartService);

  readonly user$ = this.auth.user$;
  readonly isAdmin$ = this.auth.user$.pipe(map(() => this.auth.isAdmin));
  readonly cartCount$ = this.cart.items$.pipe(
    map((items) => items.reduce((sum, i) => sum + i.quantity, 0))
  );

  logout(): void {
    this.auth.logout();
  }
}
