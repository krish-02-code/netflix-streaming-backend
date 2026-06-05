package com.netflix.streamingservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Consumed from kafka topic  : video.encoded
 * Published by encoding service after ffmpeg processing
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoEncodedEvent {
    private String movieId;
    private String hlsUrl;              // Master Playlist URL for streaming
    private String masterPlaylistKey;   // S3 key of master.m3u8
    private boolean success;
    private String errorMessage;
}
