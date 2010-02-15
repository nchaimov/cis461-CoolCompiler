// See rationale at bottom of this file
/**
 * CUP symbol extended with line#, column#.
 * 
 * @author Michal Young
 */
public class Symbolx extends java_cup.runtime.Symbol {

	/*
	 * Inherited fields from CUP's Symbol class are sym: the symbol type
	 * parse_state: the parse state. value: is the lexical value of type Object
	 * left : is the left position in the original input file right: is the
	 * right position in the original input file
	 * 
	 * It is common to repurpose the "left" and "right" fields to be line number
	 * and column number, but this makes nonsense of the way CUP identifies the
	 * position of non-termminal symbols in error messages. So, instead, we keep
	 * the left and right character positions as they are, and add a line# and
	 * col# field.
	 * 
	 * To accomodate #include and similar constructs that are handled in the
	 * lexer, we'll also have a place to put the file name, bt it's optional.
	 */

	public int line_num = -1; // -1 will mean "uninitialized"
	public int col_num = -1; // ""
	public String source_file = ""; // Can be empty except in nested files

	/*
	 * Now we need a constructor to fill this information in ... and since we
	 * are trying to keep the left and right values, we can't override the
	 * constructor Symbol(id, l, r, val). We'll need a constructor with lots of
	 * arguments, alas.
	 */

	public Symbolx(int id, int _left, int _right, int _line, int _col, Object _val) {
		super(id, _left, _right, _val);
		line_num = _line;
		col_num = _col;
	}

	public Symbolx(int id, int _left, int _right, int _line, int _col, String _file, Object _val) {
		super(id, _left, _right, _val);
		line_num = _line;
		col_num = _col;
		source_file = _file;
	}

}

// Rationale for extending java_cup.runtime.Symbol:
// CUP's built-in Symbol class has a non-standard way of labeling
// the position of an error (it keeps the leftmost and rightmost
// character position within which the error occured. This seems
// more-or-less reasonable, except that it's really incompatible
// with most tools (especially Unix tools) that expect a line number
// and column number. The standard workaround is just to
// use line# for the "left" field and column# for the "right" field ...
// which sort of works, except then the generated parser is interpreting
// them both as character positions and making nonsense by saying the
// span of an error is from the line# of the beginning of sequence of
// tokens to the column# of the end.
//
// CUP also doesn't provide decent pretty-printing and debugging
// support (e.g., the token numbers are not related to their
// symbolic names), so we could add that here too.
//
// Really it's enough to make you want to use JavaCC or Antlr ...
// both of which are good systems, but neither quite as suited
// to a compiler construction course. (E.g., JavaCC mixes lexical
// analysis into the parser spec in a way that is convenient but
// makes the logical separation less clear, and both are LL(k) parser
// generators that encourage beginners to hack grammars with long
// lookaheads).

