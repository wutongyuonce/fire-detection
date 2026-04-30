from __future__ import annotations

import base64
from datetime import datetime
from pathlib import Path
from typing import Literal

import cv2
import numpy as np
from fastapi import FastAPI
from pydantic import BaseModel, Field
from ultralytics import YOLO

PROJECT_DIR = Path(__file__).resolve().parent
DEFAULT_WEIGHTS_PATH = PROJECT_DIR / "best.pt"
DEFAULT_SNAPSHOT_DIR = PROJECT_DIR / "backend" / "storage" / "snapshots" / "fire"
DEFAULT_SNAPSHOT_REL_DIR = "snapshots/fire"

app = FastAPI(title="Fire Detection Inference Service")
MODEL_CACHE: dict[str, YOLO] = {}


class VideoAnalyzeRequest(BaseModel):
    taskNo: str
    videoPath: str
    weightsPath: str | None = None
    snapshotDir: str | None = None
    snapshotRelativeDir: str | None = None
    sourceName: str | None = None
    device: str = "cpu"
    confThreshold: float = Field(default=0.30, ge=0.0, le=1.0)


class EventItem(BaseModel):
    eventTime: str
    confidence: float
    durationSeconds: float = 0.0
    snapshotPath: str
    taskFrameNo: int


class VideoAnalyzeResponse(BaseModel):
    taskNo: str
    status: Literal["FINISHED", "FAILED"]
    frameCount: int
    fireCount: int
    resultSummary: str
    errorMessage: str | None = None
    events: list[EventItem] = []


def get_model(weights_path: str) -> YOLO:
    cached = MODEL_CACHE.get(weights_path)
    if cached is None:
        MODEL_CACHE[weights_path] = YOLO(weights_path)
        cached = MODEL_CACHE[weights_path]
    return cached


def ensure_dir(path: Path) -> Path:
    path.mkdir(parents=True, exist_ok=True)
    return path


@app.get("/health")
def health() -> dict:
    return {
        "status": "UP",
        "defaultWeightsExists": DEFAULT_WEIGHTS_PATH.exists(),
        "cachedModelCount": len(MODEL_CACHE),
    }


@app.post("/infer/video/analyze", response_model=VideoAnalyzeResponse)
def analyze_video(request: VideoAnalyzeRequest) -> VideoAnalyzeResponse:
    video_path = Path(request.videoPath).expanduser().resolve()
    weights_path = Path(request.weightsPath).expanduser().resolve() if request.weightsPath else DEFAULT_WEIGHTS_PATH
    snapshot_dir = ensure_dir(Path(request.snapshotDir).expanduser().resolve()) if request.snapshotDir else ensure_dir(DEFAULT_SNAPSHOT_DIR)
    snapshot_relative_dir = (request.snapshotRelativeDir or DEFAULT_SNAPSHOT_REL_DIR).replace("\\", "/").strip("/")

    if not video_path.exists():
        return VideoAnalyzeResponse(
            taskNo=request.taskNo,
            status="FAILED",
            frameCount=0,
            fireCount=0,
            resultSummary="Video file not found",
            errorMessage=f"Video file not found: {video_path}",
            events=[],
        )

    if not weights_path.exists():
        return VideoAnalyzeResponse(
            taskNo=request.taskNo,
            status="FAILED",
            frameCount=0,
            fireCount=0,
            resultSummary="Weights file not found",
            errorMessage=f"Weights file not found: {weights_path}",
            events=[],
        )

    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        return VideoAnalyzeResponse(
            taskNo=request.taskNo,
            status="FAILED",
            frameCount=0,
            fireCount=0,
            resultSummary="Unable to open video",
            errorMessage=f"Unable to open video: {video_path}",
            events=[],
        )

    model = get_model(str(weights_path))
    fps = cap.get(cv2.CAP_PROP_FPS)
    if fps is None or fps <= 1e-3:
        fps = 25.0

    frame_interval = max(1, int(round(fps / 3)))
    event_gap_frames = max(frame_interval, int(round(fps * 3)))

    frame_count = 0
    last_event_frame = -event_gap_frames
    events: list[EventItem] = []

    try:
        while True:
            ok, frame = cap.read()
            if not ok:
                break

            frame_count += 1
            if frame_count % frame_interval != 0:
                continue

            results = model.predict(frame, imgsz=640, conf=request.confThreshold, device=request.device, verbose=False)
            result = results[0]

            top_conf = 0.0
            for box in result.boxes:
                top_conf = max(top_conf, float(box.conf.item()))

            if top_conf < request.confThreshold:
                continue

            if frame_count - last_event_frame < event_gap_frames:
                continue

            annotated = result.plot()
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            file_name = f"{request.taskNo}_{frame_count}_{timestamp}.jpg"
            snapshot_file = snapshot_dir / file_name
            cv2.imwrite(str(snapshot_file), annotated)

            relative_snapshot_path = f"{snapshot_relative_dir}/{file_name}"
            duration_seconds = round(frame_count / fps, 2)
            video_seconds = round(frame_count / fps)
            video_timecode = f"{video_seconds // 3600:02d}:{(video_seconds % 3600) // 60:02d}:{video_seconds % 60:02d}"
            events.append(
                EventItem(
                    eventTime=video_timecode,
                    confidence=round(top_conf, 4),
                    durationSeconds=duration_seconds,
                    snapshotPath=relative_snapshot_path,
                    taskFrameNo=frame_count,
                )
            )
            last_event_frame = frame_count
    except Exception as exc:
        return VideoAnalyzeResponse(
            taskNo=request.taskNo,
            status="FAILED",
            frameCount=frame_count,
            fireCount=len(events),
            resultSummary="Video analysis failed",
            errorMessage=str(exc),
            events=events,
        )
    finally:
        cap.release()

    return VideoAnalyzeResponse(
        taskNo=request.taskNo,
        status="FINISHED",
        frameCount=frame_count,
        fireCount=len(events),
        resultSummary=f"Video analysis finished, detected {len(events)} fire event(s)",
        errorMessage=None,
        events=events,
    )


class BoundingBox(BaseModel):
    x1: float
    y1: float
    x2: float
    y2: float
    confidence: float
    class_id: int = 0
    class_name: str = "fire"


class FrameAnalyzeRequest(BaseModel):
    taskNo: str
    imageBase64: str
    weightsPath: str | None = None
    snapshotDir: str | None = None
    snapshotRelativeDir: str | None = None
    sourceName: str | None = None
    frameIndex: int = 0
    device: str = "cpu"
    confThreshold: float = Field(default=0.30, ge=0.0, le=1.0)


class FrameAnalyzeResponse(BaseModel):
    taskNo: str
    status: Literal["FINISHED", "FAILED"]
    hasFire: bool
    topConfidence: float
    boxCount: int
    boxes: list[BoundingBox] = []
    snapshotPath: str | None = None
    errorMessage: str | None = None


@app.post("/infer/frame/analyze", response_model=FrameAnalyzeResponse)
def analyze_frame(request: FrameAnalyzeRequest) -> FrameAnalyzeResponse:
    weights_path = Path(request.weightsPath).expanduser().resolve() if request.weightsPath else DEFAULT_WEIGHTS_PATH
    snapshot_dir = ensure_dir(Path(request.snapshotDir).expanduser().resolve()) if request.snapshotDir else ensure_dir(DEFAULT_SNAPSHOT_DIR)
    snapshot_relative_dir = (request.snapshotRelativeDir or DEFAULT_SNAPSHOT_REL_DIR).replace("\\", "/").strip("/")

    if not weights_path.exists():
        return FrameAnalyzeResponse(
            taskNo=request.taskNo,
            status="FAILED",
            hasFire=False,
            topConfidence=0.0,
            boxCount=0,
            errorMessage=f"Weights file not found: {weights_path}",
        )

    try:
        img_bytes = base64.b64decode(request.imageBase64)
        np_arr = np.frombuffer(img_bytes, dtype=np.uint8)
        frame = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
        if frame is None:
            return FrameAnalyzeResponse(
                taskNo=request.taskNo,
                status="FAILED",
                hasFire=False,
                topConfidence=0.0,
                boxCount=0,
                errorMessage="Failed to decode base64 image",
            )
    except Exception as exc:
        return FrameAnalyzeResponse(
            taskNo=request.taskNo,
            status="FAILED",
            hasFire=False,
            topConfidence=0.0,
            boxCount=0,
            errorMessage=f"Base64 decode error: {exc}",
        )

    model = get_model(str(weights_path))

    try:
        results = model.predict(frame, imgsz=640, conf=request.confThreshold, device=request.device, verbose=False)
        result = results[0]

        boxes: list[BoundingBox] = []
        top_conf = 0.0
        for box in result.boxes:
            conf = float(box.conf.item())
            cls_id = int(box.cls.item())
            cls_name = result.names.get(cls_id, "fire") if result.names else "fire"
            x1, y1, x2, y2 = box.xyxy[0].tolist()
            boxes.append(BoundingBox(
                x1=round(x1, 2), y1=round(y1, 2),
                x2=round(x2, 2), y2=round(y2, 2),
                confidence=round(conf, 4),
                class_id=cls_id,
                class_name=cls_name,
            ))
            top_conf = max(top_conf, conf)

        has_fire = len(boxes) > 0 and top_conf >= request.confThreshold
        snapshot_path = None

        if has_fire:
            annotated = result.plot()
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            file_name = f"{request.taskNo}_{request.frameIndex}_{timestamp}.jpg"
            snapshot_file = snapshot_dir / file_name
            cv2.imwrite(str(snapshot_file), annotated)
            snapshot_path = f"{snapshot_relative_dir}/{file_name}"

        return FrameAnalyzeResponse(
            taskNo=request.taskNo,
            status="FINISHED",
            hasFire=has_fire,
            topConfidence=round(top_conf, 4),
            boxCount=len(boxes),
            boxes=boxes,
            snapshotPath=snapshot_path,
        )

    except Exception as exc:
        return FrameAnalyzeResponse(
            taskNo=request.taskNo,
            status="FAILED",
            hasFire=False,
            topConfidence=0.0,
            boxCount=0,
            errorMessage=str(exc),
        )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("inference_service:app", host="0.0.0.0", port=8000, reload=False)
