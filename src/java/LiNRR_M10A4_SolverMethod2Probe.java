import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.lang.reflect.Method;

public final class LiNRR_M10A4_SolverMethod2Probe {
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4SolverMethod2Probe", "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\runs\\M10A4\\20260820_121258\\checkpoint_A4A_ohmic_current.mph");
    try {
      Object[] objs = {m.sol("sol19").feature("s1"), m.sol("sol19").feature("s1").feature("d1"), m.sol("sol19").feature("s1").feature("i1"), m.sol("sol19").feature("s1").feature("fc1")};
      String[] names = {"s1","d1","i1","fc1"};
      for(int k=0;k<objs.length;k++){
        Object f=objs[k]; System.out.println("=== "+names[k]+" class="+f.getClass().getName());
        for(Method meth: f.getClass().getMethods()){
          if(meth.getDeclaringClass().getName().startsWith("com.solwert") || meth.getDeclaringClass().getName().startsWith("java.lang") || meth.getDeclaringClass().getName().startsWith("java.") ) {}
          if(meth.getParameterCount()<=3 && (meth.getName().startsWith("set")||meth.getName().startsWith("is")||meth.getName().startsWith("get")||meth.getName().startsWith("active")||meth.getName().startsWith("remove")||meth.getName().startsWith("create"))){
            StringBuilder sb=new StringBuilder(meth.getName()+"(");
            for(Class<?> c:meth.getParameterTypes()){ sb.append(c.getSimpleName()).append(','); }
            sb.append("):").append(meth.getReturnType().getSimpleName());
            System.out.println(sb);
          }
        }
      }
    } finally { ModelUtil.remove("M10A4SolverMethod2Probe"); }
  }
}
