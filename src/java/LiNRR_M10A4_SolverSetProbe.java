import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_SolverSetProbe {
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4SolverSetProbe", "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\runs\\M10A4\\20260820_121258\\checkpoint_A4A_ohmic_current.mph");
    try {
      Object s1 = m.sol("sol19").feature("s1");
      Object d1 = m.sol("sol19").feature("s1").feature("d1");
      Object i1 = m.sol("sol19").feature("s1").feature("i1");
      Object fc1 = m.sol("sol19").feature("s1").feature("fc1");
      String[] props = {"active","isactive","linsolver","definesolver","solver","linearsolver","pardiso","pivot","checkconvex"};
      for (String p : props) {
        try { ((com.comsol.model.SolverFeature) d1).set(p, true); System.out.println("SET_BOOL_OK d1 " + p); } catch(Throwable e) { System.out.println("SET_BOOL_FAIL d1 " + p + " " + e.getClass().getSimpleName()); }
        try { ((com.comsol.model.SolverFeature) d1).set(p, "pardiso"); System.out.println("SET_STR_OK d1 " + p); } catch(Throwable e) { System.out.println("SET_STR_FAIL d1 " + p + " " + e.getClass().getSimpleName()); }
      }
      try { ((com.comsol.model.SolverFeature) i1).set("active", false); System.out.println("DEACTIVATE_OK i1"); } catch(Throwable e) { System.out.println("DEACTIVATE_FAIL i1 " + e); }
      try { ((com.comsol.model.SolverFeature) d1).set("active", true); System.out.println("ACTIVATE_OK d1"); } catch(Throwable e) { System.out.println("ACTIVATE_FAIL d1 " + e); }
    } finally { ModelUtil.remove("M10A4SolverSetProbe"); }
  }
}
