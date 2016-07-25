package it.csttech.core.logging;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import it.csttech.core.data.Page;
import it.csttech.core.data.PageImpl;


public class LogFileParserImpl implements LogFileParser
{
    private static final long serialVersionUID = 8273119159030929670L;

    private static final String STANDARD_REGEX = "^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2},[0-9]{3}";
    private static final String STANDARD_TIMESTAMP = "yyyy-MM-dd HH:mm:ss,SSS";

    private boolean useRegisters;

    private String regex, timestampFormat;
    private Path file;
    private long currentPosition;
    private long beginningOfMessages; //stores the actual beginning of messages in the input file (as character). Most likely it will be 0, unless the user provides a file that has been cut without care
    private int currentLine, currentMessage;
    private int startingLineOfFirstMessage; //stores the actual beginning of messages in the input file (as line). Most likely it will be 0, unless the user provides a file that has been cut without care
    private List<Long> messageInitPositions;
    private List<Integer> messageInitLines;
    public long pageBeginPosition, pageEndPosition; // these fields are involved in calculations based on general use of currentPosition without currentLine nor currentMessage

    private String orphanLine;
    private long orphanPosition;

    public LogFileParserImpl(String filename)
    {
        this(filename, STANDARD_REGEX, STANDARD_TIMESTAMP, null);
    }

    public LogFileParserImpl(String filename, Long currentPosition)
    {
        this(filename, STANDARD_REGEX, STANDARD_TIMESTAMP, currentPosition);
    }

    public LogFileParserImpl(String filename, String regex, String timestampFormat)
    {
        this(filename, regex, timestampFormat, null);
    }

    public LogFileParserImpl(String filename, String regex, String timestampFormat, Long currentPosition)
    {
        this.timestampFormat = timestampFormat;
        this.regex = regex;
        file = Paths.get(filename);

        if (!Files.exists(file))
        {// Then the file doesn't exist
            throw new IllegalArgumentException("This file does not exist");
        }

        this.currentPosition = 0;

        long positionSaver;
        StringBuffer line = new StringBuffer();
        do
        {
            positionSaver = this.currentPosition;
            line = readLine();
            if (line == null)
            { // Then it's EOF.
                throw new IllegalArgumentException("This file does not contain any Log4j messages");
            }
            else
            {}
        }
        while (!isStartOfMessage(line)); // Start of message found!

        orphanLine = line.toString();
        orphanPosition = this.currentPosition;

        this.currentPosition = positionSaver;
        startingLineOfFirstMessage = currentLine;
        beginningOfMessages = positionSaver;
        currentMessage = 0;
        messageInitPositions = new ArrayList<Long>();
        messageInitPositions.add(new Long(-1));
        messageInitPositions.add(beginningOfMessages);
        messageInitLines = new ArrayList<Integer>();
        messageInitLines.add(new Integer(-1));
        messageInitLines.add(startingLineOfFirstMessage);
        if (currentPosition == null || currentPosition < 0)
        {
            setCurrentPositionToEndOfFile();
        }
        else if (currentPosition >= this.currentPosition)
        { // this.currentPosition ora coincide con beginningOfMessages.
            this.currentPosition = currentPosition;
        }
        useRegisters = (this.currentPosition == beginningOfMessages);
    }

    /**
     * This method reads a line from the file. It starts at the currentPosition and continues to read 
     * until it finds a '\n', '\r' or a suitable (Windows) combination of the two.
     * 
     * @return the line as a StringBuffer, or null if at the currentPosition we are at the end of 
     * file (EOF), in the spirit of the readLine method found in the BufferedReader class.
     * 
     * WARNING: when calling this method, one should be sure that no IOExceptions could occur.
     */
    private StringBuffer readLine()
    {
        ByteBuffer byteBuffer;
        StringBuffer line;

        try (FileChannel fileChannel = FileChannel.open(file, StandardOpenOption.READ))
        {
            fileChannel.position(currentPosition);
            byteBuffer = ByteBuffer.allocate(1);
            line = new StringBuffer();
            int currentChar = -1;

            while (fileChannel.read(byteBuffer) != -1)
            {
                byteBuffer.rewind(); // The position is set to zero and the mark is discarded.
                currentChar = byteBuffer.get(byteBuffer.position());
                line.append((char) currentChar);
                if (currentChar == '\n' || currentChar == '\r')
                {
                    break;
                }
                else
                {}
            }

            if (currentChar == -1)
            { //Then it's the end of file; return null
                return null;
            }

            if (currentChar == '\r')
            {
                fileChannel.read(byteBuffer);
                byteBuffer.rewind();
                char nextChar = (char) byteBuffer.get(byteBuffer.position());
                if (nextChar == '\n')
                { // Then we found a '\r' followed by a '\n'
                      //                    we ignore it and increase the position by one
                    line.append('\n');
                }
                else
                { // Then we had only found a '\r' and the next character is not '\n';
                      //                    Return to the previous position for the next read
                    fileChannel.position(fileChannel.position() - 1);
                }
            }

            currentPosition = fileChannel.position(); //update this object's position with the fileChannel position
        }
        catch (IOException e)
        { // Never(!) happens: main method should avoid any errors
            System.out.println("I/O Exception: " + e);
            return null;
        }
        if (line != null)
        {
            currentLine += useRegisters ? 1 : 0;
        }
        return line;
    }

    private StringBuffer prevLine()
    {
        if (currentPosition == 0)
        {
            return null;
        }
        try (FileChannel fileChannel = FileChannel.open(file, StandardOpenOption.READ))
        {

            StringBuffer line = new StringBuffer();
            StringBuffer eol = new StringBuffer();
            int currentChar = -1;
            ByteBuffer byteBuffer;

            for (byte b = 0; b < 2; b++)
            {

                currentPosition--;
                if (currentPosition < 0)
                {
                    break;
                }
                fileChannel.position(currentPosition);
                byteBuffer = ByteBuffer.allocate(1);
                fileChannel.read(byteBuffer);
                byteBuffer.rewind();
                currentChar = byteBuffer.get(byteBuffer.position());

                if (currentChar == -1)
                {
                    break;
                }
                else
                {
                    eol.insert(0, (char) currentChar);
                }
            }

            if (eol.length() < 2)
            {
                return eol;
            }
            else if ((eol.charAt(0) == '\r' || eol.charAt(0) == '\n') && (eol.charAt(0) == '\n' || eol.charAt(1) == '\r'))
            {
                currentPosition++;
                return line.append(eol.charAt(1));
            }
            line.append(eol);

            while (true)
            {

                currentPosition--;
                if (currentPosition < 0)
                {
                    break;
                }
                fileChannel.position(currentPosition);
                byteBuffer = ByteBuffer.allocate(1);
                fileChannel.read(byteBuffer);
                byteBuffer.rewind();
                currentChar = byteBuffer.get(byteBuffer.position());

                //System.out.println(currentChar);
                if (currentChar == -1)
                {
                    break;
                }
                if (currentChar == '\n' || currentChar == '\r')
                {
                    currentPosition++;
                    break;
                }
                line.insert(0, (char) currentChar);
            }
            return line;
        }
        catch (IOException e)
        {
            System.out.println("I/O Exception: " + e);
            return null;
        }
    }

    /**
     * This method reads a full Log4j message, by calling repeatedly the readLine method. 
     * We always assume we are at the beginning of a message.
     * 
     * @return the full message as a List<String>, or null iff the EOF was already reached.
     */
    private List<String> nextMessage()
    {

        currentMessage = useRegisters ? currentMessage + 1 : 0;
        StringBuffer line = new StringBuffer();
        long positionSaver;
        List<String> lines = new ArrayList<>();

        if (useRegisters)
        {
            addMessageReadInitPosition(currentPosition);
            addMessageReadInitLine(currentLine);
        }

        if (orphanLine != null)
        {
            currentLine = useRegisters ? currentLine + 1 : 0;
            currentPosition = orphanPosition;
            positionSaver = currentPosition;
            lines.add(String.valueOf(currentLine));
            lines.add(orphanLine);
        }
        else
        {
            line = readLine();
            if (line == null)
            {
                return null;
            }
            else
            {
                lines.add(String.valueOf(currentLine));
                lines.add(line.toString());
                positionSaver = currentPosition;
            }
        }

        positionSaver = currentPosition;
        while (true)
        {
            line = readLine();
            if (line == null)
            {
                resetOrphans();
                break;
            }
            else if (!isStartOfMessage(line))
            {
                lines.add(line.toString());
                positionSaver = currentPosition;
            }
            else
            {
                orphanLine = line.toString();
                orphanPosition = currentPosition;
                currentPosition = positionSaver;
                currentLine = useRegisters ? currentLine - 1 : 0;
                break;
            }
        }
        return lines;
    }

    /**
     * This method reads a full Log4j message, by calling repeatedly the readLine method. 
     * We always assume we are at the beginning of a message.
     * 
     * @return the full message as a List<String>, or null iff the BOF was already reached.
     */
    private List<String> prevMessage()
    { // TODO: avviare ipotesi di unificazione.
        /*    	if (useRegisters) {
            if (currentMessage <= 1){
                return null;
            }
            setFromRegisters(currentMessage - 1);
            List<String> message = nextMessage();
            currentMessage--;
            return message;			
        		} else {*/
        if (useRegisters)
        {
            setFromRegisters(currentMessage - 1);
        }
        StringBuffer line;
        List<String> message = new ArrayList<>();
        message.add(useRegisters ? messageInitLines.get(currentMessage).toString() : "0");
        do
        {
            line = prevLine();
            if (line == null)
            {
                break;
            }
            message.add(1, line.toString());
        }
        while (!isStartOfMessage(line));
        return message;
        //		}

    }

    private LogMessage convertMessageFromListToLogMessage(List<String> message, boolean checkExpandRequired, String expression, boolean useRegex)
    {
        int startRow = Integer.decode(message.get(0));
        List<String> lines = new ArrayList<>();

        StringBuffer firstLine = new StringBuffer(message.get(1));
        Date timestamp = new SimpleDateFormat(timestampFormat).parse(firstLine.toString(), new ParsePosition(0));
        firstLine = firstLine.delete(0, timestampFormat.length());

        Pattern pattern = Pattern.compile("(FATAL|ERROR|WARN|INFO|DEBUG|TRACE)");
        Matcher matcher = pattern.matcher(firstLine);
        matcher.find();
        String logLevel = firstLine.substring(matcher.start(), matcher.end());
        firstLine = firstLine.delete(0, matcher.end());

        pattern = Pattern.compile("\\[[^\\[\\]]*\\]");
        matcher = pattern.matcher(firstLine);
        matcher.find();
        String threadName = firstLine.substring(matcher.start(), matcher.end());
        threadName = threadName.substring(1, threadName.length() - 1);
        firstLine = firstLine.delete(0, matcher.end());

        pattern = Pattern.compile(" - ");
        matcher = pattern.matcher(firstLine);
        matcher.find();
        String loggerName = firstLine.substring(1, matcher.end() - 3);
        firstLine = firstLine.delete(0, matcher.end());

        lines.add(firstLine.toString());

        if (message.size() > 1)
        {
            for (int i = 2; i < message.size(); i++)
            {
                lines.add(message.get(i));
            }
        }

        boolean expandRequired;
        if (message.size() == 2 || !checkExpandRequired)
        {
            expandRequired = false;
        }
        else
        {
            expandRequired = isExpressionFound(message.subList(1, message.size()), expression, useRegex);
        }

        return new LogMessage(startRow, timestamp, logLevel, threadName, loggerName, lines, expandRequired);
    }

    private Long getLastMessageInitPositionRead()
    {
        return messageInitPositions.get(messageInitPositions.size() - 1);
    }

    private boolean addMessageReadInitPosition(Long value)
    {
        if (value > getLastMessageInitPositionRead())
        {
            messageInitPositions.add(value);
            return true;
        }
        return false;
    }

    private Integer getLastMessageInitLineRead()
    {
        return messageInitLines.get(messageInitLines.size() - 1);
    }

    private boolean addMessageReadInitLine(Integer value)
    {
        if (value > getLastMessageInitLineRead())
        {
            messageInitLines.add(value);
            return true;
        }
        return false;
    }

    private boolean isStartOfMessage(StringBuffer line)
    {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(line);
        return line == null ? false : matcher.find();
    }

    private boolean isExpressionFound(List<String> message, String expression, boolean useRegex)
    {
        Pattern pattern;
        if (useRegex)
        {
            pattern = Pattern.compile(expression);
            Matcher matcher;
            for (String line : message.subList(1, message.size()))
            { // first element of message is just the starting row number and will be ignored.
                matcher = pattern.matcher(line);
                if (line == null)
                {
                    continue;
                }
                else
                {
                    if (matcher.find())
                    {
                        return true;
                    }
                }
            }
            return false;
        }
        else
        {
            for (String line : message.subList(1, message.size()))
            { // first element of message is just the starting row number and will be ignored.
                if (line.indexOf(expression) == -1)
                {
                    continue;
                }
                else
                {
                    return true;
                }
            }
            return false;
        }
    }

    // Resetta le posizioni tramite i registri, il risultato sar� avere il cursore all'inizio del messaggio corrispondente.
    private void setFromRegisters(int messageNumber)
    {
        resetOrphans();
        currentMessage = messageNumber;
        currentLine = messageInitLines.get(currentMessage);
        currentPosition = messageInitPositions.get(currentMessage);
    }

    /**
     * A boolean which gives information about whether or not the position given by the user
     * is set in order to start reading messages from such position (at the end of the message corresponding to the number).
     * 
     * @param messageNumber the message number provided by the user
     * @return true if all positions were successfully set, false iff the end of file was reached
     */
    private boolean arePositionsSet(int messageNumber)
    {
        if (messageNumber + 1 < messageInitPositions.size())
        { //This happens if we have already stored all position info about the message referenced by the user; we exploit this
            setFromRegisters(messageNumber + 1);
            return true;
        }
        else
        {
            //Then we need to navigate a section of the file
            setFromRegisters(messageInitPositions.size() - 1);
            for (int i = currentMessage; i <= messageNumber; i++)
            {
                if (nextMessage() == null)
                { // This only happens if we have already reached the EOF
                    return false;
                }
            }
            return true;
        }
    }

    @Override // Gi� unificato.
    public Page<LogMessage> nextPage(long currentMessage, long pageSize)
    {
        Page<LogMessage> result = findNext("", true, currentMessage, pageSize);
        pageEndPosition = useRegisters ? 0 : currentPosition;
        return result;
    }

    @Override
    public Page<LogMessage> prevPage(long currentMessage, long pageSize)
    {
        if (currentMessage < 0)
        {
            useRegisters = false;
            currentPosition = pageBeginPosition;
            pageEndPosition = currentPosition;
            List<LogMessage> result = new ArrayList<>();
            LogMessage message;
            for (long l = 0; l < pageSize; l++)
            {
                message = convertMessageFromListToLogMessage(prevMessage(), false, "", true);
                if (message == null)
                {
                    return generatePage(result, 1L, 1L, 1L, 1L);
                }
                else
                {
                    result.add(0, message);
                }
            }
            pageBeginPosition = currentPosition;
            return generatePage(result, 1L, 1L, 1L, 1L);
        }
        return pageSize > currentMessage ? findNext("", true, 0, currentMessage - 1) : findPrev("", true, currentMessage - pageSize + 1, pageSize);
    }

    /*
     * 
     * @return the next page starting at a message containing the given expression, which is empty if the end of file was reached when attempting to set the position given by the user
     */
    @Override
    public Page<LogMessage> findNext(String expression, boolean useRegex, long currentMessage, long pageSize)
    {
        if (currentMessage >= 0)
        {
            if (!arePositionsSet((int) currentMessage))
            {
                return generatePage(new ArrayList<LogMessage>(0), 0, 1L, 1L, 1L); //This only happens if the EOF was reached when setting the position given by the user
            }
        }
        else
        {
            useRegisters = false;
            resetOrphans();
        }

        currentPosition = useRegisters ? currentPosition : pageEndPosition;

        List<String> message;
        List<LogMessage> messageList = new ArrayList<>();

        boolean checkExpandRequired = !expression.isEmpty();

        while (true)
        {
            pageBeginPosition = useRegisters ? 0 : currentPosition;
            message = nextMessage();
            if (message == null)
            {
                return generatePage(new ArrayList<LogMessage>(0), 0, 1L, 1L, 1L); //EOF was reached while trying to match the expression and the messages
            }
            else if (isExpressionFound(message, expression, useRegex))
            {
                pageEndPosition = useRegisters ? 0 : currentPosition;
                break;
            }
        }

        messageList.add(convertMessageFromListToLogMessage(message, checkExpandRequired, expression, useRegex));

        for (int counter = 1; counter < pageSize; counter++)
        {
            message = nextMessage();
            if (message == null)
                break; //EOF was reached while populating messageList
            messageList.add(convertMessageFromListToLogMessage(message, checkExpandRequired, expression, useRegex));
        }
        return generatePage(messageList, currentMessage, 1L, 1L, 1L);
    }

    /*
     * 
     * @return the previous page starting at a message containing the given expression, which is empty if no messages are found
     */
    @Override
    public Page<LogMessage> findPrev(String expression, boolean useRegex, long currentMessage, long pageSize)
    {
        if (currentMessage >= 0)
        {
            if (!arePositionsSet((int) currentMessage))
            {
                return generatePage(new ArrayList<LogMessage>(0), 0, 1L, 1L, 1L); //This only happens if the EOF was reached when setting the position given by the user
            }
        }
        else
        {
            useRegisters = false;
            resetOrphans();
        }

        currentPosition = useRegisters ? currentPosition : pageBeginPosition;

        List<String> message;
        List<LogMessage> messageList = new ArrayList<>();
        boolean checkExpandRequired = !expression.isEmpty();

        while (true)
        {
            pageEndPosition = useRegisters ? 0 : currentPosition;
            message = prevMessage();
            if (message == null)
            {
                return generatePage(new ArrayList<LogMessage>(0), 0, 1L, 1L, 1L); //BOF was reached while trying to match the expression and the messages
            }
            else if (isExpressionFound(message, expression, useRegex))
            {
                pageBeginPosition = useRegisters ? 0 : currentPosition;
                break;
            }
        }
        currentLine = useRegisters ? messageInitLines.get(this.currentMessage) : 0;
        currentPosition = useRegisters ? messageInitPositions.get(this.currentMessage) : currentPosition;

        for (int counter = 0; counter < pageSize; counter++)
        {
            message = nextMessage();
            if (message == null)
                break; //EOF was reached while populating messageList
            messageList.add(convertMessageFromListToLogMessage(message, checkExpandRequired, expression, useRegex));
        }
        return generatePage(messageList, currentMessage, 1L, 1L, 1L);
    }

    /*
     * 
     * @return the next filtered page, which is empty if the beginning of file was reached when attempting to set the position given by the user
     */
    @Override
    public Page<LogMessage> filterNext(String expression, boolean useRegex, long currentMessage, long pageSize)
    {

        if (currentMessage >= 0)
        {
            if (!arePositionsSet((int) currentMessage))
            {
                return generatePage(new ArrayList<LogMessage>(0), 0, 1L, 1L, 1L); //This only happens if the EOF was reached when setting the position given by the user
            }
        }
        else
        {
            useRegisters = false;
            resetOrphans();
        }

        currentPosition = useRegisters ? currentPosition : pageEndPosition;

        List<String> message;
        List<LogMessage> messageList = new ArrayList<>();

        boolean checkExpandRequired = !expression.isEmpty();

        populate: for (int counter = 0; counter < pageSize; counter++)
        {
            while (true)
            {
                message = nextMessage();
                if (message == null)
                {
                    if (counter == 0)
                    {
                        return generatePage(new ArrayList<LogMessage>(0), 0, 1L, 1L, 1L); //EOF was reached while trying to match the expression and the messages
                    }
                    else
                    {
                        break populate;
                    }
                }
                else if (isExpressionFound(message, expression, useRegex))
                {
                    if (counter == 0)
                    {
                        pageBeginPosition = useRegisters ? 0 : currentPosition;
                    }
                    break;
                }
            }
            messageList.add(convertMessageFromListToLogMessage(message, checkExpandRequired, expression, useRegex));
        }
        pageEndPosition = useRegisters ? 0 : currentPosition;
        return generatePage(messageList, currentMessage, 1L, 1L, 1L);
    }

    /*
     * 
     * @return the previous filtered page, which is empty if no messages are found
     */
    @Override
    public Page<LogMessage> filterPrev(String expression, boolean useRegex, long currentMessage, long pageSize)
    {

        if (currentMessage >= 0)
        {
            if (!arePositionsSet((int) currentMessage))
            {
                return generatePage(new ArrayList<LogMessage>(0), 0, 1L, 1L, 1L); //This only happens if the EOF was reached when setting the position given by the user
            }
        }
        else
        {
            useRegisters = false;
            resetOrphans();
        }

        currentPosition = useRegisters ? currentPosition : pageBeginPosition;

        List<String> message;
        List<LogMessage> messageList = new ArrayList<>();

        boolean checkExpandRequired = !expression.isEmpty();

        int counter;
        populate: for (counter = 0; counter < pageSize; counter++)
        {
            while (true)
            {
                if (counter == 0)
                {
                    pageEndPosition = useRegisters ? 0 : currentPosition;
                }
                message = prevMessage();
                if (message == null)
                {
                    if (counter == 0)
                    {
                        return generatePage(new ArrayList<LogMessage>(0), 0, 1L, 1L, 1L); //BOF was reached while trying to match the expression and the messages
                    }
                    else
                    {
                        break populate;
                    }
                }
                else if (isExpressionFound(message, expression, useRegex))
                {
                    break;
                }
            }
            messageList.add(convertMessageFromListToLogMessage(message, checkExpandRequired, expression, useRegex));
        }
        Collections.reverse(messageList); //Messages have already been read, but in reverse order: therefore we reorder them correctly here
        pageBeginPosition = useRegisters ? 0 : currentPosition;
        return generatePage(messageList, currentMessage, 1L, 1L, 1L);

    }

    private Page<LogMessage> generatePage(List<LogMessage> messageList, long currentMessage, long currentPage, long totalPages, long totalCount)
    {
        PageImpl<LogMessage> messagePage = new PageImpl<>();

        messagePage.setData(messageList);
        messagePage.setOffset(currentMessage);
        messagePage.setCurrentPage(messageList.size() > 0 ? currentPage : 0L);
        messagePage.setTotalCount(messageList.size() > 0 ? totalCount : 0L);
        messagePage.setPageSize(messageList.size() > 0 ? messageList.size() : 25L);
        messagePage.setTotalPages(totalPages);
        return messagePage;
    }

    public Page<LogMessage> getLastMessages(long size)
    {
        long begin = System.currentTimeMillis();
        while (nextMessage() != null)
        {}
        long end = System.currentTimeMillis();
        double time = ((double) end - begin) / 1000;
        System.out.println(time);
        return prevPage(currentMessage - 2, size);
    }

    @SuppressWarnings("unused")
    private void waitingSystem()
    {
        if (currentMessage % 1000 == 0)
        {
            System.out.println("Reading message number " + currentMessage + ".");
        }
    }

    private void resetOrphans()
    {
        orphanLine = null;
        orphanPosition = -1;
    }

    @Override
    public boolean hasTimestamp()
    {
        return true;
    }

    @Override
    public boolean hasLogLevel()
    {
        return true;
    }

    @Override
    public boolean hasThreadName()
    {
        return true;
    }

    @Override
    public boolean hasLoggerName()
    {
        return true;
    }

    @Override
    public boolean hasMessage()
    {
        return true;
    }

    @SuppressWarnings("unused")
    private void testingRegisters()
    {
        System.out.println("messageInitPositions:");
        for (Long l : messageInitPositions)
        {
            System.out.println(l);
        }
        System.out.println("\nmessageInitLines:");
        for (Integer i : messageInitLines)
        {
            System.out.println(i);
        }
    }

    // Begin of other kind of logic, based only on long positions (currentPosition, pageBeginPosition, pageEndPosition).

    public Page<LogMessage> getLastPage(long size)
    {
        return setCurrentPositionToEndOfFile() ? prevPage(-1, size) : null;
    }

    private boolean setCurrentPositionToEndOfFile()
    {
        try (FileChannel fileChannel = FileChannel.open(file, StandardOpenOption.READ))
        {
            currentPosition = fileChannel.size();
            pageBeginPosition = currentPosition;
            pageEndPosition = currentPosition;
            return true;
        }
        catch (IOException e)
        {
            System.out.println("I/O Exception: " + e);
            return false;
        }
    }

}
