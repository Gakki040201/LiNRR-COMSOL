import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A3_StabSweepProbe {
  private static int n=0;
  public static void main(String[] a)throws Exception{Model m=ModelUtil.load("S","F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A3\\runs\\M10A3\\20260814_163052_real_species\\checkpoint_06_nh3.mph");try{
    for(String g:new String[]{"1e2[mol/m^3]/tds_liq.helem","1e4[mol/m^3]/tds_liq.helem","1e6[mol/m^3]/tds_liq.helem"}){
      m.component("comp_species_liq_real").physics("tds_liq").prop("MassConsistentStabilization").set("glim_mass",g);
      m.study("std_species_liq").run();
      String d="dset_species_liq",c="comp_species_liq_real";double mn=ev(m,"MinVolume",d,c,"m10a3_sel_dom_electrolyte_fluid",3,"cN2d","mol/m^3"),md=ev(m,"MinVolume",d,c,"m10a3_sel_dom_electrolyte_fluid",3,"cDonor","mol/m^3"),ma=ev(m,"MinVolume",d,c,"m10a3_sel_dom_electrolyte_fluid",3,"cNH3","mol/m^3"),src=-ev(m,"IntSurface",d,c,"m10a3_sel_bnd_electrolyte_gde_top",2,"tds_liq.ntflux_cN2d","mol/s"),out=ev(m,"IntSurface",d,c,"m10a3_sel_bnd_electrolyte_outlet",2,"tds_liq.ntflux_cN2d","mol/s");System.out.println("SWEEP|glim="+g+"|minN2="+mn+"|minDonor="+md+"|minNH3="+ma+"|src="+src+"|out="+out+"|rr="+(Math.abs(src-out)/Math.max(Math.abs(src),1e-300)));
    }
  }finally{ModelUtil.remove("S");}}
  private static double ev(Model m,String type,String data,String comp,String sel,int dim,String expr,String unit){String t="ps"+(n++);m.result().numerical().create(t,type);m.result().numerical(t).set("data",data);m.result().numerical(t).set("expr",expr);m.result().numerical(t).set("unit",unit);m.result().numerical(t).selection().geom(dim);m.result().numerical(t).selection().named(sel);double[][]v=m.result().numerical(t).getReal();m.result().numerical().remove(t);return v[0][v[0].length-1];}
}
