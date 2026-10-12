import request from '@/config/axios'
export interface Registration { id: number; organization_name: string; contact_name: string; contact_email: string; status: string; tenant_id?: number }
export interface Plan { id: number; name: string; max_users: number; max_patients: number; motion_quota: number; storage_quota: number }
export interface Tenant { id: number; name: string; registration_status: string; subscription_status: string; user_count: number; patient_count: number; motion_count: number; storage_bytes: number }
export interface Approval { registrationId: number; planId: number; packageId: number; username: string; password: string; expires: string }
const root = '/system/saas'
export const registerOrganization = (data: { organization: string; contact: string; email: string }) => request.post<string>({ url: root + '/register', data, headers: { isToken: false } })
export const getRegistrations = (afterId = 0) => request.get<Registration[]>({ url: root + '/platform/registrations', params: { afterId } })
export const getTenants = (afterId = 0) => request.get<Tenant[]>({ url: root + '/platform/tenants', params: { afterId } })
export const getPlans = () => request.get<Plan[]>({ url: root + '/platform/plans' })
export const approve = (data: Approval) => request.post<number>({ url: root + '/platform/approve', data })
export const reject = (id: number) => request.post({ url: root + '/platform/reject', params: { id } })
export const suspend = (tenantId: number) => request.post({ url: root + '/platform/suspend', params: { tenantId } })

export interface StaffInvitation { id: number; recipient_email: string; status: string; expires_at: string }
export const createPlan = (data: { name: string; maxUsers: number; maxPatients: number; motionQuota: number; storageQuota: number }) => request.post<number>({ url: root + '/platform/plans', data })
export const renew = (tenantId: number, expires: string) => request.post({ url: root + '/platform/renew', data: { tenantId, expires } })
export const getStaffInvitations = (afterId = 0) => request.get<StaffInvitation[]>({ url: root + '/staff-invitations', params: { afterId } })
export const issueStaffInvitation = (email: string) => request.post<{ id: number; tenantId: number; token: string }>({ url: root + '/staff-invitations', data: { email } })
export const revokeStaffInvitation = (id: number) => request.post({ url: root + '/staff-invitations/revoke', params: { id } })
// Deliberately do not inherit the browser's existing tenant/session or persist credentials.
export async function acceptStaffInvitation(tenantId: number, data: { token: string; username: string; nickname: string; password: string }) {
  const base = `${import.meta.env.VITE_BASE_URL || ''}${import.meta.env.VITE_API_URL || '/admin-api'}`
  const response = await fetch(base + root + '/staff-invitations/accept', {
    method: 'POST', credentials: 'omit', cache: 'no-store',
    headers: { 'Content-Type': 'application/json', 'tenant-id': String(tenantId) }, body: JSON.stringify(data)
  })
  if (!response.ok) throw new Error('Enrollment failed')
  const result = await response.json()
  if (result.code !== 0) throw new Error('Enrollment failed')
  return result.data as number
}
