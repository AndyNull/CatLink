import { request } from './request'

export interface VaultSyncRecord {
  recordId: string
  recordType: string
  keyVersion: number
  revision: number
  serverRevision: number
  ciphertext: string
  deleted: boolean
  mutationId: string
  baseServerRevision: number
  resolveConflict: boolean
}

export interface VaultSyncPayload {
  cursor: number
  records: VaultSyncRecord[]
  deviceId: string
}

export function syncVault(payload: VaultSyncPayload, token: string): Promise<unknown> {
  return request({ path: '/api/v1/vault/sync', method: 'POST', data: payload, token })
}
