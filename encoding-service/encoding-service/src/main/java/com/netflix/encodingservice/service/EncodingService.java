package com.netflix.encodingservice.service;

import com.netflix.encodingservice.event.VideoEncodeEvent;
import com.netflix.encodingservice.event.VideoUploadedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class EncodingService {
    private final S3Client s3Client;
    private final KafkaTemplate<String, VideoEncodeEvent> kafkaTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    @Value("${encoding.base-path}")
    private String basePath;

    @Value("${aws.region}")
    private String region;


    public EncodingService(S3Client s3Client, KafkaTemplate<String, VideoEncodeEvent> kafkaTemplate) {
        this.s3Client = s3Client;
        this.kafkaTemplate = kafkaTemplate;
    }

    private static final String VIDEO_ENCODED_TOPIC = "video.encoded";


    //videos qualities to upload
    // Format: resolution,bitrate = how much data is processed per sec,height

    private static final List<int[]> VIDEO_QUALITIES = Arrays.asList(
            new int[]{1920, 5000, 1080},      //1080p  -> 5000k bitrate
            new int[]{1280, 2800, 720},       //720p    ->2800k bitrate
            new int[]{854, 1200, 480},
            new int[]{640, 800, 360}
    );

    /*
     * Main encoding pipeline
     *
     * Steps :
     * 1:Download raw video from S3
     * 2:Encode to multiple qualities using ffmpeg
     * 3:Generate HLS playlist (.m3u8) for each quality
     * 4:Create master playlist
     * 5:Upload all encoded files back to s3.
     * 5:Publish videoEncodedEvent to kafka
     * @param event
     *
     * */
    public void encodeVideo(VideoUploadedEvent event) {
        log.info("Starting encoding platform for movie {}", event.getMovieId());

        // create a unique path for a movie
        String jobPath = basePath + "/" + event.getMovieId();

        try {
            // create temp directories
            Files.createDirectories(Paths.get(jobPath));
            Files.createDirectories(Paths.get(jobPath + "/encoded"));

            //step 1 : download raw video from s3
            String localVideoPath = jobPath + "/raw_video.mp4";
            downloadFromS3(event.getVideoKey(), localVideoPath);
            log.info("Raw video downloaded to : {}", localVideoPath);

            // step 2 & 3 :Encode into multiple qualities + generate HLS
            for (int[] qualities : VIDEO_QUALITIES) {
                int width = qualities[0];
                int bitrate = qualities[1];
                int height = qualities[2];

                String qualityDir = jobPath + "/encoded/" + height + "p";
                Files.createDirectories(Paths.get(qualityDir));

                encodeToHLS(localVideoPath, qualityDir, width, height, bitrate);
                log.info("Encoded {}p successfully", height);
            }

            //Step 4: generate master playlist
            String masterPlaylistPath = jobPath + "/encoded/master.m3u8";
            generateMasterPlaylist(masterPlaylistPath);
            log.info("Master playlist generated");

            // step 5: upload all resources files to s3
            String encodedPrefix = "encoded/" + event.getMovieId() + "/";
            uploadEncodedFilesToS3(jobPath + "/encoded/", encodedPrefix);
            log.info("All the encoded files uploaded to s3");

            //step 6 : publish videoEncodedEvent
            String masterPlayListKey = encodedPrefix + "master.m3u8";
            String hlsUrl = "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + masterPlayListKey;
            VideoEncodeEvent videoEncodeEvent = new VideoEncodeEvent(event.getMovieId(), hlsUrl, masterPlayListKey, true, null);

            kafkaTemplate.send(VIDEO_ENCODED_TOPIC, event.getMovieId(), videoEncodeEvent);
            log.info("VideoEncodedEvent published for movie : {}", event.getMovieId());

        } catch (Exception e) {
            log.error("Encoding failed for movie : {} - {}", event.getMovieId(), e.getMessage());

            //Publish the failure event
            VideoEncodeEvent failureEvent = new VideoEncodeEvent(event.getMovieId(), null, null, false, e.getMessage());
            kafkaTemplate.send(VIDEO_ENCODED_TOPIC, event.getMovieId(), failureEvent);
        } finally {
            // cleanup temp files
            cleanUpTempFiles(jobPath);
        }
    }

    /**
     * Upload all encoded files back to s3
     * @param localDir
     * @param encodedPrefix
     */
    private void uploadEncodedFilesToS3(String localDir, String encodedPrefix) {
        File directory = new File(localDir);
        uploadDirectoryToS3(directory, localDir, encodedPrefix);
    }

    private void uploadDirectoryToS3(File directory, String localDir, String encodedPrefix) {
        Path baseDirPath = Paths.get(localDir);

        for (File file : Objects.requireNonNull(directory.listFiles())) {
            if (file.isDirectory()) {
                uploadDirectoryToS3(file, localDir, encodedPrefix);
            } else {

                String relativePath = baseDirPath.relativize(file.toPath())
                        .toString()
                        .replace("\\", "/");   // normalize Windows separators

                String S3key = encodedPrefix + relativePath;
                String contentType = file.getName().endsWith(".m3u8") ? "application/x-mpegURL" : "video/MP2T";

                PutObjectRequest objectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(S3key)
                        .contentType(contentType)
                        .build();

                s3Client.putObject(objectRequest, RequestBody.fromFile(file));
                log.debug("uploaded {} ", S3key);
            }
        }
    }


    /**
     * Clean up files after encoding
     *
     * @param jobPath
     */
    private void cleanUpTempFiles(String jobPath) {
        try {
            Path dirPath = Paths.get(jobPath);
            if (Files.exists(dirPath)) {
                Files.walk(dirPath)
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(path -> path.toFile())
                        .forEach(file -> file.delete());


                log.info("Temp files cleaned up for job : {}", jobPath);
            }
        } catch (IOException e) {
            log.warn("Failed to clean up temp files : {}", e.getMessage());
        }
    }

    /**
     * Generate master hls playlist that references all quality playlist
     * This is the file video player downloads firsts
     * @param masterPlaylistPath
     * @throws IOException
     */

    private void generateMasterPlaylist(String masterPlaylistPath) throws IOException {
        StringBuilder master = new StringBuilder();
        master.append("#EXTM3U\n");
        master.append("#EXT-X-VERSION:3\n\n");

        //Add each quality to master playlist
        int[][] qualities = {
                {1920, 5000, 1080},
                {1280, 2800, 720},
                {854, 1200, 480},
                {640, 800, 360}
        };

        for (int[] q : qualities) {
            int width = q[0];
            int bitrate = q[1];
            int height = q[2];

            master.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                    .append(bitrate * 1000)
                    .append(", RESOLUTION=")
                    .append(width)
                    .append("x")
                    .append(height)
                    .append(",CODECS=\"avc1.42e01e,mp4a.40.2\"\n");
            master.append(height).append("p/playlist.m3u8\n\n");

        }
        Files.writeString(Paths.get(masterPlaylistPath), master.toString());
    }

    /**
     * Encode video to hls using ffmpeg
     *
     * Ffmpeg command created :
     *  -Multiple .ts segment files (10s each)
     *  -A .m3u8 playlist file for this quality
     */
    private void encodeToHLS(String inputPath, String outputDir, int width, int height, int bitrate) throws IOException, InterruptedException {
        String playlistPath = outputDir + "/playlist.m3u8";
        String segmentPattern = outputDir + "/segment_%03d.ts";


        // FFmpeg command for hls encoding
        List<String> command = List.of(
                ffmpegPath,
                "-i", inputPath,                            //Input file
                "-vf", "scale=" + width + ":" + height,        //Scale to resolution
                "-c:v", "libx264",                           // video codec
                "-b:v", bitrate + "k",                       //video bitrate
                "-c:a", "aac",                              // audio coded
                "-b:a", "128k",                            // audio bitrate
                "-hls_time", "10",                        // 10 sec segments
                "-hls_list_size", "0",                     // keep all segments
                "-hls_segment_filename", segmentPattern,          // segment naming
                "-f", "hls",                             //output format
                playlistPath                             // output playlist
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.inheritIO();
        Process process = processBuilder.start();
//        processBuilder.redirectErrorStream(true);
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("FFmpeg failed (exit " + exitCode + "): ");
        }
    }

    private void downloadFromS3(String videoKey, String localVideoPath) {
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(videoKey)
                .build();

        s3Client.getObject(objectRequest, Paths.get(localVideoPath));
    }

}