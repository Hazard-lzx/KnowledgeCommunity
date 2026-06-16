import request from './request'

export function search(params) {
  return request.get('/search', { params })
}

export function suggest(prefix) {
  return request.get('/search/suggest', { params: { prefix } })
}
