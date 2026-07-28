import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthorApiService } from '../../core/api/author-api.service';
import { BookApiService } from '../../core/api/book-api.service';
import { Author, Book, BookRequest } from '../../core/models';
import { apiErrorMessage } from '../../shared/api-error';
import { coverImageUrl } from '../../shared/cover-url';
import { browserUploadUrl } from '../../shared/upload-url';

@Component({
  selector: 'app-admin-book-form',
  standalone: true,
  imports: [ReactiveFormsModule, FormsModule, RouterLink],
  templateUrl: './admin-book-form.component.html',
  styleUrl: './admin-book-form.component.css'
})
export class AdminBookFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly booksApi = inject(BookApiService);
  private readonly authorsApi = inject(AuthorApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  bookId: number | null = null;
  book: Book | null = null;
  authors: Author[] = [];
  loading = false;
  saving = false;
  uploading = false;
  creatingAuthor = false;
  error = '';
  coverMessage = '';
  newAuthorName = '';

  readonly form = this.fb.nonNullable.group({
    title: ['', [Validators.required, Validators.maxLength(255)]],
    authorId: [0 as number, [Validators.required, Validators.min(1)]],
    isbn: [''],
    price: [0, [Validators.required, Validators.min(0)]],
    stock: [0, [Validators.required, Validators.min(0)]]
  });

  get isEdit(): boolean {
    return this.bookId != null;
  }

  get displayCoverUrl(): string | null {
    return coverImageUrl(this.book?.coverUrl);
  }

  ngOnInit(): void {
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.bookId = Number(idParam);
      if (Number.isNaN(this.bookId)) {
        this.error = 'Invalid book id';
        return;
      }
    }
    this.loadAuthors(() => {
      if (this.bookId != null) {
        this.loadBook(this.bookId);
      }
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const raw = this.form.getRawValue();
    const isbn = raw.isbn.trim();
    const body: BookRequest = {
      title: raw.title.trim(),
      authorId: Number(raw.authorId),
      isbn: isbn || null,
      price: Number(raw.price),
      stock: Number(raw.stock),
      coverUrl: this.book?.coverUrl ?? null
    };

    this.saving = true;
    this.error = '';

    if (this.bookId != null) {
      this.booksApi.update(this.bookId, body).subscribe({
        next: (book) => {
          this.book = book;
          this.saving = false;
          this.router.navigate(['/admin/books']);
        },
        error: (err) => {
          this.error = apiErrorMessage(err, 'Failed to update book');
          this.saving = false;
        }
      });
    } else {
      this.booksApi.create(body).subscribe({
        next: (book) => {
          this.saving = false;
          this.router.navigate(['/admin/books', book.id, 'edit']);
        },
        error: (err) => {
          this.error = apiErrorMessage(err, 'Failed to create book');
          this.saving = false;
        }
      });
    }
  }

  createAuthor(): void {
    const name = this.newAuthorName.trim();
    if (!name) {
      return;
    }
    this.creatingAuthor = true;
    this.error = '';
    this.authorsApi.create({ name }).subscribe({
      next: (author) => {
        this.authors = [...this.authors, author].sort((a, b) => a.name.localeCompare(b.name));
        this.form.patchValue({ authorId: author.id });
        this.newAuthorName = '';
        this.creatingAuthor = false;
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Failed to create author');
        this.creatingAuthor = false;
      }
    });
  }

  async onCoverSelected(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || this.bookId == null) {
      return;
    }
    const allowed = ['image/jpeg', 'image/png', 'image/webp'];
    if (!allowed.includes(file.type)) {
      this.coverMessage = 'Use a JPEG, PNG, or WebP image.';
      return;
    }

    this.uploading = true;
    this.coverMessage = '';
    this.error = '';

    try {
      const res = await firstValueFrom(this.booksApi.requestCoverUpload(this.bookId, file.type));
      const url = browserUploadUrl(res.uploadUrl);
      const response = await fetch(url, {
        method: 'PUT',
        headers: { 'Content-Type': file.type },
        body: file
      });
      if (!response.ok) {
        throw new Error(`Upload failed (${response.status})`);
      }
      this.book = await firstValueFrom(this.booksApi.getById(this.bookId));
      this.coverMessage = 'Cover uploaded.';
    } catch (err: unknown) {
      if (err instanceof Error && !(err as { status?: number }).status) {
        this.error = err.message;
      } else {
        this.error = apiErrorMessage(err, 'Cover upload failed');
      }
    } finally {
      this.uploading = false;
    }
  }

  private loadAuthors(after?: () => void): void {
    this.authorsApi.list(0, 100).subscribe({
      next: (page) => {
        this.authors = page.content;
        after?.();
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Failed to load authors');
        after?.();
      }
    });
  }

  private loadBook(id: number): void {
    this.loading = true;
    this.booksApi.getById(id).subscribe({
      next: (book) => {
        this.book = book;
        this.form.patchValue({
          title: book.title,
          authorId: book.authorId,
          isbn: book.isbn ?? '',
          price: book.price,
          stock: book.stock
        });
        this.loading = false;
      },
      error: (err) => {
        this.error = apiErrorMessage(err, 'Failed to load book');
        this.loading = false;
      }
    });
  }
}
