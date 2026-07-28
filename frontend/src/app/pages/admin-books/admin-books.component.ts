import { Component, OnInit, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BookApiService } from '../../core/api/book-api.service';
import { Book } from '../../core/models';
import { apiErrorMessage } from '../../shared/api-error';
import { coverImageUrl } from '../../shared/cover-url';

@Component({
  selector: 'app-admin-books',
  standalone: true,
  imports: [CurrencyPipe, FormsModule, RouterLink],
  templateUrl: './admin-books.component.html',
  styleUrl: './admin-books.component.css'
})
export class AdminBooksComponent implements OnInit {
  private readonly booksApi = inject(BookApiService);

  books: Book[] = [];
  keyword = '';
  page = 0;
  size = 20;
  totalPages = 0;
  totalElements = 0;
  loading = false;
  error = '';
  deletingId: number | null = null;

  ngOnInit(): void {
    this.load();
  }

  coverUrl(book: Book): string | null {
    return coverImageUrl(book.coverUrl);
  }

  search(): void {
    this.page = 0;
    this.load();
  }

  prev(): void {
    if (this.page > 0) {
      this.page -= 1;
      this.load();
    }
  }

  next(): void {
    if (this.page + 1 < this.totalPages) {
      this.page += 1;
      this.load();
    }
  }

  deleteBook(book: Book): void {
    if (!confirm(`Delete "${book.title}"? This cannot be undone.`)) {
      return;
    }
    this.deletingId = book.id;
    this.error = '';
    this.booksApi.delete(book.id).subscribe({
      next: () => {
        this.deletingId = null;
        this.load();
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Failed to delete book');
        this.deletingId = null;
      }
    });
  }

  private load(): void {
    this.loading = true;
    this.error = '';
    this.booksApi.list(this.keyword, this.page, this.size).subscribe({
      next: (page) => {
        this.books = page.content;
        this.totalPages = page.totalPages;
        this.totalElements = page.totalElements;
        this.page = page.number;
        this.loading = false;
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Failed to load books');
        this.loading = false;
      }
    });
  }
}
