import AxeBuilder from '@axe-core/playwright'
import { expect, type Page } from '@playwright/test'
import fs from 'fs'
import path from 'path'

export type E2EState = {
  token: string
  available: { id: number; slug: string }
  second: { id: number; slug: string }
  boutique: { id: number; slug: string }
  sold: { id: number; slug: string }
}

export const state = (): E2EState => JSON.parse(fs.readFileSync(path.resolve(__dirname, '.state.json'), 'utf8'))

/** Zero serious or critical axe violations (WCAG 2.2 A/AA), after reveals settle. */
export const expectAccessible = async (page: Page) => {
  await page.evaluate(async () => {
    // Scroll through so one-time reveals have fired, then return to the top.
    for (let y = 0; y < document.body.scrollHeight; y += window.innerHeight * 0.8) {
      window.scrollTo(0, y)
      await new Promise((r) => setTimeout(r, 60))
    }
    window.scrollTo(0, 0)
  })
  await page.waitForTimeout(1000)
  const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze()
  const serious = results.violations.filter((v) => v.impact === 'serious' || v.impact === 'critical')
  expect(serious.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)).toEqual([])
}

export const expectNoHorizontalScroll = async (page: Page) => {
  const [scrollWidth, innerWidth] = await page.evaluate(() => [document.documentElement.scrollWidth, window.innerWidth])
  expect(scrollWidth, `page is ${scrollWidth}px wide in a ${innerWidth}px window`).toBeLessThanOrEqual(innerWidth)
}
