package com.antifraud.agent.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 分页结果封装(与具体 ORM 版本解耦)。
 * <p>
 * 说明: MyBatis-Plus 3.5.12 中 {@code PaginationInnerInterceptor} 已被拆分到独立 jar
 * ({@code mybatis-plus-extension} 在中央仓库最高只到 3.5.7), 直接引用会导致"找不到符号"。
 * 因此这里不使用 MP 的 {@code Page}/{@code selectPage}, 而是手工分页并返回本 DTO。
 */
@Data
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码(从 1 开始) */
    private long current;

    /** 每页条数 */
    private long size;

    /** 总页数 */
    private long pages;

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        PageResult<T> r = new PageResult<>();
        r.setRecords(records == null ? List.of() : records);
        r.setTotal(total);
        r.setCurrent(current);
        r.setSize(size);
        r.setPages(size <= 0 ? 0 : (total + size - 1) / size);
        return r;
    }
}
