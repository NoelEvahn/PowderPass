/**
 * Minimal builders for Lexical rich-text JSON, so seed content can be written
 * as readable text. Supports paragraphs, h3/h4, bullet lists, **bold** and
 * [links](/path or https://…).
 */
type Node = Record<string, unknown>

const base = { format: '', indent: 0, version: 1, direction: 'ltr' as const }

const textNode = (text: string, bold = false): Node => ({ type: 'text', text, format: bold ? 1 : 0, style: '', mode: 'normal', detail: 0, version: 1 })

/** Parse inline **bold** and [label](url) markers. */
function inline(text: string): Node[] {
  const nodes: Node[] = []
  const pattern = /\*\*([^*]+)\*\*|\[([^\]]+)\]\(([^)]+)\)/g
  let last = 0
  let match: RegExpExecArray | null
  while ((match = pattern.exec(text))) {
    if (match.index > last) nodes.push(textNode(text.slice(last, match.index)))
    if (match[1]) nodes.push(textNode(match[1], true))
    else {
      const url = match[3]
      nodes.push({
        ...base,
        type: 'link',
        version: 3,
        fields: { linkType: 'custom', url, newTab: /^https?:/.test(url) },
        children: [textNode(match[2])],
      })
    }
    last = match.index + match[0].length
  }
  if (last < text.length) nodes.push(textNode(text.slice(last)))
  return nodes
}

export const p = (text: string): Node => ({ ...base, type: 'paragraph', textFormat: 0, textStyle: '', children: inline(text) })
export const h3 = (text: string): Node => ({ ...base, type: 'heading', tag: 'h3', children: inline(text) })
export const h4 = (text: string): Node => ({ ...base, type: 'heading', tag: 'h4', children: inline(text) })
export const ul = (items: string[]): Node => ({
  ...base,
  type: 'list',
  listType: 'bullet',
  tag: 'ul',
  start: 1,
  children: items.map((item, i) => ({ ...base, type: 'listitem', value: i + 1, children: inline(item) })),
})

/** Build an editor state from blocks; plain strings become paragraphs. */
export const rt = (...children: (Node | string)[]) => ({
  root: { ...base, type: 'root', children: children.map((c) => (typeof c === 'string' ? p(c) : c)) },
})
