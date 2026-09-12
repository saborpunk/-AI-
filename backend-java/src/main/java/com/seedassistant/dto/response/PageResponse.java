package com.seedassistant.dto.response;

import java.util.List;

public class PageResponse<T> {
    private final List<T> items;
    private final int page;
    private final int size;
    private final boolean hasMore;
    public PageResponse(List<T> items, int page, int size, boolean hasMore) {
        this.items = items; this.page = page; this.size = size; this.hasMore = hasMore;
    }
    public List<T> getItems() { return items; }
    public int getPage() { return page; }
    public int getSize() { return size; }
    public boolean isHasMore() { return hasMore; }
}
