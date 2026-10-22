import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { createPlan, fetchDepots } from '../../api/endpoints';
import { queryKeys } from '../../api/queryKeys';
import { errorMessage } from '../../lib/errors';
import { todayIso } from '../../lib/time';

export function NewPlanForm() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const depots = useQuery({ queryKey: queryKeys.depots, queryFn: fetchDepots, staleTime: Infinity });
  const [name, setName] = useState('');
  const [serviceDate, setServiceDate] = useState(todayIso);
  const [depotId, setDepotId] = useState<number | null>(null);
  const selectedDepot = depotId ?? depots.data?.[0]?.id ?? null;

  const create = useMutation({
    mutationFn: createPlan,
    onSuccess: async (plan) => {
      await queryClient.invalidateQueries({ queryKey: queryKeys.plans });
      navigate(`/plans/${plan.id}`);
    },
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (selectedDepot === null || !name.trim()) {
      return;
    }
    create.mutate({ name: name.trim(), serviceDate, depotId: selectedDepot });
  }

  return (
    <form className="form" onSubmit={handleSubmit} aria-labelledby="new-plan-title">
      <h2 id="new-plan-title" className="section-title">
        New plan
      </h2>
      <label className="field">
        <span className="field-label">Name</span>
        <input
          required
          maxLength={120}
          placeholder="e.g. Tuesday West Side"
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
      </label>
      <label className="field">
        <span className="field-label">Service date</span>
        <input type="date" required value={serviceDate} onChange={(event) => setServiceDate(event.target.value)} />
      </label>
      <label className="field">
        <span className="field-label">Depot</span>
        <select
          required
          value={selectedDepot ?? ''}
          disabled={!depots.data}
          onChange={(event) => setDepotId(Number(event.target.value))}
        >
          {depots.isPending && <option value="">Loading depots…</option>}
          {depots.data?.map((depot) => (
            <option key={depot.id} value={depot.id}>
              {depot.name}
            </option>
          ))}
        </select>
      </label>
      {depots.isError && (
        <p className="form-error" role="alert">
          Could not load depots: {errorMessage(depots.error)}
        </p>
      )}
      {create.isError && (
        <p className="form-error" role="alert">
          {errorMessage(create.error)}
        </p>
      )}
      <button type="submit" className="button button-primary" disabled={create.isPending || selectedDepot === null}>
        {create.isPending ? 'Creating…' : 'Create plan'}
      </button>
    </form>
  );
}
