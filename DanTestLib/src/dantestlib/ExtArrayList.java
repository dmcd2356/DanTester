/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dantestlib;

import java.util.ArrayList;

/**
 *
 * @author dmcd2356
 */
  public class ExtArrayList<E> {

    private E initObj;
    private ArrayList<E> arrayList;

    public ExtArrayList() {
      initObj = null;
      arrayList = new ArrayList<>();
    }
    
    public ExtArrayList(E obj) {
      initObj = obj;
      arrayList = new ArrayList<>();
    }
    
    public boolean add(E item) {
      arrayList.add(item);
      return true;
    }
    
    public E get(int index) {
      return arrayList.get(index);
    }
    
    public E first() {
      return initObj;
    }

  }
