import { Injectable, inject } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { CartItem } from '../models';

const CART_KEY = 'bookstore_cart';

@Injectable({ providedIn: 'root' })
export class CartService {
  private readonly itemsSubject = new BehaviorSubject<CartItem[]>(this.readCart());

  readonly items$ = this.itemsSubject.asObservable();

  get items(): CartItem[] {
    return this.itemsSubject.value;
  }

  get itemCount(): number {
    return this.items.reduce((sum, i) => sum + i.quantity, 0);
  }

  get subtotal(): number {
    return this.items.reduce((sum, i) => sum + i.unitPrice * i.quantity, 0);
  }

  add(bookId: number, title: string, unitPrice: number, quantity = 1): void {
    const items = [...this.items];
    const existing = items.find((i) => i.bookId === bookId);
    if (existing) {
      existing.quantity += quantity;
    } else {
      items.push({ bookId, title, unitPrice, quantity });
    }
    this.save(items);
  }

  updateQuantity(bookId: number, quantity: number): void {
    if (quantity < 1) {
      this.remove(bookId);
      return;
    }
    const items = this.items.map((i) =>
      i.bookId === bookId ? { ...i, quantity } : i
    );
    this.save(items);
  }

  remove(bookId: number): void {
    this.save(this.items.filter((i) => i.bookId !== bookId));
  }

  clear(): void {
    this.save([]);
  }

  private save(items: CartItem[]): void {
    localStorage.setItem(CART_KEY, JSON.stringify(items));
    this.itemsSubject.next(items);
  }

  private readCart(): CartItem[] {
    const raw = localStorage.getItem(CART_KEY);
    if (!raw) {
      return [];
    }
    try {
      return JSON.parse(raw) as CartItem[];
    } catch {
      return [];
    }
  }
}
