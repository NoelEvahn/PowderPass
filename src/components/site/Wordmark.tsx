import React from 'react'

export const Monogram = ({ size = 36 }: { size?: number }) => (
  <svg viewBox="0 0 64 64" width={size} height={size} aria-hidden="true" focusable="false" className="monogram">
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

/** The brand wordmark: monogram + "labeltrunc." with the wine full stop. */
export const Wordmark = ({ size = 36 }: { size?: number }) => (
  <span className="wordmark">
    <Monogram size={size} />
    <span className="wordmark__text">
      labeltrunc<span className="wordmark__dot">.</span>
    </span>
  </span>
)
