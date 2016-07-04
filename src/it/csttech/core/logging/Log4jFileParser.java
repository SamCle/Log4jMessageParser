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

	private String regex, timestampFormat, filename;
	private Path file;
	private long currentPosition;
	private int currentLine, currentMessage;
	private List<LongHolder> linePositions; //TODO eliminate
	private List<IntHolder> messageLines; //TODO change to messageInitPositions, as List<Long>

	public Log4jFileParser(String filename) {
		// TODO: chiamare l'altro costruttore, con variabili statiche di default.
	}
	
	public Log4jFileParser(String filename, String regex, String timestampFormat) {
		this.filename = filename;
		this.timestampFormat = timestampFormat;
		this.regex = regex;
		file = Paths.get(filename);
		this.currentPosition      = 0L;
		this.currentLine    = 0;
		this.currentMessage = 0;
		this.linePositions = new ArrayList<LongHolder>();
		linePositions.add(new LongHolder(-1L));
		this.messageLines = new ArrayList<IntHolder>();
		messageLines.add(new IntHolder(-1));
	}

	private long getLastLinePositionRead(){
		return linePositions.get(linePositions.size() - 1).value;
	}

	private int getLastMessageLineRead(){
		return messageLines.get(messageLines.size() - 1).value;
	}

	private boolean addLineReadPosition( long value ){
		if ( value > getLastLinePositionRead() ) {
			linePositions.add(new LongHolder(value));
			return true;
		}
		return false;
	}

	private boolean addMessageReadLine( int value ){
		if ( value > getLastMessageLineRead() ) {
			messageLines.add(new IntHolder(value));
			return true;
		}
		return false;
	}	

	public StringBuffer readLine() { // TODO: rendere private.

		addLineReadPosition(currentPosition);

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

	public LogMessage readMessage(){ // TODO: rendere private.
		int startRow = currentLine;
		boolean expandRequired = false;
		StringBuffer line = new StringBuffer();
		StringBuffer firstLine = new StringBuffer();
		long positionSaver = currentPosition;
		List<String> lines = new ArrayList<>();
		do {
			line = readLine();
			if ( line == null ) { // Then it's EOF.
				return null;
			} else { }
		} while ( ! isStartOfMessage(line) ); // Start of message found!
		firstLine = line;

		System.out.println("Reading the first line.");
		Date timestamp = new SimpleDateFormat(timestampFormat).parse(firstLine.toString(), new ParsePosition(0));
		firstLine = firstLine.delete(0, timestampFormat.length());

		System.out.println("Date is ok.");	
		String levelRegex = "(FATAL|ERROR|WARN|INFO|DEBUG|TRACE)";
		Pattern pattern = Pattern.compile(levelRegex);
		Matcher matcher = pattern.matcher(firstLine);
		matcher.find();
		String logLevel = firstLine.substring(matcher.start(), matcher.end());
		firstLine = firstLine.delete(0, matcher.end());

		String threadRegex = "\\[[^\\[\\]]*\\]";
		pattern = Pattern.compile(threadRegex);
		matcher = pattern.matcher(firstLine);
		matcher.find();
		String threadName = firstLine.substring(matcher.start(), matcher.end());
		threadName = threadName.substring(1, threadName.length() - 1);
		firstLine = firstLine.delete(0, matcher.end());

		String endLoggerNameRegex = " - ";
		pattern = Pattern.compile(endLoggerNameRegex);
		matcher = pattern.matcher(firstLine);
		matcher.find();
		String loggerName = firstLine.substring(1, matcher.end() - 3);
		firstLine = firstLine.delete(0, matcher.end());

		lines.add(firstLine.toString());

		addMessageReadLine(currentLine);
		positionSaver = currentPosition;

		while (true) {
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

		currentMessage++;
		System.out.println("Message: " + currentMessage + "; line: " + getLastMessageLineRead() + "."); // DEBUG: messageNumber getLastMessageLineRead in readMessage.
		return new LogMessage(startRow, timestamp, logLevel, threadName, loggerName, lines, expandRequired);
	}

	private boolean isStartOfMessage(StringBuffer line) {
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(line);
		return line == null ? false : matcher.find();
	}

	private boolean isExpressionFound(StringBuffer message, String expression, boolean useRegex) {
		Pattern pattern;
		if( useRegex ) {
			pattern = Pattern.compile(expression);
			Matcher matcher = pattern.matcher(message);
			return message == null ? false : matcher.find();
		} else {
			return message.indexOf(expression) != -1;
		}
	}
	
	private StringBuffer convertLogMessageToStringBuffer(LogMessage logMessage) {
		SimpleDateFormat sdf = new SimpleDateFormat(timestampFormat);
		StringBuffer message = new StringBuffer();
		message.append(logMessage.getStartRow() + ": " + sdf.format(logMessage.getTimestamp()) + " [" + logMessage.getLogLevel() + "] - " + logMessage.getLoggerName() + " - " + logMessage.getMessage());

		for(String line : logMessage.getFullMessage()) {
			message.append(line); //TODO
		}
			
		return message;
	}
	
	public LogMessage prevMessage(){
		currentMessage--;
		if (currentMessage <= 0) {
			currentMessage++;
			return null;
		}
		currentLine = messageLines.get(currentMessage).value;
		setPosition(linePositions.get(currentLine).value);
		LogMessage message = readMessage();
		currentMessage--;
		return message;
	}

	public void testingRegisters(){
		// DEBUG: testingRegisters.
		System.out.println("line:");
		for (LongHolder l : linePositions) {
			System.out.println(l.value);
		}
		System.out.println("message:");
		for (IntHolder i : messageLines) {
			System.out.println(i.value);
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
				messageList.add(readMessage());
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
