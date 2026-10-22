import { useMutation } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { ApiError } from '../../../api/client';
import { importOrders } from '../../../api/endpoints';
import { Dialog } from '../../../components/Dialog';
import { errorMessage } from '../../../lib/errors';

interface ImportCsvDialogProps {
  planId: number;
  onClose: () => void;
  onImported: (count: number) => void;
}

const FORM_ID = 'import-csv-form';
const MAX_LISTED_ERRORS = 50;

export function ImportCsvDialog({ planId, onClose, onImported }: ImportCsvDialogProps) {
  const [file, setFile] = useState<File | null>(null);
  const upload = useMutation({
    mutationFn: (csv: File) => importOrders(planId, csv),
    onSuccess: (result) => onImported(result.imported),
  });
  const rowErrors = upload.error instanceof ApiError ? upload.error.rowErrors : [];

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (file) {
      upload.mutate(file);
    }
  }

  return (
    <Dialog
      title="Import orders from CSV"
      onClose={onClose}
      footer={
        <>
          <button type="button" className="button" onClick={onClose}>
            Cancel
          </button>
          <button type="submit" form={FORM_ID} className="button button-primary" disabled={!file || upload.isPending}>
            {upload.isPending ? 'Importing…' : 'Import'}
          </button>
        </>
      }
    >
      <form id={FORM_ID} className="form" onSubmit={handleSubmit}>
        <p className="muted">
          Columns: <code>order_ref, customer, address, lat, lon, window_start, window_end, size</code>
        </p>
        <label className="field">
          <span className="field-label">CSV file</span>
          <input
            type="file"
            accept=".csv,text/csv"
            onChange={(event) => {
              setFile(event.target.files?.[0] ?? null);
              upload.reset();
            }}
          />
        </label>
      </form>
      {upload.isError && (
        <div className="form-error" role="alert">
          <p>{errorMessage(upload.error)}</p>
          {rowErrors.length > 0 && (
            <>
              <ul className="row-errors">
                {rowErrors.slice(0, MAX_LISTED_ERRORS).map((error, index) => (
                  <li key={`${error.row}-${index}`}>
                    <strong>{error.row === 0 ? 'File' : `Row ${error.row}`}:</strong> {error.message}
                  </li>
                ))}
              </ul>
              {rowErrors.length > MAX_LISTED_ERRORS && (
                <p className="small">and {rowErrors.length - MAX_LISTED_ERRORS} more.</p>
              )}
            </>
          )}
        </div>
      )}
    </Dialog>
  );
}
