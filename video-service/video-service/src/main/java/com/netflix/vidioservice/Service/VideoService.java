package com.netflix.vidioservice.Service;

import com.netflix.vidioservice.event.VideoUploadedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.protocol.types.Field;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
public class VideoService {

    private final S3Client s3Client;
    private final KafkaTemplate<String, VideoUploadedEvent> kafkaTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    private static final String VIDEO_UPLOADED_TOPIC = "video.uploaded";

    public VideoService(S3Client s3Client, KafkaTemplate<String, VideoUploadedEvent> kafkaTemplate) {
        this.s3Client = s3Client;
        this.kafkaTemplate = kafkaTemplate;
    }

    /*
    * Flow
    * 1.Receive multipart video file
    * 2.Generate unique S3 key
    * 3.upload to S3
    * 4.publish VideoUploadedEvent to kafka
    * 5.Encode Service picks up and start FFmpeg
    *
    * */

    public String uploadVideo(MultipartFile file, String movieId) throws IOException {
        log.info("Starting video upload for movie : {} file : {}",movieId,file.getOriginalFilename());

        //Generate unique S3 key for raw video
        //Format : raw/movieId/uuid_fileName

        String videoKey = "raw/"+movieId+"/"+ UUID.randomUUID()+"_"+file.getOriginalFilename();

        // build request to send the video file to s3
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(videoKey)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(),file.getSize()));

        log.info("Video uploaded to s3 successfully . key {}",videoKey);

        //publish event to kafka
        //Encoding service will consume this and start FFmpeg processing

        VideoUploadedEvent videoUploadedEvent = new VideoUploadedEvent(movieId,videoKey,bucketName,file.getOriginalFilename(),file.getSize());
        kafkaTemplate.send(VIDEO_UPLOADED_TOPIC,movieId,videoUploadedEvent);
        log.info("Video uploaded event published for movie : {}",movieId);
        return videoKey;
    }
}
