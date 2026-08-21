public final class LiNRR_M10A4_EnumProbe {
  public static void main(String[] args) throws Exception {
    for (String cn : new String[]{"com.comsol.model.applapi.VariableInfo$Type","com.comsol.model.applapi.ModelInfo$Type","com.comsol.model.applapi.PhysicsFeatureInfo$Type","com.comsol.model.applapi.VariableInfo"}) {
      try {
        Class<?> c = Class.forName(cn);
        System.out.println("CLASS=" + cn + " -> " + c.getName());
        if (c.isEnum()) { for (Object o : c.getEnumConstants()) System.out.println("ENUM|" + o); }
        else { for (java.lang.reflect.Method m : c.getMethods()) if (m.getParameterCount()==0) System.out.println("METHOD|" + m.getName()); }
      } catch (Throwable e) { System.out.println("CLASS_FAIL|" + cn + "|" + e); }
    }
  }
}
