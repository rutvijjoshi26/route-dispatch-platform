import { act, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { ClockAnchor } from '../../../lib/planClock';
import { makeRoute, makeStop } from '../../../test/utils';
import { RouteTimeline } from './RouteTimeline';

const route = makeRoute(1, [
  makeStop({ id: 1, orderRef: 'A', plannedArrival: '09:00:00', status: 'DELIVERED', actualArrival: '09:10:00' }),
  makeStop({ id: 2, orderRef: 'B', plannedArrival: '12:00:00', late: true }),
  makeStop({ id: 3, orderRef: 'C', plannedArrival: '15:00:00' }),
]);

function stoppedClock(now: string): ClockAnchor {
  return { clock: { now, timeScale: 60, dispatchedAt: '2026-10-21T13:00:00Z' }, anchoredAt: 0, running: false };
}

function leftPercent(element: HTMLElement): number {
  return Number.parseFloat(element.style.left);
}

describe('RouteTimeline', () => {
  it('renders one marker per stop positioned within the shift track', () => {
    render(<RouteTimeline route={route} clock={null} />);

    const markers = screen.getAllByRole('listitem');
    expect(markers).toHaveLength(3);
    for (const marker of markers) {
      expect(leftPercent(marker)).toBeGreaterThanOrEqual(0);
      expect(leftPercent(marker)).toBeLessThanOrEqual(100);
    }
    // 08:00-16:00 shift: noon is the midpoint; the delivered stop uses its actual 09:10 arrival.
    expect(leftPercent(markers[1]!)).toBeCloseTo(50);
    expect(leftPercent(markers[0]!)).toBeCloseTo((70 / 480) * 100);
  });

  it('applies delivered and late styling', () => {
    render(<RouteTimeline route={route} clock={null} />);

    expect(screen.getByLabelText(/^A at 09:10/)).toHaveClass('timeline-stop-delivered');
    expect(screen.getByLabelText(/^B at 12:00/)).toHaveClass('timeline-stop-late');
    expect(screen.getByLabelText(/^C at 15:00/)).toHaveClass('timeline-stop-ontime');
  });

  it('draws the now line only when a plan clock exists', () => {
    const { rerender } = render(<RouteTimeline route={route} clock={null} />);
    expect(screen.queryByRole('img', { name: /^Now/ })).not.toBeInTheDocument();

    rerender(<RouteTimeline route={route} clock={stoppedClock('10:00:00')} />);
    const now = screen.getByRole('img', { name: 'Now 10:00' });
    expect(leftPercent(now)).toBeCloseTo(25);
  });

  it('advances the now line at the plan time scale', () => {
    vi.useFakeTimers({ now: 1_000_000 });
    const clock: ClockAnchor = { ...stoppedClock('10:00:00'), anchoredAt: 1_000_000, running: true };
    render(<RouteTimeline route={route} clock={clock} />);
    expect(screen.getByRole('img', { name: 'Now 10:00' })).toBeInTheDocument();

    // Two real seconds at 60x is two plan minutes; the line ticks every 500 ms.
    act(() => vi.advanceTimersByTime(2000));
    expect(screen.getByRole('img', { name: 'Now 10:02' })).toBeInTheDocument();
    vi.useRealTimers();
  });

  it('clamps stops outside the shift to the track edges', () => {
    const early = makeRoute(2, [makeStop({ id: 9, orderRef: 'Z', plannedArrival: '06:00:00' })]);
    render(<RouteTimeline route={early} clock={null} />);
    expect(leftPercent(screen.getByRole('listitem'))).toBe(0);
  });
});
