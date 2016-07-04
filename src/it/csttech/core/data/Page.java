package it.csttech.core.data;

import java.util.List;


public interface Page<T> extends Iterable<T>
{
    public long getOffset();

    public long getCurrentPage();

    public long getPageSize();

    public long getTotalCount();

    public List<T> getData();

    long getTotalPages();
}
