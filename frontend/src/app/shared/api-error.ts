import { HttpErrorResponse } from '@angular/common/http';
import { ApiError } from '../core/models';

export function apiErrorMessage(err: unknown, fallback = 'Something went wrong'): string {
  if (err instanceof HttpErrorResponse) {
    const body = err.error as ApiError | string | null;
    if (body && typeof body === 'object' && body.message) {
      return body.message;
    }
    if (typeof body === 'string' && body.trim()) {
      return body;
    }
    if (err.status === 0) {
      return 'Cannot reach the API gateway. Is it running on port 8080?';
    }
    return err.statusText || fallback;
  }
  return fallback;
}
