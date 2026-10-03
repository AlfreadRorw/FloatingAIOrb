#!/data/data/com.termux/files/usr/bin/python
"""ALF Downloader local server (Termux). Flask + yt-dlp."""
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import threading
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from threading import Event, Lock, Thread

from flask import Flask, jsonify, request
import yt_dlp

APP_VERSION = "1.1.0"
HOST = "127.0.0.1"
PORT = int(os.environ.get("ALF_PORT", "8080"))

DEFAULT_DIR = Path("/storage/emulated/0/Download/ALF Downloader")
DOWNLOAD_DIR = Path(os.environ.get("ALF_DOWNLOAD_DIR", str(DEFAULT_DIR)))
try:
    DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)
except Exception:
    DOWNLOAD_DIR = Path.home() / "storage" / "downloads" / "ALF Downloader"
    DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)

STATE_DIR = Path.home() / ".alf-downloader"
STATE_DIR.mkdir(parents=True, exist_ok=True)
HISTORY_FILE = STATE_DIR / "history.json"

app = Flask(__name__)
lock = Lock()
jobs: dict[str, dict] = {}
order: list[str] = []
cancel_events: dict[str, Event] = {}
config = {"maxConcurrent": 2}

ACTIVE = {"starting", "downloading", "processing", "cancelling"}
FINAL = {"completed", "error", "cancelled"}

QUALITY_FORMATS = {
    "best": "bv*+ba/b",
    "2160": "bv*[height<=2160]+ba/b[height<=2160]",
    "1080": "bv*[height<=1080]+ba/b[height<=1080]",
    "720": "bv*[height<=720]+ba/b[height<=720]",
    "480": "bv*[height<=480]+ba/b[height<=480]",
    "360": "bv*[height<=360]+ba/b[height<=360]",
    "audio": "ba/b",
}
AUDIO_FORMATS = {"mp3", "m4a", "opus"}


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def safe_url(value: str) -> str:
    value = (value or "").strip()
    if not re.match(r"^https?://", value, re.I):
        raise ValueError("URL harus dimulai dengan http:// atau https://")
    if len(value) > 4096:
        raise ValueError("URL terlalu panjang")
    return value


def public(job: dict) -> dict:
    return {k: v for k, v in job.items() if k != "spec"}


def save_history() -> None:
    with lock:
        done = [public(jobs[i]) | {"spec": jobs[i].get("spec")} for i in order if jobs[i]["status"] in FINAL]
    try:
        tmp = HISTORY_FILE.with_suffix(".tmp")
        tmp.write_text(json.dumps(done[-200:]), encoding="utf-8")
        tmp.replace(HISTORY_FILE)
    except Exception as exc:  # pragma: no cover
        print("history save failed:", exc)


def load_history() -> None:
    try:
        data = json.loads(HISTORY_FILE.read_text(encoding="utf-8"))
    except Exception:
        return
    for job in data:
        if not isinstance(job, dict) or "id" not in job:
            continue
        jobs[job["id"]] = job
        order.append(job["id"])


def set_job(job_id: str, **updates) -> None:
    with lock:
        if job_id in jobs:
            jobs[job_id].update(updates)


def media_scan(path: str) -> None:
    """Make the file visible in Gallery / Music apps."""
    try:
        if shutil.which("termux-media-scan"):
            subprocess.run(["termux-media-scan", path], timeout=10, capture_output=True)
        else:
            subprocess.run(
                ["am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE", "-d", "file://" + path],
                timeout=10, capture_output=True,
            )
    except Exception:
        pass


def build_opts(job_id: str, spec: dict, files: list[str]) -> dict:
    quality = spec.get("quality", "best")
    playlist = bool(spec.get("playlist"))
    if playlist:
        tmpl = "%(playlist_title).80s/%(playlist_index)03d - %(title).150s [%(id)s].%(ext)s"
    else:
        tmpl = "%(title).180s [%(id)s].%(ext)s"

    def hook(d: dict) -> None:
        ev = cancel_events.get(job_id)
        if ev is not None and ev.is_set():
            raise yt_dlp.utils.DownloadCancelled()
        status = d.get("status")
        info = d.get("info_dict") or {}
        extra = {}
        if info.get("title"):
            extra["title"] = info.get("title")
        if info.get("thumbnail"):
            extra["thumbnail"] = info.get("thumbnail")
        if info.get("playlist_index"):
            extra["playlistIndex"] = int(info.get("playlist_index") or 0)
            extra["playlistCount"] = int(info.get("n_entries") or info.get("playlist_count") or 0)
        if status == "downloading":
            total = d.get("total_bytes") or d.get("total_bytes_estimate") or 0
            done = d.get("downloaded_bytes") or 0
            pct = (done / total * 100) if total else 0
            eta = d.get("eta")
            set_job(
                job_id, status="downloading", progress=max(0.0, min(99.0, pct)),
                downloadedBytes=int(done), totalBytes=int(total),
                speedBps=float(d.get("speed") or 0.0),
                etaSeconds=int(eta) if isinstance(eta, (int, float)) else None,
                **extra,
            )
        elif status == "finished":
            set_job(job_id, status="processing", progress=99.0, speedBps=0.0, etaSeconds=None, **extra)

    def pp_hook(d: dict) -> None:
        if d.get("status") == "started":
            set_job(job_id, status="processing")

    def post_hook(filename: str) -> None:
        files.append(filename)
        media_scan(filename)

    opts = {
        "format": QUALITY_FORMATS.get(quality, QUALITY_FORMATS["best"]),
        "outtmpl": str(DOWNLOAD_DIR / tmpl),
        "paths": {"home": str(DOWNLOAD_DIR)},
        "noplaylist": not playlist,
        "quiet": True,
        "no_warnings": True,
        "noprogress": True,
        "windowsfilenames": True,
        "progress_hooks": [hook],
        "postprocessor_hooks": [pp_hook],
        "post_hooks": [post_hook],
        "continuedl": True,
        "retries": 5,
        "fragment_retries": 5,
        "concurrent_fragment_downloads": 4,
        "socket_timeout": 20,
        "ignoreerrors": "only_download" if playlist else False,
        "js_runtimes": {"node": {}},
    }
    pps: list[dict] = []
    if spec.get("speedLimitKb", 0) > 0:
        opts["ratelimit"] = int(spec["speedLimitKb"]) * 1024

    if quality == "audio":
        codec = spec.get("audioFormat", "mp3")
        if codec not in AUDIO_FORMATS:
            codec = "mp3"
        pps.append({"key": "FFmpegExtractAudio", "preferredcodec": codec, "preferredquality": "192"})
    else:
        opts["merge_output_format"] = "mp4"

    if spec.get("embedMetadata", True):
        pps.append({"key": "FFmpegMetadata", "add_metadata": True})
    if spec.get("embedThumbnail", True):
        opts["writethumbnail"] = True
        pps.insert(0, {"key": "FFmpegThumbnailsConvertor", "format": "jpg", "when": "before_dl"})
        pps.append({"key": "EmbedThumbnail", "already_have_thumbnail": False})
    if spec.get("subtitles") and quality != "audio":
        opts["writesubtitles"] = True
        opts["writeautomaticsub"] = False
        opts["subtitleslangs"] = ["id.*", "en.*"]
        opts["subtitlesformat"] = "srt/best"
        pps.append({"key": "FFmpegSubtitlesConvertor", "format": "srt"})
    if pps:
        opts["postprocessors"] = pps
    return opts


def run_job(job_id: str) -> None:
    files: list[str] = []
    try:
        with lock:
            spec = dict(jobs[job_id].get("spec") or {})
            url = jobs[job_id]["url"]
        opts = build_opts(job_id, spec, files)
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)
        info = info or {}
        title = info.get("title") or "Untitled"
        existing = [Path(f) for f in files if Path(f).exists()]
        if not existing:
            req = info.get("requested_downloads") or []
            for r in req:
                p = Path(r.get("filepath") or r.get("_filename") or "")
                if p.exists():
                    existing.append(p)
        size = sum(p.stat().st_size for p in existing)
        if len(existing) > 1:
            filename = f"{len(existing)} file"
        elif existing:
            try:
                filename = str(existing[0].relative_to(DOWNLOAD_DIR))
            except ValueError:
                filename = existing[0].name
        else:
            filename = title
        set_job(
            job_id, status="completed", progress=100.0, title=title, filename=filename,
            sizeBytes=int(size), speedBps=0.0, etaSeconds=None, error=None, finishedAt=now(),
            thumbnail=info.get("thumbnail") or jobs[job_id].get("thumbnail"),
        )
    except yt_dlp.utils.DownloadCancelled:
        set_job(job_id, status="cancelled", error="Dibatalkan", speedBps=0.0, etaSeconds=None, finishedAt=now())
    except Exception as exc:
        msg = re.sub(r"\x1b\[[0-9;]*m", "", str(exc))
        set_job(job_id, status="error", error=msg[:600], speedBps=0.0, etaSeconds=None, finishedAt=now())
    finally:
        save_history()
        schedule()


def schedule() -> None:
    to_start = []
    with lock:
        running = sum(1 for j in jobs.values() if j["status"] in ACTIVE)
        for jid in order:
            if running >= config["maxConcurrent"]:
                break
            job = jobs.get(jid)
            if job and job["status"] == "queued":
                job["status"] = "starting"
                running += 1
                to_start.append(jid)
    for jid in to_start:
        Thread(target=run_job, args=(jid,), daemon=True).start()


def new_job(url: str, spec: dict) -> str:
    job_id = uuid.uuid4().hex[:12]
    with lock:
        jobs[job_id] = {
            "id": job_id, "url": url, "title": None, "thumbnail": None, "status": "queued",
            "progress": 0.0, "downloadedBytes": 0, "totalBytes": 0, "speedBps": 0.0,
            "etaSeconds": None, "filename": None, "error": None, "createdAt": now(),
            "finishedAt": None, "quality": spec["quality"], "audioFormat": spec["audioFormat"],
            "sizeBytes": 0, "playlistIndex": 0, "playlistCount": 0, "spec": spec,
        }
        order.append(job_id)
        cancel_events[job_id] = Event()
    schedule()
    return job_id


def parse_spec(data: dict) -> tuple[str, dict]:
    url = safe_url(str(data.get("url", "")))
    quality = str(data.get("quality", "best"))
    if quality not in QUALITY_FORMATS:
        quality = "best"
    audio = str(data.get("audioFormat", "mp3"))
    if audio not in AUDIO_FORMATS:
        audio = "mp3"
    spec = {
        "quality": quality,
        "audioFormat": audio,
        "subtitles": bool(data.get("subtitles", False)),
        "embedThumbnail": bool(data.get("embedThumbnail", True)),
        "embedMetadata": bool(data.get("embedMetadata", True)),
        "playlist": bool(data.get("playlist", False)),
        "speedLimitKb": max(0, int(data.get("speedLimitKb", 0) or 0)),
    }
    return url, spec


@app.get("/api/health")
def health():
    try:
        free = shutil.disk_usage(DOWNLOAD_DIR).free
    except Exception:
        free = 0
    return jsonify({
        "ok": True, "service": "ALF Downloader Termux Server", "version": APP_VERSION,
        "ytdlp": getattr(yt_dlp.version, "__version__", None),
        "freeBytes": int(free), "downloadDir": str(DOWNLOAD_DIR),
        "maxConcurrent": config["maxConcurrent"],
    })


@app.get("/api/info")
def info():
    try:
        url = safe_url(request.args.get("url", ""))
        playlist = request.args.get("playlist") == "1"
        opts = {"quiet": True, "no_warnings": True, "skip_download": True,
                "noplaylist": not playlist, "js_runtimes": {"node": {}}}
        if playlist:
            opts["extract_flat"] = "in_playlist"
        with yt_dlp.YoutubeDL(opts) as ydl:
            data = ydl.extract_info(url, download=False) or {}
        is_pl = data.get("_type") == "playlist"
        entries = list(data.get("entries") or []) if is_pl else []
        heights = sorted({int(f["height"]) for f in (data.get("formats") or []) if f.get("height")}, reverse=True)
        thumb = data.get("thumbnail")
        if not thumb and entries:
            thumb = (entries[0] or {}).get("thumbnail")
        return jsonify({
            "id": data.get("id", ""), "title": data.get("title", ""),
            "uploader": data.get("uploader") or data.get("channel") or "",
            "duration": data.get("duration"), "thumbnail": thumb,
            "webpageUrl": data.get("webpage_url", url),
            "extractor": data.get("extractor_key") or data.get("extractor"),
            "viewCount": data.get("view_count"), "uploadDate": data.get("upload_date"),
            "heights": heights, "isPlaylist": is_pl, "entryCount": len(entries),
        })
    except Exception as exc:
        msg = re.sub(r"\x1b\[[0-9;]*m", "", str(exc))
        return jsonify({"error": msg[:600]}), 400


@app.post("/api/jobs")
def create_job():
    try:
        url, spec = parse_spec(request.get_json(silent=True) or {})
        return jsonify({"id": new_job(url, spec)}), 202
    except Exception as exc:
        return jsonify({"error": str(exc)[:600]}), 400


@app.get("/api/jobs")
def list_jobs():
    with lock:
        return jsonify([public(jobs[i]) for i in reversed(order) if i in jobs])


@app.get("/api/jobs/<job_id>")
def get_job(job_id: str):
    with lock:
        job = jobs.get(job_id)
        data = public(job) if job else None
    if not data:
        return jsonify({"error": "Job tidak ditemukan"}), 404
    return jsonify(data)


@app.post("/api/jobs/<job_id>/cancel")
def cancel(job_id: str):
    with lock:
        job = jobs.get(job_id)
        if not job:
            return jsonify({"error": "Job tidak ditemukan"}), 404
        if job["status"] == "queued":
            job.update(status="cancelled", error="Dibatalkan", finishedAt=now())
            queued_cancel = True
        else:
            queued_cancel = False
    if queued_cancel:
        save_history()
        return jsonify({"ok": True})
    ev = cancel_events.get(job_id)
    if ev:
        ev.set()
    set_job(job_id, status="cancelling")
    return jsonify({"ok": True})


@app.post("/api/jobs/<job_id>/retry")
def retry(job_id: str):
    with lock:
        job = jobs.get(job_id)
        if not job:
            return jsonify({"error": "Job tidak ditemukan"}), 404
        if job["status"] in ACTIVE or job["status"] == "queued":
            return jsonify({"error": "Job masih berjalan"}), 409
        job.update(
            status="queued", progress=0.0, downloadedBytes=0, totalBytes=0, speedBps=0.0,
            etaSeconds=None, error=None, finishedAt=None, createdAt=now(),
        )
        cancel_events[job_id] = Event()
        if job_id in order:
            order.remove(job_id)
        order.append(job_id)
    schedule()
    return jsonify({"id": job_id}), 202


@app.delete("/api/jobs/<job_id>")
def delete_job(job_id: str):
    with lock:
        job = jobs.get(job_id)
        if not job:
            return jsonify({"ok": True})
        if job["status"] in ACTIVE:
            return jsonify({"error": "Batalkan job dulu"}), 409
        jobs.pop(job_id, None)
        cancel_events.pop(job_id, None)
        if job_id in order:
            order.remove(job_id)
    save_history()
    return jsonify({"ok": True})


@app.post("/api/jobs/clear")
def clear_finished():
    with lock:
        for jid in [i for i in order if jobs[i]["status"] in FINAL]:
            jobs.pop(jid, None)
            cancel_events.pop(jid, None)
            order.remove(jid)
    save_history()
    return jsonify({"ok": True})


@app.route("/api/config", methods=["GET", "POST"])
def api_config():
    if request.method == "POST":
        data = request.get_json(silent=True) or {}
        try:
            config["maxConcurrent"] = max(1, min(6, int(data.get("maxConcurrent", config["maxConcurrent"]))))
        except (TypeError, ValueError):
            return jsonify({"error": "maxConcurrent tidak valid"}), 400
        schedule()
    return jsonify(config)


@app.post("/api/shutdown")
def shutdown():
    threading.Timer(0.4, lambda: os._exit(0)).start()
    return jsonify({"ok": True})


def main() -> None:
    load_history()
    for job in jobs.values():
        if job.get("status") in ACTIVE or job.get("status") == "queued":
            job.update(status="error", error="Server dimulai ulang", finishedAt=now())
    print(f"ALF Downloader Server {APP_VERSION}")
    print(f"Serving on http://{HOST}:{PORT}")
    print(f"Download directory: {DOWNLOAD_DIR}")
    try:
        from waitress import serve
        serve(app, host=HOST, port=PORT, threads=8, ident="alf")
    except ImportError:
        app.run(host=HOST, port=PORT, threaded=True)


if __name__ == "__main__":
    main()
