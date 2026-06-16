import request from './request'

export function likeArticle(articleId) {
  return request.post(`/articles/${articleId}/like`)
}

export function unlikeArticle(articleId) {
  return request.delete(`/articles/${articleId}/like`)
}

export function collectArticle(articleId) {
  return request.post(`/articles/${articleId}/collect`)
}

export function uncollectArticle(articleId) {
  return request.delete(`/articles/${articleId}/collect`)
}
