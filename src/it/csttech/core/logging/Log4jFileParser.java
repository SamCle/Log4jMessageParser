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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import it.csttech.core.data.Page;
import it.csttech.core.data.PageImpl;

public class Log4jFileParser implements LogFileParser {

	private static final String STANDARD_REGEX = "^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2},[0-9]{3}";
	private static final String STANDARD_TIMESTAMP = "yyyy-MM-dd HH:mm:ss,SSS";

	private String regex, timestampFormat;
	private Path file;
	private long currentPosition; 
	private long beginningOfMessages; //stores the actual beginning of messages in the input file (as character). Most likely it will be 0, unless the user provides a file that has been cut without care
	private int currentLine, currentMessage;
	private int startingLineOfFirstMessage; //stores the actual beginning of messages in the input file (as line). Most likely it will be 0, unless the user provides a file that has been cut without care
	private List<Long> messageInitPositions;
	private List<Integer> messageInitLines;

	public Log4jFileParser(String filename) {
		this(filename, STANDARD_REGEX, STANDARD_TIMESTAMP);
	}

	public Log4jFileParser(String filename, String regex, String timestampFormat) {
		this.timestampFormat = timestampFormat;
		this.regex = regex;
		file = Paths.get(filename);

		currentPosition = 0L;
		long positionSaver;
		StringBuffer line = new StringBuffer();
		do {
			positionSaver = currentPosition;
			line = readLine();
			if ( line == null ) { // Then it's EOF.
				throw new IllegalArgumentException("This file does not contain any Log4j messages");
			} else { }
		} while ( ! isStartOfMessage(line) ); // Start of message found!

		currentPosition = positionSaver;
		startingLineOfFirstMessage = currentLine; 
		beginningOfMessages = positionSaver;
		currentMessage = 0;
		messageInitPositions = new ArrayList<Long>();
		messageInitPositions.add(new Long(-1));
		messageInitLines = new ArrayList<Integer>();
		messageInitLines.add(new Integer(-1));
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

	private StringBuffer readLine() {
		ByteBuffer byteBuffer;
		StringBuffer line;

		try (FileChannel fileChannel = FileChannel.open(file, StandardOpenOption.READ)) {
			fileChannel.position(currentPosition);
			byteBuffer = ByteBuffer.allocate(1);
			line = new StringBuffer();
			int currentChar = -1;

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

			currentPosition = fileChannel.position();  //update this object's position with the fileChannel position
		} catch (IOException e) { // Never(!) happens: main method should avoid any errors
			System.out.println("I/O Exception: " + e);
			return null;
		}
		if (line != null) {
			currentLine++;
		}
		return line;
	}

	/**
	 * This method reads a full Log4j message, by calling repeatedly the readLine method. 
	 * We always assume we are at the beginning of a message.
	 * 
	 * @return the full message as a List<String>, or null iff the EOF was already reached.
	 */
	private List<String> nextMessage() {
		currentMessage++;
		int startRow = currentLine;
		StringBuffer line = new StringBuffer();
		long positionSaver;
		List<String> lines = new ArrayList<>();

		addMessageReadInitPosition(currentPosition);
		addMessageReadInitLine(currentLine);

		line = readLine();
		if (line == null) {
			return null; // It happens only at EOF.
		} else {
			lines.add(String.valueOf(startRow));
			lines.add(line.toString());
			positionSaver = currentPosition;
			while (true) {
				line = readLine();
				if ( line == null ) {
					break;
				} else if ( ! isStartOfMessage(line) ) {
					lines.add(line.toString());
					positionSaver = currentPosition;
				} else {
					currentPosition = positionSaver;
					currentLine--;
					break;
				}
			}
			return lines;
		}

	}

	/**
	 * This method reads a full Log4j message, by calling repeatedly the readLine method. 
	 * We always assume we are at the beginning of a message.
	 * 
	 * @return the full message as a List<String>, or null iff the BOF was already reached.
	 */
	private List<String> prevMessage() {
		currentMessage--;
		if (currentMessage <= 0) {
			currentMessage++;
			return null;
		}
		currentLine = messageInitLines.get(currentMessage);
		currentPosition = messageInitPositions.get(currentMessage); 
		List<String> message = nextMessage();
		currentMessage--;
		return message;
	}

	private LogMessage convertMessageFromListToLogMessage(List<String> message) {
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

		if(message.size() > 1) {
			for(int i = 2; i < message.size(); i++) {
				lines.add(message.get(i));
			}
		}

		boolean expandRequired = false; //TODO actually deal with this properly

		return new LogMessage(startRow, timestamp, logLevel, threadName, loggerName, lines, expandRequired);
	}

	private Long getLastMessageInitPositionRead() {
		return messageInitPositions.get(messageInitPositions.size() - 1);
	}

	private boolean addMessageReadInitPosition(Long value) {
		if ( value > getLastMessageInitPositionRead() ) {
			messageInitPositions.add(value);
			return true;
		}
		return false;
	}

	private Integer getLastMessageInitLineRead() {
		return messageInitLines.get(messageInitLines.size() - 1);
	}

	private boolean addMessageReadInitLine(Integer value) {
		if ( value > getLastMessageInitLineRead() ) {
			messageInitLines.add(value);
			return true;
		}
		return false;
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
			for(String line : message.subList(1, message.size())) { // first element of message is just the starting row number and will be ignored.
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
			for(String line : message.subList(1, message.size())) { // first element of message is just the starting row number and will be ignored.
				if(line.indexOf(expression) == - 1) {
					continue;
				} else {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * A boolean which gives information about whether or not the position given by the user
	 * is set in order to start reading messages from such position.
	 * 
	 * @param messageNumber the message number provided by the user
	 * @return true if all positions were successfully set, false iff the end of file was reached
	 */
	private boolean arePositionsSet(int messageNumber) {
		if(messageNumber == 0 ) {
			currentPosition = beginningOfMessages; 
			currentLine = startingLineOfFirstMessage;
			currentMessage = 0;
			return true;
		} else if (messageNumber < messageInitPositions.size()) {
			//This happens if we have already stored all position info about the message referenced by the user; we exploit this
			currentPosition = messageInitPositions.get(messageNumber);
			currentLine = messageInitLines.get(messageNumber);
			currentMessage = messageNumber; 
			return true;
		} else {
			//Then we need to navigate a section of the file
			if(messageInitLines.size() == 1) { //Then we need to navigate the whole file; next three lines reposition at BOF
 				currentPosition = beginningOfMessages; 
				currentLine = startingLineOfFirstMessage;
				currentMessage = 0;
			} else { //Then we already navigated a previous section of the file; next three lines reposition at the last message read
				currentPosition = messageInitPositions.get(messageInitPositions.size()-1);
				currentLine = messageInitLines.get(messageInitPositions.size()-1);
				currentMessage = messageInitPositions.size()-2; 
			}
				
			for (int i = currentMessage; i < messageNumber; i++) {
				if(nextMessage() == null) { // This only happens if we have already reached the EOF
					return false;
				}
			}
			return true;
		}
	}

	
	@Override
	public Page<LogMessage> nextPage(long currentMessage, long pageSize) {
		return findNext("", true, currentMessage, pageSize);
	}

	@Override
	public Page<LogMessage> prevPage(long currentMessage, long pageSize) {
		return findPrev("", true, currentMessage - pageSize + 1, pageSize);
	}

	/*
	 * 
	 * @return the next page starting at a message containing the given expression, unless the end of file was reached when attempting to set the position given by the user
	 */
	@Override
	public Page<LogMessage> findNext(String expression, boolean useRegex, long currentMessage, long pageSize) {
		if (arePositionsSet((int) currentMessage)) { //We set all positions to the one passed by the user
			List<String> message;
			List<LogMessage> messageList = new ArrayList<>();

			while (true) {
				message = nextMessage();
				if(message == null) {
					return null; //EOF was reached while trying to match the expression and the messages
				} else if (isExpressionFound(message, expression, useRegex)) {
					break;
				}
			}
			currentLine = messageInitLines.get(this.currentMessage);
			currentPosition = messageInitPositions.get(this.currentMessage);

			for(int counter = 0; counter < pageSize; counter++) {
				message = nextMessage();
				if (message == null) break; //EOF was reached while populating messageList
				messageList.add(convertMessageFromListToLogMessage(message));
			}

			return generatePage(messageList, currentMessage, 1L, 1L, 0L); //FIXME: dummy values are used here
		} else {
			return null; //This only happens if the EOF was reached when setting the position given by the user
		}
	}

	/*
	 * 
	 * @return the previous page starting at a message containing the given expression, unless... TODO: check
	 */
	@Override
	public Page<LogMessage> findPrev(String expression, boolean useRegex, long currentMessage, long pageSize) {
		if (arePositionsSet((int) currentMessage)) { //We set all positions to the one passed by the user
			List<String> message;
			List<LogMessage> messageList = new ArrayList<>();

			while (true) {
				message = prevMessage();
				if(message == null) {
					return null; //BOF was reached while trying to match the expression and the messages
				} else if (isExpressionFound(message, expression, useRegex)) {
					break;
				}
			}
			currentLine = messageInitLines.get(this.currentMessage);
			currentPosition = messageInitPositions.get(this.currentMessage);

			for(int counter = 0; counter < pageSize; counter++) {
				message = nextMessage();
				if (message == null) break; //EOF was reached while populating messageList
				messageList.add(convertMessageFromListToLogMessage(message));
			}

			return generatePage(messageList, currentMessage, 1L, 1L, 0L); //FIXME: dummy values are used here
		} else {
			return null; //TODO: check whether this ever happens; note that arePositionsSet returns null iff the end of file was reached when attempting to set the position given by the user
		}
	}

	/*
	 * 
	 * @return the next filtered page, unless the beginning of file was reached when attempting to set the position given by the user
	 */
	@Override
	public Page<LogMessage> filterNext(String expression, boolean useRegex, long currentMessage, long pageSize) {
		if (arePositionsSet((int) currentMessage)) { // We set all positions to the one passed by the user
			List<String> message;
			List<LogMessage> messageList = new ArrayList<>();

			populate: 
				for(int counter = 0; counter < pageSize; counter ++) {
					while (true) {
						message = nextMessage();
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

			return generatePage(messageList, currentMessage, 1L, 1L, 0L); //FIXME: dummy values are used here
		} else {
			return null;
		}
	}

	/*
	 * 
	 * @return the previous filtered page, unless... TODO: check
	 */
	@Override
	public Page<LogMessage> filterPrev(String expression, boolean useRegex, long currentMessage, long pageSize) {
		if (arePositionsSet((int) currentMessage)) { //We set all positions to the one passed by the user
			List<String> message;
			List<LogMessage> messageList = new ArrayList<>();

			int counter;
			populate: 
				for(counter = 0; counter < pageSize; counter ++) {
					while (true) {
						message = prevMessage();
						if(message == null) {
							if(counter == 0) {
								return null; //BOF was reached while trying to match the expression and the messages
							} else {
								break populate;
							}
						} else if (isExpressionFound(message, expression, useRegex)) {
							break;
						}
					}
					messageList.add(convertMessageFromListToLogMessage(message));
				}
			Collections.reverse(messageList); //Messages have already been read, but in reverse order: therefore we reorder them correctly here

			return generatePage(messageList, currentMessage, 1L, 1L, 0L); //FIXME: dummy values are used here
		} else {
			return null;
		}
	}

	private Page<LogMessage> generatePage(List<LogMessage> messageList, long currentMessage, long currentPage, long totalPages, long totalCount) {
		PageImpl<LogMessage> messagePage = new PageImpl<>();

		messagePage.setData(messageList);
		messagePage.setOffset(currentMessage);
		messagePage.setCurrentPage(1L);
		messagePage.setPageSize(messageList.size());
		messagePage.setTotalPages(1L);
		messagePage.setTotalCount(0L); // TODO: chiarire questo campo.
		return messagePage;
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
