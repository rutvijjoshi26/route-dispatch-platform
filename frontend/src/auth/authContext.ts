import { createContext } from 'react';
import type { LoginRequest } from '../api/types';
import type { Session } from './session';

export interface AuthState {
  session: Session | null;
  login: (credentials: LoginRequest) => Promise<Session>;
  logout: () => void;
}

export const AuthContext = createContext<AuthState | null>(null);
