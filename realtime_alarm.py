"""
实时视频火焰报警（摄像头/视频）

功能：
1. 实时检测火焰目标并画框
2. 当检测置信度 >= FIRE_CONF_THRES 且持续时间 >= ALARM_HOLD_SECONDS 时触发报警
3. 报警时截图保存 + 弹窗提示（Windows）
4. 输出 JSON 事件日志，便于后续前后端对接
"""

from __future__ import annotations

import argparse
import ctypes
import json
import threading
import time
from datetime import datetime
from pathlib import Path

import cv2
from ultralytics import YOLO


# ============================================================================
# 用户可配置参数（顶部集中）
# ============================================================================

PROJECT_DIR = Path(__file__).resolve().parent.parent

DEFAULT_WEIGHTS_PATH = PROJECT_DIR / "runs" / "train1" / "weights" / "best.pt"
DEFAULT_SOURCE = "0"  # 摄像头可用 "0" / "1"，也可填视频路径
DEFAULT_DEVICE = "0"  # "0" 使用 GPU，"cpu" 使用 CPU

FIRE_CONF_THRES = 0.30
ALARM_HOLD_SECONDS = 1.5
ALARM_COOLDOWN_SECONDS = 8.0

DEFAULT_IMGSZ = 640
DEFAULT_SAVE_VIDEO = True
DEFAULT_SHOW_WINDOW = True

RESULTS_ROOT = PROJECT_DIR / "results" / "realtime_alarm"
SNAPSHOT_DIR = RESULTS_ROOT / "snapshots"
EVENTS_DIR = RESULTS_ROOT / "events"


def resolve_source(source: str):
    """把 source 字符串解析为摄像头索引或文件路径。"""
    s = str(source).strip()
    if s.isdigit():
        return int(s)
    return s


def now_iso() -> str:
    return datetime.now().isoformat(timespec="seconds")


def append_event(event_file: Path, payload: dict):
    event_file.parent.mkdir(parents=True, exist_ok=True)
    with event_file.open("a", encoding="utf-8") as f:
        f.write(json.dumps(payload, ensure_ascii=False) + "\n")


def write_latest_status(status_file: Path, payload: dict):
    status_file.parent.mkdir(parents=True, exist_ok=True)
    with status_file.open("w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)


def show_windows_popup_async(title: str, message: str):
    """Windows 异步弹窗，避免阻塞视频循环。"""

    def _worker():
        try:
            ctypes.windll.user32.MessageBoxW(0, message, title, 0x00001000)
        except Exception:
            pass

    t = threading.Thread(target=_worker, daemon=True)
    t.start()


def draw_alarm_overlay(frame, text: str):
    """在画面顶部叠加报警横幅。"""
    h, w = frame.shape[:2]
    banner_h = max(48, h // 14)
    cv2.rectangle(frame, (0, 0), (w, banner_h), (0, 0, 255), -1)
    cv2.putText(
        frame,
        text,
        (16, int(banner_h * 0.7)),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.9,
        (255, 255, 255),
        2,
        cv2.LINE_AA,
    )


def is_target_box(box, names: dict, min_conf: float) -> bool:
    """判断单个检测框是否满足报警条件。"""
    conf = float(box.conf.item())
    if conf < min_conf:
        return False

    # 当前你的数据集为单类火焰，默认只按置信度判定。
    # 如后续扩展多类，可在这里加 class 白名单过滤。
    _ = names
    return True


def main():
    parser = argparse.ArgumentParser(description="实时火焰报警（阈值+持续时长）")
    parser.add_argument("--weights", type=str, default=str(DEFAULT_WEIGHTS_PATH), help="完整权重文件路径")
    parser.add_argument("--source", type=str, default=DEFAULT_SOURCE, help="摄像头编号(0/1)或视频路径")
    parser.add_argument("--device", type=str, default=DEFAULT_DEVICE, help="推理设备: 0/cpu")
    parser.add_argument("--imgsz", type=int, default=DEFAULT_IMGSZ, help="推理尺寸")
    parser.add_argument("--conf", type=float, default=FIRE_CONF_THRES, help="火焰置信度阈值")
    parser.add_argument("--hold", type=float, default=ALARM_HOLD_SECONDS, help="连续触发报警时长(秒)")
    parser.add_argument("--cooldown", type=float, default=ALARM_COOLDOWN_SECONDS, help="两次报警最小间隔(秒)")
    parser.add_argument("--save-video", action="store_true", default=DEFAULT_SAVE_VIDEO, help="保存标注后视频")
    parser.add_argument("--no-save-video", action="store_false", dest="save_video", help="不保存标注后视频")
    parser.add_argument("--show", action="store_true", default=DEFAULT_SHOW_WINDOW, help="显示实时窗口")
    parser.add_argument("--no-show", action="store_false", dest="show", help="不显示实时窗口")
    args = parser.parse_args()

    weights_path = Path(args.weights)
    if not weights_path.is_absolute():
        weights_path = (PROJECT_DIR / weights_path).resolve()
    if not weights_path.exists():
        raise FileNotFoundError(f"找不到权重文件: {weights_path}")

    RESULTS_ROOT.mkdir(parents=True, exist_ok=True)
    SNAPSHOT_DIR.mkdir(parents=True, exist_ok=True)
    EVENTS_DIR.mkdir(parents=True, exist_ok=True)

    event_file = EVENTS_DIR / "events.jsonl"
    latest_status_file = EVENTS_DIR / "latest_status.json"

    source = resolve_source(args.source)
    cap = cv2.VideoCapture(source)
    if not cap.isOpened():
        raise RuntimeError(f"无法打开视频源: {args.source}")

    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 1280)
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 720)
    fps = cap.get(cv2.CAP_PROP_FPS)
    if fps is None or fps <= 1e-3:
        fps = 25.0

    writer = None
    out_video_path = None
    if args.save_video:
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        out_video_path = RESULTS_ROOT / f"realtime_{stamp}.avi"
        fourcc = cv2.VideoWriter_fourcc(*"XVID")
        writer = cv2.VideoWriter(str(out_video_path), fourcc, fps, (width, height))

    model = YOLO(str(weights_path))

    fire_started_at = None
    last_alarm_at = 0.0
    last_status_push = 0.0
    frame_index = 0

    append_event(
        event_file,
        {
            "time": now_iso(),
            "type": "session_start",
            "weights": str(weights_path),
            "source": str(args.source),
            "conf": args.conf,
            "hold_seconds": args.hold,
            "cooldown_seconds": args.cooldown,
        },
    )

    print("=" * 68)
    print("实时火焰报警已启动")
    print("按 q 退出，按 s 手动截图")
    print("=" * 68)

    try:
        while True:
            ok, frame = cap.read()
            if not ok:
                break

            frame_index += 1
            now = time.time()

            results = model.predict(frame, imgsz=args.imgsz, conf=args.conf, device=args.device, verbose=False)
            r = results[0]

            has_fire = False
            top_conf = 0.0

            for box in r.boxes:
                conf = float(box.conf.item())
                top_conf = max(top_conf, conf)
                if is_target_box(box, r.names, args.conf):
                    has_fire = True

            annotated = r.plot()

            # 连续时长逻辑：检测到 -> 计时；未检测到 -> 清空计时
            if has_fire:
                if fire_started_at is None:
                    fire_started_at = now
                duration = now - fire_started_at
            else:
                fire_started_at = None
                duration = 0.0

            alarm_triggered = False
            if has_fire and duration >= args.hold and (now - last_alarm_at) >= args.cooldown:
                alarm_triggered = True
                last_alarm_at = now

                alarm_stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
                snapshot_path = SNAPSHOT_DIR / f"alarm_{alarm_stamp}.jpg"
                cv2.imwrite(str(snapshot_path), annotated)

                draw_alarm_overlay(annotated, f"FIRE ALERT! conf={top_conf:.2f} hold={duration:.1f}s")

                if args.show and isinstance(source, int):
                    show_windows_popup_async(
                        "火焰报警",
                        f"检测到持续火焰\n置信度: {top_conf:.2f}\n持续: {duration:.1f}s\n截图: {snapshot_path}",
                    )

                append_event(
                    event_file,
                    {
                        "time": now_iso(),
                        "type": "alarm",
                        "frame": frame_index,
                        "confidence": round(top_conf, 4),
                        "duration": round(duration, 3),
                        "snapshot": str(snapshot_path),
                    },
                )

            status = {
                "time": now_iso(),
                "frame": frame_index,
                "has_fire": has_fire,
                "top_conf": round(top_conf, 4),
                "fire_duration": round(duration, 3),
                "alarm_triggered": alarm_triggered,
                "weights": str(weights_path),
                "source": str(args.source),
            }

            # 状态文件每秒更新一次，方便后续前后端轮询
            if now - last_status_push >= 1.0:
                write_latest_status(latest_status_file, status)
                last_status_push = now

            if has_fire:
                draw_alarm_overlay(annotated, f"Fire detected conf={top_conf:.2f} hold={duration:.1f}s")

            if writer is not None:
                writer.write(annotated)

            if args.show:
                cv2.imshow("Realtime Fire Alarm", annotated)
                key = cv2.waitKey(1) & 0xFF
                if key == ord("q"):
                    break
                if key == ord("s"):
                    manual_path = SNAPSHOT_DIR / f"manual_{datetime.now().strftime('%Y%m%d_%H%M%S')}.jpg"
                    cv2.imwrite(str(manual_path), annotated)
                    append_event(
                        event_file,
                        {
                            "time": now_iso(),
                            "type": "manual_snapshot",
                            "frame": frame_index,
                            "path": str(manual_path),
                        },
                    )

    finally:
        cap.release()
        if writer is not None:
            writer.release()
        cv2.destroyAllWindows()

        append_event(
            event_file,
            {
                "time": now_iso(),
                "type": "session_end",
                "saved_video": str(out_video_path) if out_video_path else None,
            },
        )

        print("\n已退出实时报警")
        print(f"事件日志: {event_file}")
        print(f"状态文件: {latest_status_file}")
        if out_video_path:
            print(f"输出视频: {out_video_path}")


if __name__ == "__main__":
    main()
