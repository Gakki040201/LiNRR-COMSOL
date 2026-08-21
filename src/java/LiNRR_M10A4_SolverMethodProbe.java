import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.lang.reflect.Method;

public final class LiNRR_M10A4_SolverMethodProbe {
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4SolverMethodProbe", "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\runs\\M10A4\\20260820_121258\\checkpoint_A4A_ohmic_current.mph");
    try {
      for (String path : new String[]{"sol19/s1","sol19/s1/fc1","sol19/s1/d1","sol19/s1/i1","sol19/s1/aDef"}) {
        String[] parts = path.split("/");
        Object f = m;
        try {
          if (parts.length == 2) f = m.sol(parts[0]).feature(parts[1]);
          else if (parts.length == 3) f = m.sol(parts[0]).feature(parts[1]).feature(parts[2]);
          System.out.println("=== " + path + " class=" + f.getClass().getName());
          for (Method meth : f.getClass().getMethods()) {
            if (meth.getParameterCount() == 0 && (meth.getName().startsWith("get") || meth.getName().startsWith("is") || meth.getName().equals("toString") || meth.getName().startsWith("set"))) {
              try {
                Object v = meth.invoke(f);
                String s = v == null ? "<null>" : (v instanceof String[] ? String.join(",", (String[]) v) : v.toString());
                if (s.length() < 300) System.out.println("METH|" + meth.getName() + "|" + meth.getReturnType().getSimpleName() + "|" + s);
              } catch (Throwable e) { }
            }
          }
        } catch(Throwable e) { System.out.println("ERR " + path + " " + e); }
      }
    } finally { ModelUtil.remove("M10A4SolverMethodProbe"); }
  }
}
