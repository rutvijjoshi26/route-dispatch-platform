import { ApiError } from '../api/client';

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.detail;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return 'Something went wrong.';
}

export function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.status === 404;
}
