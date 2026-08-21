import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_A3FlowSolProbe {
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4A3FlowSolProbe", "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph");
    try {
      System.out.println("SOL_TAGS=" + String.join(",", m.sol().tags()));
      for (String s : new String[]{"sol12","sol13","sol14","sol15","sol16","sol17","sol18"}) if (m.sol().hasTag(s)) System.out.println("SOL|" + s + "|type=" + m.sol(s).getType() + "|label=" + m.sol(s).label());
      for (String d : new String[]{"dset_species_liq_flow","dset_species_liq_n2","dset_species_liq_nh3"}) if (m.result().dataset().hasTag(d)) System.out.println("DATASET|" + d + "|solution=" + m.result().dataset(d).getString("solution") + "|comp=" + m.result().dataset(d).getString("comp"));
      for (String s : new String[]{"std_species_liq_flow","std_species_liq_n2","std_species_liq_nh3"}) if (m.study().hasTag(s)) { String[] sols=m.study(s).getSolverSequences("SolverSequence"); System.out.println("STUDY|" + s + "|solvers=" + String.join(",", sols)); }
      String c="comp_species_liq_real";
      for (String vt : m.component(c).variable().tags()) { String[] names=m.component(c).variable(vt).varnames(); for(String n:names){String def=m.component(c).variable(vt).get(n); if(n.contains("liq_species")||def.contains("sol14")) System.out.println("VAR|"+vt+"|"+n+"|"+def);} }
      m.result().numerical().create("evu","AvVolume");
      try { m.result().numerical("evu").set("data","dset_species_liq_flow"); m.result().numerical("evu").selection().geom(m.component(c).geom().tags()[0],3); m.result().numerical("evu").selection().set(m.component(c).selection("m10a3_sel_dom_electrolyte_fluid").entities(3)); m.result().numerical("evu").set("expr", new String[]{"sqrt(u_liq_species^2+v_liq_species^2+w_liq_species^2)"}); m.result().numerical("evu").set("unit", new String[]{"m/s"}); double[][] v=m.result().numerical("evu").getReal(); System.out.println("FLOW_MEAN_SPEED=" + (v==null?"null":String.valueOf(v[0][v[0].length-1]))); } catch(Throwable e) { System.out.println("FLOW_EVAL_ERR " + e.getMessage()); } finally { m.result().numerical().remove("evu"); }
    } finally { ModelUtil.remove("M10A4A3FlowSolProbe"); }
  }
}
