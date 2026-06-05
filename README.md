# 🎬 Netflix-Style Video Streaming Platform

A production-grade microservices backend for video streaming, inspired by Netflix architecture. Built with Java Spring Boot, Apache Kafka, AWS S3, FFmpeg, and Redis.

---

## Architecture Overview

```
User Uploads Video
        ↓
  [video-service]  →  Upload to AWS S3  →  Publish Kafka Event
        ↓
[encoding-service] →  Download from S3  →  FFmpeg HLS Encoding  →  Upload back to S3
        ↓
[streaming-service] →  Redis Cache  →  Presigned URLs  →  Proxy Playlists
        ↓
  Frontend Player (hls.js) streams video
```

---

## Services

| Service | Port | Responsibility |
|---|---|---|
| `video-service` | 8082 | Accepts video uploads, stores raw video to AWS S3, publishes Kafka event |
| `encoding-service` | 8083 | Consumes Kafka event, encodes video to HLS via FFmpeg (4 qualities), uploads back to S3 |
| `streaming-service` | 8084 | Serves presigned HLS master playlist URLs, proxies quality playlists, Redis caching |
| `content-service` | 8081 | Manages movie metadata (title, description, genres) |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.x |
| Messaging | Apache Kafka |
| Storage | AWS S3 |
| Video Processing | FFmpeg + HLS (adaptive bitrate) |
| Caching | Redis |
| Database | MySQL |
| Containerization | Docker + Docker Compose |

---

## Key Features

- **Adaptive Bitrate Streaming** — Videos encoded to 4 qualities: 1080p, 720p, 480p, 360p
- **HLS Protocol** — Video split into 10-second `.ts` segments with `.m3u8` playlists
- **Presigned URLs** — Time-limited secure access to private S3 content (no public bucket)
- **Playlist Proxy** — Backend rewrites `.m3u8` URLs so all requests go through the service
- **Redis Caching** — Presigned URLs cached for 55 minutes to avoid repeated AWS calls
- **Event-Driven** — Kafka decouples upload from encoding (async processing)

---

## Project Structure

```
Netflix/
├── video-service/           # Upload service
├── encoding-service/        # FFmpeg encoding service  
├── streaming-service/       # HLS streaming + presigned URLs
├── content-service/         # Movie metadata service
├── docker-compose.yml       # Kafka + Redis + Zookeeper
├── player.html              # Test HLS player (hls.js)
└── README.md
```

---

## Setup & Running

### Prerequisites

- Java 17+
- Maven
- Docker Desktop
- FFmpeg installed ([download here](https://ffmpeg.org/download.html))
- AWS account with S3 bucket

### Step 1 — Clone the Repo

```bash
git clone https://github.com/krish-02-code/netflix-streaming-backend.git
cd netflix-streaming-backend
```

### Step 2 — Configure Each Service

Copy the example config and fill in your values for **each service**:

```bash
cp src/main/resources/application.yml.example src/main/resources/application.yml
```

Fill in:
```yaml
aws:
  access-key: YOUR_AWS_ACCESS_KEY
  secret-key: YOUR_AWS_SECRET_KEY
  region: your-region
  s3:
    bucket-name: your-bucket-name

ffmpeg:
  path: C:/ffmpeg/bin/ffmpeg.exe   # or /usr/bin/ffmpeg on Linux

encoding:
  base-path: C:/tmp/encoding        # temp folder for encoding
```

### Step 3 — Start Kafka and Redis

```bash
docker-compose up -d
```

### Step 4 — Run All Services

Start each service from IntelliJ or via Maven:

```bash
cd video-service && mvn spring-boot:run
cd encoding-service && mvn spring-boot:run
cd streaming-service && mvn spring-boot:run
cd content-service && mvn spring-boot:run
```

---

## API Endpoints

### Video Service
| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/v1/videos/upload` | Upload a raw video file |

### Streaming Service
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/stream/{movieId}` | Get presigned HLS master playlist URL |
| `GET` | `/api/v1/stream/{movieId}/playlist?path=...` | Get signed quality playlist (proxied) |

### Content Service
| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/content/{movieId}` | Get movie metadata |
| `POST` | `/api/v1/content` | Create movie entry |

---

## How Streaming Works

1. Frontend calls `GET /api/v1/stream/{movieId}`
2. Service returns a **presigned URL** for `master.m3u8` (valid 60 min, cached in Redis)
3. `hls.js` fetches `master.m3u8` — contains links to quality playlists
4. Quality playlist links point back to **our proxy** (`/playlist?path=...`)
5. Proxy reads the quality `.m3u8` from S3 and rewrites `.ts` segment URLs as presigned S3 links
6. `hls.js` fetches `.ts` segments **directly from S3** using presigned URLs

This ensures the S3 bucket stays **fully private** — no public access ever.

---

## HLS Output Structure (S3)

```
encoded/{movieId}/
├── master.m3u8
├── 1080p/
│   ├── playlist.m3u8
│   ├── segment_000.ts
│   └── segment_001.ts ...
├── 720p/
│   └── ...
├── 480p/
│   └── ...
└── 360p/
    └── ...
```

---

## Environment Variables

| Variable | Used In | Description |
|---|---|---|
| `AWS_ACCESS_KEY` | all services | AWS IAM access key |
| `AWS_SECRET_KEY` | all services | AWS IAM secret key |
| `AWS_REGION` | all services | S3 bucket region |
| `AWS_BUCKET_NAME` | all services | S3 bucket name |
| `FFMPEG_PATH` | encoding-service | Path to FFmpeg binary |
| `TEMP_DIR` | encoding-service | Temp folder for encoding |
| `DB_USERNAME` | content-service | MySQL username |
| `DB_PASSWORD` | content-service | MySQL password |

---

## Author

**Krish** — B.Tech Computer Engineering, PCCOE Pune  
[GitHub](https://github.com/krish-02-code) • [LeetCode](https://leetcode.com/u/Krish1231)
