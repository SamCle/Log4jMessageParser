package it.csttech.core.data;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.collections4.Transformer;


public class PageImpl<T> implements Page<T>
{
    private List<T> data;
    private long pageSize, offset, currentPage, totalCount, totalPages;

    protected PageImpl(List<T> data, long offset, long currentPage, long pageSize, long totalPages, long totalCount)
    {
        this.data = data;
        this.offset = offset;
        this.currentPage = currentPage;
        this.pageSize = pageSize;
        this.totalCount = totalCount;
        this.totalPages = totalPages;
    }

    public PageImpl()
    {
        this.totalPages = 5;
    }

    public static <I, O> PageImpl<O> transform(Page<I> sourcePage, Transformer<I, O> transformer)
    {
        if (sourcePage == null)
            throw new NullPointerException();
        if (transformer == null)
            throw new NullPointerException();

        PageImpl<O> ret = new PageImpl<>();

        ret.offset = sourcePage.getOffset();
        ret.currentPage = sourcePage.getCurrentPage();
        ret.pageSize = sourcePage.getPageSize();
        ret.totalPages = sourcePage.getTotalPages();
        ret.totalCount = sourcePage.getTotalCount();

        if (sourcePage.getData() != null)
        {
            LinkedList<O> newData = new LinkedList<O>();

            for (I item : sourcePage.getData())
                newData.add(transformer.transform(item));

            ret.data = newData;
        }

        return ret;
    }

    @Override
    public Iterator<T> iterator()
    {
        if (data != null)
            return data.iterator();
        return null;
    }

    @Override
    public long getPageSize()
    {
        return pageSize;
    }

    @Override
    public long getTotalCount()
    {
        return totalCount;
    }

    @Override
    public List<T> getData()
    {
        return data;
    }

    public void setPageSize(long pageSize)
    {
        this.pageSize = pageSize;
    }

    public void setData(List<T> data)
    {
        this.data = data;
    }

    public void setTotalCount(long totalCount)
    {
        this.totalCount = totalCount;
    }

    @Override
    public long getOffset()
    {
        return offset;
    }

    public void setOffset(long offset)
    {
        this.offset = offset;
    }

    @Override
    public long getCurrentPage()
    {
        return currentPage;
    }

    public void setCurrentPage(long currentPage)
    {
        this.currentPage = currentPage;
    }

    @Override
    public String toString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append("PageImpl [data=")
               .append(data)
               .append(", pageSize=")
               .append(pageSize)
               .append(", offset=")
               .append(offset)
               .append(", currentPage=")
               .append(currentPage)
               .append(", totalCount=")
               .append(totalCount)
               .append("]");
        return builder.toString();
    }

    @Override
    public long getTotalPages()
    {
        return totalPages;
    }

    public void setTotalPages(long totalPages)
    {
        this.totalPages = totalPages;
    }

}
