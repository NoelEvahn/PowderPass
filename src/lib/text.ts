/** Keys whose string values are human-readable content worth indexing. */
const TEXT_KEYS = new Set([
  'text', 'heading', 'intro', 'eyebrow', 'title', 'body', 'description', 'term', 'tagline',
  'items', 'caption', 'question', 'word', 'supporting', 'value', 'summary', 'name', 'role',
  'bio', 'note', 'meta', 'label', 'factLine', 'stageLabel',
])

/**
 * Flatten block or Lexical rich-text JSON into plain text.
 * Lexical stores words in `text` nodes, so the same walk handles both.
 */
export const extractText = (input: unknown, limit = 20_000): string => {
  const parts: string[] = []
  let size = 0
  const walk = (node: unknown, key?: string) => {
    if (size > limit || node == null) return
    if (typeof node === 'string') {
      if (key && TEXT_KEYS.has(key)) {
        parts.push(node)
        size += node.length
      }
      return
    }
    if (Array.isArray(node)) {
      node.forEach((child) => walk(child, key === 'children' ? undefined : key))
      return
    }
    if (typeof node === 'object') {
      for (const [k, v] of Object.entries(node as Record<string, unknown>)) {
        if (k === 'id' || k === 'blockType' || k === 'url' || k === 'reference') continue
        walk(v, k)
      }
    }
  }
  walk(input)
  return parts.join(' ').replace(/\s+/g, ' ').trim().slice(0, limit)
}

/** A readable excerpt around the first occurrence of `term`. */
export const excerptAround = (text: string, term: string, radius = 90): string => {
  if (!text) return ''
  const index = text.toLowerCase().indexOf(term.toLowerCase())
  if (index < 0) return text.length > radius * 2 ? `${text.slice(0, radius * 2).trimEnd()}…` : text
  const start = Math.max(0, index - radius)
  const end = Math.min(text.length, index + term.length + radius)
  return `${start > 0 ? '…' : ''}${text.slice(start, end).trim()}${end < text.length ? '…' : ''}`
}
