#!/data/data/com.termux/files/usr/bin/python
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
from pathlib import Path
from threading import Lock, Event
import re
import shutil
import uuid

from flask import Flask, jsonify, request
import yt_dlp

APP_VERSION = "1.0.0"
HOST = "127.0.0.1"
PORT = 8080
DOWNLOAD_DIR = Path("/storage/emulated/0/Download/ALF Downloader")
DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)

app = Flask(__name__)
executor = ThreadPoolExecutor(max_workers=2)
lock = Lock()
jobs: dict[str, dict] = {}
cancel_events: dict[str, Event] = {}

QUALITY_FORMATS = {
    "best": "bv*+ba/b",
    "1080": "bv*[height<=1080]+ba/b[height<=1080]",
    "720": "bv*[height<=720]+ba/b[height<=720]",
    "480": "bv*[height<=480]+ba/b[height<=480]",
    "360": "bv*[height<=360]+ba/b[height<=360]",
    "audio": "ba/b",
}

def now() -> str:
    return datetime.now(timezone.utc).isoformat()

def safe_url(value: str) -> str:
    value = value.strip()
    if not re.match(r"^https?://", value, re.I):
        raise ValueError("URL harus dimulai dengan http:// atau https://")
    if len(value) > 4096:
        raise ValueError("URL terlalu panjang")
    return value

def clean_filename(path: Path) -> str:
    try:
        rel = path.relative_to(DOWNLOAD_DIR)
        return str(rel)
    except ValueError:
        return path.name

def set_job(job_id: str, **updates) -> None:
    with lock:
        if job_id in jobs:
            jobs[job_id].update(updates)

def progress_hook(job_id: str, event: dict) -> None:
    if cancel_events[job_id].is_set():
        raise yt_dlp.utils.DownloadCancelled()
    status = event.get("status")
    if status == "downloading":
        total = event.get("total_bytes") or event.get("total_bytes_estimate") or 0
        downloaded = event.get("downloaded_bytes") or 0
        pct = (downloaded / total * 100) if total else 0
        set_job(
            job_id,
            status="downloading",
            progress=max(0.0, min(100.0, pct)),
            downloadedBytes=int(downloaded),
            totalBytes=int(total),
            speed=event.get("_speed_str") or event.get("speed_str"),
            eta=event.get("_eta_str") or event.get("eta"),
            filename=event.get("filename"),
        )
    elif status == "finished":
        set_job(job_id, status="processing", progress=99.0, filename=event.get("filename"))

def build_opts(job_id: str, url: str, quality: str) -> dict:
    fmt = QUALITY_FORMATS.get(quality, QUALITY_FORMATS["best"])
    outtmpl = str(DOWNLOAD_DIR / "%(title).180s [%(id)s].%(ext)s")
    opts = {
        "format": fmt,
        "outtmpl": outtmpl,
        "paths": {"home": str(DOWNLOAD_DIR)},
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "restrictfilenames": False,
        "windowsfilenames": True,
        "progress_hooks": [lambda d: progress_hook(job_id, d)],
        "continuedl": True,
        "retries": 3,
        "fragment_retries": 3,
        "concurrent_fragment_downloads": 4,
        "socket_timeout": 20,
        "js_runtimes": ["node"],
    }
    if quality != "audio":
        opts["merge_output_format"] = "mp4"
    return opts

def run_job(job_id: str, url: str, quality: str) -> None:
    set_job(job_id, status="starting")
    try:
        opts = build_opts(job_id, url, quality)
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)
            title = info.get("title") or "Untitled"
            requested = info.get("requested_downloads") or []
            filename = None
            if requested:
                filename = requested[0].get("filepath") or requested[0].get("_filename")
            if not filename:
                filename = ydl.prepare_filename(info)
            p = Path(filename)
            if p.exists():
                filename = clean_filename(p)
            else:
                filename = p.name
            set_job(job_id, status="completed", progress=100.0, title=title, filename=filename, eta=None)
    except yt_dlp.utils.DownloadCancelled:
        set_job(job_id, status="cancelled", error="Cancelled")
    except Exception as exc:
        set_job(job_id, status="error", error=str(exc)[:1000])

@app.get("/api/health")
def health():
    return jsonify({"ok": True, "service": "ALF Downloader Termux Server", "version": APP_VERSION})

@app.get("/api/info")
def info():
    try:
        url = safe_url(request.args.get("url", ""))
        with yt_dlp.YoutubeDL({"quiet": True, "no_warnings": True, "skip_download": True, "noplaylist": True, "js_runtimes": ["node"]}) as ydl:
            data = ydl.extract_info(url, download=False)
        return jsonify({
            "id": data.get("id", ""),
            "title": data.get("title", ""),
            "uploader": data.get("uploader", ""),
            "duration": data.get("duration"),
            "thumbnail": data.get("thumbnail"),
            "webpageUrl": data.get("webpage_url", url),
        })
    except Exception as exc:
        return jsonify({"error": str(exc)[:1000]}), 400

@app.post("/api/jobs")
def create_job():
    try:
        data = request.get_json(silent=True) or {}
        url = safe_url(str(data.get("url", "")))
        quality = str(data.get("quality", "best"))
        if quality not in QUALITY_FORMATS:
            quality = "best"
        job_id = uuid.uuid4().hex[:12]
        with lock:
            jobs[job_id] = {
                "id": job_id, "url": url, "title": None, "status": "queued",
                "progress": 0.0, "downloadedBytes": 0, "totalBytes": 0,
                "speed": None, "eta": None, "filename": None, "error": None,
                "createdAt": now(),
            }
            cancel_events[job_id] = Event()
        executor.submit(run_job, job_id, url, quality)
        return jsonify({"id": job_id}), 202
    except Exception as exc:
        return jsonify({"error": str(exc)[:1000]}), 400

@app.get("/api/jobs")
def list_jobs():
    with lock:
        return jsonify(list(jobs.values())[::-1])

@app.get("/api/jobs/<job_id>")
def get_job(job_id: str):
    with lock:
        job = jobs.get(job_id)
    if not job:
        return jsonify({"error": "Job tidak ditemukan"}), 404
    return jsonify(job)

@app.post("/api/jobs/<job_id>/cancel")
def cancel(job_id: str):
    event = cancel_events.get(job_id)
    if not event:
        return jsonify({"error": "Job tidak ditemukan"}), 404
    event.set()
    set_job(job_id, status="cancelling")
    return jsonify({"ok": True})

if __name__ == "__main__":
    print(f"ALF Downloader Server {APP_VERSION}")
    print(f"Serving on http://{HOST}:{PORT}")
    print(f"Download directory: {DOWNLOAD_DIR}")
    app.run(host=HOST, port=PORT, threaded=True)
