package org.arl.fjage.test

import org.arl.fjage.*
import org.arl.fjage.param.*;


public enum Params implements Parameter {
  x, y, z, a, b, s
}

public class ParamServerAgent extends Agent {
  // These will have automatic Getter/Setter Param.a and Param.b
  int a = 0;
  float b = 42.0;
  int z = 2;

  public int x = 1;

  // These are local variables (storage)
  private int z1 = 4;

  // This will be mapped to Param.y
  public float getY() { return 2; }
  public float getY(int index) { if (index == 1) return 3; }

  // This will be mapped to Param.s
  public String getS() { return "xxx"; }
  public String getS(int index) { if (index == 1) return "yyy"; }
  public String setS(int index, String val) { if (index == 1) return "yyy"; }

  // This will be mapped to Param.z for index 1
  public int getZ(int index) {
    if (index == 1) return z1;
  }

  public int setZ(int index, int val) {
    if (index == 1) {
      z1 = val;
      return z1;
    }
  }

  // This will allow Param.x to be only settable
  public int setX(int x) { return x+1; }
  public ParamServerAgent() {
    super();
  }

  @Override
  public void init() {
    register("server");
    add(new ParameterMessageBehavior(Params){
      @Override
      protected List<? extends Parameter> getParameterList(int ndx) {
        if (ndx < 0) return getParameterList();
        else if (ndx == 1) return getParameterList();
      }
    });
  }
}
