import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A4_FlowDatasetProbe {
  public static void main(String[] args) throws Exception {
    Model m = ModelUtil.load("M10A4FlowDatasetProbe", "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A3_real_species_transport.mph");
    try {
      String c="comp_species_liq_real";
      for (String sol : new String[]{"sol4","sol5","sol12","sol13","sol14"}) {
        String ds="ds_probe_"+sol;
        try {
          if (m.result().dataset().hasTag(ds)) m.result().dataset().remove(ds);
          m.result().dataset().create(ds,"Solution");
          m.result().dataset(ds).set("solution",sol);
          m.result().dataset(ds).set("comp",c);
          for (String field : new String[]{"u","v","w","p"}) {
            String t="ev_"+ds+"_"+field;
            try {
              m.result().numerical().create(t,"AvVolume");
              m.result().numerical(t).set("data",ds);
              m.result().numerical(t).set("expr", new String[]{field});
              m.result().numerical(t).set("unit", new String[]{"m/s"});
              m.result().numerical(t).selection().geom(m.component(c).geom().tags()[0],3);
              m.result().numerical(t).selection().set(m.component(c).selection("m10a3_sel_dom_electrolyte_fluid").entities(3));
              double[][] v=m.result().numerical(t).getReal();
              System.out.println("EVAL|"+sol+"|"+field+"|ok=" + (v!=null && v.length>0 && v[0].length>0 ? String.valueOf(v[0][v[0].length-1]) : "EMPTY"));
            } catch(Throwable e) { System.out.println("EVAL|"+sol+"|"+field+"|ERR="+e.getMessage()); }
            finally { if(m.result().numerical().hasTag(t)) m.result().numerical().remove(t); }
          }
        } finally { if(m.result().dataset().hasTag(ds)) m.result().dataset().remove(ds); }
      }
    } finally { ModelUtil.remove("M10A4FlowDatasetProbe"); }
  }
}
