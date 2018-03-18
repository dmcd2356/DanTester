/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package danparse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 *
 * @author dmcd2356
 */
public class DanParse {

  private static final String NEWLINE = System.getProperty("line.separator");

  private static enum testType { NONE, UNINSTRUMENTED, OBJECT };
  
                                              // inputs that generate the specified new state
  private static enum stateType { NONE,       // starting point
                                  ERROR,      // an error occurred
                                  EXIT,       // TESTEXIT
                                  TRY,        // TEST  : UNINSTR TRY
                                  SKIP,       // TEST  : UNINSTR SKIP
                                  CALLBACK,   // TEST  : UNINSTR CALLBACK
                                  REPLACE,    // TEST  : UNINSTR REPLACE
                                  RUN,        // UNINST: 
                                  OBJBEGIN,   // TEST  : OBJECT[xx]: BEGIN
                                  COOKSTACK,  // TEST  : OBJECT[xx]: COOKED STACK
                                  COOKLOCAL   // TEST  : OBJECT[xx]: COOKED LOCAL
                                };
  
  // set from expected settings
  private static String expType;          // the expected test type to run
  private static String expObject;        // the expected method or object to be tested
  private static String expOther;         // the expected other method (UNINSTR tests)
  
  private static String  linenum;         // the line number being processed
  private static int     showMessages;    // 1 to print state messages, 2 to print all messages
  private static boolean bFailure;        // true if test failure occurred
  private static int     stateIndex;      // current index in stateList to next valid state
  private static ArrayList<stateType> stateList; // list of state changes expected
  private static ArrayList<stateType> ignoreList; // list of states to ignore
  
  private static void debugPrint(String message) {
    if (showMessages > 0) {
      System.out.println(message);
    }
  }
  
  private static void extendedPrint(String message) {
    if (showMessages > 1) {
      System.out.println(message);
    }
  }
  
  private static void ignoreAllObjects() {
    ignoreList.add(stateType.OBJBEGIN);
    ignoreList.add(stateType.COOKSTACK);
    ignoreList.add(stateType.COOKLOCAL);
  }
  
  private static void ignoreAllUninstrs() {
    ignoreList.add(stateType.TRY);
    ignoreList.add(stateType.SKIP);
    ignoreList.add(stateType.CALLBACK);
    ignoreList.add(stateType.REPLACE);
    ignoreList.add(stateType.RUN);
  }
  
  private static boolean isStateIgnored(stateType state) {
    // always ignore the following states (should never be thrown)
    if (state == stateType.NONE || state == stateType.ERROR) {
      return true;
    }
    
    // and ignore any states that were specifically marked
    for (stateType next : ignoreList) {
      if (next == state) {
        return true;
      }
    }
    return false;
  }
  
  private static boolean isValidNumeric(String str) {
    try {
      int value = Integer.parseInt(str);
    } catch (NumberFormatException ex) {
      return false;
    }
    return true;
  }

  private static boolean isValidDebugMessage(String line) {
    // All debug messages are assumed to start with: "xxxxxxxx [xx:xx.xxx] " to express the line
    // number and timestamp of the message, followed by message type (chars 21-26) and the
    // message contents starting at offset 29.
    return !(line.length() < 30 ||
            line.charAt(9) != '[' || line.charAt(12) != ':' ||
            line.charAt(15) != '.' || line.charAt(19) != ']' ||
            !isValidNumeric(line.substring(0, 8)) ||
            !isValidNumeric(line.substring(10, 11)) ||
            !isValidNumeric(line.substring(13, 14)) ||
            !isValidNumeric(line.substring(16, 18)));
  }
  
  private static void setTestFail(stateType newState, String error) {
    debugPrint("FAIL - " + expType + " :: STATE_" + newState.toString() + " :: " + error);
    bFailure = true;
  }

  private static void setTestPass(stateType newState) {
    debugPrint("PASS - " + expType + " :: STATE_" + newState.toString());
  }
  
  private static void checkStateNext(stateType newState) {
    // ignore entries not pertanent to the test
    if (bFailure || isStateIgnored(newState)) {
      return;
    }
    
    if (stateList.isEmpty()) {
      setTestFail(newState, "No state machine entries set up");
      return;
    }
    
    stateType nextState = stateList.get(stateIndex);
    if (newState == nextState) {
      setTestPass(newState);
      ++stateIndex;
    } else {
      setTestFail(newState, "valid state: " + nextState.toString());
    }
  }
  
  private static stateType parseTestUninstrumented(String line) {
    stateType state;
    String origMethod = "";
    String newMethod = "";

    String[] array = line.split(" "); // UNINSTR has been stripped off the line
    switch(array[0]) { // 1st word is the UNINSTR type specified
      case "TRY:":        // TRY: <origMethod>
        state = stateType.TRY;
        origMethod = array[1];
        break;
      case "SKIP:":       // SKIP: <origMethod>
        state = stateType.SKIP;
        origMethod = array[1];
        break;
      case "CALLBACK:":   // CALLBACK: <newMethod> FROM: <origMethod>
        state = stateType.CALLBACK;
        newMethod = array[1];
        if (array.length > 3) {
          origMethod = array[3];
        } else {
          origMethod = "JVM"; // if omitted, the JVM did the method substitution
        }
        break;
      case "REPLACE:":    // REPLACE: <method> FROM: <origClass> WITH: <newClass>
        state = stateType.REPLACE;
        String method = array[1];
        String origClass = array[3];
        String newClass  = array[5];
        origMethod = origClass + "." + method;
        newMethod = newClass + "." + method;
        break;
      default:
        state = stateType.ERROR;
        debugPrint("ERROR line " + linenum + ": Unknown UNINSTR type = " + array[0]);
        break;
    }
    
    // ignore if method does not match the expected one
    if (!expObject.equals(origMethod)) {
      state = stateType.NONE;
    } else {
      // if it matches and there was a substitute method defined, verify it matches the expected
      if (!newMethod.isEmpty() && !newMethod.contains(expOther)) {
        setTestFail(state, "newmeth incorrect: " + newMethod);
        state = stateType.ERROR;
      }
    }
    return state;
  }

  private static stateType parseTestObject(String line) {
    stateType state;
    String objType;

    String[] array = line.split(" "); // OBJECT[xx]: has been stripped off the line
    switch(array[0]) { // 1st word is the UNINSTR type specified
      case "BEGIN":           // BEGIN <objType>
        state = stateType.OBJBEGIN;
        objType = array[1];
        break;
      case "COOKED":
        switch (array[1]) {
          case "STACK":       // COOKED STACK <objType>
          state = stateType.COOKSTACK;
          objType = array[2];
          break;
        case "LOCAL":         // COOKED LOCAL <objType>
          state = stateType.COOKLOCAL;
          objType = array[2];
          break;
        default:
          state = stateType.ERROR;
          debugPrint("ERROR line " + linenum + ": Unknown OBJECT COOKED type = " + array[1]);
          break;
        }
        break;
      default:
        state = stateType.ERROR;
        debugPrint("ERROR line " + linenum + ": Unknown OBJECT type = " + array[0]);
        break;
    }
    return state;
  }
  
  /**
   * parses the next raw output line and determines the state it infers, then checks the next
   * state for validity.
   * 
   * @param line - the line read from the raw output file
   * @return - true if the test exit condition has been found
   */
  private static boolean parseLine(String line) {
    stateType state;

    // check if we are exiting the test
    if (line.equals("TESTEXIT")) {
      checkStateNext(stateType.EXIT);
      return true;
    }
    
    // ignore any invalid debug lines
    if (!isValidDebugMessage(line)) {
      return false;
    }
    
    linenum = line.substring(0, 8);
    line = line.substring(21);

    String debugType = line.substring(0, 6).trim();
    String message = line.substring(8).trim();
    switch (debugType) {
      case "TEST":
        // This handles the TEST type debug messages
        if (!message.contains(" ")) {
          debugPrint("ERROR line " + linenum + ": Invalid TEST line: " + message);
          bFailure = true;
          return false;
        }
        
        // extract the 1st item from the line and pass the remaining line to the intended parser
        int offset = message.indexOf(" ");
        String type = message.substring(0, offset);
        message = message.substring(offset).trim();
        if (type.contains("[")) {
          type = type.substring(0,type.indexOf("["));
        }
        if (type.contains(":")) {
          type = type.substring(0,type.indexOf(":"));
        }

        // run the parser and state checker for each applicable line
        extendedPrint(linenum + ": " + message);
        if (type.equals("UNINSTR")) {
          checkStateNext(parseTestUninstrumented(message));
        } else if (type.equals("OBJECT")) {
          checkStateNext(parseTestObject(message));
        }
        break;
      case "UNINST":
        // This handles the UNINST type debug messages
        message = message.trim();
        extendedPrint(linenum + ": " + message);
        if (expObject.equals(message)) {
          state = stateType.RUN;
        } else {
          state = stateType.NONE;
        }

        // now check the new state to see if it is valid
        if (state != stateType.ERROR) {
          checkStateNext(state);
        }
        break;
      default:
        break;
    }
    
    return false;
  }
  
  /**
   * parse a line from the debug file to see if we have the start condition.
   * This line is output by DanTest itself rather than the debug output, but will still be
   * included in the standard output that is saved as the captured raw file.
   * This provides the type of test being performed and any additional parameters needed to define
   * the test conditions.
   * 
   * @param line - the line from the file
   * @return true if we got the start condition
   */
  private static boolean parseStart(String[] array) {
    if (array.length == 0 || !array[0].equals("EXPECTED:")) {
      return false;
    }
    
    // gather the expected test result info
    if (array.length > 1) {
      expType = array[1];             // the expected test type to run
      expObject = "";
      expOther = "";
      if (array.length > 2) {
        expObject = array[2];         // the expected method to be tested or test object
        if (array.length > 3) {
          expOther = array[3];        // the expected other method
        }
      }

      // determine the test type being run (testing Objects or Uninstrumented)
      String expMessage = "";
      String methodonly;
      int offset;
      switch (expType) {
        case "METH_NORMAL":
          expMessage = "no change (uninstrumented method called)";
          // setup valid states for test
          ignoreAllObjects();
          stateList.add(stateType.TRY);
          stateList.add(stateType.RUN);
          stateList.add(stateType.EXIT);
          break;
        case "METH_CALLBACK":
          expMessage = "CALLBACK to " + expOther;
          // setup valid states for test
          ignoreAllObjects();
          stateList.add(stateType.TRY);
          stateList.add(stateType.CALLBACK);
          stateList.add(stateType.RUN);
          stateList.add(stateType.EXIT);
          break;
        case "METH_REPLACE":
          methodonly = "";
          offset = expObject.indexOf('.');
          if (offset > 0) {
            methodonly = expObject.substring(offset);
          }
          expMessage = "REPLACED by " + expOther + methodonly;
          // setup valid states for test
          ignoreAllObjects();
          stateList.add(stateType.TRY);
          stateList.add(stateType.REPLACE);
          stateList.add(stateType.SKIP);
          stateList.add(stateType.EXIT);
          break;
        case "METH_REPL_NEST":
          methodonly = "";
          offset = expObject.indexOf('.');
          if (offset > 0) {
            methodonly = expObject.substring(offset);
          }
          expMessage = "REPLACED by " + expOther + methodonly;
          // setup valid states for test
          ignoreAllObjects();
          stateList.add(stateType.TRY);
          stateList.add(stateType.REPLACE);
          stateList.add(stateType.TRY);
          stateList.add(stateType.RUN);
          stateList.add(stateType.SKIP);
          stateList.add(stateType.EXIT);
          break;
        case "OBJ_UNINTRUMENTED":
          expMessage = "SIMPLE UNINSTRUMENTED OBJECT";
          // setup valid states for test
          ignoreAllUninstrs();
          stateList.add(stateType.OBJBEGIN);
          stateList.add(stateType.COOKSTACK);
          stateList.add(stateType.EXIT);
          break;
        case "OBJ_SIMPLE":
          expMessage = "SIMPLE INSTRUMENTED OBJECT";
          // setup valid states for test
          ignoreAllUninstrs();
          stateList.add(stateType.OBJBEGIN);
          stateList.add(stateType.COOKLOCAL);
          stateList.add(stateType.EXIT);
          break;
        case "OBJ_CLINIT":
          expMessage = "UNINSTRUMENTED OBJECT";
          // setup valid states for test
          ignoreList.add(stateType.TRY);
          ignoreList.add(stateType.SKIP);
//          ignoreList.add(stateType.CALLBACK);
          ignoreList.add(stateType.REPLACE);
          ignoreList.add(stateType.RUN);

          stateList.add(stateType.OBJBEGIN);
//          stateList.add(stateType.CALLBACK);
          stateList.add(stateType.COOKLOCAL);
          stateList.add(stateType.EXIT);
          break;
        case "UNKNOWN":
          expMessage = "EXPECTED values not defined in test";
          bFailure = true;
          break;
        default:
          expMessage = "- invalid type " + expType;
          break;
      }
        
      if (!expObject.isEmpty()) {
        debugPrint("EXPECTED: " + expObject);
        debugPrint("          " + expMessage);
      }
    }
    return true;
  }
  
  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    showMessages = 0;
    bFailure = false;
    stateIndex = 0;
    stateList = new ArrayList<>();
    ignoreList = new ArrayList<>();
    String inputfilename = "";
    String outputfilename = "";
    
    if (args.length < 2) {
      System.out.println("Usage: DanParse [-t] <inputfile> <outputfile>");
      System.exit(0);
    }
    
    // get user args
    for (String arg : args) {
      // check for test mode
      if (arg.equals("-t")) {
        showMessages = 1;
      } else if (arg.equals("-T")) {
        showMessages = 2;
      } else if (inputfilename.isEmpty()) {
        inputfilename = arg;
      } else {
        outputfilename = arg;
      }
    }

    // delete any pre-existing output file
    File file = new File(outputfilename);
    if (file.exists()) {
      file.delete();
    }
    
    // read and parse the input file
    file = new File(inputfilename);
    BufferedReader br;
    try {
      br = new BufferedReader(new FileReader(file));
      String line;
      boolean start = false;
      boolean end = false;
      while ((line = br.readLine()) != null && !end && !bFailure) {
        String[] array = line.split(" ");
        if (array.length > 0) {
          if (!start) {
            start = parseStart(array);
          } else {
            end = parseLine(line);
          }
        }
      }
      if (!end && !bFailure) {
        System.out.println("WARNING: No test exit message found!");
      }
    } catch (IOException ex) {
      System.out.println(ex.getMessage());
      System.exit(1);
    }

    // define the output file response
    String status = bFailure ? "FAIL" : "PASS";
    
    // open the file to write to
    try {
      PrintWriter writer = new PrintWriter(outputfilename);
      writer.println(status);
      writer.close();
    } catch (FileNotFoundException ex) {
      System.out.println(ex.getMessage());
      System.exit(1);
    }
  }
  
}
