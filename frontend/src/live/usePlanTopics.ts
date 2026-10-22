import { useEffect, useRef, useState } from 'react';
import { useAuth } from '../auth/useAuth';
import type { VehiclePosition } from '../api/types';
import type { PlanUpdateEnvelope, StopEvent } from './messages';
import { createStompClient } from './stompClient';

export interface PlanTopicHandlers {
  /** Called on every (re)connect so callers can catch up on anything missed while offline. */
  onConnected: () => void;
  onUpdate: (message: PlanUpdateEnvelope) => void;
  onStop: (event: StopEvent) => void;
  onPosition: (position: VehiclePosition) => void;
}

/** Delivers a frame's JSON body, dropping frames that are not valid JSON rather than breaking the subscription. */
function deliver<T>(body: string, handler: (message: T) => void): void {
  let message: T;
  try {
    message = JSON.parse(body) as T;
  } catch {
    return;
  }
  handler(message);
}

export type ConnectionState = 'connecting' | 'live' | 'offline';

/**
 * Subscribes to a plan's live topics for as long as the component is mounted.
 * Handlers are read through a ref so callers can pass inline functions without resubscribing.
 */
export function usePlanTopics(planId: number, handlers: PlanTopicHandlers): ConnectionState {
  const { session } = useAuth();
  const token = session?.token ?? null;
  const handlersRef = useRef(handlers);
  const [state, setState] = useState<ConnectionState>('connecting');

  useEffect(() => {
    handlersRef.current = handlers;
  });

  useEffect(() => {
    if (!token) {
      return undefined;
    }
    const base = `/topic/plans/${planId}`;
    const client = createStompClient(token, {
      onConnect: () => {
        setState('live');
        handlersRef.current.onConnected();
        client.subscribe(`${base}/updates`, (frame) => deliver(frame.body, handlersRef.current.onUpdate));
        client.subscribe(`${base}/stops`, (frame) => deliver(frame.body, handlersRef.current.onStop));
        client.subscribe(`${base}/positions`, (frame) => deliver(frame.body, handlersRef.current.onPosition));
      },
      onDisconnect: () => setState('offline'),
    });
    client.activate();

    return () => {
      void client.deactivate();
    };
  }, [planId, token]);

  return state;
}
