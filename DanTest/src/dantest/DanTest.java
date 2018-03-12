/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dantest;

/**
 *
 * @author dmcd2356
 */
public class DanTest {

  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    switch (args.length) {
      case 0:
        // if no args, run all tests
        int testcount = 0;
        testcount += runAllTests(new TestUninstrumented());
        testcount += runAllTests(new TestNewObject());
        System.out.println(testcount + " tests run");
      case 2:
        // else, run the specified test
        int testtype = Integer.parseInt(args[0]);
        int testnum = Integer.parseInt(args[1]);
        TestObject testObj;
        switch (testtype) {
          case 1:
            testObj = new TestUninstrumented();
            break;
          case 2:
            testObj = new TestNewObject();
            break;
          default:
            System.out.println("!!!INVALID");
            System.exit (1);
            return;
        }

        int retcode = testObj.run(testnum);
        String testname = testObj.getClass().toString();
        if (retcode >= 0) {
          System.out.println(testname + " " + testnum + " returned: " + retcode);
        } else {
          System.out.println(testname + " " + testnum + " was invalid selection");
          System.exit (1);
        }
        break;
      default:
        System.out.println("Invalid number of args. Either no args or <testtype> <testnum>");
        System.exit (1);
    }
  }

  private static int runAllTests(TestObject testObj) {
    String testname = testObj.getClass().toString();
    int testnum = 0;
    int retcode = 0;
    while (retcode >= 0) {
      retcode = testObj.run(++testnum);
      if (retcode >= 0) {
        System.out.println(testname + " " + testnum + " returned: " + retcode);
      }
    }
    return testnum;
  }
  
}
