package com.netflix.streamingservice.contoller;

import com.netflix.streamingservice.dto.StreamResponse;
import com.netflix.streamingservice.serivce.StreamingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("api/v1/stream")
@CrossOrigin(origins = "*")
@Slf4j
@RequiredArgsConstructor
public class StreamController {

    private final StreamingService service;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String MASTER_PLAYLIST_KEY_PREFIX = "streaming:playlist:";

    /**
     * Get streaming url for a movie
     * Returns presigned HLS master playlist url
     *
     * GET /api/v1/stream/movieId
     */

    @GetMapping("/{movieId}")
    public ResponseEntity<StreamResponse> getStreaming(@PathVariable String movieId) {
        log.info("Streaming request for a movie : {}", movieId);

        String playlistKey = redisTemplate.opsForValue()
                .get(MASTER_PLAYLIST_KEY_PREFIX + movieId);


        if (playlistKey == null) {
            return ResponseEntity.notFound().build();
        }
        StreamResponse response = service.getStreamingUrl(movieId, playlistKey);

        return new ResponseEntity<>(response, HttpStatus.OK);

    }

    /**
     * Server signed m3u8 playlist content
     * called by hls player for each quality
     * @param movieId
     * @param path
     * @return
     */
    @GetMapping("/{movieId}/playlist")
    public ResponseEntity<String>getSignedPlaylist(@PathVariable String movieId, @RequestParam String path){
        String signedPlaylist = service.getSignedPlaylist(movieId,path);
        return ResponseEntity.ok().header("Content-type","application/x-mpegURL")
                .body(signedPlaylist);
    }


}
