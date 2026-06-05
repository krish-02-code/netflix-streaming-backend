package com.netflix.contentservice.service;

import com.netflix.contentservice.dto.MovieRequest;
import com.netflix.contentservice.dto.MovieResponse;
import com.netflix.contentservice.model.Genre;
import com.netflix.contentservice.model.Movie;
import com.netflix.contentservice.model.VideoStatus;
import com.netflix.contentservice.repository.ContentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ContentService {

    private final ContentRepository contentRepository;

    public ContentService(ContentRepository contentRepository) {
        this.contentRepository = contentRepository;
    }

    // add the new movie to the catalog
    // video is not yet uploaded at this stage
    public MovieResponse addMovie(MovieRequest movieRequest) {
        log.info("Adding new Movie : {}", movieRequest.getTitle());
        Movie movie = new Movie();
        movie.setTitle(movieRequest.getTitle());
        movie.setDescription(movieRequest.getDescription());
        movie.setGenre(movieRequest.getGenre());
        movie.setDirector(movieRequest.getDirector());
        movie.setRating(movieRequest.getRating());
        movie.setDurationMinutes(movieRequest.getDurationMinutes());
        movie.setReleaseYear(movieRequest.getReleaseYear());
        movie.setThumbnailUrl(movieRequest.getThumbnailUrl());
        movie.setCast(movieRequest.getCast());
        movie.setVideoStatus(VideoStatus.PENDING);

        Movie savedMovie = contentRepository.save(movie);
        log.info("Movie added with id : {}", savedMovie.getId());
        return mapToResponse(savedMovie);
    }

    private MovieResponse mapToResponse(Movie savedMovie) {
        MovieResponse movieResponse = new MovieResponse();
        movieResponse.setId(savedMovie.getId());
        movieResponse.setTitle(savedMovie.getTitle());
        movieResponse.setCast(savedMovie.getCast());
        movieResponse.setGenre(savedMovie.getGenre());
        movieResponse.setDescription(savedMovie.getDescription());
        movieResponse.setDirector(savedMovie.getDirector());
        movieResponse.setRating(savedMovie.getRating());
        movieResponse.setDurationMinutes(savedMovie.getDurationMinutes());
        movieResponse.setReleaseYear(savedMovie.getReleaseYear());
        movieResponse.setCreatedAt(savedMovie.getCreatedAt());
        movieResponse.setThumbnailUrl(savedMovie.getThumbnailUrl());
        movieResponse.setHlsUrl(savedMovie.getHlsUrl());
        movieResponse.setVideoKey(savedMovie.getVideoKey());
        movieResponse.setVideoStatus(savedMovie.getVideoStatus());
        return movieResponse;
    }


    public List<MovieResponse> getAll() {
        return contentRepository.findAll().stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    //get movie by genre
    public List<MovieResponse> getMoviesByGenre(Genre genre) {
        List<Movie> movie = contentRepository.findMovieByGenre(genre).orElseThrow(() -> new RuntimeException("Movie Not Found !"));
        return movie.stream().map(this::mapToResponse).toList();
    }

    public MovieResponse getMoviesById(String movieId) {
        Movie movie = contentRepository.findMovieById(movieId).orElseThrow(() -> new RuntimeException("Movie Not Found !"));
        return mapToResponse(movie);
    }

    public List<MovieResponse> searchMovies(String title) {
        List<Movie> movie = contentRepository.findMovieByTitle(title).orElseThrow(() -> new RuntimeException("Movie Not Found !"));
        return movie.stream().map(this::mapToResponse).toList();
    }

    public void update(String movieID, String videoKey) {
        log.info("updating video key for movie : {}", movieID);
        Movie movie = contentRepository.findMovieById(movieID).orElseThrow(() -> new RuntimeException("Movie Not found !"));
        movie.setVideoKey(videoKey);
        movie.setVideoStatus(VideoStatus.UPLOADED);
        contentRepository.save(movie);
    }

    public void updateVideoStatus(String movieId,VideoStatus videoStatus){
        Movie movie = contentRepository.findMovieById(movieId).orElseThrow(()->new RuntimeException("Movie not found"));
        movie.setVideoStatus(videoStatus);
        contentRepository.save(movie);
    }

    public void updateHLSUrl(String movieID, String hlsUrl) {
        log.info("updating hls url for movie : {}", movieID);
        Movie movie = contentRepository.findMovieById(movieID).orElseThrow(() -> new RuntimeException("Movie Not found !"));
        movie.setHlsUrl(hlsUrl);
        movie.setVideoStatus(VideoStatus.READY);
        contentRepository.save(movie);
        log.info("Movie is now ready for streaming !");
    }
}
