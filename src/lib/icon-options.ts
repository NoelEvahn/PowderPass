/**
 * Icons editors can attach to terms (values, habits, programmes). Keys are
 * stored in the database: labels may change, keys must not.
 * Kept free of React imports so the CMS config can use it.
 */
export const ICON_OPTIONS = [
  { label: 'Scales (fairness)', value: 'scale' },
  { label: 'Shield (integrity)', value: 'shield-check' },
  { label: 'Checklist (responsibility)', value: 'clipboard-check' },
  { label: 'Globe (our world)', value: 'earth' },
  { label: 'Celebration', value: 'party-popper' },
  { label: 'Light bulb (ideas)', value: 'lightbulb' },
  { label: 'Flame (passion)', value: 'flame' },
  { label: 'Caring hands', value: 'heart-handshake' },
  { label: 'Mountain (resilience)', value: 'mountain' },
  { label: 'People (together)', value: 'users' },
  { label: 'Cycle (reflection)', value: 'refresh-ccw' },
  { label: 'Handshake', value: 'handshake' },
  { label: 'Magnifier (enquiry)', value: 'search' },
  { label: 'Footprints (independence)', value: 'footprints' },
  { label: 'Rocket (risk taking)', value: 'rocket' },
  { label: 'Speech bubbles', value: 'messages-square' },
  { label: 'Compass', value: 'compass' },
  { label: 'Open book', value: 'book-open' },
  { label: 'Sparkles', value: 'sparkles' },
  { label: 'Star', value: 'star' },
] as const

export type IconKey = (typeof ICON_OPTIONS)[number]['value']
