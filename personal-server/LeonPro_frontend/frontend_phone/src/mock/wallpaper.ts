/**
 * 壁纸示例数据：返回“原始”后端结构（未经 mapGroup/mapImage），同时验证字段映射。
 * 图片是本地生成的 SVG（data: URL），不依赖外网。
 */

interface GroupDef {
  groupKey: string
  name: string
  description: string
  count: number
  hue: number
  portrait?: boolean
}

const GROUP_DEFS: GroupDef[] = [
  { groupKey: 'landscape', name: '风景', description: '山川湖海，远方与旷野。', count: 50, hue: 200 },
  { groupKey: 'city', name: '城市', description: '霓虹、街巷与天际线。', count: 18, hue: 260 },
  { groupKey: 'portrait', name: '人像', description: '光影里的人与故事（竖图）。', count: 14, hue: 330, portrait: true },
  { groupKey: 'minimal', name: '极简', description: '留白与秩序，适合做桌面。', count: 12, hue: 30 },
  { groupKey: 'ocean', name: '海洋', description: '潮汐、海岸和深蓝。', count: 27, hue: 190 },
  { groupKey: 'forest', name: '森林', description: '树影婆娑，满目苍翠。', count: 9, hue: 130 },
  { groupKey: 'night', name: '夜色', description: '星空与城市的夜晚。', count: 15, hue: 235 },
  { groupKey: 'architecture', name: '建筑', description: '线条、结构与光影。', count: 21, hue: 20 },
  { groupKey: 'animals', name: '动物', description: '可爱或野性的生灵们。', count: 7, hue: 45 },
  { groupKey: 'abstract', name: '抽象', description: '色彩与形状的游戏。', count: 33, hue: 290 },
  // 分组列表里有计数，但图片接口返回空页：用来看空状态和“计数回写”
  { groupKey: 'empty', name: '待整理', description: '图片还在整理中。', count: 6, hue: 0 },
  // 没有图片的分组：API 层 fetchGroups 会过滤掉
  { groupKey: 'drafts', name: '草稿', description: '还没有放图片的空分组。', count: 0, hue: 0 },
]

function mockId(seed: string): string {
  let h = 0x811c9dc5
  let out = ''
  for (let round = 0; out.length < 32; round++) {
    for (const ch of `${seed}#${round}`) h = Math.imul(h ^ ch.charCodeAt(0), 0x01000193) >>> 0
    out += h.toString(16).padStart(8, '0')
  }
  return out.slice(0, 32)
}

function svgUrl(w: number, h: number, hue: number, i: number, portrait: boolean): string {
  const h1 = (hue + i * 17) % 360
  const h2 = (h1 + 40) % 360
  const sunX = portrait ? w * 0.5 : w * (0.2 + ((i * 37) % 60) / 100)
  const sunY = portrait ? h * 0.2 : h * 0.3
  const r = Math.round(Math.min(w, h) * 0.09)
  // 竖图在上 20% 处画一个“头像”圆，便于核对 object-position: 50% 20%
  const figure = portrait
    ? `<circle cx="${w / 2}" cy="${h * 0.2}" r="${w * 0.14}" fill="hsl(${h1},30%,92%)"/><rect x="${w * 0.28}" y="${h * 0.3}" width="${w * 0.44}" height="${h * 0.7}" rx="${w * 0.2}" fill="hsl(${h1},30%,85%)"/>`
    : `<circle cx="${sunX}" cy="${sunY}" r="${r}" fill="hsl(${h1},90%,92%)" opacity=".9"/>`
  const hills = portrait
    ? ''
    : `<path d="M0 ${h * 0.72} L${w * 0.25} ${h * 0.5} L${w * 0.45} ${h * 0.68} L${w * 0.7} ${h * 0.42} L${w} ${h * 0.66} V${h} H0Z" fill="hsl(${h2},35%,28%)" opacity=".75"/>` +
      `<path d="M0 ${h * 0.85} L${w * 0.35} ${h * 0.66} L${w * 0.6} ${h * 0.82} L${w} ${h * 0.7} V${h} H0Z" fill="hsl(${h2},40%,18%)"/>`
  const svg =
    `<svg xmlns="http://www.w3.org/2000/svg" width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">` +
    `<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="hsl(${h1},70%,62%)"/><stop offset="1" stop-color="hsl(${h2},65%,82%)"/></linearGradient></defs>` +
    `<rect width="100%" height="100%" fill="url(#g)"/>${figure}${hills}</svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

const SIZES: Array<[number, number]> = [
  [1920, 1080],
  [2560, 1440],
  [3840, 2160],
]

const DB = GROUP_DEFS.map((def, idx) => {
  const id = mockId(`group-${def.groupKey}`)
  const images =
    def.groupKey === 'empty'
      ? []
      : Array.from({ length: def.count }, (_, i) => {
          const portrait = !!def.portrait || (def.groupKey === 'landscape' && i % 7 === 3)
          const [lw, lh] = SIZES[(i + idx) % SIZES.length]
          const [width, height] = portrait ? [lh, lw] : [lw, lh]
          return {
            id: mockId(`lp-${def.groupKey}-${i + 1}`),
            groupId: id,
            title: `${def.name} · ${String(i + 1).padStart(2, '0')}`,
            url: svgUrl(portrait ? 1080 : 1920, portrait ? 1920 : 1080, def.hue, i, portrait),
            thumbUrl: svgUrl(portrait ? 270 : 480, portrait ? 480 : 270, def.hue, i, portrait),
            width,
            height,
            fileSize: 800_000 + ((i * 7919) % 3_000_000),
            sort: i + 1,
            enabled: true,
            createTime: '2026-09-01 10:00:00',
          }
        })
  return {
    group: {
      id,
      name: def.name,
      groupKey: def.groupKey,
      description: def.description,
      sort: idx + 1,
      imageCount: def.count,
      isPublic: 1,
      coverUrl: images[0]?.url ?? null,
      coverThumbUrl: images[0]?.thumbUrl ?? null,
    },
    images,
  }
})

export function groups() {
  return DB.map((x) => ({ ...x.group }))
}

/** 返回 null 表示分组不存在或未公开（由 mock 路由转成 HTTP 404） */
export function images(groupKey: string, current: number, size: number) {
  const entry = DB.find((x) => x.group.groupKey === groupKey)
  if (!entry) return null
  const total = entry.images.length
  const cur = Math.max(1, current)
  const start = (cur - 1) * size
  // 与后端一致：Page 中不一定带 size
  return { records: entry.images.slice(start, start + size), total, current: cur, pages: Math.ceil(total / size) }
}
