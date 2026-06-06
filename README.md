# Netflix-Style Video Streaming Backend

When you hit play on Netflix, video starts in under 3 seconds. Millions of people are watching simultaneously, all streaming unique content, with zero buffering. I always wondered how that actually works under the hood.

So I tried to build it.

The problem that got me thinking: suppose you upload a raw 2-hour movie — 50GB file. 10 million people want to watch it, on different devices, different network speeds, some on 4G, some on WiFi, some on slow connections. You can't just serve that 50GB file directly — it would kill the server instantly.

Netflix solves three problems:

**Storage** — You can't store a 50GB raw file and serve it directly to millions. It needs to be compressed, processed, and distributed efficiently.

**Streaming** — You can't make someone download the whole file before watching. Video needs to load in chunks, just ahead of where the viewer is.

**Quality** — A person on 4G shouldn't get the same file as someone on gigabit internet. The video needs to adapt to your network speed in real time.

This project is my attempt at solving all three from scratch.

---

## How it works

```
Upload raw video
      ↓
Store to AWS S3
      ↓
Kafka event triggers encoding
      ↓
FFmpeg encodes to 4 qualities (1080p, 720p, 480p, 360p)
      ↓
Split into 10-second HLS chunks, uploaded back to S3
      ↓
Player picks the right quality based on network speed
      ↓
Presigned URLs serve chunks securely from private S3
```

---

## Services

| Service | Port | What it does |
|---|---|---|
| video-service | 8082 | Accepts upload, stores raw video to S3, fires Kafka event |
| encoding-service | 8083 | Picks up Kafka event, runs FFmpeg, uploads HLS output to S3 |
| streaming-service | 8084 | Issues presigned URLs, proxies playlists, caches in Redis |
| content-service | 8081 | Stores movie metadata |

---

## Tech Stack

- Java 17 + Spring Boot
- Apache Kafka — decouples upload from encoding
- AWS S3 — stores both raw and encoded video
- FFmpeg — does the actual video encoding
- HLS — splits video into chunks, handles adaptive quality
- Redis — caches presigned URLs so we don't hammer AWS
- MySQL — movie metadata
- Docker + Docker Compose

---

## Setup

### Prerequisites
- Java 17+
- Docker Desktop
- FFmpeg ([download](https://ffmpeg.org/download.html))
- AWS account with an S3 bucket

### Steps

```bash
# 1. Clone
git clone https://github.com/krish-02-code/netflix-streaming-backend.git
cd netflix-streaming-backend

# 2. Configure each service
cp src/main/resources/application.yml.example src/main/resources/application.yml
# Fill in AWS keys, FFmpeg path, temp directory

# 3. Start Kafka and Redis
docker-compose up -d

# 4. Run services
cd video-service && mvn spring-boot:run
cd encoding-service && mvn spring-boot:run
cd streaming-service && mvn spring-boot:run
cd content-service && mvn spring-boot:run
```

---

## API

| Method | Endpoint | Description |
|---|---|---|
| POST | /api/v1/videos/upload | Upload raw video |
| GET | /api/v1/stream/{movieId} | Get HLS streaming URL |
| GET | /api/v1/stream/{movieId}/playlist?path=... | Proxied quality playlist |
| GET | /api/v1/content/{movieId} | Get movie metadata |

---

## S3 Structure After Encoding

```
encoded/{movieId}/
├── master.m3u8          ← player downloads this first
├── 1080p/
│   ├── playlist.m3u8
│   ├── segment_000.ts
│   └── segment_001.ts ...
├── 720p/ ...
├── 480p/ ...
└── 360p/ ...
```

---

## Environment Variables

| Variable | Description |
|---|---|
| AWS_ACCESS_KEY | IAM access key |
| AWS_SECRET_KEY | IAM secret key |
| AWS_REGION | S3 bucket region |
| AWS_BUCKET_NAME | S3 bucket name |
| FFMPEG_PATH | Path to FFmpeg binary |
| TEMP_DIR | Temp folder for encoding work |
| DB_USERNAME | MySQL username |
| DB_PASSWORD | MySQL password |

---

## Current Limitations

- No CDN yet — chunks are served directly from S3
- Single encoding worker — no parallel encoding across services
- No authentication on streaming endpoints yet

---
