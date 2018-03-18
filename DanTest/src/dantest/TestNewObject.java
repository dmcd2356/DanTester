/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dantest;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import javax.swing.Timer;
import dantestlib.ZipDecompress;
import dantestlib.FileVisitor;
import java.io.File;

/**
 *
 * @author dmcd2356
 */
public class TestNewObject implements TestObject {
  
  private enum TestType { OBJ_UNINTRUMENTED, OBJ_SIMPLE, OBJ_CLINIT, UNKNOWN };
  
  @Override
  public int run(int testnum) {
    switch(testnum) {
      case 1:
        // create simple uninstrumented object
        expectedCondition(TestType.OBJ_UNINTRUMENTED, "java.util.ArrayList");
        ArrayList<String> obj1 = new ArrayList<>();
        break;
      case 2:
        // create simple instrumented object
        expectedCondition(TestType.OBJ_SIMPLE, "dantest.TestNewObject$NewObject2");
        NewObject2 obj2 = new NewObject2(1);
        break;
      case 3:
        // create instrumented object that has static init
        expectedCondition(TestType.OBJ_CLINIT, "dantest.TestNewObject$NewObject3");
        NewObject3 obj3 = new NewObject3(1);
//        ArrayList<NewObject3> obj3;
//        obj3 = new ArrayList<>();
//        obj3.add(new NewObject3(1));
        break;
      case 4:
        // create instrumented object that creates another instrumented object
        expectedCondition(TestType.UNKNOWN, "dantest.TestNewObject$NewObject4");
        NewObject4 obj4 = new NewObject4();
        break;
      case 5:
        // create instrumented object that creates an uninstrumented object (NOT WORKING!)
        expectedCondition(TestType.UNKNOWN, "dantest.TestNewObject$NewObject5");
        NewObject5 obj5 = new NewObject5();
        break;
      case 6:
        // call un-instrumented method that gets instrumented callback that returns an object (NOT WORKING!)
        expectedCondition(TestType.UNKNOWN, "dantest.TestNewObject$NewObject6");
        NewObject6 obj6 = new NewObject6();
        break;
      default:
        System.out.println("!!!INVALID");
        System.exit (1);
    }
    System.out.println("TESTEXIT");
    return 0;
  }

  private void expectedCondition(TestType type, String objectType) {
    System.out.println("EXPECTED: " + type.toString() + " " + objectType);
  }
  
  private class NewObject2 {
    public int value;
    NewObject2(int val) {
      value = val;
    }
  }
  
  private static class NewObject3 {
    public static int value = 0;
    public static String[] array = { "hello", "world" };
    
    NewObject3(int val) {
      value = val;
    }
  }
  
  private class NewObject4 {
    public MySimpleObject obj;

    NewObject4() {
      obj = new MySimpleObject(1);
    }
    
    private class MySimpleObject {
      public int value;
      MySimpleObject(int val) {
        value = val;
      }
    }
  }
  
  private class NewObject5 {
    public MyComplexObject obj;

    NewObject5() {
      obj = new MyComplexObject(1);
    }

    private class MyComplexObject {
      public ArrayList<MySimpleObject> obj1;
      public MySimpleObject obj2;
      MyComplexObject(int val) {
        obj2 = new MySimpleObject(val);
        obj1 = new ArrayList<>();
        obj1.add(obj2);
      }
    }
    
    private class MySimpleObject {
      public int value;
      MySimpleObject(int val) {
        value = val;
      }
    }
  }

  private class NewObject6 {
    Timer timer;
    NewObject6() {
      String path = "resources/Pettables.zip";
      ZipDecompress zd = new ZipDecompress();
      try {
        final FileInputStream filestream = new FileInputStream(path);
        if (filestream.available() <= 0) {
          System.out.println("No content loaded in file:" + path);
        }
        Path tempdir = Files.createTempDirectory("", (FileAttribute<?>[])new FileAttribute[0]);
        tempdir.toFile().deleteOnExit();
        zd.decompress(path, tempdir.toString());

        System.out.println("Temp dir:" + tempdir.toString());
        int count = new File(tempdir.toString()).listFiles().length;
        System.out.println("files in dir:" + count);

        FileVisitor visitor = new FileVisitor();
        Files.walkFileTree(tempdir, visitor);
      }
      catch (IOException ex) {
        System.out.println("ERROR:" + ex.getMessage());
      }
    }
    
  }
  
}
