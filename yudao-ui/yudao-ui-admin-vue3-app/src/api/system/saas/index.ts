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
