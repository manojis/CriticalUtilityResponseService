package edu.gatech.erms.api.dto;

import edu.gatech.erms.domain.PageResult;
import java.util.List;
import java.util.function.Function;

/** A page of results. {@code page} is zero-based. */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {

  public static <S, T> PageResponse<T> from(PageResult<S> result, Function<S, T> mapper) {
    return new PageResponse<>(
        result.content().stream().map(mapper).toList(),
        result.page(),
        result.size(),
        result.totalElements(),
        result.totalPages());
  }
}
