import L from 'leaflet';

function escapeHtml(value: string): string {
  return value.replace(/[&<>"']/g, (char) => `&#${char.charCodeAt(0)};`);
}

const vanIcons = new Map<string, L.DivIcon>();

/** Rounded badge showing the van number; cached because markers re-render several times a second. */
export function vanIcon(label: string, color: string, emphasized: boolean): L.DivIcon {
  const key = `${label}|${color}|${emphasized}`;
  let icon = vanIcons.get(key);
  if (!icon) {
    icon = L.divIcon({
      className: `van-marker${emphasized ? ' van-marker-selected' : ''}`,
      html: `<span style="background:${color}">${escapeHtml(label)}</span>`,
      iconSize: [28, 28],
      iconAnchor: [14, 14],
    });
    vanIcons.set(key, icon);
  }
  return icon;
}

export const depotIcon = L.divIcon({
  className: 'depot-marker',
  html: '<span aria-hidden="true">D</span>',
  iconSize: [30, 30],
  iconAnchor: [15, 15],
});

const deliveredIcons = new Map<string, L.DivIcon>();

export function deliveredIcon(color: string, dimmed: boolean): L.DivIcon {
  const key = `${color}|${dimmed}`;
  let icon = deliveredIcons.get(key);
  if (!icon) {
    icon = L.divIcon({
      className: `delivered-marker${dimmed ? ' marker-dimmed' : ''}`,
      html: `<span style="border-color:${color};color:${color}">&#10003;</span>`,
      iconSize: [16, 16],
      iconAnchor: [8, 8],
    });
    deliveredIcons.set(key, icon);
  }
  return icon;
}

export const pickIcon = L.divIcon({
  className: 'pick-marker',
  html: '<span></span>',
  iconSize: [22, 22],
  iconAnchor: [11, 22],
});
