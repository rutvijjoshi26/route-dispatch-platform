/**
 * Route palette drawn from the Okabe-Ito and Paul Tol sets, which stay distinguishable
 * under common color-vision deficiencies. Red and black are reserved for failed and unassigned stops.
 */
export const ROUTE_PALETTE = [
  '#0072B2',
  '#E69F00',
  '#009E73',
  '#CC79A7',
  '#56B4E9',
  '#882255',
  '#999933',
  '#332288',
  '#44AA99',
  '#AA4499',
] as const;

export const FAILED_COLOR = '#C62828';
export const UNASSIGNED_COLOR = '#3A3A3A';

/** Colors are keyed by vehicle id so a van keeps its color across re-plans. */
export function routeColor(vehicleId: number): string {
  const index = (((vehicleId - 1) % ROUTE_PALETTE.length) + ROUTE_PALETTE.length) % ROUTE_PALETTE.length;
  return ROUTE_PALETTE[index] ?? ROUTE_PALETTE[0];
}
