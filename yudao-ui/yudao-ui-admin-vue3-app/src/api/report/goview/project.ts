import request from '@/config/axios'

/** 安全模式仅提供本人项目元数据，不接入外部设计器或任意 SQL。 */
export interface GoViewProjectVO {
  id: number
  name: string
  status: number
  createTime?: string
}

const url = '/report/go-view/project'

export const GoViewProjectApi = {
  getMyPage: (params: { pageNo: number; pageSize: number }) =>
    request.get<{ list: GoViewProjectVO[]; total: number }>({ url: `${url}/my-page`, params }),
  create: (name: string) => request.post<number>({ url: `${url}/create`, data: { name } }),
  rename: (id: number, name: string) =>
    request.put<boolean>({ url: `${url}/update`, data: { id, name } }),
  remove: (id: number) => request.delete<boolean>({ url: `${url}/delete`, params: { id } })
}
