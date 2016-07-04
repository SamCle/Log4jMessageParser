package it.csttech.core.logging;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.omg.CORBA.IntHolder;
import org.omg.CORBA.LongHolder;

import it.csttech.core.data.Page;
import it.csttech.core.data.PageImpl;

public class Log4jFileParser implements LogFileParser {

	private static final String STANDARD_REGEX = "^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2},[0-9]{3}";
	private static final String STANDARD_TIMESTAMP = "yyyy-MM-dd HH:mm:ss,SSS";
	
	private String regex, timestampFormat, filename;
	private Path file;
	private long currentPosition; 
	private long beginningOfMessages; //stores the actual beginning of messages in the input file. Most likely it will be 0, unless the user provides a file that has been cut without care
	private int currentLine, currentMessage;
	private List<Long> messageInitPositions;

	public Log4jFileParser(String filename) {
		this(filename, STANDARD_REGEX, STANDARD_TIMESTAMP);
	}
	
	public Log4jFileParser(String filename, String regex, String timestampFormat) {
		this.filename = filename;
		this.timestampFormat = timestampFormat;
		this.regex = regex;
		file = Paths.get(filename);
	
		this.currentPosition = 0L;
		long positionSaver;
		StringBuffer line = new StringBuffer();
		do {
			positionSaver = this.currentPosition;
			line = readLine();
			if ( line == null ) { // Then it's EOF.
				throw new IllegalArgumentException("This file does not contain any Log4j messages");
			} else { }
		} while ( ! isStartOfMessage(line) ); // Start of message found! Update of currentPosition is done in the readLine method itself
		
		this.currentPosition = positionSaver;
		this.beginningOfMessages = positionSaver;
		
		this.currentLine = 0;
		this.currentMessage = 0;
		this.messageInitPositions = new ArrayList<Long>();
		messageInitPositions.add(new Long(-1));
	}


	private Long getLastMessageInitPositionRead(){
		return messageInitPositions.get(messageInitPositions.size() - 1);
	}

	private boolean addMessageReadInitPosition( Long value ){
		if ( value > getLastMessageInitPositionRead() ) {
			messageInitPositions.add(value);
			return true;
		}
		return false;
	}	

	public StringBuffer readLine() { // TODO: rendere private.
		ByteBuffer byteBuffer;
		StringBuffer line;

		try (FileChannel fileChannel = FileChannel.open(file, StandardOpenOption.READ)) {
			fileChannel.position(currentPosition);
			byteBuffer = ByteBuffer.allocate(1);
			line = new StringBuffer();
			int currentChar = -1;

			System.out.println("position: " + currentPosition); // DEBUG: position in readLine.

			while(fileChannel.read(byteBuffer) != -1) {
				byteBuffer.rewind(); 	// The position is set to zero and the mark is discarded.
				currentChar = byteBuffer.get(byteBuffer.position());
				line.append((char) currentChar);				
				if(currentChar == '\n' || currentChar == '\r') {
					break;
				} else { }
			}

			if(currentChar == -1) { //Then it's the end of file; return null
				return null;
			}

			if(currentChar == '\r') {
				fileChannel.read(byteBuffer);
				byteBuffer.rewind();
				char nextChar = (char) byteBuffer.get(byteBuffer.position());
				if(nextChar == '\n') { // Then we found a '\r' followed by a '\n'
					//                    we ignore it and increase the position by one
					line.append('\n');
				} else {               // Then we had only found a '\r' and the next character is not '\n';
					//                    Return to the previous position for the next read
					fileChannel.position(fileChannel.position() - 1);
				}
			}

			setPosition( fileChannel.position() );  //update this object's position with the fileChannel position
		} catch (IOException e) { // Never(!) happens: main method should avoid any errors
			System.out.println("I/O Exception: " + e);
			return null;
		}
		if (line != null) {
			currentLine++;
			System.out.println("Line: " + currentLine /*+ "; length: " + (line == null ? -1 : line.length())*/ + "."); // DEBUG: lineNumber in readLine. 
			//System.out.println( line == null ? -1 : line.length() );
		}
		return line;
	}

	private void setPosition(long position) {
		this.currentPosition = position;
	}

	
	private LogMessage convertMessageFromListToLogMessage(List<String> message) {
		int startRow = Integer.decode(message.get(0));
		List<String> lines = new ArrayList<>();
		
		StringBuffer firstLine = new StringBuffer(message.get(1));
		
		System.out.println("Reading the first line."); //TODO remove
		Date timestamp = new SimpleDateFormat(timestampFormat).parse(firstLine.toString(), new ParsePosition(0));
		firstLine = firstLine.delete(0, timestampFormat.length());
		
		System.out.println("Date is ok.");	//TODO remove
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
		
		if(message.size() > 1) {
			for(int i = 2; i < message.size(); i++) {
				lines.add(message.get(i));
			}
		}
		
		boolean expandRequired = false; //TODO actually deal with this properly

		System.out.println("Message: " + currentMessage + "; line: " + startRow + "."); //TODO: remove

		return new LogMessage(startRow, timestamp, logLevel, threadName, loggerName, lines, expandRequired);
	}
	
	public LogMessage readMessage0() {
		return convertMessageFromListToLogMessage(readMessage());
	}
	
	private List<String> readMessage() {
		int startRow = currentLine;
		StringBuffer line = new StringBuffer();
		long positionSaver = currentPosition;
		List<String> lines = new ArrayList<>();
		
		addMessageReadInitPosition(currentPosition);
		// positionSaver = currentPosition;

		lines.add(String.valueOf(startRow));
		
		do {
			positionSaver = currentPosition;
			line = readLine();
			if (line == null) {
				break;
			} else {
				lines.add(line.toString());
			}
		} while ( ! isStartOfMessage(line) );
		
		
/*		while (true) {
			line = readLine();
			if ( line == null ) {
				break;
			} else if ( ! isStartOfMessage(line) ) {
				lines.add(line.toString());
				positionSaver = currentPosition;
			} else {
				setPosition( positionSaver );
				currentLine--;
				break;
			}
		}
*/
		currentMessage++;
		return lines;
	}

	private boolean isStartOfMessage(StringBuffer line) {
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(line);
		return line == null ? false : matcher.find();
	}

	private boolean isExpressionFound(List<String> message, String expression, boolean useRegex) {
		Pattern pattern;
		if( useRegex ) {
			pattern = Pattern.compile(expression);
			Matcher matcher;
			for(String line : message) {
				matcher = pattern.matcher(line);
				if(line == null) {
					continue;
				} else {
					if(matcher.find()) return true;
				}
			}
			return false;
		} else {
			for(String line : message) {
				if(line.indexOf(expression) == - 1) {
					continue;
				} else {
					return true;
				}
			}
			return false;
		}
	}
	
	public LogMessage prevMessage0() {
		return convertMessageFromListToLogMessage(prevMessage());
	}
	
	private List<String> prevMessage(){
		currentMessage--;
		if (currentMessage <= 0) {
			currentMessage++;
			return null;
		}
		setPosition(messageInitPositions.get(currentMessage));
		List<String> message = readMessage();
		currentMessage--;
		return message;
	}

	public void testingRegisters(){
		// DEBUG: testingRegisters.
		System.out.println("Begin of Message position:");
		for (Long l : messageInitPositions) {
			System.out.println(l);
		}

	}

	private boolean offset(int messageNumber){
		for (int i = 0 ; i < messageNumber; i++) {
			if(readMessage() == null){
				return false;
			}
		}
		return true;
	}
	
	// FIXME: non c'� controllo sui readMessage null!
	
	private String buildFirstLine(LogMessage message){
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.sss");
		return sdf.format(message.getTimestamp()) + " [" +message.getLogLevel() + "] - " + message.getMessage();
	} 
	
	@SuppressWarnings("unused")
	private boolean filter(LogMessage message, String regex){ // FIXME: deve diventare parte del nextMessage.  
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(buildFirstLine(message));
		if (matcher.find()) {
			return true;
		}
		return false;
	}

	@Override
	public Page<LogMessage> nextPage(long currentMessage, long pageSize) {
		if ( offset((int) currentMessage) ) { // se ad esempio � 0, non ne salta. Current message � adesso l'ultimo non letto.
			List<LogMessage> messageList = new ArrayList<>((int) pageSize);
			for (long l = 0; l < pageSize; l++) {
				messageList.add(convertMessageFromListToLogMessage(readMessage()));
			}
			PageImpl<LogMessage> messagePage = new PageImpl<>();
			messagePage.setData(messageList);
			messagePage.setOffset(currentMessage);
			messagePage.setCurrentPage(1L);
			messagePage.setPageSize(pageSize);
			messagePage.setTotalPages(1L);
			messagePage.setTotalCount(0L); // TODO: chiarire questo campo.
			return messagePage;
		} else {
			return null;
		}
	}

	@Override
	public Page<LogMessage> prevPage(long currentMessage, long pageSize) {
		return nextPage(currentMessage - pageSize, pageSize);
	}

	@Override
	public Page<LogMessage> findNext(String expression, boolean useRegex, long currentMessage, long pageSize) {
		Pattern pattern;
		String regex = expression;
		if ( ! useRegex ) {
			pattern = Pattern.compile(expression, Pattern.LITERAL);
			regex = pattern.pattern();
		}
		Log4jFileParser otherParser = new Log4jFileParser(filename, regex, timestampFormat);
		return otherParser.nextPage(currentMessage, pageSize);
	}

	@Override
	public Page<LogMessage> findPrev(String expression, boolean useRegex, long currentMessage, long pageSize) {
		return findNext(expression, useRegex, currentMessage - pageSize, pageSize);
	}

	@Override
	public Page<LogMessage> filterNext(String expression, boolean useRegex, long currentMessage, long pageSize) {
		
		// FIXME: Auto-generated method stub.
		return null;
	}

	@Override
	public Page<LogMessage> filterPrev(String expression, boolean useRegex, long currentMessage, long pageSize) {
		// FIXME: Auto-generated method stub.
		return null;
	}

	@Override
	public boolean hasTimestamp() {
		return true;
	}

	@Override
	public boolean hasLogLevel() {
		return true;
	}

	@Override
	public boolean hasThreadName() {
		return true;
	}

	@Override
	public boolean hasLoggerName() {
		return true;
	}

	@Override
	public boolean hasMessage() {
		return true;
	}

}
