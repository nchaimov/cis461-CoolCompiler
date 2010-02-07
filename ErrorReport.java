//
//  Errors may be detected and reported in the scanner, the parser, 
//  or in further stages that analyze the internal form and synthesize 
//  output. This simple class just centralizes the printing of error messages, 
//  so that (for example) we can centralize the decision about where the 
//  messages should go.  An ErrorReport object should be created by the driver
//  and passed to each stage that needs a place to send error messages. 
//

import java.io.*; 

class ErrorReport {

    /** Print an error message.  It is up to the client to include 
     *  information like the line and position at which the errror 
     *  appeared; this just spews it to the appropriate place. 
     */
       public void err(String msg) {
             System.err.println(msg); 
       }

    // You could definitely improve this by making different
    // severity levels of error and warning, and perhaps by killing
    // the program when too many errors have occurred. 
  
}    
