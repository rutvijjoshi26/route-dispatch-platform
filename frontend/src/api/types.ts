export type Role = 'DISPATCHER' | 'DRIVER';

export type PlanStatus = 'DRAFT' | 'PLANNING' | 'PLANNED' | 'DISPATCHED' | 'COMPLETED';

export type StopStatus = 'PENDING' | 'EN_ROUTE' | 'ARRIVED' | 'DELIVERED' | 'FAILED';

export type PackageSize = 'SMALL' | 'MEDIUM' | 'LARGE';

export type RevisionReason = 'INITIAL' | 'REOPTIMIZE' | 'NEW_ORDER' | 'DRIVER_LATE' | 'MANUAL';

/** A [lat, lon] pair as sent by the API. */
export type LatLonTuple = [number, number];

export interface LoginRequest {
  email: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  role: Role;
  displayName: string;
  vehicleId: number | null;
}

export interface Depot {
  id: number;
  name: string;
  address: string;
  lat: number;
  lon: number;
}

export interface PlanSummary {
  id: number;
  name: string;
  serviceDate: string;
  status: PlanStatus;
  version: number;
  stops: number;
  depotName: string;
  createdAt: string;
}

export interface CreatePlanRequest {
  name: string;
  serviceDate: string;
  depotId: number;
}

export interface PlanClock {
  now: string;
  timeScale: number;
  dispatchedAt: string;
}

export interface PlanTotals {
  stops: number;
  assigned: number;
  unassigned: number;
  delivered: number;
  failed: number;
  predictedLate: number;
  distanceMeters: number;
  travelSeconds: number;
}

export interface Stop {
  id: number;
  orderRef: string;
  customerName: string;
  address: string;
  lat: number;
  lon: number;
  windowStart: string;
  windowEnd: string;
  size: PackageSize;
  demand: number;
  status: StopStatus;
  vehicleId: number | null;
  sequence: number | null;
  plannedArrival: string | null;
  plannedDeparture: string | null;
  committedEta: string | null;
  actualArrival: string | null;
  actualDeparture: string | null;
  late: boolean;
  addedMidShift: boolean;
}

export interface Route {
  vehicleId: number;
  vehicleName: string;
  capacity: number;
  shiftStart: string;
  shiftEnd: string;
  load: number;
  distanceMeters: number;
  travelSeconds: number;
  departDepot: string | null;
  returnDepot: string | null;
  path: LatLonTuple[];
  stops: Stop[];
}

export interface Plan {
  id: number;
  name: string;
  serviceDate: string;
  status: PlanStatus;
  version: number;
  score: string | null;
  optimizing: boolean;
  depot: Depot;
  clock: PlanClock | null;
  totals: PlanTotals;
  routes: Route[];
  unassigned: Stop[];
}

export interface NewOrderRequest {
  orderRef: string;
  customerName: string;
  address: string;
  lat: number;
  lon: number;
  windowStart: string;
  windowEnd: string;
  size: PackageSize;
}

export interface ImportResult {
  imported: number;
}

export interface Revision {
  version: number;
  reason: RevisionReason;
  detail: string | null;
  createdAt: string;
  solveMillis: number;
  totalMillis: number;
  score: string | null;
  travelSeconds: number;
  distanceMeters: number;
  pinnedStops: number;
  changedVehicles: number;
}

export interface VehiclePosition {
  vehicleId: number;
  lat: number;
  lon: number;
  speedMps: number | null;
  headingDeg: number | null;
  recordedAt: string;
}

export interface NearbyVehicle {
  vehicleId: number;
  lat: number;
  lon: number;
  recordedAt: string;
  distanceMeters: number;
}

export interface VehicleTrail {
  vehicleId: number;
  path: LatLonTuple[];
}

/** Field-level validation error from a 400 problem. */
export interface FieldError {
  field: string;
  message: string;
}

/** CSV row error from an import 400 problem; row 0 is a file-level problem. */
export interface RowError {
  row: number;
  message: string;
}

/** RFC 9457 problem details body. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: Array<FieldError | RowError>;
}
