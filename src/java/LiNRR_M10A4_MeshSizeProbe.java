import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/** Read-only accepted liquid mesh size-feature probe. */
public final class LiNRR_M10A4_MeshSizeProbe {
 public static void main(String[]a)throws Exception{Model m=ModelUtil.load("MeshSizeProbe","F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\runs\\M10A4\\20260820_121258\\checkpoint_A4_electrical_loss.mph");try{String c="comp_species_liq_real",mesh=m.component(c).mesh().tags()[0];System.out.println("MESH|tag="+mesh+"|elements="+m.component(c).mesh(mesh).getNumElem());for(String t:m.component(c).mesh(mesh).feature().tags()){System.out.println("FEATURE|tag="+t+"|label="+m.component(c).mesh(mesh).feature(t).label());for(String p:new String[]{"hauto","custom","hmax","hmin","hgrad","hcurve","hnarrow"})try{System.out.println("PROP|tag="+t+"|"+p+"="+m.component(c).mesh(mesh).feature(t).getString(p));}catch(Exception ignored){}}}finally{ModelUtil.remove("MeshSizeProbe");}}
}
