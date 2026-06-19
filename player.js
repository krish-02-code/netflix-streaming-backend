// Streaming Service (port 8084)
// API: GET /api/v1/stream/{movieId}
// Returns: StreamResponse  →  field: masterPlaylistKey (proxy URL path key)
const STREAMING_SERVICE_BASE_URL = 'http://localhost:8084';



const video      = document.getElementById('videoPlayer');
const statusBar  = document.getElementById('statusBar');
const statusText = document.getElementById('statusText');
const idleOverlay = document.getElementById('idleOverlay');

/** Currently active hls.js instance (destroyed before each new stream) */
let hlsInstance = null;


/**
 * Update the status bar appearance and message.
 * @param {'idle'|'loading'|'success'|'error'} type
 * @param {string} msg
 */
function setStatus(type, msg) {
  statusBar.className = 'status-bar ' + type;
  statusText.textContent = msg;
}

/**
 * Destroy any existing hls.js instance to avoid memory leaks
 * before loading a new stream.
 */
function destroyHls() {
  if (hlsInstance) {
    hlsInstance.destroy();
    hlsInstance = null;
  }
}


/**
 * Load and play an HLS stream.
 * Uses hls.js on Chrome/Firefox,  back fallsto native HLS on Safari.
 * @param {string} m3u8Url - Full URL to the .m3u8 master playlist
 */
function loadAndPlay(m3u8Url) {
  destroyHls();
  idleOverlay.classList.add('hidden');

  if (Hls.isSupported()) {
    // ── hls.js path (Chrome, Firefox, Edge) ──
    hlsInstance = new Hls({
      enableWorker: true,
      lowLatencyMode: false,
    });

    hlsInstance.loadSource(m3u8Url);
    hlsInstance.attachMedia(video);

    hlsInstance.on(Hls.Events.MANIFEST_PARSED, () => {
      setStatus('success', 'HLS manifest loaded — streaming started');
      video.play().catch(() => {}); // Ignore autoplay policy errors
    });

    hlsInstance.on(Hls.Events.ERROR, (event, data) => {
      if (data.fatal) {
        if (data.type === Hls.ErrorTypes.NETWORK_ERROR) {
          setStatus('error', 'Network error — check S3 permissions (AccessDenied?) or CORS settings');
        } else {
          setStatus('error', 'Fatal HLS error: ' + data.details);
        }
        idleOverlay.classList.remove('hidden');
      }
    });

  } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
    // ── Native HLS path (Safari) ──
    video.src = m3u8Url;
    video.addEventListener('loadedmetadata', () => {
      setStatus('success', 'Native HLS loaded (Safari)');
      video.play().catch(() => {});
    });
    video.addEventListener('error', () => {
      setStatus('error', 'Failed to load stream. Check S3 access and CORS.');
      idleOverlay.classList.remove('hidden');
    });

  } else {
    setStatus('error', 'HLS is not supported in this browser.');
  }
}


// ═══════════════════════════════════════════════════════════════
// PLAY VIA STREAMING SERVICE  (recommended for private S3 buckets)
// GET /api/v1/stream/{movieId}  →  generates a fresh AWS presigned URL
// The presigned URL is valid for ~60 min and works with private buckets.
// ═══════════════════════════════════════════════════════════════

async function playByMovieIdPresigned() {
  const movieId = document.getElementById('movieIdInput').value.trim();
  if (!movieId) {
    setStatus('error', 'Please enter a Movie ID');
    return;
  }

  setStatus('loading', 'Fetching stream info from Streaming Service...');

  try {
    const res = await fetch(`${STREAMING_SERVICE_BASE_URL}/api/v1/stream/${movieId}`);

    if (res.status === 404) {
      setStatus('error', 'Movie not found in Streaming Service — video may not be encoded yet.');
      return;
    }
    if (!res.ok) {
      setStatus('error', `Streaming Service returned ${res.status}: ${res.statusText}`);
      return;
    }

    const data = await res.json();
    console.log('[Streaming Service] Response:', data);

    // Use the backend playlist proxy URL — this is the correct approach for private S3.
    //
    // WHY NOT use data.streamingUrl directly?
    // The presigned URL only signs master.m3u8.
    // Inside master.m3u8 are relative paths (e.g. "720p/index.m3u8").
    // hls.js follows them WITHOUT a signature → S3 rejects with 403.
    //
    // The proxy endpoint reads each .m3u8 from S3 (server-side, no auth issue)
    // and rewrites:
    //   - .m3u8 lines  → backend proxy URLs (so we sign sub-playlists too)
    //   - .ts lines    → presigned S3 URLs  (hls.js fetches segments directly)
    const masterKey = data.masterPlaylistKey;

    if (!masterKey) {
      setStatus('error', 'Streaming Service did not return a masterPlaylistKey. Check console.');
      console.warn('[Streaming Service] Full response:', data);
      return;
    }

    // Point hls.js at the backend proxy — NOT the raw S3 URL
    
    const proxyUrl = `${STREAMING_SERVICE_BASE_URL}/api/v1/stream/${movieId}/playlist?path=${encodeURIComponent(masterKey)}`;

    setStatus('loading', 'Initialising HLS player via secure proxy...');
    loadAndPlay(proxyUrl);

  } catch (err) {
    setStatus('error', 'Could not reach Streaming Service: ' + err.message);
  }
}

// ═══════════════════════════════════════════════════════════════
// PLAY DIRECT M3U8 URL
// Paste any publicly accessible .m3u8 URL — no backend call needed.
// ═══════════════════════════════════════════════════════════════

document.getElementById('movieIdInput').addEventListener('keydown', e => {
  if (e.key === 'Enter') playByMovieIdPresigned();
});
