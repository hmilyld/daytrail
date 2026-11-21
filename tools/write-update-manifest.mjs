#!/usr/bin/env node
// 生成 update.json：每个 Release 附带的静态更新清单。
//
// 为什么需要它：客户端读的是
//   https://github.com/<owner>/<repo>/releases/latest/download/update.json
// 这是 CDN 上的静态文件，不消耗 GitHub API 的 60 次/小时匿名配额。
// 它还提供 API 报文里拿不到的两样东西：versionCode 与 apkSha256。
//
// 用法（发布工作流里调用）：
//   node tools/write-update-manifest.mjs \
//     --out update.json --version 0.4.0 --code 7 --tag v0.4.0 \
//     --apk app/build/outputs/apk/release/app-release.apk \
//     --notes release-notes.md --repo owner/repo

import { createHash } from 'node:crypto'
import { readFileSync, statSync, writeFileSync } from 'node:fs'
import { basename } from 'node:path'

function arg(name) {
  const index = process.argv.indexOf(`--${name}`)
  return index < 0 ? undefined : process.argv[index + 1]
}

function required(name) {
  const value = arg(name)
  if (!value) {
    console.error(`缺少必需参数 --${name}`)
    process.exit(1)
  }
  return value
}

const out = required('out')
const version = required('version')
const tag = required('tag')
const apk = required('apk')
const repo = required('repo')
const code = arg('code') ?? '0'
const notesFile = arg('notes')

if (!/^\d+\.\d+\.\d+$/.test(version)) {
  console.error(`versionName 必须是语义化版本，收到：${version}`)
  process.exit(1)
}

const bytes = readFileSync(apk)
const sha256 = createHash('sha256').update(bytes).digest('hex')
const size = statSync(apk).size
const fileName = basename(apk)

const notes = notesFile ? readFileSync(notesFile, 'utf8').trim() : ''

// apkUrl 必须钉到本 tag，不能用 releases/latest/download/：
// 否则用户点「下载 0.4.0」时可能拿到 0.5.0。
const manifest = {
  versionName: version,
  versionCode: Number.parseInt(code, 10) || 0,
  tag,
  title: `${tag}`,
  notes,
  pageUrl: `https://github.com/${repo}/releases/tag/${tag}`,
  apkUrl: `https://github.com/${repo}/releases/download/${tag}/${fileName}`,
  apkSize: size,
  apkSha256: sha256,
}

writeFileSync(out, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')
console.log(`写入 ${out}：${version}（code=${manifest.versionCode}，${size} 字节，sha256=${sha256.slice(0, 12)}…）`)
