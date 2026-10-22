import { useEffect } from 'react';
import { Marker, useMap, useMapEvents } from 'react-leaflet';
import type { MapPoint } from '../addOrder/validation';
import { pickIcon } from './icons';

interface LocationPickerProps {
  point: MapPoint | null;
  onPick: (point: MapPoint) => void;
}

/** While mounted, map clicks choose a location and the cursor becomes a crosshair. */
export function LocationPicker({ point, onPick }: LocationPickerProps) {
  const map = useMap();
  useMapEvents({
    click: (event) => {
      // Normalize longitude in case the map was panned across the antimeridian.
      const { lat, lng } = event.latlng.wrap();
      onPick({ lat, lon: lng });
    },
  });

  useEffect(() => {
    const container = map.getContainer();
    container.classList.add('map-picking');
    return () => container.classList.remove('map-picking');
  }, [map]);

  return point ? <Marker position={[point.lat, point.lon]} icon={pickIcon} interactive={false} /> : null;
}
