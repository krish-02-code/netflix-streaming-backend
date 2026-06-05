package com.netflix.contentservice.repository;

import com.netflix.contentservice.model.Genre;
import com.netflix.contentservice.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentRepository extends JpaRepository<Movie,String> {

    Optional<List<Movie>>findMovieByGenre(Genre genre);
    Optional<Movie>findMovieById(String movieId);
    Optional<List<Movie>> findMovieByTitle(String title);
}
