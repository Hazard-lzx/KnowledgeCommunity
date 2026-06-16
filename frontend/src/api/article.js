import request from './request'

export function createArticle(data) {
  return request.post('/articles', data)
}

export function getArticle(id) {
  return request.get(`/articles/${id}`)
}

export function updateArticle(id, data) {
  return request.put(`/articles/${id}`, data)
}

export function deleteArticle(id) {
  return request.delete(`/articles/${id}`)
}

export function getUserArticles(userId, status) {
  return request.get(`/articles/user/${userId}`, { params: { status } })
}
