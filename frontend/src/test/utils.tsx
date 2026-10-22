import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderOptions } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';
import type { Plan, Route, Stop } from '../api/types';

export function renderWithClient(ui: ReactElement, options?: RenderOptions) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  );
  return { client, ...render(ui, { wrapper, ...options }) };
}

export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
  });
}

export function makeStop(overrides: Partial<Stop> = {}): Stop {
  return {
    id: 1,
    orderRef: 'CHI-1001',
    customerName: 'Sam Hernandez',
    address: '789 N Kimball Ave, Chicago, IL',
    lat: 41.896,
    lon: -87.71,
    windowStart: '09:00:00',
    windowEnd: '17:00:00',
    size: 'SMALL',
    demand: 1,
    status: 'PENDING',
    vehicleId: 1,
    sequence: 0,
    plannedArrival: '09:30:00',
    plannedDeparture: '09:34:00',
    committedEta: null,
    actualArrival: null,
    actualDeparture: null,
    late: false,
    addedMidShift: false,
    ...overrides,
  };
}

export function makeRoute(vehicleId: number, stops: Stop[], overrides: Partial<Route> = {}): Route {
  return {
    vehicleId,
    vehicleName: `Van ${vehicleId}`,
    capacity: 60,
    shiftStart: '08:00:00',
    shiftEnd: '16:00:00',
    load: stops.length,
    distanceMeters: 10000,
    travelSeconds: 3600,
    departDepot: '08:00:00',
    returnDepot: '15:00:00',
    path: [],
    stops,
    ...overrides,
  };
}

export function makePlan(routes: Route[], overrides: Partial<Plan> = {}): Plan {
  const stops = routes.flatMap((route) => route.stops);
  return {
    id: 7,
    name: 'Test plan',
    serviceDate: '2026-10-21',
    status: 'PLANNED',
    version: 1,
    score: '0hard/0medium/-100soft',
    optimizing: false,
    depot: { id: 1, name: 'Depot', address: '1 Main St', lat: 41.88, lon: -87.68 },
    clock: null,
    totals: {
      stops: stops.length,
      assigned: stops.length,
      unassigned: 0,
      delivered: 0,
      failed: 0,
      predictedLate: 0,
      distanceMeters: 0,
      travelSeconds: 0,
    },
    routes,
    unassigned: [],
    ...overrides,
  };
}
