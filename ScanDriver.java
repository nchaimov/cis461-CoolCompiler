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

	static public void main(String args[]) {
		ScanDriver driver = new ScanDriver();
		driver.go(args);
	}

	public void go(String[] args) {
		report = new ErrorReport();
		parseCommandLine(args);
		System.out.println("Beginning parse ...");
		try {
			coolScanner scanner = new coolScanner(new FileReader(sourceFile));
			scanner.setErrorReport(report);

			Symbolx s = scanner.next_token();
			while (s.sym != sym.EOF) {
				System.out.println(s.line_num + "," + s.col_num + ": " + s.sym + "("
						+ symNames.toString(s.sym) + ") " + s.value);
				s = scanner.next_token();
			}

			System.out.println("Done parsing");
		} catch (Exception e) {
			System.err.println("Yuck, blew up in parse/validate phase");
			e.printStackTrace();
			System.exit(1);
		}

	}

	void parseCommandLine(String args[]) {
		try {
			// Comman line parsing
			Options options = new Options();
			CommandLineParser cliParser = new GnuParser();
			CommandLine cmd = cliParser.parse(options, args);
			String[] remaining = cmd.getArgs();
			int argc = remaining.length;
			if (argc == 0) {
				report.err("Input file name required");
				System.exit(1);
			} else if (argc == 1) {
				sourceFile = remaining[0];
			} else {
				report.err("Only 1 input file name can be given;" + " ignoring other(s)");
			}
		} catch (Exception e) {
			System.err.println("Argument parsing problem");
			System.err.println(e.toString());
			System.exit(1);
		}
	}
}
