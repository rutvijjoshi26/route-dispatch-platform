import { request } from './client';
import type {
  CreatePlanRequest,
  Depot,
  ImportResult,
  LoginRequest,
  LoginResponse,
  NearbyVehicle,
  NewOrderRequest,
  Plan,
  PlanSummary,
  Revision,
  Stop,
  VehiclePosition,
  VehicleTrail,
} from './types';

export const login = (credentials: LoginRequest) =>
  request<LoginResponse>('/api/auth/login', { method: 'POST', body: credentials });

export const fetchDepots = () => request<Depot[]>('/api/fleet/depots');

export const fetchPlans = () => request<PlanSummary[]>('/api/plans');

export const createPlan = (body: CreatePlanRequest) =>
  request<Plan>('/api/plans', { method: 'POST', body });

export const fetchPlan = (planId: number, signal?: AbortSignal) =>
  request<Plan>(`/api/plans/${planId}`, { signal });

export function importOrders(planId: number, file: File): Promise<ImportResult> {
  const form = new FormData();
  form.append('file', file);
  return request<ImportResult>(`/api/plans/${planId}/orders/import`, { method: 'POST', body: form });
}

export const addOrder = (planId: number, body: NewOrderRequest) =>
  request<Stop>(`/api/plans/${planId}/orders`, { method: 'POST', body });

export const optimizePlan = (planId: number) =>
  request<void>(`/api/plans/${planId}/optimize`, { method: 'POST' });

export const dispatchPlan = (planId: number, timeScale: number) =>
  request<Plan>(`/api/plans/${planId}/dispatch`, { method: 'POST', body: { timeScale } });

export const replanPlan = (planId: number) =>
  request<void>(`/api/plans/${planId}/replan`, { method: 'POST' });

export const fetchRevisions = (planId: number) =>
  request<Revision[]>(`/api/plans/${planId}/revisions`);

export const fetchPositions = (planId: number) =>
  request<VehiclePosition[]>(`/api/plans/${planId}/positions`);

export function fetchNearestVehicles(planId: number, lat: number, lon: number, limit = 3) {
  const query = new URLSearchParams({ lat: String(lat), lon: String(lon), limit: String(limit) });
  return request<NearbyVehicle[]>(`/api/plans/${planId}/vehicles/nearest?${query.toString()}`);
}

export const fetchTrail = (planId: number, vehicleId: number) =>
  request<VehicleTrail>(`/api/plans/${planId}/vehicles/${vehicleId}/trail`);
