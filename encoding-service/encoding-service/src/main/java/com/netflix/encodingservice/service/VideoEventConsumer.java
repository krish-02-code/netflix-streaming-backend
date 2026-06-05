package com.netflix.encodingservice.service;

import com.netflix.encodingservice.event.VideoUploadedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class VideoEventConsumer {

    private final EncodingService encodingService;

    public VideoEventConsumer(EncodingService encodingService) {
        this.encodingService = encodingService;
    }

    /**
     * Listen to video.uploaded Kafka topic
     * Triggered when video service upload a raw video to S3
     *
     * FLOW :
     *
     * Video Service -> S3 upload -> Kafka (video.uploaded)
     *                            ->This Consumer
     *                            ->Encoding Service -> FFmpeg -> S3
     *                            ->Kafka (video.encoded)
     */

    @KafkaListener(topics = "video.uploaded",groupId = "encoding-service-group")
    public void consumeVideoUploadedEvent(VideoUploadedEvent uploadedEvent){
        log.info("Consume video uploaded event for movie : {} file : {}",uploadedEvent.getMovieId(),uploadedEvent.getOriginalFileName());
        try{
            encodingService.encodeVideo(uploadedEvent);
        }catch(Exception e){
            log.error("Failed to process encoding for movie : {} - {} ",uploadedEvent.getMovieId(),e.getMessage());
        }
    }

}
