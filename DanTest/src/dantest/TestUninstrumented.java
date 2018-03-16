/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dantest;

import dantestlib.Callback;
import dantestlib.Callback.CallBack;
import java.util.ArrayList;

/**
 *
 * @author dmcd2356
 */
public class TestUninstrumented implements TestObject {

  private enum TestType { METH_NORMAL, METH_CALLBACK, METH_REPLACE, METH_REPL_NEST, UNKNOWN };
  
  @Override
  public int run(int testnum) {
    ArrayList list;
    switch(testnum) {
      case 1:
        // un-instrumented method replaced by overridden instrumented method
        expectedCondition(TestType.METH_NORMAL, "java/util/ArrayList.add(Ljava/lang/Object;)Z",
                                            "");
        UninstrNormal mylist6 = new UninstrNormal();
        break;
      case 2:
        // un-instrumented method performs callback to instrumented method
        expectedCondition(TestType.METH_CALLBACK, "dantestlib/Callback.makeCall()V",
                                             "methodToCallBack()I");
        Callback cb = new Callback(new UninstrCallback());
        cb.makeCall();
        break;
      case 3:
        // un-instrumented method replaced by overridden instrumented method
        expectedCondition(TestType.METH_REPLACE, "java/util/ArrayList.add(Ljava/lang/Object;)Z",
                                            "dantest/TestUninstrumented$UninstrReplace1");
        UninstrReplace1<String> mylist1 = new UninstrReplace1<>();
        list = mylist1;
        list.add("1stentry");
        //String entry = (String) list.get(0);
        break;
      case 4:
        // un-instrumented method replaced by overridden instrumented method that calls un-instr method
        expectedCondition(TestType.METH_REPL_NEST, "java/util/ArrayList.add(Ljava/lang/Object;)Z",
                                            "dantest/TestUninstrumented$UninstrReplace2");
        UninstrReplace2<String> mylist2 = new UninstrReplace2<>(3);
        list = mylist2;
        list.add("1stentry");
        break;
      default:
        System.out.println("!!!INVALID");
        System.exit (1);
    }
    System.out.println("TESTEXIT");
    return 0;
  }

  private void expectedCondition(TestType type, String uninstrmeth, String meth) {
    System.out.println("EXPECTED: " + type.toString() + " " + uninstrmeth + " " + meth);
  }
  
  private static class UninstrNormal {
    ArrayList<String> arrayList;

    private UninstrNormal() {
      arrayList = new ArrayList<>();
      arrayList.add("hello world!");
    }
  }

  private class UninstrReplace1<E> extends ArrayList<E> {

    private final int MAX_SIZE = 10;
    String[] arrayList;
    int size;

    private UninstrReplace1() {
      arrayList = new String[MAX_SIZE];
      size = 0;
    }
    
    @Override
    public boolean add(E item) {
      if (size < MAX_SIZE) {
        arrayList[size++] = (String) item;
      }
      return true;
    }
    
    @Override
    public E get(int index) {
      if (index < size) {
        return (E) arrayList[index];
      }
      return null;
    }
  }

  private static class UninstrReplace2<E> extends ArrayList<E> {

    private static int maxSize = 10;
    ArrayList<String> arrayList;

    private UninstrReplace2(int size) {
      arrayList = new ArrayList<>();
      maxSize = size;
    }
    
    @Override
    public boolean add(E item) {
      if (arrayList.size() < maxSize) {
        arrayList.add((String) item);
      }
      return true;
    }
    
    @Override
    public E get(int index) {
      if (arrayList.size() < maxSize) {
        return (E) arrayList.get(index);
      } else {
        return (E) null;
      }
    }
  }

  private class UninstrCallback implements CallBack {
    @Override
    public int methodToCallBack() {
      System.out.println("4 got called back!");
      return 4;
    }
  }

  public interface CallBackLocal {
    int methodToCallBack();
  }

  public class MyCallback {
    CallBackLocal callbackImpl = null;
  
    public MyCallback(CallBackLocal method) {
      callbackImpl = method;
    }
  
    public void makeCall() {
      if (callbackImpl != null) {
        int rc = callbackImpl.methodToCallBack();
        System.out.println("local callback returned: " + rc);
      }
    }
  }

}
