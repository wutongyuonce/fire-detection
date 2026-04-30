import http from './http'

export function getOverview() {
  return http.get('/api/dashboard/overview')
}

export function uploadVideo(formData) {
  return http.post('/api/tasks/video/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

export function getTasks(params = {}) {
  return http.get('/api/tasks', { params })
}

export function getTaskDetail(taskId) {
  return http.get(`/api/tasks/${taskId}`)
}

export function getEvents(params = {}) {
  return http.get('/api/fire-events', { params })
}

export function getEventDetail(id) {
  return http.get(`/api/fire-events/${id}`)
}

export function deleteEvent(id) {
  return http.delete(`/api/fire-events/${id}`)
}

export function getImages(params = {}) {
  return http.get('/api/fire-images', { params })
}

export function getImageDetail(id) {
  return http.get(`/api/fire-images/${id}`)
}

export function deleteImage(id) {
  return http.delete(`/api/fire-images/${id}`)
}
