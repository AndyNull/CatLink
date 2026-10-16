import { cacheNamesWithAncestors } from '@/utils/keepAlive'

describe('cacheNamesWithAncestors', () => {
  const router = {
    resolve: () => ({
      matched: [
        { name: 'CatLink' },
        { name: 'CatLinkContent' },
        { name: 'CatLinkPosts' }
      ]
    })
  }

  it('keeps nested menu containers alive with the opened leaf', () => {
    const names = cacheNamesWithAncestors(
      ['CatLinkPosts'],
      [{ path: '/catlink/posts', name: 'CatLinkPosts' }],
      router as never
    )

    expect(names).toEqual(['CatLinkPosts', 'CatLink', 'CatLinkContent'])
  })

  it('does not cache a noCache leaf through its visited tab', () => {
    const names = cacheNamesWithAncestors(
      [],
      [{ path: '/catlink/posts', name: 'CatLinkPosts', meta: { noCache: true }}],
      router as never
    )

    expect(names).toEqual(['CatLink', 'CatLinkContent'])
  })
})
