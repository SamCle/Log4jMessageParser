package it.csttech.core;

import java.text.SimpleDateFormat;
import java.util.Scanner;

import it.csttech.core.data.Page;
import it.csttech.core.logging.DummyLogFileParser;
import it.csttech.core.logging.LogFileParserImpl;
import it.csttech.core.logging.LogFileParser;
import it.csttech.core.logging.LogMessage;

public class LogParser {

	private static String REGEX;
	private static String FILE_NAME;
	private static LogFileParser parser;
	private static LogFileParserImpl otherParser;
	private static String timestampFormat;
	@SuppressWarnings("unused")
	private static Scanner scanner;

	public static void main(String[] args) {
		scanner = new Scanner(System.in);
		
		String testingMode = "readpages"; // hardcoded
		
		REGEX = "^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2},[0-9]{3}"; // hardcoded
		timestampFormat = "yyyy-MM-dd HH:mm:ss,SSS";
		//                        2016-05-16 11:08:29,492
		FILE_NAME = "input/input.log"; // hardcoded
		otherParser = new LogFileParserImpl(FILE_NAME,REGEX,timestampFormat, null); // hardcoded
		switch (testingMode.toLowerCase()) {
		case "lastmessage":
			lastmessageMethod(32);
			break;
/*		case "readline":
			readlineMethod(10); // hardcoded
			break;
		case "readmessage":
			readmessageMethod(5); // hardcoded
			otherParser.testingRegisters();
			break;
		case "readcommanded":
			readcommandedMethod();
			break;
		case "readrandom":
			readrandomMethod(100, 10); // hardcoded
			otherParser.testingRegisters();
			break;
			*/			
		case "lastpage":
			lastpageMethod();
			break;
		case "readpages":
			readpagesMethod();
			break;
		case "readsingle":
			printPage(otherParser.nextPage(0, 25)); // hardcoded
			break;
		default:
			exmain();
			break;
		}
	}

/*	private static void readlineMethod(int arg){
		for (int i = 0; i < arg; i++) {
			System.out.println(otherParser.readLine());
		}
	} 
*/
/*	private static void readmessageMethod(int arg){
		for (int i = 0; i < arg; i++) {
			printMessage(otherParser.readMessage0());
		}
	}
*/
/*	private static void readrandomMethod(int offset, int size){
		if (offSet(offset)) {
			return;
		}
		Random random = new Random(System.nanoTime());
		LogMessage message = null;
		for (int i = 0 ; i < size && message != null; i++) {
			printMessage(message);
			boolean direction = random.nextBoolean();
			message = basicOperation(direction?"1":"-1");
			System.out.println(direction?"Avanti":"Indietro");
		}
		return;
	}
*/
/*	private static void readcommandedMethod(){
		String newInput;
		String output;
		for (;;) {
			System.out.print("\nInsert -1 to get the previous message, 1 to get the next one, or 0 to exit the program: ");
			newInput = new String(scanner.next());
			while( ! (newInput.equals("-1") || newInput.equals("0") || newInput.equals("1")) ){
				System.out.print("\nInsert -1 to get the previous message, 1 to get the next one, or 0 to exit the program: ");
				newInput = scanner.next();
			}
			if (newInput.equals("0")){
				return;
			}
			output = basicOperation(newInput);
			if (output == null) {
				System.out.println("No message available.");
				return;
			}
			System.out.println(output);
		}
	}*/

	private static void readpagesMethod() {
		otherParser = new LogFileParserImpl(FILE_NAME,REGEX,timestampFormat, 0L);
		System.out.println("Printing next page (of size 5), when current position is 0:");
		printPage(otherParser.nextPage(0, 5));
		System.out.println("\nPrinting next page (of size 5), when current position is 100:");
		printPage(otherParser.nextPage(100, 5));
		System.out.println("\nPrinting previous page (of size 5), when current position is 50:");		
		printPage(otherParser.prevPage(50, 5));
		System.out.println("\nPrinting next page (of size 5), when current position is 5, against the string literal '2016-05':");
		printPage(otherParser.findNext("2016-05", false, 5, 5));
		System.out.println("\nPrinting previous page (of size 5), when current position is 50, against the string literal '2016-05':");		
		printPage(otherParser.findPrev("2016-05", false, 50, 5));
		System.out.println("\nPrinting next filtered page (of size 5), when current position is 10, against the string literal 'INFO':");		
		printPage(otherParser.filterNext("INFO", false, 10, 5));
		System.out.println("\nPrinting previous filtered page (of size 5), when current position is 900, against the string literal 'DEBUG':");		
		printPage(otherParser.filterPrev("DEBUG", false, 900, 5));
		System.out.println("\nPrinting previous page (of size 5), when current position is 3:");		
		printPage(otherParser.prevPage(3, 5));
		System.out.println("\nPrinting previous page (of size 5), when current position is 3, agains the string literal '2016':");		
		printPage(otherParser.findPrev("2016", false, 3, 5));
		System.out.println("\nPrinting previous filtered page (of size 5), when current position is 3, agains the string literal '2016':");		
		printPage(otherParser.filterPrev("2016", false, 7, 10));
		System.out.println("\nPrinting next filtered page (of size 5), when current position is 10, against the string literal 'INFO':");
		printPage(otherParser.filterNext("INFO", false, 10, 5));

	}

	private static void lastpageMethod(){
		otherParser = new LogFileParserImpl(FILE_NAME,REGEX,timestampFormat, null);
		System.out.println("lastPage");
		printPage(otherParser.getLastPage(5));
		System.out.println(otherParser.pageBeginPosition + " - " + otherParser.pageEndPosition);
		System.out.println("");
		System.out.println("prevPage");
		printPage(otherParser.prevPage(-1, 5));
		System.out.println(otherParser.pageBeginPosition + " - " + otherParser.pageEndPosition);
		System.out.println("");
		System.out.println("nextPage");
		printPage(otherParser.nextPage(-1, 5));
		System.out.println(otherParser.pageBeginPosition + " - " + otherParser.pageEndPosition);
		System.out.println("");
		System.out.println("findPrev");
		printPage(otherParser.findPrev("2016-05", false, -1, 5));
		System.out.println(otherParser.pageBeginPosition + " - " + otherParser.pageEndPosition);
		System.out.println("");
		System.out.println("findNext");
		printPage(otherParser.findNext("2016-06-09", false, -1, 5));
		System.out.println(otherParser.pageBeginPosition + " - " + otherParser.pageEndPosition);
		System.out.println("");
		System.out.println("filterPrev");
		printPage(otherParser.filterPrev("INFO", false, -1, 5));
		System.out.println(otherParser.pageBeginPosition + " - " + otherParser.pageEndPosition);
		System.out.println("");
		System.out.println("filterNext");
		printPage(otherParser.filterNext("-06-", false, -1, 5));
		System.out.println(otherParser.pageBeginPosition + " - " + otherParser.pageEndPosition);
	}
	
/*	private static String basicOperation(String direction){
		switch(direction){
		case "-1": return otherParser.prevMessage().get(1);
		case "1": return otherParser.nextMessage().get(1);
		default: return null;
		}
	}*/

/*	private static boolean offSet(int lineNumber){
		for (int i = 0 ; i < lineNumber; i++) {
			if(otherParser.readMessage0() == null){
				return false;
			}
		}
		return true;
	}
*/
	private static void exmain() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");

		System.out.println("Start");
		parser = new DummyLogFileParser();

		long offset = 0;
		long pageSize = 50;

		Page<LogMessage> page = parser.nextPage(offset, pageSize);

		System.out.println("New page: offset " + offset + ", pageSize " + pageSize);
		for (LogMessage message : page.getData())
		{
			System.out.println("  " + message.getStartRow() + ": " + sdf.format(message.getTimestamp()) + " [" +message.getLogLevel() + "] - " + message.getMessage());
		}

		System.out.println("Completed :)");
	}
	
//	@SuppressWarnings("unused")
	private static void printMessage(LogMessage message){
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");
		System.out.print(message.getStartRow() + ": " + sdf.format(message.getTimestamp()) + " [" +message.getLogLevel() + "] - " + message.getLoggerName() + " - ");
		for (String s : message.getFullMessage()) {
			System.out.print(s);
		}
		// System.out.println("  Is expand required: " + message.isExpandRequired());
	}
	
	private static void printPage(Page<LogMessage> page) {
		if (page == null) {
			System.out.println("NULL");
		} else {
			for(LogMessage message : page.getData()) {
				printMessage(message);
			}
		}
	}
	
	private static void lastmessageMethod(int size){
		printPage(otherParser.getLastMessages(size));
	}

}
