package com.netflix.contentservice.controller;


import com.netflix.contentservice.dto.MovieRequest;
import com.netflix.contentservice.dto.MovieResponse;
import com.netflix.contentservice.model.Genre;
import com.netflix.contentservice.service.ContentService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/movies")
@CrossOrigin(origins = "*")
@Slf4j
public class ContentController {

    private final ContentService contentService;

    public ContentController(ContentService contentService) {
        this.contentService = contentService;
    }

    //Add movie to catalog
    @PostMapping
    public ResponseEntity<MovieResponse>addMovie(@Valid @RequestBody MovieRequest movieRequest){
          return new ResponseEntity<>(contentService.addMovie(movieRequest),HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<MovieResponse>>getAllMovies(){
        return ResponseEntity.ok(contentService.getAll());
    }

    // get movie by genre
    @GetMapping("/genre/{genre}")
    public ResponseEntity<List<MovieResponse>>getMovieByGenre(@PathVariable Genre genre){
        return ResponseEntity.ok(contentService.getMoviesByGenre(genre));
    }

    // get movie by id
    @GetMapping("/{movieId}")
    public ResponseEntity<MovieResponse>getMovieById(@PathVariable String movieId){
        return ResponseEntity.ok(contentService.getMoviesById(movieId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<MovieResponse>>searchMovies(@RequestParam String title){
        return ResponseEntity.ok(contentService.searchMovies(title));
    }


}


