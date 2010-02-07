/* 
 * Driver for Cool compiler.  We'll keep adding to this as we go. 
 *
 */

import java.io.*;
import java_cup.runtime.Symbol; 
import org.apache.commons.cli.*; // Command line parsing package

public class Cool {
    
    // Command line options
    String sourceFile = ""; 

    // Internal state
    ErrorReport report; 

    boolean DebugMode = false; // True => parse in debug mode 
	boolean printTree = false;


    static public void main(String args[]) {
	Cool cool = new Cool(); 
	cool.go(args); 
    }

    public void go(String[] args) {
        report = new ErrorReport(); 
	parseCommandLine(args); 
	parseProgram(); 
    }

    void parseCommandLine(String args[]) {
	try {
	    // Comman line parsing
	    Options options = new Options(); 
	    options.addOption("d", false, "debug mode (trace parse states)"); 
		options.addOption("t", false, "output abstract syntax tree in graphviz format");
	    CommandLineParser  cliParser = new GnuParser(); 
	    CommandLine cmd = cliParser.parse( options, args); 
	    DebugMode = cmd.hasOption("d"); 
	    printTree = cmd.hasOption("t");
	    String[] remaining = cmd.getArgs(); 
	    int argc = remaining.length; 
	    if (argc == 0) {
		report.err("Input file name required"); 
		System.exit(1); 
	    } else if (argc == 1) {
		sourceFile = remaining[0]; 
	    } else {
		report.err("Only 1 input file name can be given;"+
				    " ignoring other(s)"); 
	    }
	} catch (Exception e) {
	    System.err.println("Argument parsing problem"); 
	    System.err.println(e.toString()); 
	    System.exit(1); 
	}
    }


    void parseProgram() { 
        System.err.println("Beginning parse ..."); 
        try {
	    coolScanner scanner = 
		new coolScanner (new FileReader ( sourceFile )); 
            parser p = new parser( scanner); 
            p.setErrorReport(report); 
	    Symbol result;
	    if (DebugMode) { result =  p.debug_parse(); }
	    else { result = p.parse(); }
	    System.err.println("Done parsing"); 
		ASTnode tree = (ASTnode) result.value;
		if (printTree) tree.dump();
        } catch (Exception e) {
            System.err.println("Yuck, blew up in parse/validate phase"); 
            e.printStackTrace(); 
	    System.exit(1); 
        }
    }

}
