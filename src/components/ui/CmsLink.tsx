import type { LinkField } from '@/payload-types'
import { resolveLink } from '@/lib/links'
import { ArrowLink } from './ArrowLink'
import { Button } from './Button'

type Props = { link: LinkField | null | undefined; appearance?: LinkField['appearance']; className?: string }

/** Render any CMS link with its chosen appearance. Renders nothing for broken links. */
export function CmsLink({ link, appearance, className }: Props) {
  const resolved = resolveLink(link)
  if (!resolved) return null
  const style = appearance ?? link?.appearance ?? 'arrow'
  if (style === 'arrow') {
    return (
      <ArrowLink href={resolved.href} external={resolved.external} newTab={resolved.newTab} className={className}>
        {resolved.label}
      </ArrowLink>
    )
  }
  return (
    <Button href={resolved.href} variant={style} external={resolved.external} newTab={resolved.newTab} className={className}>
      {resolved.label}
    </Button>
  )
}

export function CmsLinks({ links, className }: { links?: { link: LinkField; id?: string | null }[] | null; className?: string }) {
  if (!links?.length) return null
  return (
    <div className={className}>
      {links.map((item, index) => (
        <CmsLink key={item.id ?? index} link={item.link} />
      ))}
    </div>
  )
}
