import http from './http'

export function getOverview() {
  return http.get('/api/dashboard/overview')
}

export function uploadVideo(formData) {
  return http.post('/api/tasks/video/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 300000,
  })
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

export function clearUploadedVideos() {
  return http.delete('/api/tasks/video/clear')
}

export function analyzeCameraFrame(data) {
  return http.post('/api/tasks/camera/analyze-frame', data, { timeout: 30000 })
}
