package it.csttech.core.logging;

import it.csttech.core.data.Page;


public interface LogFileParser
{

    /**
     * Returns the next page, starting at specified message and with the required size.
     */
    public Page<LogMessage> nextPage(long currentMessage, long pageSize);

    /**
     * Returns the previous page, starting at specified message and with the required size.
     */
    public Page<LogMessage> prevPage(long currentMessage, long pageSize);

    /**
     * Find the next page, starting with the first message that match the input expression. Search starts at 'currentMessage' rows.
     */
    public Page<LogMessage> findNext(String expression, boolean useRegex, long currentMessage, long pageSize);

    /**
     * Find the previous page, starting with the first message that match the input expression. Search starts at 'currentMessage' rows.
     */
    public Page<LogMessage> findPrev(String expression, boolean useRegex, long currentMessage, long pageSize);

    /**
     * Find the next page with only messages that match the input expression. Start filtering messages only record after 'currentMessage' rows.
     */
    public Page<LogMessage> filterNext(String expression, boolean useRegex, long currentMessage, long pageSize);

    /**
     * Find the previous page with only messages that match the input expression. Start filtering messages only record after 'currentMessage' rows.
     */
    public Page<LogMessage> filterPrev(String expression, boolean useRegex, long currentMessage, long pageSize);

    /**
     * Returns true if this parser has the timestamp field.
     */
    public boolean hasTimestamp();

    /**
     * Returns true if this parser has the timestamp field.
     */
    public boolean hasLogLevel();

    /**
     * Returns true if this parser has the timestamp field.
     */
    public boolean hasThreadName();

    /**
     * Returns true if this parser has the timestamp field.
     */
    public boolean hasLoggerName();

    /**
     * Returns true if this parser has the timestamp field.
     */
    public boolean hasMessage();

}
