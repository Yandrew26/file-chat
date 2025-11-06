package com.andrewproject.filechat.dto;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PageDTO<T> {
    private List<T> data;
    private Long pageNo;
    private Long pageSize;
    private Long total;
    private Long pages;

    private PageDTO(List<T> data, Long pageNo, Long pageSize, Long total, Long pages) {
        this.data = data;
        this.pageNo = pageNo;
        this.pageSize = pageSize;
        this.total = total;
        this.pages = pages;
    }

    public static <T> PageDTO<T> of(List<T> data, Long pageNo, Long pageSize, Long total, Long pages) {
        return new PageDTO<T>(data, pageNo, pageSize, total, pages);
    }

    public <S> PageDTO<S> convert(Function<T, S> convertToNewRow) {
        List<S> retRows = (List)this.data.stream().map(convertToNewRow).collect(Collectors.toList());
        return of(retRows, this.pageNo, this.pageSize, this.total, this.pages);
    }

    public List<T> getData() {
        return this.data;
    }

    public Long getPageNo() {
        return this.pageNo;
    }

    public Long getPageSize() {
        return this.pageSize;
    }

    public Long getTotal() {
        return this.total;
    }

    public Long getPages() {
        return this.pages;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    public void setPageNo(Long pageNo) {
        this.pageNo = pageNo;
    }

    public void setPageSize(Long pageSize) {
        this.pageSize = pageSize;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public void setPages(Long pages) {
        this.pages = pages;
    }
}
