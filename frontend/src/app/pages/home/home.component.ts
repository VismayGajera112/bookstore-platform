import { Component, OnInit, inject } from '@angular/core';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BookApiService } from '../../core/api/book-api.service';
import { Book } from '../../core/models';
import { apiErrorMessage } from '../../shared/api-error';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CurrencyPipe, FormsModule, RouterLink],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css'
})
export class HomeComponent implements OnInit {
  private readonly booksApi = inject(BookApiService);

  books: Book[] = [];
  keyword = '';
  page = 0;
  size = 20;
  totalPages = 0;
  totalElements = 0;
  loading = false;
  error = '';

  ngOnInit(): void {
    this.load();
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
