package com.netflix.streamingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamResponse {
    private String movieID;
    private String streamingUrl;              // Presigned HLS master playlist URL
    private String masterPlaylistKey;         // S3 key of the master playlist (for proxy endpoint)
    private String quality;                   // available qualities
    private long expiredInMinutes;            // url expiry in minutes
}
