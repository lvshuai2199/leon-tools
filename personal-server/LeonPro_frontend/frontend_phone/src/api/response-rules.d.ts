export const AUTH_EXPIRED_MESSAGE: string
export const FORBIDDEN_MESSAGE: string
export const GENERIC_MESSAGE: string
export function bodyCode(body: unknown): string
export function bodyMessage(body: unknown): string
export type Interpreted =
  | { kind: 'ok'; data: unknown }
  | { kind: 'empty' }
  | { kind: 'authExpired'; message: string; code: number; fromServer: boolean }
  | { kind: 'error'; message: string; code: string | number | undefined; fromServer: boolean }
export function interpretResponse(httpStatus: number, body: unknown, opts?: { authCheck?: boolean }): Interpreted
