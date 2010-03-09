/* 
 * Driver for Cool compiler.  We'll keep adding to this as we go. 
 *
 */

import java.io.FileReader;

import java_cup.runtime.Symbol;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;

public class Cool {

	// Command line options
	String sourceFile = "";

	// Internal state
	ErrorReport report;

	private static final String PARSE_DEBUG_OPTION = "dp";
	private static final String TYPECHECK_DEBUG_OPTION = "dt";
	private static final String CODEGEN_DEBUG_OPTION = "dc";
	private static final String PRINT_TREE_OPTION = "t";

	boolean debugParser = false; // True => parse in debug mode
	boolean debugTypeChecker = false;
	boolean debugCodegen = false;
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
			options.addOption(PARSE_DEBUG_OPTION, false, "parser debug mode (trace parse states)");
			options.addOption(PRINT_TREE_OPTION, false,
					"output abstract syntax tree in graphviz format");
			options.addOption(TYPECHECK_DEBUG_OPTION, false, "typechecker debug mode");
			options.addOption(CODEGEN_DEBUG_OPTION, false, "code generator debug mode");
			CommandLineParser cliParser = new GnuParser();
			CommandLine cmd = cliParser.parse(options, args);
			debugParser = cmd.hasOption(PARSE_DEBUG_OPTION);
			printTree = cmd.hasOption(PRINT_TREE_OPTION);
			debugTypeChecker = cmd.hasOption(TYPECHECK_DEBUG_OPTION);
			debugCodegen = cmd.hasOption(CODEGEN_DEBUG_OPTION);
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

	void parseProgram() {
		System.err.println("Beginning parse ...");
		try {
			coolScanner scanner = new coolScanner(new FileReader(sourceFile));
			parser p = new parser(scanner);
			p.setErrorReport(report);
			Symbol result;
			if (debugParser) {
				System.err.println("Parsing in debug mode...");
				result = p.debug_parse();
			} else {
				result = p.parse();
			}
			System.err.println("Done parsing");
			ASTnode tree = (ASTnode) result.value;
			if (tree == null) {
				System.err.println("*** Parsing failed!");
				System.exit(3);
			}
			System.err.println("Beginning typecheck...");
			TypeChecker typeChecker = new TypeChecker(tree, debugTypeChecker);
			if (typeChecker.typecheck()) {
				System.err.println("Done typechecking");
				if (printTree) {
					tree.dump();
				} else {
					System.err.println("Beginning code generation...");
					CodeGenerator codeGenerator = new CodeGenerator(typeChecker.getEnvironment(),
							debugCodegen);
					final String code = codeGenerator.generateCode();
					System.err.println("Done generating code\n\n");
					System.out.println(code);
				}
			} else {
				System.exit(2);
			}
		} catch (Exception e) {
			System.err.println("Yuck, blew up in parse/validate phase");
			e.printStackTrace();
			System.exit(1);
		}
	}
}
