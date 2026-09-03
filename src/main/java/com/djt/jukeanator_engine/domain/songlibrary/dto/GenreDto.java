package com.djt.jukeanator_engine.domain.songlibrary.dto;

import java.util.List;

public record GenreDto(Integer genreId, String genreName, List<Integer> albumIds, Integer numPlays) {
}
