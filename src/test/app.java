package test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.*;


import org.apache.jena.query.Dataset ;
import org.apache.jena.rdf.model.Model ;
import org.apache.jena.riot.Lang ;
import org.apache.jena.riot.RDFDataMgr ;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.jena.graph.Factory;
import org.apache.jena.graph.Graph;
import org.apache.jena.graph.Triple ;
import org.apache.jena.riot.RDFParser ;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.lang.CollectorStreamBase;
import org.apache.jena.riot.lang.CollectorStreamTriples;
import org.apache.jena.riot.lang.PipedRDFIterator;
import org.apache.jena.riot.lang.PipedRDFStream;
import org.apache.jena.riot.lang.PipedTriplesStream;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.riot.system.StreamRDFWriter;
import org.apache.jena.shared.JenaException;
import org.apache.jena.sparql.graph.GraphFactory;
import org.hamcrest.Matcher;

public class app {
	int errNum = 1;
	String modifiedText = "";
	public void errorsFinder(String txt) {
		//System.out.println("hello");


		FileOutputStream fop = null;
		File file = new File("Battery.ttl");

		final String filename = "Battery.ttl";
		BufferedReader br = null;
		String sCurrentLine = null;
		String text = "";
		Boolean enterINCatchBlock = false;
		if(txt == "") {

			try
			{
				br = new BufferedReader(
						new FileReader(filename));
				int lineNum = 1;
				while ((sCurrentLine = br.readLine()) != null)
				{
					//System.out.println("line "+lineNum+" "+sCurrentLine);
					lineNum++;
					text += sCurrentLine+"\n";
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			finally
			{
				try
				{
					if (br != null)
						br.close();
				} catch (IOException ex)
				{
					ex.printStackTrace();
				}
			}

		}
		else
			text = txt;
		//System.out.print(text);

		//        CollectorStreamTriples inputStream = new CollectorStreamTriples();
		//        RDFParser.source(filename).parse(inputStream);
		//        
		//        
		//        
		//System.out.println(text);

		Graph graph = GraphFactory.createGraphMem();
		InputStream input = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
		try {
			RDFParser.create().lang(Lang.TTL).source(input).parse(graph);

			//StreamRDFWriter.write(System.out, graph, Lang.NT) ;

			//RDFDataMgr.write(System.out, graph, Lang.TTL) ;
		}catch(JenaException e) {
			//System.out.print(e.getMessage());
			String errorLocation = StringUtils.substringBetween(e.getMessage(), "[", "]");
			String beforeText= "";
			String afterText= "", errorMessage ="";
			String numsplit = errorLocation.replaceAll("[^0-9]+",";");
			String[] nums = numsplit.split(";");
			//System.out.print(Arrays.toString(nums));
			int lineNum = Integer.parseInt(nums[1]);
			int columnNum = Integer.parseInt(nums[2]);
			System.out.print("Syntax Error "+ errNum++ +" [ Line "+ lineNum +" , Col " + columnNum);
			errorMessage = e.getMessage().split("]")[1];
			System.out.print(" ] Error Message "+ errorMessage +"\n");
			if(modifiedText == "")
				modifiedText = text;
			
			// From the beginning, add a dummy prefix 
			if(!modifiedText.contains("@prefix dummyPrefix:<http://www.exmaple.com>  \n")) {
				modifiedText = "@prefix dummyPrefix:<http://www.exmaple.com>  \n" + modifiedText;
			}
			// first case
			if(errorMessage.contains("Triples not terminated by DOT")) {
				int lines = 1;
				Scanner scanner = new Scanner(text);
				while (lineNum > lines) {
					String line = scanner.nextLine();

					beforeText += line +"\n";
					if(lineNum - 1 == lines) {
						break;
					}
					lines++;
				}
				while (scanner.hasNextLine()) {
					afterText += scanner.nextLine() +"\n";
				}

				scanner.close();
				enterINCatchBlock = true;

				beforeText = removeWhileSpaceEnd(beforeText);
				if(beforeText != "" || beforeText.length()>0)
					beforeText += ". \n";

				modifiedText = beforeText +" "+ afterText;
				errorsFinder(modifiedText);

			}else if(errorMessage.contains("Undefined prefix:")) {
				String misMatchedPrefix = "";
				misMatchedPrefix = errorMessage.split("prefix: ")[1];

				modifiedText = modifiedText.replace(misMatchedPrefix,"dummyPrefix");
				errorsFinder(modifiedText);
			}else if (errorMessage.contains("Bad character in IRI (space):")) {
				// to get the location of the token which comes after it a space
				String splitter = errorMessage.split("\\(space\\): ")[1];
				splitter = splitter.split("\\[")[0];
				int position = modifiedText.indexOf(StringEscapeUtils.escapeJava(splitter+" "))+splitter.length();
				// just remove any space comes after it 
				while(modifiedText.charAt(position) == ' ') {
					StringBuilder sb = new StringBuilder(modifiedText);
					sb.deleteCharAt(position);
					modifiedText = sb.toString();
				}
				// callback to check if there are other errors 
				errorsFinder(modifiedText);

			}
			else if (errorMessage.contains("Unrecognized directive: ")) {
				String catchedError = errorMessage.split("directive: ")[1];  
				modifiedText = modifiedText.replace(catchedError,"prefix");
				errorsFinder(modifiedText);

			}else if (errorMessage.contains("@prefix or PREFIX requires a prefix with no suffix")) {
				modifiedText = removeLine(modifiedText, errorMessage.split("PREFIXED_NAME:")[1], -1);
				errorsFinder(modifiedText);			
			}
			else if (errorMessage.contains("@prefix or PREFIX requires a prefix  (found '[IRI:")) {
				modifiedText = removeLine(modifiedText, errorMessage.split("IRI:")[1], -1);
				errorsFinder(modifiedText);
			}
			else if (errorMessage.contains("@prefix or PREFIX requires a prefix (found '[KEYWORD:")) {
				modifiedText = removeLine(modifiedText, errorMessage.split("KEYWORD:")[1], -1);
				errorsFinder(modifiedText);
			}
			else if (errorMessage.contains("@prefix requires an IRI (found '")) {
				modifiedText = removeLine(modifiedText, "", lineNum );
				errorsFinder(modifiedText);
			}			
			else if (errorMessage.contains("@base requires an IRI (found '")) {
				modifiedText = removeLine(modifiedText, "", lineNum + 1);
				errorsFinder(modifiedText);
			}
			else if (errorMessage.contains("Expected IRI for predicate: got: [")) {
				String catchedError =  errorMessage.split("\\[")[1].split(":")[1]; 
				modifiedText = modifyLine(modifiedText, catchedError , "left");
				errorsFinder(modifiedText);
			}
			else if (errorMessage.contains("Out of place: ")) {
				modifiedText = removeLine(modifiedText, "", lineNum + 2  );
				Scanner scanner = new Scanner(modifiedText);
				int counter = 1;
				while(scanner.hasNextLine()) {

					System.out.print("line "+counter ++ +" "+scanner.nextLine()+"\n" );

				}
				scanner.close();
				//System.out.print(modifiedText);
				//errorsFinder(modifiedText);
			}
			else{
				System.out.print(e.getMessage());
			}
		}catch(Exception e) {
			System.out.print(e.getMessage());
		}


	}
	public String removeWhileSpaceEnd(String text) {

		String temp = "";
		if(text.length() > 0 || text != null) {
			temp = text;
			while(temp.charAt(temp.length() - 1) == '\n'|| temp.charAt(temp.length() - 1) == ' ') {
				temp = temp.substring(0, temp.length() - 1);
				//System.out.print("temp "+temp);
			}
		}
		return temp;
	}
	public String removeLine(String text, String toBeRemoved, int lineNumber) {

		String beforeText ="", afterText = "";
		int lineCount = 1; 
		Scanner scanner = new Scanner(text);

		while(scanner.hasNextLine()) {
			String line = scanner.nextLine();
			lineCount++;
			if(toBeRemoved != "")
				if(line.contains(toBeRemoved)) {
					beforeText += "\n";
					break;
				}
				else
					beforeText += line+"\n";
			if(lineNumber != -1)
				if(lineCount == lineNumber) {
					beforeText += "\n";
					break;
				}
				else
					beforeText += line+"\n";
				
		}
		while(scanner.hasNextLine()) {

			afterText += scanner.nextLine()+"\n";
		}
		scanner.close();

		return beforeText+" "+afterText;
	}
	
	public String modifyLine(String text, String errorString, String direction) {

		String beforeText ="", afterText = "";
		int lineCount = 1; 
		Scanner scanner = new Scanner(text);

		while(scanner.hasNextLine()) {
			String line = scanner.nextLine();
			lineCount++;

			if( direction == "left")
				if(line.contains(errorString)) {
					int position = line.indexOf(errorString);
					line = line.substring(0,position -1  ) +" rdfs:Dummy " + line.substring(position  - 1, line.length() -1);
					beforeText += line + "\n";
					break;
				}
				else
					beforeText += line +"\n";	
		}
		while(scanner.hasNextLine()) {

			afterText += scanner.nextLine()+"\n";
		}
		scanner.close();

		return beforeText+" "+afterText;
	}

	public static void main(String[] args) {

		app a = new app();
		a.errorsFinder("");


	}


}


