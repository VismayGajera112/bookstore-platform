import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';
import { adminGuard } from './core/auth/admin.guard';
import { HomeComponent } from './pages/home/home.component';
import { LoginComponent } from './pages/login/login.component';
import { RegisterComponent } from './pages/register/register.component';
import { BookDetailComponent } from './pages/book-detail/book-detail.component';
import { CartComponent } from './pages/cart/cart.component';
import { CheckoutComponent } from './pages/checkout/checkout.component';
import { OrderResultComponent } from './pages/order-result/order-result.component';
import { AdminBooksComponent } from './pages/admin-books/admin-books.component';
import { AdminBookFormComponent } from './pages/admin-book-form/admin-book-form.component';

export const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  { path: 'books/:id', component: BookDetailComponent },
  { path: 'cart', component: CartComponent },
  { path: 'checkout', component: CheckoutComponent, canActivate: [authGuard] },
  { path: 'orders/:id', component: OrderResultComponent, canActivate: [authGuard] },
  { path: 'admin/books', component: AdminBooksComponent, canActivate: [adminGuard] },
  { path: 'admin/books/new', component: AdminBookFormComponent, canActivate: [adminGuard] },
  { path: 'admin/books/:id/edit', component: AdminBookFormComponent, canActivate: [adminGuard] },
  { path: '**', redirectTo: '' }
];
