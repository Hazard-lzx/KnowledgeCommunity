import request from './request'

export function askQuestion(articleId, question, signal) {
  const token = localStorage.getItem('accessToken')
  return fetch(`/api/qa/ask`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify({ articleId, question }),
    signal,
  })
}

export function writingAssist(type, content, context) {
  const token = localStorage.getItem('accessToken')
  return fetch(`/api/ai/writing-assist`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`,
    },
    body: JSON.stringify({ type, content, context }),
  })
}

export function getPresignedUrl(fileName) {
  return request.get('/oss/presign', { params: { fileName } })
}

export function uploadFile(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/oss/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}