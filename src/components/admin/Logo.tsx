import React from 'react'

const Monogram = ({ size = 32 }: { size?: number }) => (
  <svg viewBox="0 0 64 64" width={size} height={size} aria-hidden="true" focusable="false">
    <rect width="64" height="64" rx="14" fill="#501C2B" />
    <g fill="#F7EFEA">
      <rect x="18" y="14" width="7" height="36" />
      <rect x="14" y="14" width="15" height="2" />
      <rect x="18" y="48" width="30" height="2.4" />
      <rect x="45.6" y="42" width="2.4" height="8" />
      <rect x="20" y="14" width="30" height="2.4" />
      <rect x="47.6" y="14" width="2.4" height="7" />
      <rect x="33" y="14" width="7" height="30" />
      <rect x="29" y="42" width="15" height="2" />
    </g>
  </svg>
)

export const Logo = () => (
  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 12 }}>
    <Monogram size={44} />
    <span style={{ fontFamily: 'Georgia, "Times New Roman", serif', fontWeight: 700, fontSize: 28 }}>
      labeltrunc<span style={{ color: '#501C2B' }}>.</span> <span style={{ fontSize: 16, fontWeight: 400, opacity: 0.7 }}>editor</span>
    </span>
  </span>
)

export const Icon = () => <Monogram size={20} />
