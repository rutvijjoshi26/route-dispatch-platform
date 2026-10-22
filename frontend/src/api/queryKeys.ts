export const queryKeys = {
  depots: ['depots'] as const,
  plans: ['plan-list'] as const,
  plan: (planId: number) => ['plan', planId] as const,
  revisions: (planId: number) => ['plan', planId, 'revisions'] as const,
  positions: (planId: number) => ['plan', planId, 'positions'] as const,
  nearest: (planId: number, lat: number, lon: number) => ['plan', planId, 'nearest', lat, lon] as const,
  trail: (planId: number, vehicleId: number) => ['plan', planId, 'trail', vehicleId] as const,
};
