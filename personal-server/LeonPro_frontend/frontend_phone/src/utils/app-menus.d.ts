import type { AppMenu } from '../api/types'

export function normalizeAppMenus(list: unknown): AppMenu[]
export function canAccessMenu(
  menus: Array<{ path: string }>,
  menuPath: string,
  manifest?: Array<{ path: string; parent?: string | null; hidden?: boolean }>,
): boolean
export function homeToolMenus(menus: AppMenu[]): AppMenu[]
