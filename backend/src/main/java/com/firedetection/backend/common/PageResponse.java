package com.firedetection.backend.common;

import java.util.List;

public class PageResponse<T> {

    private List<T> records;
    private long total;
    private int pageNum;
    private int pageSize;

    public PageResponse() {
    }

    public PageResponse(List<T> records, long total, int pageNum, int pageSize) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
    }

    public static <T> PageResponse<T> of(List<T> records, long total, int pageNum, int pageSize) {
        return new PageResponse<>(records, total, pageNum, pageSize);
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
