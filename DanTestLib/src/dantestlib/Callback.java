/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dantestlib;

/**
 *
 * @author dmcd2356
 */
public class Callback {
  CallBack callbackImpl = null;
  
  // the callback interface
  public interface CallBack {
    int methodToCallBack();
  }

  public Callback(CallBack method) {
    callbackImpl = method;
  }
  
  public void makeCall() {
    if (callbackImpl != null) {
      int rc = callbackImpl.methodToCallBack();
      System.out.println("callback returned: " + rc);
    }
  }
  
}
