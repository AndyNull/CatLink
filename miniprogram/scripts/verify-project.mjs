import { existsSync, readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'

const root = resolve(import.meta.dirname, '..')
const source = resolve(root, 'miniprogram')
const config = JSON.parse(readFileSync(resolve(source, 'app.json'), 'utf8'))
const pages = [
  ...config.pages,
  ...config.subPackages.flatMap(({ root, pages }) => pages.map((page) => `${root}/${page}`)),
]

for (const page of pages) {
  for (const extension of ['ts', 'json', 'wxml', 'wxss']) {
    const file = resolve(source, `${page}.${extension}`)
    if (!existsSync(file)) throw new Error(`缺少页面文件: ${file}`)
  }
}

if (config.tabBar.list.length !== 5) throw new Error('主导航必须保持五个入口')
if (!config.tabBar.custom) throw new Error('自定义底栏未启用')

for (const extension of ['json', 'wxml', 'wxss']) {
  const file = resolve(source, `custom-tab-bar/index.${extension}`)
  if (!existsSync(file)) throw new Error(`缺少底栏文件: ${file}`)
}

if (!['js', 'ts'].some((extension) => existsSync(resolve(source, `custom-tab-bar/index.${extension}`)))) {
  throw new Error('缺少底栏脚本: custom-tab-bar/index.js 或 index.ts')
}

for (const tab of ['home', 'archive', 'community', 'ai', 'profile']) {
  for (const suffix of ['', '-active']) {
    const file = resolve(source, `assets/icons/${tab}${suffix}.svg`)
    if (!existsSync(file)) throw new Error(`缺少底栏图标: ${file}`)
  }
}

function verifyJson(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const file = resolve(directory, entry.name)
    if (entry.isDirectory()) verifyJson(file)
    else if (entry.name.endsWith('.json')) JSON.parse(readFileSync(file, 'utf8'))
  }
}

verifyJson(source)
console.log(`结构校验通过：${config.pages.length} 个主包页面，${pages.length - config.pages.length} 个分包页面，5 个底栏入口`)
