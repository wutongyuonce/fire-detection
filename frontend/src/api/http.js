import axios from 'axios'

const http = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 120000,
})
http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const message =
      error.response?.data?.message || error.response?.data?.error || error.message || '请求失败'
    return Promise.reject(new Error(message))
  }
)

export default http
