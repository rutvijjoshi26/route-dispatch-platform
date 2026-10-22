import type { LoginResponse, Role } from '../api/types';

export interface Session {
  token: string;
  expiresAt: string;
  role: Role;
  displayName: string;
}

const STORAGE_KEY = 'route-dispatch.session';

export function loadSession(now: Date = new Date()): Session | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) {
    return null;
  }
  try {
    const session = JSON.parse(raw) as Session;
    if (!session.token || new Date(session.expiresAt) <= now) {
      clearSession();
      return null;
    }
    return session;
  } catch {
    clearSession();
    return null;
  }
}

export function saveSession(response: LoginResponse): Session {
  const session: Session = {
    token: response.token,
    expiresAt: response.expiresAt,
    role: response.role,
    displayName: response.displayName,
  };
  localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  return session;
}

export function clearSession(): void {
  localStorage.removeItem(STORAGE_KEY);
}
