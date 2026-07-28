import { Component, OnInit, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { BookApiService } from '../../core/api/book-api.service';
import { CartService } from '../../core/cart/cart.service';
import { Book } from '../../core/models';
import { apiErrorMessage } from '../../shared/api-error';

@Component({
  selector: 'app-book-detail',
  standalone: true,
  imports: [CurrencyPipe, FormsModule, RouterLink],
  templateUrl: './book-detail.component.html',
  styleUrl: './book-detail.component.css'
})
export class BookDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly booksApi = inject(BookApiService);
  private readonly cart = inject(CartService);

  book: Book | null = null;
  quantity = 1;
  loading = false;
  error = '';
  message = '';

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      this.error = 'Invalid book id';
      return;
    }
    this.loading = true;
    this.booksApi.getById(id).subscribe({
      next: (book) => {
        this.book = book;
        this.loading = false;
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Book not found');
        this.loading = false;
      }
    });
  }

  get coverIsHttp(): boolean {
    const url = this.book?.coverUrl;
    return !!url && (url.startsWith('http://') || url.startsWith('https://'));
  }

  addToCart(): void {
    if (!this.book) {
      return;
    }
    const qty = Math.max(1, Math.floor(this.quantity) || 1);
    if (this.book.stock < 1) {
      this.message = 'Out of stock';
      return;
    }
    this.cart.add(this.book.id, this.book.title, Number(this.book.price), qty);
    this.message = `Added ${qty} to cart.`;
  }
}
