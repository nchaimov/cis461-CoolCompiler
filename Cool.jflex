/* JFlex spec for Cool scanner */ 
/* Author: Nicholas Chaimov    */
   

%% 

%{
  java.util.Stack<String> input_stack = new java.util.Stack<String>(); 
  String cur_file = ""; 

  /* Convenience methods to pack various bookkeeping information 
   * into the symbol returned to the parser.  Note that yylineno 
   * numbers lines from 0; we bump it to conform to what most 
   * editors and IDEs expect.  We use Symbolx as the symbol type; 
   * it's an extension of the built-in CUP symbol class. 
   */ 
  Symbolx mkSym(int token) { 
    return new Symbolx(token, yychar, yychar+yytext().length(), 
    	       		      yyline+1, yycolumn, null);
  }
		
  Symbolx mkSym(int token, Object val) { 
    return new Symbolx(token, yychar, yychar+yytext().length(), 
    	       		      yyline+1, yycolumn, val);
  }

   int lexical_error_count = 0; 
   int comment_begin_line = 0; /* For running off end of file in comment */ 
   int MAX_LEX_ERRORS = 20;
   java.lang.StringBuilder strLiteral = new java.lang.StringBuilder();

  // If the driver gives us an error report class, we use it to print lexical
  // error messages
  ErrorReport report = null; 
  public void setErrorReport( ErrorReport _report) {
       report = _report;
  }
  void err(String msg) {
    if (report == null) {
        System.err.println(msg); 
    } else {
        report.err(msg); 
    }
   }

  void lexical_error(String msg) {
    String full_msg = "Lexical error at " + cur_file + 
    		      " line " + yyline + 
    		       ", column " + yycolumn +
		       ": " + msg; 
    err(full_msg); 
    if (++lexical_error_count > MAX_LEX_ERRORS) {
       err("Too many lexical errors, giving up."); 
       System.exit(1); 
    }
  }
  
%}


%implements java_cup.runtime.Scanner
%function next_token
%type Symbolx
%class coolScanner
%char
%line
%column
// %debug  // Makes a LOT of noise 


%state INCLUDEFILE
%xstate INCOMMENT
%xstate INSTRING

SPACE = [ \n\t]+
LINECOMMENT = "--".*[\n]
FILE = [.-_/a-zA-Z0-9]+

%%

{SPACE}    { ; /* skip */ }

{LINECOMMENT}  { ; /* skip */ }

"(*" { yybegin(INCOMMENT); comment_begin_line = yyline; }
<INCOMMENT> {
  "*)"  { yybegin(YYINITIAL);  }
  [^\*]+ { /* skip */ }
  .     { /* skip */ }
  \n    { /* skip */ }
  <<EOF>> { lexical_error("Comment \"(*...\"  missing ending \"*)\"" +
                          "\nComment began on line " +comment_begin_line ); 
	    yybegin(YYINITIAL); 
          }
}

/* This isn't in the COOL language spec, but it's a good
 * trick to know.  Note that you must use the --skel option
 * with jflex and specify the skeleton.nested skeleton file, 
 * in place of the standard jflex skeleton. 
 */
"#include" { yybegin(INCLUDEFILE); }
<INCLUDEFILE>{FILE}  { 
             String filename=yytext(); 
   	     yybegin(YYINITIAL); 
	     input_stack.push(cur_file); 
	     cur_file = filename; 
	     yypushStream(new java.io.FileReader(yytext())); 
}
<<EOF>>    { if (yymoreStreams()) {
	        yypopStream(); 
		cur_file = input_stack.pop(); 
	     }  else {
                return mkSym( sym.EOF ); 
	     }
           }


/* Punctuation */ 

"("	   { return mkSym( sym.LPAREN ); }
")"	   { return mkSym( sym.RPAREN ); }
"{"	   { return mkSym( sym.LBRACE ); }
"}"	   { return mkSym( sym.RBRACE ); }
";"	   { return mkSym( sym.SEMI ); }
"."	   { return mkSym( sym.DOT );  }
"="	   { return mkSym( sym.EQ );  }
"<"	   { return mkSym( sym.LT );  }
"<="	   { return mkSym( sym.LEQ); }
"=>"	   { return mkSym( sym.RIGHTARROW); }
"/"        { return mkSym( sym.DIV ); }
":="	   { return mkSym( sym.ASSIGN); }
"~"	   { return mkSym( sym.NEG); }
"@"	   { return mkSym( sym.AT); }
"*"		{ return mkSym( sym.TIMES); }
"+"		{ return mkSym( sym.PLUS); }
"-"		{ return mkSym( sym.MINUS); }
":" 	{ return mkSym( sym.COLON); }
","		{ return mkSym( sym.COMMA); }


/* Keywords */

"class"	    { return mkSym( sym.CLASS ); }
"else"	    { return mkSym( sym.ELSE ); }
"false"	    { return mkSym( sym.FALSE ); }
"fi"	    { return mkSym( sym.FI ); }
"if"	    { return mkSym( sym.IF ); }
"in"	    { return mkSym( sym.IN ); }
"inherits"  { return mkSym( sym.INHERITS ); }
"isvoid"    { return mkSym( sym.ISVOID ); }
"let"	    { return mkSym( sym.LET ); }
"do"	    { return mkSym( sym.DO ); }
"od"	    { return mkSym( sym.OD ); }
"then"	    { return mkSym( sym.THEN ); }
"while"	    { return mkSym( sym.WHILE ); }
"case"	    { return mkSym( sym.CASE ); }
"esac"	    { return mkSym( sym.ESAC ); }
"new"	    { return mkSym( sym.NEW ); }
"of"	    { return mkSym( sym.OF ); }
"not"	    { return mkSym( sym.NOT ); }
"true"	    { return mkSym( sym.TRUE ); }

/* Identifiers */ 

[a-z][_a-zA-Z0-9]*  { return mkSym( sym.ID, yytext()); }
[A-Z][_a-zA-Z0-9]*  { return mkSym( sym.TYPEID, yytext()); }

/* Literals (other than booleans which are keywords) */ 
/* Note we return the string value here, and let the parser
 * convert it to the appropriate internal type when it needs to. 
 * That might not be the best practice, but it saves us from having
 * to subtype "Symbol" for different value kinds, or turn things into
 * an Object that has to be cast back to the appropriate type. It's 
 * the same code wherever it lives.
 */ 
[0-9]+	{ return mkSym( sym.INTLIT, yytext() ); }

/* Strings require some pre-processing on this end, to convert 
 * escapes and so on; I haven't done it yet, and haven't even 
 * tested this pattern, so you'll have to.  If it gets too hairy, 
 * consider breaking it down using states like in the multi-line 
 * comment pattern above ... then you can take understandable chunks
 * and string them together. 
 */ 
"\"" { yybegin(INSTRING); strLiteral = new java.lang.StringBuilder(); }
<INSTRING> {
	[^\n\"\\]+		{ strLiteral.append( yytext() ); }
	"\\b"			{ strLiteral.append("\b"); }
	"\\t"			{ strLiteral.append("\t"); }
	"\\n"			{ strLiteral.append("\n"); }
	"\\f"			{ strLiteral.append("\f"); }
	"\\\n"			{ /* ignore escaped newline */  }
	"\n"			{ lexical_error("Illegal unescaped newline in string"); }
	"\\".			{ strLiteral.append(yytext().charAt(1)); }
	"\""			{ yybegin(YYINITIAL); return mkSym(sym.STRINGLIT, strLiteral.toString()); }
	
}				  

/* Default when we don't match anything above 
 * is a scanning error.  We don't want too many of 
 * these, but it's hard to know how much to gobble ... 
 */ 
.   { lexical_error("Illegal character '" +
      	              yytext() +
		      "' "); 
    }

