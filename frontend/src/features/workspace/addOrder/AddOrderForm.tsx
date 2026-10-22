import { useId, useState, type FormEvent, type ReactNode } from 'react';
import { ApiError } from '../../../api/client';
import type { NewOrderRequest, PackageSize } from '../../../api/types';
import { errorMessage } from '../../../lib/errors';
import type { TimeWindow } from '../../../lib/orders';
import { serverFieldErrors, validateOrder, type MapPoint, type OrderErrors, type OrderField, type OrderFormValues } from './validation';

const SIZES: PackageSize[] = ['SMALL', 'MEDIUM', 'LARGE'];

interface AddOrderFormProps {
  point: MapPoint | null;
  suggestedRef: string;
  defaultWindow: TimeWindow;
  submitting: boolean;
  serverError: unknown;
  onSubmit: (order: NewOrderRequest) => void;
  onCancel: () => void;
}

export function AddOrderForm({
  point,
  suggestedRef,
  defaultWindow,
  submitting,
  serverError,
  onSubmit,
  onCancel,
}: AddOrderFormProps) {
  const [values, setValues] = useState<OrderFormValues>({
    orderRef: suggestedRef,
    customerName: '',
    address: '',
    windowStart: defaultWindow.start,
    windowEnd: defaultWindow.end,
    size: 'SMALL',
  });
  const [errors, setErrors] = useState<OrderErrors>({});
  const serverErrors = serverError instanceof ApiError ? serverFieldErrors(serverError.fieldErrors) : {};
  const shown: OrderErrors = { ...serverErrors, ...errors };

  function update<K extends keyof OrderFormValues>(field: K, value: OrderFormValues[K]) {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const found = validateOrder(values, point);
    setErrors(found);
    if (Object.keys(found).length > 0 || !point) {
      return;
    }
    onSubmit({
      orderRef: values.orderRef.trim(),
      customerName: values.customerName.trim(),
      address: values.address.trim(),
      lat: point.lat,
      lon: point.lon,
      windowStart: values.windowStart,
      windowEnd: values.windowEnd,
      size: values.size,
    });
  }

  return (
    <form className="form" onSubmit={handleSubmit} noValidate>
      <div className={`location-readout${shown.location ? ' location-missing' : ''}`}>
        {point ? (
          <>
            Location <strong>{point.lat.toFixed(5)}, {point.lon.toFixed(5)}</strong>
            <span className="muted small"> Click the map again to move it.</span>
          </>
        ) : (
          'Click the map where the order should be delivered.'
        )}
        {shown.location && <FieldMessage id="location-error">{shown.location}</FieldMessage>}
      </div>

      <TextField label="Order reference" field="orderRef" values={values} errors={shown} onChange={update} />
      <TextField label="Customer" field="customerName" values={values} errors={shown} onChange={update} />
      <TextField label="Address" field="address" values={values} errors={shown} onChange={update} />

      <div className="field-row">
        <TextField label="Window start" field="windowStart" type="time" values={values} errors={shown} onChange={update} />
        <TextField label="Window end" field="windowEnd" type="time" values={values} errors={shown} onChange={update} />
      </div>

      <label className="field">
        <span className="field-label">Package size</span>
        <select value={values.size} onChange={(event) => update('size', event.target.value as PackageSize)}>
          {SIZES.map((size) => (
            <option key={size} value={size}>
              {size.charAt(0) + size.slice(1).toLowerCase()}
            </option>
          ))}
        </select>
      </label>

      {Boolean(serverError) && (
        <p className="form-error" role="alert">
          {errorMessage(serverError)}
        </p>
      )}

      <div className="form-actions">
        <button type="button" className="button" onClick={onCancel}>
          Cancel
        </button>
        <button type="submit" className="button button-primary" disabled={submitting}>
          {submitting ? 'Adding…' : 'Add order'}
        </button>
      </div>
    </form>
  );
}

type TextFieldName = Exclude<keyof OrderFormValues, 'size'>;

interface TextFieldProps {
  label: string;
  field: TextFieldName;
  type?: 'text' | 'time';
  values: OrderFormValues;
  errors: OrderErrors;
  onChange: (field: TextFieldName, value: string) => void;
}

function TextField({ label, field, type = 'text', values, errors, onChange }: TextFieldProps) {
  const id = useId();
  const error = errors[field as OrderField];
  const errorId = `${id}-error`;
  return (
    <div className="field">
      <label className="field-label" htmlFor={id}>
        {label}
      </label>
      <input
        id={id}
        type={type}
        name={field}
        value={values[field]}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? errorId : undefined}
        onChange={(event) => onChange(field, event.target.value)}
      />
      {error && <FieldMessage id={errorId}>{error}</FieldMessage>}
    </div>
  );
}

function FieldMessage({ id, children }: { id: string; children: ReactNode }) {
  return (
    <span id={id} className="field-error">
      {children}
    </span>
  );
}
