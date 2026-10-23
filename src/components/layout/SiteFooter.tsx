import { Mail, MapPin, Phone } from 'lucide-react'
import { resolveLink } from '@/lib/links'
import { getFooter, getSettings } from '@/lib/queries'
import { CookieSettingsButton } from '../consent/CookieSettingsButton'
import { SmartLink } from '../ui/SmartLink'
import styles from './Footer.module.css'

const telHref = (number: string) => `tel:${number.replace(/[^\d+]/g, '')}`

export async function SiteFooter() {
  const [footer, settings] = await Promise.all([getFooter(), getSettings()])
  const year = new Date().getFullYear()

  return (
    <footer className={styles.footer}>
      <div className={`container ${styles.top}`}>
        <div className={styles.identity}>
          {/* eslint-disable-next-line @next/next/no-img-element -- official SVG lockup */}
          <img src="/brand/isp-logo.svg" alt={settings.schoolName} width={300} height={58} className={styles.logo} loading="lazy" />
          <p className={styles.location}>{settings.location}</p>
          <a className={styles.email} href={`mailto:${settings.generalEmail}`}>
            <Mail size={18} aria-hidden="true" />
            {settings.generalEmail}
          </a>
        </div>

        <ul className={styles.campuses} aria-label="Campuses">
          {(settings.campuses ?? []).map((campus) => (
            <li key={campus.id ?? campus.name} className={styles.campus}>
              <p className={styles.campusName}>
                {campus.name}
                {campus.ages && <span> · {campus.ages}</span>}
              </p>
              <p className={styles.address}>
                <MapPin size={18} aria-hidden="true" />
                <span>{campus.address}</span>
              </p>
              {campus.phones?.slice(0, 1).map((phone) => (
                <a key={phone.number} className={styles.phone} href={telHref(phone.number)}>
                  <Phone size={18} aria-hidden="true" />
                  <span>
                    <span className="visually-hidden">{phone.label}: </span>
                    {phone.number}
                  </span>
                </a>
              ))}
              {campus.mapUrl && (
                <SmartLink href={campus.mapUrl} newTab className={styles.map}>
                  Directions
                </SmartLink>
              )}
            </li>
          ))}
        </ul>
      </div>

      <div className={`container ${styles.columns}`}>
        {(footer.columns ?? []).map((column) => (
          <nav key={column.id ?? column.title} aria-label={column.title} className={styles.column}>
            <p className={styles.columnTitle}>{column.title}</p>
            <ul>
              {(column.links ?? []).map((item, index) => {
                const link = resolveLink(item.link)
                return link ? (
                  <li key={item.id ?? index}>
                    <SmartLink href={link.href} newTab={link.newTab}>
                      {link.label}
                    </SmartLink>
                  </li>
                ) : null
              })}
            </ul>
          </nav>
        ))}
        <div className={styles.column}>
          <p className={styles.columnTitle}>Follow ISP</p>
          <ul>
            {(settings.social ?? []).map((s) => (
              <li key={s.url}>
                <SmartLink href={s.url} newTab>
                  {s.platform === 'linkedin' ? 'LinkedIn' : s.platform.charAt(0).toUpperCase() + s.platform.slice(1)}
                </SmartLink>
              </li>
            ))}
            {settings.sisterSchool?.url && (
              <li>
                <SmartLink href={settings.sisterSchool.url} newTab>
                  {settings.sisterSchool.label}
                </SmartLink>
              </li>
            )}
          </ul>
        </div>
      </div>

      {footer.memberships && footer.memberships.length > 0 && (
        <div className={`container ${styles.memberships}`}>
          <p className={styles.columnTitle}>Examination boards & associations</p>
          <ul>
            {footer.memberships.map((item, index) => {
              const link = resolveLink(item.link)
              return link ? (
                <li key={item.id ?? index}>
                  <SmartLink href={link.href} newTab={link.newTab}>
                    {link.label}
                  </SmartLink>
                </li>
              ) : null
            })}
          </ul>
        </div>
      )}

      <div className={styles.bottom}>
        <div className={`container ${styles.bottomInner}`}>
          <p>
            © {year} {settings.schoolName}
          </p>
          <ul className={styles.legal}>
            {(footer.legalLinks ?? []).map((item, index) => {
              const link = resolveLink(item.link)
              return link ? (
                <li key={item.id ?? index}>
                  <SmartLink href={link.href} newTab={link.newTab}>
                    {link.label}
                  </SmartLink>
                </li>
              ) : null
            })}
            <li>
              <CookieSettingsButton className={styles.cookieButton} />
            </li>
          </ul>
        </div>
      </div>
    </footer>
  )
}
