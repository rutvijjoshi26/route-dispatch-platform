import { lazy, Suspense } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router';
import { LoginPage } from './auth/LoginPage';
import { RequireDispatcher } from './auth/RequireDispatcher';
import { NotFoundPage } from './components/NotFoundPage';
import { LoadingState } from './components/StateMessages';
import { PlansPage } from './features/plans/PlansPage';

// The workspace carries the map stack; load it only when a plan is opened.
const WorkspacePage = lazy(() =>
  import('./features/workspace/WorkspacePage').then((module) => ({ default: module.WorkspacePage })),
);

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/" element={<Navigate to="/plans" replace />} />
        <Route
          path="/plans"
          element={
            <RequireDispatcher>
              <PlansPage />
            </RequireDispatcher>
          }
        />
        <Route
          path="/plans/:planId"
          element={
            <RequireDispatcher>
              <Suspense fallback={<LoadingState label="Loading workspace…" />}>
                <WorkspacePage />
              </Suspense>
            </RequireDispatcher>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  );
}
