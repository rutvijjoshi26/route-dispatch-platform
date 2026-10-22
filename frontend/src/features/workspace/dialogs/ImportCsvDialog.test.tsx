import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { jsonResponse, renderWithClient } from '../../../test/utils';
import { ImportCsvDialog } from './ImportCsvDialog';

const csv = new File(['order_ref,customer\nX,Y'], 'orders.csv', { type: 'text/csv' });

describe('ImportCsvDialog', () => {
  it('lists row-level errors from a 400 problem', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(
        {
          title: 'Bad Request',
          status: 400,
          detail: 'The CSV file has 2 invalid rows',
          errors: [
            { row: 3, message: 'lat must be a number' },
            { row: 7, message: 'window_end must be after window_start' },
          ],
        },
        400,
      ),
    );
    const onImported = vi.fn();
    renderWithClient(<ImportCsvDialog planId={5} onClose={vi.fn()} onImported={onImported} />);

    await userEvent.upload(screen.getByLabelText('CSV file'), csv);
    await userEvent.click(screen.getByRole('button', { name: 'Import' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('The CSV file has 2 invalid rows');
    expect(alert).toHaveTextContent('Row 3: lat must be a number');
    expect(alert).toHaveTextContent('Row 7: window_end must be after window_start');
    expect(onImported).not.toHaveBeenCalled();
    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/plans/5/orders/import');
  });

  it('reports the imported count on success', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse({ imported: 200 }));
    const onImported = vi.fn();
    renderWithClient(<ImportCsvDialog planId={5} onClose={vi.fn()} onImported={onImported} />);

    await userEvent.upload(screen.getByLabelText('CSV file'), csv);
    await userEvent.click(screen.getByRole('button', { name: 'Import' }));

    await vi.waitFor(() => expect(onImported).toHaveBeenCalledWith(200));
  });
});
