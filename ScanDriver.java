/**
 * Simple driver for a JFLEX-generated scanner
 *
 */

import java.io.FileReader;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;

public class ScanDriver {
	
	// Command line options
	String sourceFile = "";
	
	// Internal state
	ErrorReport report;
	
	static public void main(final String args[]) {
		final ScanDriver driver = new ScanDriver();
		driver.go(args);
	}
	
	public void go(final String[] args) {
		report = new ErrorReport();
		parseCommandLine(args);
		System.out.println("Beginning parse ...");
		try {
			final coolScanner scanner = new coolScanner(new FileReader(
					sourceFile));
			scanner.setErrorReport(report);
			
			Symbolx s = scanner.next_token();
			while (s.sym != sym.EOF) {
				System.out.println(s.line_num + "," + s.col_num + ": " + s.sym
						+ "(" + Util.idToName(s.sym) + ") " + s.value);
				s = scanner.next_token();
			}
			
			System.out.println("Done parsing");
		} catch (final Exception e) {
			System.err.println("Yuck, blew up in parse/validate phase");
			e.printStackTrace();
			System.exit(1);
		}
		
	}
	
	void parseCommandLine(final String args[]) {
		try {
			// Comman line parsing
			final Options options = new Options();
			final CommandLineParser cliParser = new GnuParser();
			final CommandLine cmd = cliParser.parse(options, args);
			final String[] remaining = cmd.getArgs();
			final int argc = remaining.length;
			if (argc == 0) {
				report.err("Input file name required");
				System.exit(1);
			} else if (argc == 1) {
				sourceFile = remaining[0];
			} else {
				report.err("Only 1 input file name can be given;"
						+ " ignoring other(s)");
			}
		} catch (final Exception e) {
			System.err.println("Argument parsing problem");
			System.err.println(e.toString());
			System.exit(1);
		}
	}
}
