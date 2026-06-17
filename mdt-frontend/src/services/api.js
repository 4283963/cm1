import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

request.interceptors.request.use(
  (config) => {
    const userId = localStorage.getItem('userId')
    if (userId) {
      config.headers['X-User-Id'] = userId
    }
    return config
  },
  (error) => Promise.reject(error)
)

request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    console.error('请求错误:', error)
    return Promise.reject(error)
  }
)

export const consultationApi = {
  create: (data) => request.post('/consultation/create', data),

  accept: (id, expertId) =>
    request.post(`/consultation/${id}/accept`, { expertId }),

  decline: (id, expertId, reason) =>
    request.post(`/consultation/${id}/decline`, { expertId, reason }),

  start: (id, userId) =>
    request.post(`/consultation/${id}/start`, { userId }),

  end: (id, userId) =>
    request.post(`/consultation/${id}/end`, { userId }),

  getDetail: (id) => request.get(`/consultation/${id}`),

  getMyInitiated: (userId) =>
    request.get('/consultation/my/initiated', { params: { userId } }),

  getMyInvitations: (expertId) =>
    request.get('/consultation/my/invitations', { params: { expertId } }),

  getDepartments: () => request.get('/consultation/departments'),

  getExperts: (params) =>
    request.get('/consultation/experts', { params }),

  inviteAdditionalExperts: (consultationId, operatorId, expertIds, urgent = false) =>
    request.post(`/consultation/${consultationId}/invite-experts`, {
      operatorId,
      expertIds,
      urgent,
    }),

  transferPresenter: (consultationId, fromUserId, toUserId) =>
    request.post(`/consultation/${consultationId}/transfer-presenter`, {
      fromUserId,
      toUserId,
    }),

  getControlState: (consultationId) =>
    request.get(`/consultation/${consultationId}/control-state`),

  broadcastControlEvent: (consultationId, operatorId, eventType, payload = {}) =>
    request.post(`/consultation/${consultationId}/control-event`, {
      operatorId,
      eventType,
      payload,
    }),
}

export default request
