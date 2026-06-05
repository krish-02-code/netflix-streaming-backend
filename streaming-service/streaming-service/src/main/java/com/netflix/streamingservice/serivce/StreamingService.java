package com.netflix.streamingservice.serivce;

import com.netflix.streamingservice.dto.StreamResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-expiry}")
    private long presignedUrlExpiry; // in minutes

    // Self URL — used to rewrite .m3u8 references back to this backend proxy
    // so hls.js always goes through us (never raw S3) for playlist files.
    @Value("${streaming.service.base-url:http://localhost:8084}")
    private String streamingServiceBaseUrl;

    private static final String STREAMING_URL_CACHE_PREFIX = "streaming:url:";

    // PRIMARY ENTRY POINT
    // Returns a StreamResponse containing:
    //   - streamingUrl      : presigned URL for the master playlist (direct S3)
    //   - masterPlaylistKey : S3 key, used by the frontend to build the proxy URL

    /**
     * FLOW:
     * 1. Check Redis cache for an existing presigned URL
     * 2. If cached → return immediately
     * 3. If not cached → generate a fresh presigned URL from S3
     * 4. Cache the URL in Redis (55 min to avoid edge-cases near expiry)
     * 5. Return StreamResponse
     */
    public StreamResponse getStreamingUrl(String movieId, String playlistKey) {
        log.info("Getting streaming URL for movie: {}", movieId);

        String cacheKey = STREAMING_URL_CACHE_PREFIX + movieId;

        // Check Redis cache first
        String cachedUrl = redisTemplate.opsForValue().get(cacheKey);
        if (cachedUrl != null) {
            log.info("Cache hit — returning cached URL for movie: {}", movieId);
            return new StreamResponse(movieId, cachedUrl, playlistKey, "1080p, 720p, 480p, 360p", presignedUrlExpiry);
        }

        // Generate fresh presigned URL
        log.info("Cache miss — generating new presigned URL for movie: {}", movieId);
        String presignedUrl = generatePresignedUrl(playlistKey);

        // Cache for 55 minutes (5 min buffer before actual expiry)
        redisTemplate.opsForValue().set(cacheKey, presignedUrl, 55, TimeUnit.MINUTES);
        log.info("Presigned URL cached for movie: {}", movieId);

        return new StreamResponse(movieId, presignedUrl, playlistKey, "1080p, 720p, 480p, 360p", presignedUrlExpiry);
    }

    // PLAYLIST PROXY  (the key method for secure HLS streaming)

    /**
     * @param movieId the movie being streamed (used to build proxy URLs)
     * @param path    the S3 key of the .m3u8 file to read and rewrite
     */
    public String getSignedPlaylist(String movieId, String path) {
        // Derive the directory prefix of this playlist file
        // e.g. "encoded/abc/720p/index.m3u8" → "encoded/abc/720p/"
        String basePath = path.substring(0, path.lastIndexOf('/') + 1);

        log.info("Proxying playlist for movie: {}, path: {}", movieId, path);

        // Read the raw .m3u8 content from S3
        String m3u8Content = readFromS3(path);

        // Rewrite all URLs inside the playlist
        return rewriteM3u8Urls(m3u8Content, basePath, movieId);
    }

    // PRIVATE HELPERS

    /**
     * Rewrites every non-comment, non-empty line in the m3u8 content:
     *   - .m3u8 reference  → backend proxy URL  (keeps traffic through our service)
     *   - anything else    → presigned S3 URL   (direct, time-limited S3 access)
     */
    private String rewriteM3u8Urls(String m3u8Content, String basePath, String movieId) {
        StringBuilder rewritten = new StringBuilder();

        for (String line : m3u8Content.split("\n")) {
            String trimmed = line.trim();

            // Pass comments and blank lines through unchanged
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                rewritten.append(line).append("\n");
                continue;
            }

            // Build the full S3 key for this reference
            String fullKey = basePath + trimmed;

            if (trimmed.endsWith(".m3u8")) {
                // Variant playlist -> route through our proxy so we can sign
                // the segment URLs inside it on the next request
                String proxyUrl = streamingServiceBaseUrl
                        + "/api/v1/stream/" + movieId
                        + "/playlist?path=" + fullKey;
                rewritten.append(proxyUrl).append("\n");
            } else {
                // Media segment (.ts, .aac, .mp4, etc.) → presign directly
                String signedUrl = generatePresignedUrl(fullKey);
                rewritten.append(signedUrl).append("\n");
            }
        }

        return rewritten.toString();
    }

    /**
     * Generate an AWS presigned GET URL for the given S3 key.
     * The URL expires after the configured duration.
     */
    private String generatePresignedUrl(String s3Key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiry))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    /**
     * Invalidate the cached presigned URL for a movie.
     * Call this when a video is re-encoded or updated.
     */
    public void invalidateCache(String movieId) {
        String cacheKey = STREAMING_URL_CACHE_PREFIX + movieId;
        redisTemplate.delete(cacheKey);
        log.info("Streaming URL cache invalidated for movie: {}", movieId);
    }

    /**
     * Read the full text content of an S3 object.
     */
    private String readFromS3(String s3Key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);
        return new BufferedReader(new InputStreamReader(response))
                .lines()
                .collect(Collectors.joining("\n"));
    }
}
