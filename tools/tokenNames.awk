# $Id: tokenNames.awk,v 1.1 2006/04/06 05:18:17 michal Exp $ 
#
# Java cup produces a sym.java file with token values, but does 
# not produce a table for translating those tokens to string names.
# Why?  Who knows.  It's easy to remedy by producing the missing table
# ourselves. 
#
# Usage: 
#   awk -f tokenNames.awk sym.java >symNames.java
#

BEGIN {
  print "/* Generated from sym.java by tokenNames.awk */";
  print "public class symNames {";
  print "   static String toString(int token) {";
  print "      switch (token) {";
}

/public static final int/ {
  name = $5;
  value = $7;  sub(";","",value);
  print "           case ", value, ": return \"" name "\"; ";
}

END {
  print "           default: return \"NO SUCH TOKEN\";";
  print "       }";
  print "    }";
  print "}";
}

