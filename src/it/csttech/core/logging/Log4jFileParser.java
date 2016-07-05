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
	
	private String regex, timestampFormat;
	private Path file;
	private long currentPosition; 
	private long beginningOfMessages; //stores the actual beginning of messages in the input file. Most likely it will be 0, unless the user provides a file that has been cut without care
	private int currentLine, currentMessage;
	private List<Long> messageInitPositions;

	public Log4jFileParser(String filename) {
		this(filename, STANDARD_REGEX, STANDARD_TIMESTAMP);
	}
	
	public Log4jFileParser(String filename, String regex, String timestampFormat) {
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

	/**
	 * This method reads a line from the file. It starts at the currentPosition and continues to read 
	 * until it finds a '\n', '\r' or a suitable (Windows) combination of the two.
	 * 
	 * @return the line as a StringBuffer, or null if at the currentPosition we are at the end of 
	 * file (EOF), in the spirit of the readLine method found in the BufferedReader class.
	 * 
	 * WARNING: when calling this method, one should be sure that no IOExceptions could occur.
	 */
	
	public StringBuffer readLine() { // TODO: rendere private.
		ByteBuffer byteBuffer;
		StringBuffer line;

		try (FileChannel fileChannel = FileChannel.open(file, StandardOpenOption.READ)) {
			fileChannel.position(currentPosition);
			byteBuffer = ByteBuffer.allocate(1);
			line = new StringBuffer();
			int currentChar = -1;

			//System.out.println("position: " + currentPosition); // DEBUG: position in readLine. TODO remove

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
			//System.out.println("Line: " + currentLine /*+ "; length: " + (line == null ? -1 : line.length())*/ + "."); // DEBUG: lineNumber in readLine. TODO remove
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
		
		//System.out.println("Reading the first line."); //TODO remove
		Date timestamp = new SimpleDateFormat(timestampFormat).parse(firstLine.toString(), new ParsePosition(0));
		firstLine = firstLine.delete(0, timestampFormat.length());
		
		//System.out.println("Date is ok.");	//TODO remove
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

		//System.out.println("Message: " + currentMessage + "; line: " + startRow + "."); //TODO: remove

		return new LogMessage(startRow, timestamp, logLevel, threadName, loggerName, lines, expandRequired);
	}
	
	//TODO: remove this method when class is completed
	public LogMessage readMessage0() {
		return convertMessageFromListToLogMessage(readMessage());
	}
	
	/**
	 * This method reads a full Log4j message, by calling repeteadly the readLine method.
	 * 
	 * @return the full message as a List<String>, or null iff the EOF was already reached.
	 */
	private List<String> readMessage() {
		int startRow = currentLine + 1; //TODO: ...
		StringBuffer line = new StringBuffer();
		long positionSaver = currentPosition; //TODO: maybe it's not needed
		List<String> lines = new ArrayList<>();
		
		addMessageReadInitPosition(currentPosition);
		// positionSaver = currentPosition;
		
		do {
			positionSaver = currentPosition; //TODO maybe it's not needed
			line = readLine();
			if (line == null) {
				break;
			} else {
				lines.add(line.toString());
			}
		} while ( ! isStartOfMessage(line) );
		
		if( lines.isEmpty() ) {
			return null;
		} else {
			lines.add(0, String.valueOf(startRow));
			currentMessage++;
			return lines;
		}
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
					if(matcher.find()) {
						return true;
					}
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
	
	//TODO: remove this method when class is completed
	public LogMessage prevMessage0() {
		List<String> message = prevMessage();
		
		return message == null ? null : convertMessageFromListToLogMessage(prevMessage());
	}
	
	//TODO modify currentLine value
	private List<String> prevMessage() {
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

	//TODO: remove this method when class is completed
	public void testingRegisters() {
		// DEBUG: testingRegisters. 
		System.out.println("Begin of Message position:");
		for (Long l : messageInitPositions) {
			System.out.println(l);
		}

	}

	private boolean positionsAreSet(int messageNumber) {
		//Next three lines reposition at BOF
		currentPosition = 0L; 
		currentLine = 0;
		currentMessage = 0;
		//TODO exploit the MessageInitPositions vectors to set directly the position if it has already been passed before
		for (int i = 0 ; i < messageNumber; i++) {
			if(readMessage() == null) {
				//This only happens if we have already reached the EOF
				return false;
			}
		}
		return true;
	}

	@Override
	public Page<LogMessage> nextPage(long currentMessage, long pageSize) {
		return findNext("", true, currentMessage, pageSize);
	}

	@Override
	public Page<LogMessage> prevPage(long currentMessage, long pageSize) {
		return findNext("", true, currentMessage - pageSize - 1, pageSize);
	}

	/**
	 * 
	 * @return the next page, unless  
	 */
	@Override
	public Page<LogMessage> findNext(String expression, boolean useRegex, long currentMessage, long pageSize) {
		if (positionsAreSet((int) currentMessage)) { // se ad esempio � 0, non ne salta. Current message � adesso l'ultimo non letto.
			List<LogMessage> messageList = new ArrayList<>((int) pageSize);
			PageImpl<LogMessage> messagePage = new PageImpl<>();
			
			List<String> message;
			
			while (true) {
				message = readMessage();
				if(message == null) {
					return null; //EOF was reached while trying to match the expression and the messages
				} else if (isExpressionFound(message, expression, useRegex)) {
					break;
				}
			}
			
			for(int counter = 0; counter < pageSize; counter++) {
				messageList.add(convertMessageFromListToLogMessage(message));
				message = readMessage();
				if (message == null) break; //EOF was reached while populating messageList
			}

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
	public Page<LogMessage> findPrev(String expression, boolean useRegex, long currentMessage, long pageSize) {
		List<String> message;
		do {
			message = prevMessage();
			currentMessage--;
		} while ( !isExpressionFound(message, expression, useRegex));

		currentMessage--;
		return nextPage(currentMessage, pageSize);
	}

	@Override
	public Page<LogMessage> filterNext(String expression, boolean useRegex, long currentMessage, long pageSize) {
		//TODO change
		if (positionsAreSet((int) currentMessage)) { // se ad esempio � 0, non ne salta. Current message � adesso l'ultimo non letto.
			List<LogMessage> messageList = new ArrayList<>((int) pageSize);
			PageImpl<LogMessage> messagePage = new PageImpl<>();
			
			List<String> message;
			
			populate: 
			for(int counter = 0; counter < pageSize; counter ++) {
				while (true) {
					message = readMessage();
					if(message == null) {
						if(counter == 0) {
							return null; //EOF was reached while trying to match the expression and the messages
						} else {
							break populate;
						}
					} else if (isExpressionFound(message, expression, useRegex)) {
						break;
					}
				}
				messageList.add(convertMessageFromListToLogMessage(message));
			}

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
