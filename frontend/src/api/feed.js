import request from './request'

export function getFeed(params) {
  return request.get('/feed', { params })
}

export function getFollowingFeed(params) {
  return request.get('/feed', { params: { ...params, mode: 'following' } })
}
