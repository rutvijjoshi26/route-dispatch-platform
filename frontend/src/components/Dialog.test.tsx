import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Dialog } from './Dialog';

function renderDialog(onClose = vi.fn()) {
  render(
    <>
      <button type="button">Outside</button>
      <Dialog title="Confirm" onClose={onClose} footer={<button type="button">Save</button>}>
        <input aria-label="Name" />
      </Dialog>
    </>,
  );
  return onClose;
}

describe('Dialog', () => {
  it('moves focus into the dialog when it opens', () => {
    renderDialog();
    expect(screen.getByLabelText('Name')).toHaveFocus();
  });

  it('keeps Tab and Shift+Tab inside the dialog', async () => {
    renderDialog();
    const close = screen.getByRole('button', { name: 'Close' });
    const save = screen.getByRole('button', { name: 'Save' });

    save.focus();
    await userEvent.tab();
    expect(close).toHaveFocus();

    await userEvent.tab({ shift: true });
    expect(save).toHaveFocus();
    expect(screen.getByRole('button', { name: 'Outside' })).not.toHaveFocus();
  });

  it('closes on Escape', async () => {
    const onClose = renderDialog();
    await userEvent.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledOnce();
  });
});
