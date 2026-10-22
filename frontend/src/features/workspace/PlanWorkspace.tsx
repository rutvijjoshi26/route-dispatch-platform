import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { queryKeys } from '../../api/queryKeys';
import type { Plan, Stop } from '../../api/types';
import { errorMessage } from '../../lib/errors';
import { plural } from '../../lib/format';
import type { ClockAnchor } from '../../lib/planClock';
import { AddOrderPanel } from './addOrder/AddOrderPanel';
import type { MapPoint } from './addOrder/validation';
import { DispatchDialog } from './dialogs/DispatchDialog';
import { ImportCsvDialog } from './dialogs/ImportCsvDialog';
import { WorkspaceHeader } from './header/WorkspaceHeader';
import type { PlanAction } from './header/actionAvailability';
import { useWorkspaceLive } from './live/useWorkspaceLive';
import { PlanMap } from './map/PlanMap';
import { NoticeBanner } from './NoticeBanner';
import { ActivityTab } from './panel/ActivityTab';
import { HistoryTab } from './panel/HistoryTab';
import { RoutesTab } from './panel/RoutesTab';
import { SidePanel } from './panel/SidePanel';
import { UnassignedTab } from './panel/UnassignedTab';
import { trailQuery } from './queries';
import { SolveProgressBanner } from './SolveProgressBanner';
import { usePlanActions } from './usePlanActions';

interface PlanWorkspaceProps {
  plan: Plan;
  fetchedAt: number;
}

type OpenDialog = 'import' | 'dispatch' | null;

export function PlanWorkspace({ plan, fetchedAt }: PlanWorkspaceProps) {
  const queryClient = useQueryClient();
  const live = useWorkspaceLive(plan);
  const { optimize, replan, dispatch } = usePlanActions(plan.id);
  // Only the clock display and timeline now-lines tick; the workspace itself re-renders on data changes.
  const clockAnchor = useMemo<ClockAnchor | null>(
    () => (plan.clock ? { clock: plan.clock, anchoredAt: fetchedAt, running: plan.status === 'DISPATCHED' } : null),
    [plan.clock, plan.status, fetchedAt],
  );

  const [selectedVehicleId, setSelectedVehicleId] = useState<number | null>(null);
  const [showTrail, setShowTrail] = useState(false);
  const [dialog, setDialog] = useState<OpenDialog>(null);
  const [addingOrder, setAddingOrder] = useState(false);
  const [pickedPoint, setPickedPoint] = useState<MapPoint | null>(null);

  const trail = useQuery({
    ...trailQuery(plan.id, selectedVehicleId ?? 0),
    enabled: showTrail && selectedVehicleId !== null,
  });

  const stopAddingOrder = () => {
    setAddingOrder(false);
    setPickedPoint(null);
  };

  useEffect(() => {
    if (!addingOrder) return undefined;
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setAddingOrder(false);
        setPickedPoint(null);
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [addingOrder]);

  const pendingAction: PlanAction | null = optimize.isPending
    ? 'optimize'
    : replan.isPending
      ? 'replan'
      : dispatch.isPending
        ? 'dispatch'
        : null;
  const actionError = optimize.error ?? replan.error;
  const solving = plan.optimizing || plan.status === 'PLANNING' || live.solveProgress !== null;

  function handleAction(action: PlanAction) {
    switch (action) {
      case 'import':
      case 'dispatch':
        setDialog(action);
        break;
      case 'optimize':
        optimize.mutate();
        break;
      case 'replan':
        replan.mutate();
        break;
      case 'addOrder':
        if (addingOrder) {
          stopAddingOrder();
        } else {
          setAddingOrder(true);
        }
        break;
    }
  }

  const selectRoute = useCallback((vehicleId: number) => {
    setSelectedVehicleId((current) => (current === vehicleId ? null : vehicleId));
    setShowTrail(false);
  }, []);

  function handleOrderAdded(stop: Stop) {
    stopAddingOrder();
    live.notify('success', `Order ${stop.orderRef} added.${plan.version > 0 ? ' Re-planning routes…' : ''}`);
    void queryClient.invalidateQueries({ queryKey: queryKeys.plan(plan.id) });
  }

  function handleImported(count: number) {
    setDialog(null);
    live.notify('success', `Imported ${plural(count, 'order')}.`);
    void queryClient.invalidateQueries({ queryKey: queryKeys.plan(plan.id) });
    void queryClient.invalidateQueries({ queryKey: queryKeys.plans });
  }

  const routesWithStops = plan.routes.filter((route) => route.stops.length > 0).length;

  return (
    <div className="workspace">
      <WorkspaceHeader
        plan={plan}
        clock={clockAnchor}
        connection={live.connection}
        pendingAction={pendingAction}
        addingOrder={addingOrder}
        onAction={handleAction}
      />
      <div className="banners">
        {solving && <SolveProgressBanner progress={live.solveProgress} />}
        {live.notice && <NoticeBanner notice={live.notice} onDismiss={live.dismissNotice} />}
        {actionError && (
          <NoticeBanner
            notice={{ id: 0, tone: 'danger', message: errorMessage(actionError) }}
            onDismiss={() => {
              optimize.reset();
              replan.reset();
            }}
          />
        )}
      </div>
      <div className="workspace-body">
        <PlanMap
          plan={plan}
          positions={live.positions}
          selectedVehicleId={selectedVehicleId}
          changedVehicles={live.changedVehicles}
          trail={showTrail ? (trail.data?.path ?? null) : null}
          picking={addingOrder}
          pickedPoint={pickedPoint}
          onPick={setPickedPoint}
          onSelectRoute={selectRoute}
        />
        {addingOrder ? (
          <AddOrderPanel
            plan={plan}
            point={pickedPoint}
            clock={clockAnchor}
            onAdded={handleOrderAdded}
            onCancel={stopAddingOrder}
          />
        ) : (
          <SidePanel
            tabs={[
              {
                id: 'routes',
                label: 'Routes',
                count: routesWithStops,
                content: (
                  <RoutesTab
                    plan={plan}
                    selectedVehicleId={selectedVehicleId}
                    changedVehicles={live.changedVehicles}
                    clock={clockAnchor}
                    showTrail={showTrail}
                    onSelect={selectRoute}
                    onToggleTrail={setShowTrail}
                  />
                ),
              },
              {
                id: 'unassigned',
                label: 'Unassigned',
                count: plan.unassigned.length,
                content: <UnassignedTab stops={plan.unassigned} />,
              },
              {
                id: 'activity',
                label: 'Activity',
                count: live.activity.length,
                content: <ActivityTab entries={live.activity} />,
              },
              { id: 'history', label: 'History', content: <HistoryTab planId={plan.id} /> },
            ]}
          />
        )}
      </div>
      {dialog === 'import' && (
        <ImportCsvDialog planId={plan.id} onClose={() => setDialog(null)} onImported={handleImported} />
      )}
      {dialog === 'dispatch' && (
        <DispatchDialog
          routeCount={routesWithStops}
          pending={dispatch.isPending}
          error={dispatch.error}
          onConfirm={(timeScale) => dispatch.mutate(timeScale, { onSuccess: () => setDialog(null) })}
          onClose={() => {
            dispatch.reset();
            setDialog(null);
          }}
        />
      )}
    </div>
  );
}
