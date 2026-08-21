import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_SolverValueProbe {
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4SolverValueProbe", "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\runs\\M10A4\\20260820_121258\\checkpoint_A4A_ohmic_current.mph");
    try {
      Object d1 = m.sol("sol19").feature("s1").feature("d1");
      String[] vals = ((com.comsol.model.SolverFeature)d1).getAllowedPropertyValues("linsolver");
      System.out.println("LINSOLVER_ALLOWED=" + (vals==null?"null":String.join(",", vals)));
      System.out.println("LINSOLVER_CURRENT=" + ((com.comsol.model.SolverFeature)d1).getString("linsolver"));
    } finally { ModelUtil.remove("M10A4SolverValueProbe"); }
  }
}
