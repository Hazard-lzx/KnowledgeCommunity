import request from './request'

export function followUser(userId) {
  return request.post(`/users/${userId}/follow`)
}

export function unfollowUser(userId) {
  return request.delete(`/users/${userId}/follow`)
}

export function getUserProfile(userId) {
  return request.get(`/users/${userId}`)
}

export function updateProfile(data) {
  return request.put('/users/me', data)
}
