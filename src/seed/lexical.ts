/**
 * Tiny builders for Lexical rich text, used to seed pages with real formatting.
 * Inline strings support **bold** and [link text](url).
 */

type Node = Record<string, unknown>

const textNode = (text: string, format = 0): Node => ({
  type: 'text',
  text,
  format,
  detail: 0,
  mode: 'normal',
  style: '',
  version: 1,
})

const linkNode = (text: string, url: string): Node => ({
  type: 'link',
  version: 3,
  direction: 'ltr',
  format: '',
  indent: 0,
  fields: { linkType: 'custom', url, newTab: /^https?:/.test(url) },
  children: [textNode(text)],
})

export const inline = (source: string): Node[] => {
  const nodes: Node[] = []
  const pattern = /\*\*(.+?)\*\*|\[(.+?)\]\((.+?)\)/g
  let last = 0
  let m: RegExpExecArray | null
  while ((m = pattern.exec(source))) {
    if (m.index > last) nodes.push(textNode(source.slice(last, m.index)))
    if (m[1]) nodes.push(textNode(m[1], 1))
    else nodes.push(linkNode(m[2], m[3]))
    last = m.index + m[0].length
  }
  if (last < source.length) nodes.push(textNode(source.slice(last)))
  return nodes
}

const block = (type: string, children: Node[], extra: Node = {}): Node => ({
  type,
  version: 1,
  direction: 'ltr',
  format: '',
  indent: 0,
  children,
  ...extra,
})

export const p = (text: string) => block('paragraph', inline(text), { textFormat: 0, textStyle: '' })
export const h2 = (text: string) => block('heading', inline(text), { tag: 'h2' })
export const h3 = (text: string) => block('heading', inline(text), { tag: 'h3' })
export const ul = (items: string[]) =>
  block(
    'list',
    items.map((item, i) => block('listitem', inline(item), { value: i + 1 })),
    { listType: 'bullet', start: 1, tag: 'ul' },
  )
export const ol = (items: string[]) =>
  block(
    'list',
    items.map((item, i) => block('listitem', inline(item), { value: i + 1 })),
    { listType: 'number', start: 1, tag: 'ol' },
  )

/** Wrap block nodes into a Lexical document. */
export const doc = (...children: Node[]) => ({
  root: { type: 'root', version: 1, direction: 'ltr' as const, format: '' as const, indent: 0, children },
})
