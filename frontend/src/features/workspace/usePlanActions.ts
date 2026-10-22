import { useMutation, useQueryClient } from '@tanstack/react-query';
import { dispatchPlan, optimizePlan, replanPlan } from '../../api/endpoints';
import { queryKeys } from '../../api/queryKeys';
import type { Plan } from '../../api/types';

/** Plan-level commands; each refreshes the plan so the status and optimizing flag update at once. */
export function usePlanActions(planId: number) {
  const queryClient = useQueryClient();
  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: queryKeys.plan(planId) });
    void queryClient.invalidateQueries({ queryKey: queryKeys.plans });
  };

  const optimize = useMutation({ mutationFn: () => optimizePlan(planId), onSuccess: refresh });
  const replan = useMutation({ mutationFn: () => replanPlan(planId), onSuccess: refresh });
  const dispatch = useMutation({
    mutationFn: (timeScale: number) => dispatchPlan(planId, timeScale),
    onSuccess: (plan: Plan) => {
      queryClient.setQueryData(queryKeys.plan(planId), plan);
      refresh();
    },
  });

  return { optimize, replan, dispatch };
}
