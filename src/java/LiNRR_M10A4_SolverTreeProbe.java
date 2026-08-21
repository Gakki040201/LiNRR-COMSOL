import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_SolverTreeProbe {
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4SolverTreeProbe", "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\runs\\M10A4\\20260820_121258\\checkpoint_A4A_ohmic_current.mph");
    try {
      for (String s : m.sol().tags()) {
        System.out.println("SOL|" + s + "|type=" + m.sol(s).getType());
        for (String a : m.sol(s).feature().tags()) {
          System.out.println("  FEATURE|" + a + "|type=" + m.sol(s).feature(a).getType());
          for (String b : m.sol(s).feature(a).feature().tags()) {
            System.out.println("    SUB|" + b + "|type=" + m.sol(s).feature(a).feature(b).getType());
            for (String c : m.sol(s).feature(a).feature(b).feature().tags()) {
              System.out.println("      LEAF|" + c + "|type=" + m.sol(s).feature(a).feature(b).feature(c).getType());
            }
          }
        }
      }
    } finally { ModelUtil.remove("M10A4SolverTreeProbe"); }
  }
}
