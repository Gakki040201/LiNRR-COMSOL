import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

public final class LiNRR_M10A3_LiquidAuditProbe {
  private static int n=0;
  public static void main(String[] a)throws Exception{
    String file="F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A3\\runs\\M10A3\\20260814_200748_real_species\\checkpoint_06_nh3.mph";
    Model m=ModelUtil.load("P",file);try{
      String c="comp_species_liq_real",in="m10a3_sel_bnd_electrolyte_inlet",out="m10a3_sel_bnd_electrolyte_outlet",src="m10a3_sel_bnd_electrolyte_gde_top",dom="m10a3_sel_dom_electrolyte_fluid";
      for(String[] x:new String[][]{{"dset_species_liq_n2","cN2d","zN2","k_N2_ssc*(c_N2_eq_mapped-cN2d)"},{"dset_species_liq_nh3","cNH3","zNH3","J_NH3_source"}}){
        String d=x[0],f=x[1],z=x[2],S=x[3];
        for(int q:new int[]{2,3,4,5,6,8}){
          double rf=ev(m,"IntSurface",d,c,in,2,"reacf("+z+")","mol/s",q);
          double o=ev(m,"IntSurface",d,c,out,2,f+"*(u_liq_species*nx+v_liq_species*ny+w_liq_species*nz)","mol/s",q);
          double s=ev(m,"IntSurface",d,c,src,2,"(1-exp(-t/t_species_ramp))*("+S+")","mol/s",q);
          double ac=ev(m,"IntVolume",d,c,dom,3,"d("+f+",t)","mol/s",q);
          double ac2=ev(m,"IntVolume",d,c,dom,3,f+"*"+z+"t","mol/s",q);
          double res=-rf+s-o-ac;
          System.out.println("AUDIT|"+f+"|q="+q+"|reacf="+rf+"|in="+(-rf)+"|out="+o+"|src="+s+"|acc="+ac+"|acc2="+ac2+"|res="+res+"|rr="+(Math.abs(res)/Math.max(Math.max(Math.abs(o),Math.abs(s)),1e-300)));
        }
      }
    }finally{ModelUtil.remove("P");}
  }
  private static double ev(Model m,String type,String data,String comp,String sel,int dim,String expr,String unit,int q){String t="pa"+(n++);m.result().numerical().create(t,type);m.result().numerical(t).set("data",data);m.result().numerical(t).set("expr",expr);m.result().numerical(t).set("unit",unit);m.result().numerical(t).set("intorderactive",true);m.result().numerical(t).set("intorder",q);m.result().numerical(t).selection().geom(dim);m.result().numerical(t).selection().named(sel);double[][]v=m.result().numerical(t).getReal();m.result().numerical().remove(t);return v[0][v[0].length-1];}
}
