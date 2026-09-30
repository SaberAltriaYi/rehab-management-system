import { request } from '../utils/request'

export function getMyPatients(params) {
  return request({ url: '/app-admin/patients/my-page', data: params })
}

export function getPatientSummary(id) {
  return request({ url: '/app-admin/patients/summary', data: { id } })
}

export function getPatientCheckins(params) {
  return request({ url: '/app-admin/patients/checkins', data: params })
}
