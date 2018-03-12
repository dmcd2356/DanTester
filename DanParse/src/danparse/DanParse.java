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
  
  private static enum stateType { NONE, ERROR,
                                  TRY, SKIP, CALLBACK, INSERT, REPLACE, RUN,
                                  OBJBEGIN, COOKSTACK, COOKLOCAL };
  
  // set from expected settings
  private static String expType;          // the expected test type to run
  private static String expObject;        // the expected method or object to be tested
  private static String expOther;         // the expected other method (UNINSTR tests)
  private static testType expTest;        // the type of test we are running
  
  private static String  linenum;         // the line number being processed
  private static stateType runState;      // current running state
  private static int     showMessages;    // 1 to print state messages, 2 to print all messages
  private static boolean bFailure;        // true if test failure occurred
  
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
  
  private static boolean isValidNumeric(String str) {
    try {
      int value = Integer.parseInt(str);
    } catch (NumberFormatException ex) {
      return false;
    }
    return true;
  }
  
  private static void setTestFail(stateType newState, String error) {
    debugPrint("FAIL - " + expType + " :: STATE_" + newState.toString() + " :: " + error);
    bFailure = true;
  }

  private static void setTestPass(stateType newState) {
    debugPrint("PASS - " + expType + " :: STATE_" + newState.toString());
    runState = newState;
  }
  
  private static void checkStateNextUninst(stateType newState) {
    // ignore entries not pertanent to the test
    if (newState == stateType.NONE || newState == stateType.ERROR) {
      return;
    }

    // compile the list of valid next states for the current one
    ArrayList<stateType> validStates = new ArrayList<>();
    
    switch (expType) {
      case "NORMAL":
        switch (runState) {
          case NONE:
            validStates.add(stateType.TRY);
            break;
          case TRY:
            validStates.add(stateType.RUN);
            break;
          default:
            break;
        }
        break;
      case "CALLBACK":
        switch (runState) {
          case NONE:
            validStates.add(stateType.TRY);
            break;
          case TRY:
            validStates.add(stateType.CALLBACK);
            break;
          case CALLBACK:
            validStates.add(stateType.RUN);
            break;
          default:
            break;
        }
        break;
      case "INSERT":
        switch (runState) {
          case NONE:
            validStates.add(stateType.TRY);
            break;
          case TRY:
            validStates.add(stateType.INSERT);
            break;
          case INSERT:
            validStates.add(stateType.RUN);
            break;
          default:
            break;
        }
        break;
      case "REPLACE":
        switch (runState) {
          case NONE:
            validStates.add(stateType.TRY);
            break;
          case TRY:
            validStates.add(stateType.REPLACE);
            validStates.add(stateType.RUN);   // this allows a nested call to the un-instr case
            break;
          case REPLACE:
            validStates.add(stateType.SKIP);
            validStates.add(stateType.TRY);   // this allows a nested call to the un-instr case
            break;
          case RUN:
            validStates.add(stateType.SKIP);   // this allows a nested call to the un-instr case
            break;
          default:
            break;
        }
        break;
      default:
        return; // invalid expected type - should never happen
    }

    // check if no valid next states for the specified state
    if (validStates.isEmpty()) {
      setTestFail(newState, "invalid state");
      return;
    }
    
    // now loop thru valid states to verify the entered state is correct
    String validlist = "";
    for (stateType validCase : validStates) {
      validlist += validCase.toString() + " ";
      if (newState == validCase) {
        setTestPass(newState);
        return;
      }
    }

    setTestFail(newState, "valid state(s): " + validlist);
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
      case "INSERT:":     // INSERT: [skipLevel] <newMethod> BEFORE: <origMethod>
        state = stateType.INSERT;
        newMethod = array[2];
        origMethod = array[4];
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
    String objType = "";

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
  
  private static void parseLine(String line) {
    stateType state;
    // verify the line is valid (starts with "xxxxxxxx [xx:xx.xxx] " (line number and timestamp)
    // and the message begins at char offset 29, so the string must be longer than this
    // just exit to ignore any invalid lines.
    if (line.length() < 30 ||
        line.charAt(9) != '[' || line.charAt(12) != ':' ||
        line.charAt(15) != '.' || line.charAt(19) != ']' ||
        !isValidNumeric(line.substring(0, 8)) ||
        !isValidNumeric(line.substring(10, 11)) ||
        !isValidNumeric(line.substring(13, 14)) ||
        !isValidNumeric(line.substring(16, 18)) ) {
      return;
    }
    
    linenum = line.substring(0, 8);
    line = line.substring(21);

    String debugType = line.substring(0, 6).trim();
    String message = line.substring(8).trim();
    switch (debugType) {
      case "TEST":
        if (!message.contains(" ")) {
          runState = stateType.ERROR;
          debugPrint("ERROR line " + linenum + ": Invalid TEST line: " + message);
          return;
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
        if (expTest == testType.UNINSTRUMENTED && type.equals("UNINSTR")) {
          extendedPrint(linenum + ": " + message);
          state = parseTestUninstrumented(message);
          checkStateNextUninst(state);
        } else if (expTest == testType.OBJECT && type.equals("OBJECT")) {
          extendedPrint(linenum + ": " + message);
          state = parseTestObject(message);
//          checkStateNextObject(state);
        }
        break;
      case "UNINST":
        if (expTest == testType.UNINSTRUMENTED) {
          message = message.trim();
          extendedPrint(linenum + ": " + message);
          if (expObject.equals(message)) {
            state = stateType.RUN;
          } else {
            state = stateType.NONE;
          }

          // now check the new state to see if it is valid
          if (state != stateType.ERROR) {
            checkStateNextUninst(state);
          }
        }
        break;
      default:
        break;
    }
  }
  
  /**
   * parse a line from the debug file to see if we have the start condition.
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
      switch (expType) {
        case "NORMAL":
          expMessage = "no change (uninstrumented method called)";
          expTest = testType.UNINSTRUMENTED;
          break;
        case "CALLBACK":
          expMessage = "CALLBACK to " + expOther;
          expTest = testType.UNINSTRUMENTED;
          break;
        case "INSERT":
          expMessage = "INSERTED " + expOther;
          expTest = testType.UNINSTRUMENTED;
          break;
        case "REPLACE":
          String methodonly = "";
          int offset = expObject.indexOf('.');
          if (offset > 0) {
            methodonly = expObject.substring(offset);
          }
          expMessage = "REPLACED by " + expOther + methodonly;
          expTest = testType.UNINSTRUMENTED;
          break;
        case "INSTR":
          expMessage = "INSTRUMENTED OBJECT";
          expTest = testType.OBJECT;
          break;
        case "UNINSTR":
          expMessage = "UNINSTRUMENTED OBJECT";
          expTest = testType.OBJECT;
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
   * parse a line from the debug file to see if we have the exit condition.
   * 
   * @param line - the line from the file
   * @return true if we got the exit condition
   */
  private static boolean parseExit(String[] array) {
    if (array.length == 0 || !array[0].equals("TESTEXIT")) {
      return false;
    }
    
    // if no errors yet, check on the exit state for each expected type.
    if (!bFailure) {
      // all case should end in RUN state, except for REPLACE type, which should end in SKIP state
      switch (expType) {
        case "REPLACE":
          if (runState != stateType.SKIP) {
            bFailure = true;
          }
          break;
        default:
          if (runState != stateType.RUN) {
            bFailure = true;
          }
          break;
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
    runState = stateType.NONE;
    expTest = testType.NONE;
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
            if (bFailure) {
              break;
            }
          } else {
            parseLine(line);
            end = parseExit(array);
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
