import request from '@/config/axios'

export interface DirectoryItem {
  customerId: number
  name: string
  claimedDeptId: number | null
}

export interface DepartmentCase {
  id: number
  customerId: number
  customerName: string
  deptId: number
  claimTime: string
  privateNote: string | null
}

export interface Workspace {
  deptId: number
  displayName: string
  enabled: boolean
}

export interface TransferItem {
  id: number
  caseId: number
  fromDeptId: number
  toDeptId: number
  customerName: string
  reason: string | null
  createTime: string
}

export const getDirectory = (params: { name?: string; pageNo: number; pageSize: number }) =>
  request.get<{ list: DirectoryItem[]; total: number }>({ url: '/crm/department/directory', params })
export const getWorkspaces = () =>
  request.get<Workspace[]>({ url: '/crm/department/workspaces' })
export const configureWorkspace = (data: Workspace) =>
  request.put({ url: '/crm/department/workspace', data })
export const getCases = (params: { pageNo: number; pageSize: number }) =>
  request.get<{ list: DepartmentCase[]; total: number }>({ url: '/crm/department/cases', params })
export const claim = (customerId: number) =>
  request.post<number>({ url: '/crm/department/claim', data: { customerId } })
export const saveNote = (caseId: number, content: string) =>
  request.put({ url: '/crm/department/note', data: { caseId, content } })
export const requestTransfer = (caseId: number, toDeptId: number, reason: string) =>
  request.post<number>({ url: '/crm/department/transfer', data: { caseId, toDeptId, reason } })
export const getIncoming = () =>
  request.get<TransferItem[]>({ url: '/crm/department/transfers/incoming' })
export const resolveTransfer = (transferId: number, accept: boolean) =>
  request.put({ url: '/crm/department/transfers/resolve', data: { transferId, accept } })
