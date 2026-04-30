import { readonly, ref } from 'vue'
import { analyzeCameraFrame } from '../api'

const ANALYZE_INTERVAL = 3000

let cameraStream = null
let analyzeTimer = null
let mediaRecorder = null
let hiddenVideo = null

const monitoring = ref(false)
const cameraActive = ref(false)
const recording = ref(false)
const fireStatus = ref(false)
const latestConfidence = ref(0)
const lastBoxes = ref([])
const analyzingNow = ref(false)
const frameIndex = ref(0)
const recordedUrl = ref(null)

let recordedChunks = []
let eventCallbacks = []

function onFireDetected(cb) {
  eventCallbacks.push(cb)
}

function offFireDetected(cb) {
  eventCallbacks = eventCallbacks.filter(fn => fn !== cb)
}

function captureFrame() {
  if (!hiddenVideo || !cameraStream) return null
  const canvas = document.createElement('canvas')
  canvas.width = hiddenVideo.videoWidth || 640
  canvas.height = hiddenVideo.videoHeight || 480
  const ctx = canvas.getContext('2d')
  ctx.drawImage(hiddenVideo, 0, 0, canvas.width, canvas.height)
  return canvas.toDataURL('image/jpeg', 0.8)
}

async function captureAndAnalyze() {
  if (analyzingNow.value) return
  const dataUrl = captureFrame()
  if (!dataUrl) return

  frameIndex.value++
  analyzingNow.value = true
  try {
    const base64 = dataUrl.split(',')[1]
    const response = await analyzeCameraFrame({
      imageBase64: base64,
      sourceName: '摄像头监控',
      frameIndex: frameIndex.value,
    })
    const result = response.data
    lastBoxes.value = result.boxes || []
    latestConfidence.value = result.topConfidence || 0
    fireStatus.value = result.hasFire || false
    if (result.hasFire) {
      eventCallbacks.forEach(cb => cb())
    }
  } catch {
    lastBoxes.value = []
  } finally {
    analyzingNow.value = false
  }
}

function startRecording() {
  if (!cameraStream || recording.value) return
  recordedChunks = []
  recordedUrl.value = null
  const options = { mimeType: 'video/webm;codecs=vp9' }
  if (!MediaRecorder.isTypeSupported(options.mimeType)) {
    options.mimeType = 'video/webm;codecs=vp8'
    if (!MediaRecorder.isTypeSupported(options.mimeType)) {
      options.mimeType = 'video/webm'
    }
  }
  mediaRecorder = new MediaRecorder(cameraStream, options)
  mediaRecorder.ondataavailable = (event) => {
    if (event.data.size > 0) {
      recordedChunks.push(event.data)
    }
  }
  mediaRecorder.onstop = () => {
    if (recordedChunks.length > 0) {
      const blob = new Blob(recordedChunks, { type: mediaRecorder.mimeType })
      recordedUrl.value = URL.createObjectURL(blob)
    }
  }
  mediaRecorder.start(100)
  recording.value = true
}

function stopRecording() {
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    mediaRecorder.stop()
  }
  mediaRecorder = null
  recording.value = false
}

function startMonitoring() {
  if (monitoring.value) return
  monitoring.value = true
  frameIndex.value = 0
  startRecording()
  analyzeTimer = window.setInterval(captureAndAnalyze, ANALYZE_INTERVAL)
}

function stopMonitoring() {
  if (analyzeTimer) {
    window.clearInterval(analyzeTimer)
    analyzeTimer = null
  }
  monitoring.value = false
}

async function startCamera() {
  if (cameraActive.value) return
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: { width: 1280, height: 720 }, audio: false })
    cameraStream = stream
    cameraActive.value = true

    hiddenVideo = document.createElement('video')
    hiddenVideo.setAttribute('data-hidden-camera', 'true')
    hiddenVideo.srcObject = stream
    hiddenVideo.muted = true
    hiddenVideo.playsInline = true
    hiddenVideo.style.display = 'none'
    document.body.appendChild(hiddenVideo)
    await hiddenVideo.play()

    startMonitoring()
  } catch {
    throw new Error('无法访问摄像头，请检查浏览器权限设置')
  }
}

function stopCamera() {
  stopMonitoring()
  stopRecording()
  if (cameraStream) {
    cameraStream.getTracks().forEach(track => track.stop())
    cameraStream = null
  }
  if (hiddenVideo) {
    hiddenVideo.srcObject = null
    hiddenVideo.remove()
    hiddenVideo = null
  }
  cameraActive.value = false
  monitoring.value = false
  lastBoxes.value = []
  fireStatus.value = false
}

function clearRecordedUrl() {
  recordedUrl.value = null
}

export function useCameraMonitor() {
  return {
    monitoring: readonly(monitoring),
    cameraActive: readonly(cameraActive),
    recording: readonly(recording),
    fireStatus: readonly(fireStatus),
    latestConfidence: readonly(latestConfidence),
    lastBoxes: readonly(lastBoxes),
    analyzingNow: readonly(analyzingNow),
    frameIndex: readonly(frameIndex),
    recordedUrl,
    startCamera,
    stopCamera,
    clearRecordedUrl,
    onFireDetected,
    offFireDetected,
  }
}
