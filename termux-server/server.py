#!/usr/bin/env python3
import argparse, json, os, re, signal, subprocess, threading, time, uuid
from pathlib import Path
from flask import Flask, jsonify, request

VERSION="1.1.0"
DOWNLOAD_DIR=Path("/storage/emulated/0/Download/ALF Downloader")
DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)
app=Flask(__name__)
jobs={}
lock=threading.Lock()

def ts(): return int(time.time())
def valid_url(u): return bool(re.match(r"^https?://", u or "", re.I))
def update(j, **kw):
    with lock:
        if j in jobs: jobs[j].update(kw)

def progress(line):
    m=re.search(r"\[download\]\s+(\d+(?:\.\d+)?)%", line)
    return float(m.group(1)) if m else None

def worker(jid,url,quality):
    update(jid,status="downloading",started_at=ts())
    output=str(DOWNLOAD_DIR/"%(title).160B [%(id)s].%(ext)s")
    if quality=="audio":
        fmt="bestaudio/best"; extra=["-x","--audio-format","mp3","--audio-quality","0"]
    elif quality=="720p":
        fmt="bestvideo[height<=720]+bestaudio/best[height<=720]"; extra=[]
    elif quality=="480p":
        fmt="bestvideo[height<=480]+bestaudio/best[height<=480]"; extra=[]
    else:
        fmt="bestvideo+bestaudio/best"; extra=[]
    cmd=["yt-dlp","--newline","--no-playlist","--restrict-filenames",
         "--merge-output-format","mp4","-f",fmt,"-o",output,*extra,url]
    try:
        p=subprocess.Popen(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,
                           text=True,bufsize=1)
        update(jid,pid=p.pid)
        for line in p.stdout:
            line=line.rstrip()
            pct=progress(line)
            if pct is not None: update(jid,progress=pct)
            if "Destination:" in line or "Merging formats" in line:
                update(jid,message=line)
            with lock: cancel=jobs.get(jid,{}).get("cancel_requested",False)
            if cancel:
                try: p.terminate()
                except Exception: pass
        code=p.wait()
        with lock: cancel=jobs.get(jid,{}).get("cancel_requested",False)
        if cancel: update(jid,status="cancelled",finished_at=ts(),progress=0)
        elif code==0: update(jid,status="completed",finished_at=ts(),progress=100)
        else: update(jid,status="error",finished_at=ts(),error=f"yt-dlp exited with code {code}")
    except FileNotFoundError:
        update(jid,status="error",finished_at=ts(),error="yt-dlp tidak ditemukan. Jalankan install.sh.")
    except Exception as e:
        update(jid,status="error",finished_at=ts(),error=str(e))

@app.get("/api/health")
def health():
    return jsonify(ok=True,service="ALF Downloader Server",version=VERSION,
                   download_directory=str(DOWNLOAD_DIR))

@app.get("/api/info")
def info():
    url=request.args.get("url","").strip()
    if not valid_url(url): return jsonify(ok=False,error="URL tidak valid"),400
    try:
        r=subprocess.run(["yt-dlp","--dump-single-json","--no-playlist","--skip-download",url],
                         capture_output=True,text=True,timeout=45)
        if r.returncode: return jsonify(ok=False,error=r.stderr.strip()[-1000:] or "Gagal mengambil info"),400
        d=json.loads(r.stdout)
        return jsonify(ok=True,id=d.get("id"),title=d.get("title"),uploader=d.get("uploader"),
                       duration=d.get("duration"),thumbnail=d.get("thumbnail"),
                       webpage_url=d.get("webpage_url"),extractor=d.get("extractor_key"),
                       width=d.get("width"),height=d.get("height"))
    except subprocess.TimeoutExpired: return jsonify(ok=False,error="Timeout mengambil info"),504
    except FileNotFoundError: return jsonify(ok=False,error="yt-dlp belum terpasang"),500
    except Exception as e: return jsonify(ok=False,error=str(e)),500

@app.post("/api/download")
def download():
    b=request.get_json(silent=True) or {}
    url=str(b.get("url","")).strip()
    q=str(b.get("quality","best")).lower()
    if not valid_url(url): return jsonify(ok=False,error="URL tidak valid"),400
    if q not in {"best","720p","480p","audio"}: q="best"
    jid=uuid.uuid4().hex[:12]
    job={"id":jid,"url":url,"quality":q,"status":"queued","progress":0,
         "message":"Menunggu proses...","created_at":ts(),"started_at":None,
         "finished_at":None,"pid":None,"error":None,"cancel_requested":False}
    with lock: jobs[jid]=job
    threading.Thread(target=worker,args=(jid,url,q),daemon=True).start()
    return jsonify(ok=True,job=job),202

@app.get("/api/jobs")
def all_jobs():
    with lock: data=list(jobs.values())
    data.sort(key=lambda x:x.get("created_at",0),reverse=True)
    return jsonify(ok=True,jobs=data[:50])

@app.get("/api/jobs/<jid>")
def get_job(jid):
    with lock: job=jobs.get(jid)
    if not job: return jsonify(ok=False,error="Job tidak ditemukan"),404
    return jsonify(ok=True,job=job)

@app.post("/api/jobs/<jid>/cancel")
def cancel(jid):
    with lock:
        job=jobs.get(jid)
        if not job: return jsonify(ok=False,error="Job tidak ditemukan"),404
        job["cancel_requested"]=True
        pid=job.get("pid")
    if pid:
        try: os.kill(pid,signal.SIGTERM)
        except Exception: pass
    return jsonify(ok=True,message="Cancel diminta")

if __name__=="__main__":
    p=argparse.ArgumentParser()
    p.add_argument("--host",default="127.0.0.1"); p.add_argument("--port",type=int,default=8080)
    a=p.parse_args()
    app.run(host=a.host,port=a.port,debug=False,threaded=True)
