package it.csttech.core.logging;

import java.util.Date;
import java.util.List;


public class LogMessage
{

    private final Date timestamp;

    private final String logLevel;

    private final String threadName;

    private final String loggerName;

    private final List<String> lines;

    private final boolean expandRequired;

    private final long startRow;

    public LogMessage(long startRow, Date timestamp, String logLevel, String threadName, String loggerName, List<String> lines)
    {
        this(startRow, timestamp, logLevel, threadName, loggerName, lines, false);
    }

    public LogMessage(long startRow, Date timestamp, String logLevel, String threadName, String loggerName, List<String> lines, boolean expandRequired)
    {
        if (lines == null || lines.isEmpty())
            throw new IllegalArgumentException("At least one line is required to build a message");

        this.startRow = startRow;
        this.timestamp = timestamp;
        this.logLevel = logLevel;
        this.threadName = threadName;
        this.loggerName = loggerName;
        this.lines = lines;
        this.expandRequired = expandRequired;
    }

    public long getStartRow()
    {
        return startRow;
    }

    public Date getTimestamp()
    {
        return timestamp;
    }

    public String getLogLevel()
    {
        return logLevel;
    }

    public String getThreadName()
    {
        return threadName;
    }

    public String getLoggerName()
    {
        return loggerName;
    }

    /**
     * Returns the first line of this message.
     */
    public String getMessage() // return first row of message
    {
        return lines.get(0);
    }

    /**
     * Returns the list of all lines contained in this message.
     */
    public List<String> getFullMessage()
    {
        return lines;
    }

    /**
     * Returns true if this message contains more than one line, false otherwise.
     */
    public boolean isMultiline()
    {
        return lines.size() > 1;
    }

    /**
     * Returns the number of lines in this message
     */
    public int lineCount()
    {
        return lines.size();
    }

    /**
     * Return true if the record should always be expanded (usually because the search query has been found in the message body (not the first record),
     * false otherwise.
     */
    public boolean isExpandRequired()
    {
        return expandRequired;
    }

}
