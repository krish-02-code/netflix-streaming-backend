package com.netflix.vidioservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/*
* Event published to kafka  when the video is uploaded to S3
* Encoding service consume this to start FFmpeg processing
*
* TOPIC : Video Uploaded
*
* */


@NoArgsConstructor
@Data
@AllArgsConstructor
public class VideoUploadedEvent {
    private String movieId;
    private String videoKey;
    private String bucketName;
    private String originalFileName;
    private long fileSizeInBytes;
}
