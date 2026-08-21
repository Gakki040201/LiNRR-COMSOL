import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_CheckpointSolProbe {
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4CheckpointSolProbe", "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\runs\\M10A4\\20260820_121258\\checkpoint_A4A_ohmic_current.mph");
    try {
      System.out.println("SOL_TAGS=" + String.join(",", m.sol().tags()));
      System.out.println("STUDY_TAGS=" + String.join(",", m.study().tags()));
      System.out.println("DATASET_TAGS=" + String.join(",", m.result().dataset().tags()));
      for (String d : new String[]{"dset_species_liq_flow","dset_a4a_ohmic","dset_species_liq_n2"}) {
        if (m.result().dataset().hasTag(d)) {
          System.out.println("DATASET|" + d + "|solution=" + m.result().dataset(d).getString("solution") + "|comp=" + m.result().dataset(d).getString("comp"));
        } else System.out.println("DATASET_MISSING|" + d);
      }
      for (String s : m.sol().tags()) {
        try { System.out.println("SOLUTION|" + s + "|type=" + m.sol(s).getType() + "|label=" + m.sol(s).label()); } catch(Throwable e) { System.out.println("SOLUTION_ERR|" + s + "|" + e.getMessage()); }
      }
      for (String s : new String[]{"std_liq_stationary","std_species_liq_flow","std_species_liq_n2","std_species_liq_nh3","std_a4a_ohmic"}) {
        if (m.study().hasTag(s)) {
          String[] sols = m.study(s).getSolverSequences("SolverSequence");
          System.out.println("STUDY|" + s + "|solvers=" + String.join(",", sols));
        } else System.out.println("STUDY_MISSING|" + s);
      }
      String c = "comp_species_liq_real";
      for (String vt : m.component(c).variable().tags()) {
        String[] names = m.component(c).variable(vt).varnames();
        for (String n : names) {
          String def = m.component(c).variable(vt).get(n);
          if (n.contains("liq_species") || def.contains("sol14") || def.contains("withsol")) System.out.println("VAR|" + vt + "|" + n + "|" + def);
        }
      }
    } finally { ModelUtil.remove("M10A4CheckpointSolProbe"); }
  }
}
