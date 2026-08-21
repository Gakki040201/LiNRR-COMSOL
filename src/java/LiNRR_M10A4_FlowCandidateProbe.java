import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_FlowCandidateProbe {
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4FlowCandidateProbe", "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph");
    try {
      String c="comp_species_liq_real";
      for (String sol : new String[]{"sol4","sol5","sol12","sol13","sol14"}) {
        for (String field : new String[]{"u","v","w"}) {
          String t="ev_"+sol+"_"+field;
          try {
            m.result().numerical().create(t,"AvVolume");
            m.result().numerical(t).set("expr", new String[]{"withsol('"+sol+"',"+field+")"});
            m.result().numerical(t).set("unit", new String[]{"m/s"});
            m.result().numerical(t).selection().geom(m.component(c).geom().tags()[0],3);
            m.result().numerical(t).selection().set(m.component(c).selection("m10a3_sel_dom_electrolyte_fluid").entities(3));
            double[][] v=m.result().numerical(t).getReal();
            System.out.println("EVAL|"+sol+"|"+field+"|ok=" + (v!=null && v.length>0 && v[0].length>0 ? String.valueOf(v[0][v[0].length-1]) : "EMPTY"));
          } catch(Throwable e) { System.out.println("EVAL|"+sol+"|"+field+"|ERR="+e.getMessage()); }
          finally { if(m.result().numerical().hasTag(t)) m.result().numerical().remove(t); }
        }
      }
      for (String sol : new String[]{"sol4","sol5","sol12","sol13","sol14"}) {
        if (!m.sol().hasTag(sol)) continue;
        System.out.println("SOL_INFO|"+sol+"|type="+m.sol(sol).getType()+"|label="+m.sol(sol).label());
      }
    } finally { ModelUtil.remove("M10A4FlowCandidateProbe"); }
  }
}
