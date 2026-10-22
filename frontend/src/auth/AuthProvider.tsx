import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { configureClient } from '../api/client';
import { login as loginRequest } from '../api/endpoints';
import type { LoginRequest } from '../api/types';
import { AuthContext, type AuthState } from './authContext';
import { clearSession, loadSession, saveSession, type Session } from './session';

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [session, setSession] = useState<Session | null>(() => {
    const restored = loadSession();
    configureClient({ getToken: () => restored?.token ?? null });
    return restored;
  });

  const logout = useCallback(() => {
    clearSession();
    configureClient({ getToken: () => null });
    setSession(null);
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    configureClient({ onUnauthorized: logout });
  }, [logout]);

  const login = useCallback(async (credentials: LoginRequest) => {
    const next = saveSession(await loginRequest(credentials));
    configureClient({ getToken: () => next.token });
    setSession(next);
    return next;
  }, []);

  const value = useMemo<AuthState>(() => ({ session, login, logout }), [session, login, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
