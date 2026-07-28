import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginResponse, User } from '../models';

const TOKEN_KEY = 'bookstore_token';
const USER_KEY = 'bookstore_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly userSubject = new BehaviorSubject<User | null>(this.readStoredUser());

  readonly user$ = this.userSubject.asObservable();

  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  get currentUser(): User | null {
    return this.userSubject.value;
  }

  get isLoggedIn(): boolean {
    return !!this.token;
  }

  get isAdmin(): boolean {
    const fromUser = this.normalizeRole(this.currentUser?.role);
    if (fromUser === 'ADMIN') {
      return true;
    }
    // Fallback: role claim in JWT (handles stale/missing localStorage user).
    return this.normalizeRole(this.roleFromToken()) === 'ADMIN';
  }

  /** Normalize ROLE_ADMIN / admin → ADMIN. */
  private normalizeRole(role: string | null | undefined): string | null {
    if (!role) {
      return null;
    }
    const upper = role.trim().toUpperCase();
    return upper.startsWith('ROLE_') ? upper.slice(5) : upper;
  }

  private roleFromToken(): string | null {
    const token = this.token;
    if (!token) {
      return null;
    }
    try {
      const payload = token.split('.')[1];
      if (!payload) {
        return null;
      }
      const json = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')));
      return typeof json.role === 'string' ? json.role : null;
    } catch {
      return null;
    }
  }

  register(username: string, email: string, password: string): Observable<User> {
    return this.http.post<User>(`${environment.apiBaseUrl}/api/auth/register`, {
      username,
      email,
      password
    });
  }

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>(`${environment.apiBaseUrl}/api/auth/login`, { username, password })
      .pipe(tap((res) => this.persistSession(res.token, res.user)));
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.userSubject.next(null);
    this.router.navigate(['/login']);
  }

  loadMe(): Observable<User> {
    return this.http.get<User>(`${environment.apiBaseUrl}/api/users/me`).pipe(
      tap((user) => {
        localStorage.setItem(USER_KEY, JSON.stringify(user));
        this.userSubject.next(user);
      })
    );
  }

  clearSession(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.userSubject.next(null);
  }

  private persistSession(token: string, user: User): void {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
    this.userSubject.next(user);
  }

  private readStoredUser(): User | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as User;
    } catch {
      return null;
    }
  }
}
