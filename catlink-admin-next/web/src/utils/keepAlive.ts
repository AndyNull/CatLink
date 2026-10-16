import type { Router } from 'vue-router'
import type { VisitedView } from '@/stores/tagsView'

export function cacheNamesWithAncestors(cached: string[], visited: VisitedView[], router: Router) {
  const names = new Set(cached)
  for (const view of visited) {
    const matched = router.resolve(view.fullPath || view.path).matched
    for (const record of matched.slice(0, -1)) {
      if (record.name) names.add(String(record.name))
    }
  }
  return [...names]
}
