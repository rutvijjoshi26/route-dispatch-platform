import type { FieldError, PackageSize } from '../../../api/types';
import { parseClock } from '../../../lib/time';

export interface OrderFormValues {
  orderRef: string;
  customerName: string;
  address: string;
  windowStart: string;
  windowEnd: string;
  size: PackageSize;
}

export type OrderField = keyof OrderFormValues | 'location';
export type OrderErrors = Partial<Record<OrderField, string>>;

export interface MapPoint {
  lat: number;
  lon: number;
}

export function validateOrder(values: OrderFormValues, point: MapPoint | null): OrderErrors {
  const errors: OrderErrors = {};
  if (!point) errors.location = 'Choose a location on the map first.';
  if (!values.orderRef.trim()) errors.orderRef = 'Order reference is required.';
  if (!values.customerName.trim()) errors.customerName = 'Customer is required.';
  if (!values.address.trim()) errors.address = 'Address is required.';
  if (!values.windowStart) errors.windowStart = 'Start time is required.';
  if (!values.windowEnd) errors.windowEnd = 'End time is required.';
  if (values.windowStart && values.windowEnd && parseClock(values.windowEnd) <= parseClock(values.windowStart)) {
    errors.windowEnd = 'Window end must be after the start.';
  }
  return errors;
}

const SERVER_FIELDS: Record<string, OrderField> = {
  orderRef: 'orderRef',
  customerName: 'customerName',
  address: 'address',
  windowStart: 'windowStart',
  windowEnd: 'windowEnd',
  windowValid: 'windowEnd',
  size: 'size',
  lat: 'location',
  lon: 'location',
};

/** Maps server validation errors onto form fields; unknown fields are left for the summary. */
export function serverFieldErrors(errors: FieldError[]): OrderErrors {
  const mapped: OrderErrors = {};
  for (const error of errors) {
    const field = SERVER_FIELDS[error.field];
    if (field && !mapped[field]) {
      mapped[field] = error.message;
    }
  }
  return mapped;
}
