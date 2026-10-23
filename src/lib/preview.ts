/** URL the CMS opens for Preview and Live Preview. The route checks the editor's session before enabling draft mode. */
export const previewUrl = (path: string): string => {
  const base = process.env.NEXT_PUBLIC_SERVER_URL || ''
  return `${base}/next/preview?path=${encodeURIComponent(path.startsWith('/') ? path : `/${path}`)}`
}
