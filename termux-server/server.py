import json
import os
import re
import shutil
import subprocess
import threading
import time
import uuid
from pathlib import Path

from flask import Flask, jsonify, request

HOST = "127.0.0.1"
PORT = 8080
VERSION = "1.0.0"

DOWNLOAD_DIR = Path("/storage/emulated/0/Download/ALF Downloader")
DATA_DIR = Path.home() / ".alf-downloader"
HISTORY_FILE = DATA_DIR / "history.json"

app = Flask(__name__)
jobs = {}
jobs_lock = threading.Lock()


def ensure_dirs():
    DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    if not HISTORY_FILE.exists():
        HISTORY_FILE.write_text("[]", encoding="utf-8")


def command_exists(name):
    return shutil.which(name) is not None


def load_history():
    ensure_dirs()
    try:
        return json.loads(HISTORY_FILE.read_text(encoding="utf-8"))
    except Exception:
        return []


def save_history(items):
    ensure_dirs()
    HISTORY_FILE.write_text(
        json.dumps(items[:200], ensure_ascii=False, indent=2),
        encoding="utf-8"
    )


def add_history(item):
    history = load_history()
    history.insert(0, item)
    save_history(history)


def clean_title(value):
    value = value or "download"
    value = re.sub(r'[\\/:*?"<>|]+', "_", value)
    value = re.sub(r"\s+", " ", value).strip()
    return value[:180] or "download"


def get_ytdlp_command():
    if command_exists("yt-dlp"):
        return ["yt-dlp"]
    return ["python", "-m", "yt_dlp"]


def validate_url(url):
    if not isinstance(url, str):
        return False
    url = url.strip()
    return url.startswith("http://") or url.startswith("https://")


def run_json_command(args, timeout=120):
    result = subprocess.run(
        args,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        timeout=timeout,
    )
    if result.returncode != 0:
        raise RuntimeError(result.stderr.strip()[-2500:] or "yt-dlp failed")
    return json.loads(result.stdout)


def quality_format(quality):
    if quality == "audio":
        return {
            "format": "bestaudio/best",
            "post": ["-x", "--audio-format", "mp3", "--audio-quality", "0"],
        }

    if quality == "best":
        return {
            "format": "bv*+ba/b",
            "post": [],
        }

    if quality in {"1080p", "720p", "480p"}:
        height = quality[:-1]
        return {
            "format": (
                f"bv*[height<={height}]+ba/"
                f"b[height<={height}]/"
                f"b"
            ),
            "post": [],
        }

    raise ValueError("Unsupported quality")


def percent_from_line(line):
    match = re.search(r"(\d+(?:\.\d+)?)%", line)
    if match:
        try:
            return float(match.group(1))
        except ValueError:
            return None
    return None


def update_job(job_id, **values):
    with jobs_lock:
        if job_id in jobs:
            jobs[job_id].update(values)


def download_worker(job_id, url, quality):
    started = time.time()

    try:
        spec = quality_format(quality)

        update_job(
            job_id,
            status="downloading",
            progress=0.0,
            message="Starting yt-dlp...",
        )

        output_template = str(DOWNLOAD_DIR / "%(title)s.%(ext)s")

        cmd = (
            get_ytdlp_command()
            + [
                "--newline",
                "--no-playlist",
                "--restrict-filenames",
                "--progress",
                "--progress-template",
                "%(progress._percent_str)s|%(progress.eta)s|%(progress.speed)s|%(filename)s",
                "-f",
                spec["format"],
                "-o",
                output_template,
                url,
            ]
            + spec["post"]
        )

        process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )

        output_lines = []
        last_filename = None

        for raw in process.stdout:
            line = raw.strip()
            if not line:
                continue

            output_lines.append(line)
            if len(output_lines) > 80:
                output_lines.pop(0)

            parts = line.split("|", 3)
            if len(parts) == 4:
                pct = percent_from_line(parts[0])
                if pct is not None:
                    update_job(
                        job_id,
                        progress=max(0.0, min(100.0, pct)),
                        eta=parts[1],
                        speed=parts[2],
                        filename=parts[3],
                        message="Downloading...",
                    )
                    last_filename = parts[3]
            elif "has already been downloaded" in line:
                update_job(job_id, progress=100.0, message="Already downloaded")

        return_code = process.wait()

        if return_code != 0:
            raise RuntimeError("\n".join(output_lines[-15:])[-4000:])

        final_file = None
        if last_filename:
            candidate = DOWNLOAD_DIR / last_filename
            if candidate.exists():
                final_file = candidate

        if final_file is None:
            files = [
                p for p in DOWNLOAD_DIR.iterdir()
                if p.is_file()
            ]
            if files:
                final_file = max(files, key=lambda p: p.stat().st_mtime)

        elapsed = round(time.time() - started, 2)

        item = {
            "id": job_id,
            "url": url,
            "quality": quality,
            "status": "completed",
            "filename": final_file.name if final_file else "downloaded file",
            "path": str(final_file) if final_file else str(DOWNLOAD_DIR),
            "completed_at": int(time.time()),
            "duration_seconds": elapsed,
        }

        update_job(
            job_id,
            status="completed",
            progress=100.0,
            message="Download complete",
            filename=item["filename"],
        )
        add_history(item)

    except Exception as exc:
        message = str(exc) or "Unknown download error"
        update_job(
            job_id,
            status="error",
            progress=0.0,
            message=message[-4000:],
        )
        add_history({
            "id": job_id,
            "url": url,
            "quality": quality,
            "status": "error",
            "error": message[-4000:],
            "completed_at": int(time.time()),
        })


@app.get("/api/health")
def health():
    return jsonify({
        "ok": True,
        "service": "ALF Downloader",
        "version": VERSION,
        "host": HOST,
        "port": PORT,
        "download_dir": str(DOWNLOAD_DIR),
        "yt_dlp": command_exists("yt-dlp") or command_exists("python"),
        "ffmpeg": command_exists("ffmpeg"),
    })


@app.post("/api/info")
def info():
    data = request.get_json(silent=True) or {}
    url = data.get("url", "").strip()

    if not validate_url(url):
        return jsonify({"ok": False, "error": "Invalid URL"}), 400

    try:
        cmd = get_ytdlp_command() + [
            "--dump-single-json",
            "--skip-download",
            "--no-playlist",
            "--no-warnings",
            url,
        ]
        meta = run_json_command(cmd, timeout=120)

        formats = []
        seen = set()

        for fmt in meta.get("formats", []):
            height = fmt.get("height")
            ext = fmt.get("ext")
            if not height or not ext:
                continue
            if height not in seen:
                seen.add(height)
                formats.append({
                    "height": height,
                    "ext": ext,
                })

        formats.sort(key=lambda x: x["height"], reverse=True)

        return jsonify({
            "ok": True,
            "title": meta.get("title") or "Untitled",
            "duration": meta.get("duration"),
            "uploader": meta.get("uploader"),
            "thumbnail": meta.get("thumbnail"),
            "webpage_url": meta.get("webpage_url") or url,
            "formats": formats[:30],
        })

    except subprocess.TimeoutExpired:
        return jsonify({"ok": False, "error": "Info lookup timed out"}), 504
    except Exception as exc:
        return jsonify({"ok": False, "error": str(exc)[-4000:]}), 500


@app.post("/api/download")
def download():
    data = request.get_json(silent=True) or {}
    url = data.get("url", "").strip()
    quality = data.get("quality", "best")

    if not validate_url(url):
        return jsonify({"ok": False, "error": "Invalid URL"}), 400

    try:
        quality_format(quality)
    except ValueError:
        return jsonify({"ok": False, "error": "Unsupported quality"}), 400

    ensure_dirs()

    job_id = uuid.uuid4().hex[:12]

    with jobs_lock:
        jobs[job_id] = {
            "id": job_id,
            "url": url,
            "quality": quality,
            "status": "queued",
            "progress": 0.0,
            "message": "Queued",
            "created_at": int(time.time()),
        }

    thread = threading.Thread(
        target=download_worker,
        args=(job_id, url, quality),
        daemon=True,
    )
    thread.start()

    return jsonify({
        "ok": True,
        "job_id": job_id,
        "status": "queued",
    }), 202


@app.get("/api/jobs/<job_id>")
def job_status(job_id):
    with jobs_lock:
        job = jobs.get(job_id)

    if not job:
        return jsonify({
            "ok": False,
            "error": "Job not found",
        }), 404

    return jsonify({
        "ok": True,
        "job": job,
    })


@app.get("/api/history")
def history():
    return jsonify({
        "ok": True,
        "items": load_history(),
    })


@app.post("/api/history/clear")
def clear_history():
    save_history([])
    return jsonify({"ok": True})


if __name__ == "__main__":
    ensure_dirs()

    print(f"ALF Downloader Server {VERSION}")
    print(f"Serving on http://{HOST}:{PORT}")
    print(f"Download directory: {DOWNLOAD_DIR}")

    app.run(
        host=HOST,
        port=PORT,
        debug=False,
        threaded=True,
    )
