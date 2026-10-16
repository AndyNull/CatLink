import { chromium } from '@playwright/test'

const token = process.env.CATLINK_ADMIN_E2E_TOKEN
if (!token) throw new Error('CATLINK_ADMIN_E2E_TOKEN is required')

const routes = [
  '/dashboard',
  '/catlink/cats',
  '/catlink/public-cats',
  '/catlink/posts',
  '/catlink/media',
  '/catlink/logs',
  '/catlink/bricks'
]

const browser = await chromium.launch({ channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome', headless: true })
const context = await browser.newContext()
await context.addCookies([{
  name: 'Admin-Token', value: token, domain: 'localhost', path: '/', sameSite: 'Lax'
}])
const page = await context.newPage()
const errors = []
const requests = new Map()
page.on('pageerror', error => errors.push(error.message))
page.on('request', request => {
  const url = new URL(request.url())
  if (url.pathname.startsWith('/admin-api/')) {
    requests.set(url.pathname, (requests.get(url.pathname) || 0) + 1)
  }
})

await page.goto('http://localhost:9527/#/dashboard', { waitUntil: 'networkidle' })
const samples = []
const matches = {}
for (let round = 0; round < 3; round++) {
  for (const route of routes) {
    const started = performance.now()
    await page.evaluate(nextHash => { window.location.hash = nextHash }, `#${route}`)
    await page.waitForURL(`**/#${route}`)
    await page.waitForLoadState('networkidle')
    const main = page.locator('.app-main')
    const text = (await main.innerText()).trim()
    matches[route] ||= await page.evaluate(() => {
      const app = document.querySelector('#app')?.__vue_app__
      return app?.config?.globalProperties?.$router?.currentRoute?.value?.matched?.map(record => ({
        name: record.name, path: record.path
      })) || []
    })
    samples.push({ round: round + 1, route, actual: new URL(page.url()).hash, ms: Math.round(performance.now() - started), blank: text.length === 0 })
  }
}

if (process.env.CATLINK_NAV_SCREENSHOT) {
  await page.goto('http://localhost:9527/#/dashboard', { waitUntil: 'networkidle' })
  await page.screenshot({ path: process.env.CATLINK_NAV_SCREENSHOT, fullPage: true })
}

await browser.close()
console.log(JSON.stringify({ samples, matches, requests: Object.fromEntries(requests), errors }, null, 2))
