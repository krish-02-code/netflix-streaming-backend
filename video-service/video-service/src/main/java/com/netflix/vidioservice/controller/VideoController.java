package com.netflix.vidioservice.controller;


import com.netflix.vidioservice.Service.VideoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("api/v1/videos")
@Slf4j
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    // upload the video file for the movie
    // Accepts the multipart file upload

    //POST /api/v1/videos/upload/movieId

    @PostMapping("upload/{movieId}")
    public ResponseEntity<String> uploadVideo(@PathVariable String movieId, @RequestParam("file") MultipartFile file) throws IOException {
        log.info("Video upload request for movie : {} file size : {}MB", movieId, file.getSize() / (1024 * 1024));
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("file is Empty !");
        }
        String videoKey = videoService.uploadVideo(file, movieId);

        return new ResponseEntity<>("Video Uploaded Successfully! Key : "
                + videoKey + " Encoding started automatically via kafka", HttpStatus.OK);
    }

}
