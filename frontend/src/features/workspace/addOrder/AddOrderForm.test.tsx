import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../api/client';
import { AddOrderForm } from './AddOrderForm';

const point = { lat: 41.9012, lon: -87.6543 };

function renderForm(overrides: Partial<Parameters<typeof AddOrderForm>[0]> = {}) {
  const onSubmit = vi.fn();
  render(
    <AddOrderForm
      point={point}
      suggestedRef="NEW-4"
      defaultWindow={{ start: '13:00', end: '15:00' }}
      submitting={false}
      serverError={null}
      onSubmit={onSubmit}
      onCancel={vi.fn()}
      {...overrides}
    />,
  );
  return onSubmit;
}

describe('AddOrderForm', () => {
  it('requires customer and address', async () => {
    const onSubmit = renderForm();

    await userEvent.click(screen.getByRole('button', { name: 'Add order' }));

    expect(screen.getByText('Customer is required.')).toBeInTheDocument();
    expect(screen.getByText('Address is required.')).toBeInTheDocument();
    expect(screen.getByLabelText('Customer')).toHaveAttribute('aria-invalid', 'true');
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('rejects a window that ends before it starts', async () => {
    const onSubmit = renderForm();
    await userEvent.type(screen.getByLabelText('Customer'), 'Ada Park');
    await userEvent.type(screen.getByLabelText('Address'), '1 W Madison St');
    const end = screen.getByLabelText('Window end');
    await userEvent.clear(end);
    await userEvent.type(end, '12:30');

    await userEvent.click(screen.getByRole('button', { name: 'Add order' }));

    expect(screen.getByText('Window end must be after the start.')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('asks for a map location before submitting', async () => {
    const onSubmit = renderForm({ point: null });
    await userEvent.type(screen.getByLabelText('Customer'), 'Ada Park');
    await userEvent.type(screen.getByLabelText('Address'), '1 W Madison St');

    await userEvent.click(screen.getByRole('button', { name: 'Add order' }));

    expect(screen.getByText('Choose a location on the map first.')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits the order with the clicked coordinates and suggested reference', async () => {
    const onSubmit = renderForm();
    await userEvent.type(screen.getByLabelText('Customer'), 'Ada Park');
    await userEvent.type(screen.getByLabelText('Address'), '1 W Madison St');
    await userEvent.selectOptions(screen.getByLabelText('Package size'), 'LARGE');

    await userEvent.click(screen.getByRole('button', { name: 'Add order' }));

    expect(onSubmit).toHaveBeenCalledWith({
      orderRef: 'NEW-4',
      customerName: 'Ada Park',
      address: '1 W Madison St',
      lat: 41.9012,
      lon: -87.6543,
      windowStart: '13:00',
      windowEnd: '15:00',
      size: 'LARGE',
    });
  });

  it('shows server errors next to fields and as a summary', () => {
    renderForm({
      serverError: new ApiError(409, { status: 409, detail: 'Order NEW-4 already exists in this plan' }),
    });
    expect(screen.getByRole('alert')).toHaveTextContent('Order NEW-4 already exists in this plan');
  });
});
