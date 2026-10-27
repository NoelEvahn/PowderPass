import type { ItemType } from '@doors/shared';

const S = (body: string) => `<svg viewBox="0 0 32 32" fill="none" stroke="#e8d0a0" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${body}</svg>`;

export const ICONS: Record<ItemType | 'gold', string> = {
  flashlight: S('<rect x="5" y="13" width="14" height="6" rx="1.5"/><path d="M19 12l6-3v14l-6-3z"/><path d="M27 12l3-1M27 16h3M27 20l3 1" stroke="#ffe9a0"/>'),
  lighter: S('<rect x="10" y="14" width="10" height="13" rx="1.5"/><rect x="10" y="10" width="10" height="4"/><path d="M16 9c-2-2 0-4 1-6 1 2 2 3 1 5" stroke="#ffb050"/>'),
  battery: S('<rect x="8" y="9" width="16" height="18" rx="2"/><rect x="13" y="5" width="6" height="4"/><path d="M16 13v8M12 17h8" stroke="#e8c040"/>'),
  lockpick: S('<path d="M6 26l14-14M10 26l12-12"/><path d="M20 12l4-1 1-4M22 14l4 0 1-3"/><rect x="3" y="24" width="6" height="5" rx="1"/>'),
  vitamins: S('<rect x="9" y="10" width="14" height="18" rx="3" stroke="#e8a040"/><rect x="8" y="5" width="16" height="5" rx="1"/><path d="M13 19h6"/>'),
  bandage: S('<rect x="5" y="11" width="22" height="10" rx="5" transform="rotate(-30 16 16)"/><path d="M14 14l4 4M18 14l-4 4" stroke="#e05050"/>'),
  crucifix: S('<path d="M16 4v24M9 11h14" stroke="#e8c060" stroke-width="3"/>'),
  key: S('<circle cx="9" cy="16" r="5"/><path d="M14 16h14M24 16v4M20 16v3"/>'),
  hintPaper: S('<path d="M8 4h12l5 5v19H8z"/><path d="M12 13h9M12 17h9M12 21h6"/>'),
  electricalKey: S('<circle cx="9" cy="16" r="5" stroke="#e05040"/><path d="M14 16h14M24 16v4M20 16v3"/><path d="M8 13l2 3-2 3" stroke="#ffe060"/>'),
  breaker: S('<rect x="9" y="6" width="14" height="20" rx="2"/><rect x="13" y="9" width="6" height="8" fill="#a33" stroke="none"/><path d="M12 22h8"/>'),
  skeletonKey: S('<circle cx="8" cy="16" r="5"/><circle cx="8" cy="16" r="2"/><path d="M13 16h15M25 16v5M21 16v4M17 16v3"/>'),
  gold: S('<circle cx="16" cy="16" r="10" stroke="#e8c040"/><circle cx="16" cy="16" r="6" stroke="#e8c040"/>'),
};

export const SHAPE_SVG = [
  '<circle cx="16" cy="16" r="10"/>',
  '<path d="M16 5l11 20H5z"/>',
  '<rect x="6" y="6" width="20" height="20"/>',
  '<path d="M16 4l3.5 8 8.5.8-6.5 5.6 2 8.6L16 22.5 8.5 27l2-8.6L4 12.8l8.5-.8z"/>',
  '<path d="M16 3l11 13-11 13L5 16z"/>',
  '<path d="M12 4h8v8h8v8h-8v8h-8v-8H4v-8h8z"/>',
  '<path d="M21 5a11 11 0 1 0 0 22 9 9 0 0 1 0-22z"/>',
  '<path d="M16 27S4 19 4 11a6 6 0 0 1 12-2 6 6 0 0 1 12 2c0 8-12 16-12 16z"/>',
].map((b) => `<svg viewBox="0 0 32 32" fill="none" stroke="#e8d0a0" stroke-width="2">${b}</svg>`);
