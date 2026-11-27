package edu.gatech.erms.domain;

import java.util.List;

/** A page of results plus the total number of matching rows. */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {

  public PageResult {
    content = List.copyOf(content);
  }

  public int totalPages() {
    return size == 0 ? 0 : (int) ((totalElements + size - 1) / size);
  }
}
