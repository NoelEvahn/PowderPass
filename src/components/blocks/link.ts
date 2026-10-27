/** True for paths on this site such as /shop (not protocol-relative URLs). */
export const isInternalHref = (href: string) => href.startsWith('/') && !href.startsWith('//')
export const isWebHref = (href: string) => /^https?:\/\//i.test(href)
