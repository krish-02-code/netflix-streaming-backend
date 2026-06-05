package com.netflix.contentservice.model;

/*
 * Tracks the video processing life cycle
 *
 * Flow
 * Pending->Uploaded->Encoding->Encoded->Ready
 *                            ->failed
 * */
public enum VideoStatus {
    PENDING,  // movie added but not uploaded yet
    UPLOADED, // raw video uploaded to S3
    ENCODING,  // FF is encoding the video
    ENCODED,   // Encoding complete
    READY, // Hls playlist ready and can be streamed
    FAILED    // Encoding failed
}
