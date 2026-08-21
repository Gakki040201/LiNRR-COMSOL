import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.util.Locale;

/** Read-only wall-entity audit used to design a distinct low-impact coarse mesh. */
public final class LiNRR_M10A4_WallAudit {
 private static final String MPH="F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\runs\\M10A4\\20260820_121258\\checkpoint_A4_electrical_loss.mph",COMP="comp_species_liq_real",GEOM="geom_electrolyte_fluid1",WALL="m10a3_sel_bnd_electrolyte_walls",DATA="dset_a4b_ionic_en";private static int n=0;
 public static void main(String[]a)throws Exception{Model m=ModelUtil.load("WallAudit",MPH);try{for(int id:m.component(COMP).selection(WALL).entities(2)){double ar=e(m,id,"IntSurface","1","m^2"),x=e(m,id,"AvSurface","x","m"),y=e(m,id,"AvSurface","y","m"),z=e(m,id,"AvSurface","z","m");System.out.println("WALL|id="+id+"|area_m2="+f(ar)+"|x="+f(x)+"|y="+f(y)+"|z="+f(z));}}finally{ModelUtil.remove("WallAudit");}}
 private static double e(Model m,int id,String type,String ex,String u){String t="wa"+(++n);m.result().numerical().create(t,type);try{m.result().numerical(t).set("data",DATA);m.result().numerical(t).selection().geom(GEOM,2);m.result().numerical(t).selection().set(new int[]{id});m.result().numerical(t).set("expr",new String[]{ex});m.result().numerical(t).set("unit",new String[]{u});double[][]v=m.result().numerical(t).getReal();return v[0][v[0].length-1];}finally{m.result().numerical().remove(t);}}
 private static String f(double x){return String.format(Locale.ROOT,"%.15g",x);}
}
