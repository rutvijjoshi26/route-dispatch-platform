import { queryOptions } from '@tanstack/react-query';
import {
  fetchNearestVehicles,
  fetchPlan,
  fetchPositions,
  fetchRevisions,
  fetchTrail,
} from '../../api/endpoints';
import { queryKeys } from '../../api/queryKeys';

export const planQuery = (planId: number) =>
  queryOptions({
    queryKey: queryKeys.plan(planId),
    queryFn: ({ signal }) => fetchPlan(planId, signal),
    // Live updates drive refreshes; polling is only a safety net for a dropped socket.
    refetchInterval: (query) => {
      const plan = query.state.data;
      if (!plan) return false;
      if (plan.optimizing || plan.status === 'PLANNING') return 3000;
      return plan.status === 'DISPATCHED' ? 30000 : false;
    },
  });

export const revisionsQuery = (planId: number) =>
  queryOptions({
    queryKey: queryKeys.revisions(planId),
    queryFn: () => fetchRevisions(planId),
  });

export const positionsQuery = (planId: number) =>
  queryOptions({
    queryKey: queryKeys.positions(planId),
    queryFn: () => fetchPositions(planId),
  });

export const trailQuery = (planId: number, vehicleId: number) =>
  queryOptions({
    queryKey: queryKeys.trail(planId, vehicleId),
    queryFn: () => fetchTrail(planId, vehicleId),
    refetchInterval: 15000,
  });

export const nearestQuery = (planId: number, lat: number, lon: number) =>
  queryOptions({
    queryKey: queryKeys.nearest(planId, lat, lon),
    queryFn: () => fetchNearestVehicles(planId, lat, lon),
  });
