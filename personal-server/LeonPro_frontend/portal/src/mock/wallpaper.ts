/**
 * 本地 Mock 数据（VITE_USE_MOCK=true 时生效）。
 * 返回“原始”后端结构（未经 mapGroup/mapImage），以便同时验证字段映射。
 */

interface MockGroupDef {
  groupKey: string
  name: string
  description: string
  count: number
}

const GROUP_DEFS: MockGroupDef[] = [
  { groupKey: 'landscape', name: '风景', description: '山川湖海，远方与旷野。', count: 50 },
  { groupKey: 'city', name: '城市', description: '霓虹、街巷与天际线。', count: 18 },
  { groupKey: 'minimal', name: '极简', description: '留白与秩序，适合做桌面。', count: 12 },
  { groupKey: 'ocean', name: '海洋', description: '潮汐、海岸和深蓝。', count: 27 },
  { groupKey: 'forest', name: '森林', description: '树影婆娑，满目苍翠。', count: 9 },
  { groupKey: 'night', name: '夜色', description: '星空与城市的夜晚。', count: 15 },
  { groupKey: 'architecture', name: '建筑', description: '线条、结构与光影。', count: 21 },
  { groupKey: 'animals', name: '动物', description: '可爱或野性的生灵们。', count: 7 },
  { groupKey: 'abstract', name: '抽象', description: '色彩与形状的游戏。', count: 33 },
  { groupKey: 'portrait', name: '人像', description: '光影里的人与故事。', count: 14 },
  // 空分组：API 层 fetchGroups 会将其过滤掉
  { groupKey: 'drafts', name: '待整理', description: '还没有放图片的空分组。', count: 0 },
]

const SIZES: Array<[number, number]> = [
  [1920, 1080],
  [2560, 1440],
  [3840, 2160],
]

/** 生成确定性的 32 位十六进制字符串 id（模拟后端 UUID 去横线） */
function mockId(seed: string): string {
  let h1 = 0x811c9dc5
  let out = ''
  for (let round = 0; out.length < 32; round++) {
    for (const ch of `${seed}#${round}`) h1 = Math.imul(h1 ^ ch.charCodeAt(0), 0x01000193) >>> 0
    out += h1.toString(16).padStart(8, '0')
  }
  return out.slice(0, 32)
}

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms))
const jitter = () => 200 + Math.floor(Math.random() * 300)

function makeImages(def: MockGroupDef, groupIndex: number, groupId: string) {
  return Array.from({ length: def.count }, (_, i) => {
    const seed = `lp-${def.groupKey}-${i + 1}`
    const [width, height] = SIZES[(i + groupIndex) % SIZES.length]
    return {
      id: mockId(seed),
      groupId,
      title: `${def.name} · ${String(i + 1).padStart(2, '0')}`,
      // 统一使用 1920×1080 原图地址，width/height 仅用于展示分辨率
      url: `https://picsum.photos/seed/${seed}/1920/1080`,
      thumbUrl: `https://picsum.photos/seed/${seed}/480/270`,
      width,
      height,
      fileSize: 800_000 + ((i * 7919) % 3_000_000),
      sort: i + 1,
      enabled: true,
      createTime: new Date(Date.UTC(2026, 0, 1) + (groupIndex * 40 + i) * 86400000).toISOString().slice(0, 19).replace('T', ' '),
    }
  })
}

const DB = GROUP_DEFS.map((def, idx) => {
  const id = mockId(`group-${def.groupKey}`)
  const images = makeImages(def, idx + 1, id)
  return {
    group: {
      id,
      name: def.name,
      groupKey: def.groupKey,
      description: def.description,
      sort: idx + 1,
      imageCount: images.length,
      isPublic: 1,
      coverUrl: images[0]?.url ?? null,
      coverThumbUrl: images[0]?.thumbUrl ?? null,
    },
    images,
  }
})

export async function groups() {
  await delay(jitter())
  return DB.map((x) => ({ ...x.group }))
}

export async function images(groupKey: string, current: number, size: number) {
  await delay(jitter())
  const entry = DB.find((x) => x.group.groupKey === groupKey)
  if (!entry) throw new Error('分组不存在或未公开')
  const total = entry.images.length
  const cur = Math.max(1, current)
  const start = (cur - 1) * size
  // 与后端一致：Page 中不一定带 size
  return {
    records: entry.images.slice(start, start + size),
    total,
    current: cur,
    pages: Math.ceil(total / size),
  }
}
