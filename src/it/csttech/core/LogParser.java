package it.csttech.core;

import java.text.SimpleDateFormat;
import java.util.Random;
import java.util.Scanner;

import it.csttech.core.data.Page;
import it.csttech.core.logging.DummyLogFileParser;
import it.csttech.core.logging.Log4jFileParser;
import it.csttech.core.logging.LogFileParser;
import it.csttech.core.logging.LogMessage;

public class LogParser {

	private static String REGEX;
	private static String FILE_NAME;
	private static LogFileParser parser;
	private static Log4jFileParser otherParser;
	private static Scanner scanner;

	public static void main(String[] args) {
		boolean condition = true; // hardcoded
		if (condition) {
			scanner = new Scanner(System.in);
			String testingMode = "readcommanded"; // hardcoded
			REGEX = "^[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2},[0-9]{3}"; // hardcoded
			String timestampFormat = "yyyy-MM-dd HH:mm:ss,SSS";
			//                        2016-05-16 11:08:29,492
			FILE_NAME = "input/inputDoubled.log"; // hardcoded
			otherParser = new Log4jFileParser(FILE_NAME,REGEX,timestampFormat);
			switch (testingMode.toLowerCase()) {
			case "readline":
				readlineMethod(10); // hardcoded
				break;
			case "readmessage":
				readmessageMethod(5); // hardcoded
				otherParser.testingRegisters();
				break;
			case "readcommanded":
				readcommandedMethod();
				otherParser.testingRegisters();
				break;
			case "readrandom":
				readrandomMethod(100, 10); // hardcoded
				otherParser.testingRegisters();
				break;
			default:
				exmain();
				break;
			}
		} else {
			exmain();
		}
	}

	private static void readlineMethod(int arg){
		for (int i = 0; i < arg; i++) {
			System.out.println(otherParser.readLine());
		}
	} 

	private static void readmessageMethod(int arg){
		for (int i = 0; i < arg; i++) {
			printMessage(otherParser.readMessage0());
		}
	}

	private static void readrandomMethod(int offset, int size){
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

	private static void readcommandedMethod(){
		String newInput;
		LogMessage output;
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
			printMessage(output);
		}
	}


	private static LogMessage basicOperation(String direction){
		switch(direction){
		case "-1": return otherParser.prevMessage0();
		case "1": return otherParser.readMessage0();
		default: return null;
		}
	}

	private static boolean offSet(int lineNumber){
		for (int i = 0 ; i < lineNumber; i++) {
			if(otherParser.readMessage0() == null){
				return false;
			}
		}
		return true;
	}

	private static void exmain() {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS");

		System.out.println("Start");

		//TODO: replace with real implementation.
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
		System.out.print(message.getStartRow() + ": " + sdf.format(message.getTimestamp()) + " [" +message.getLogLevel() + "] - " + message.getLoggerName());
		for (String s : message.getFullMessage()) {
			System.out.print(s);
		}
	}

}
