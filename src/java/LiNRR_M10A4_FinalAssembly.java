import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Final warning-free accepted-medium M10A4 assembly. No solve and no cache removal. */
public final class LiNRR_M10A4_FinalAssembly {
 private static final String ROOT="F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED",RUN=ROOT+"\\runs\\M10A4\\20260820_121258",INPUT=RUN+"\\checkpoint_A4_electrical_loss.mph",PRE=RUN+"\\checkpoint_A4_final_pre_audit.mph",FINAL=ROOT+"\\models\\generated\\LiNRR_M10A4_ionic_current_li_plating.mph";
 public static void main(String[]a)throws Exception{if(Files.exists(Paths.get(PRE))||Files.exists(Paths.get(FINAL)))throw new IllegalStateException("REFUSE_OVERWRITE_FINAL_ARTIFACT");Model m=ModelUtil.load("M10A4Final",INPUT);try{
  if(m.study().hasTag("std_a4a_ohmic"))m.study("std_a4a_ohmic").label("A4A Ohmic Current Distribution");
  if(m.study().hasTag("std_a4b_flow_repair"))m.study("std_a4b_flow_repair").label("A4B Real Liquid Flow Support");
  if(m.study().hasTag("std_a4b_ionic_en"))m.study("std_a4b_ionic_en").label("A4B Ionic Transport");
  label(m,"pg00_physical_cell","00 | PHYSICAL CELL | Full 3D | REAL / PHYSICAL");
  label(m,"pg_a4a_electrolyte_potential","03 | IONICS | Electrolyte Potential | NOT FULL-CELL VOLTAGE");
  label(m,"pg_a4b_li_conc","03 | IONICS | Li+ Concentration | PROVISIONAL SENSITIVITY");
  label(m,"pg_a4b_bf4_conc","03 | IONICS | BF4- Concentration | PROVISIONAL SENSITIVITY");
  label(m,"pg_a4b_ionic_current_mag","03 | IONICS | Ionic Current Density | SPECIES-FLUX IDENTITY");
  label(m,"pg_a4c_cathode_current","04 | CURRENT DISTRIBUTION | Cathode Current Density | ELECTROLYTE OUTWARD");
  label(m,"pg_a4c_anode_current","04 | CURRENT DISTRIBUTION | Anode Current Density | ELECTROLYTE OUTWARD");
  label(m,"pg_a4c_nonuniformity","04 | CURRENT DISTRIBUTION | Current Nonuniformity | MAGNITUDE DIAGNOSTIC");
  label(m,"pg_a4c_crowding","04 | CURRENT DISTRIBUTION | Current-Crowding Diagnostic | SPATIAL ASSOCIATION");
  label(m,"pg_a4d_rate","05 | LI-EQUIVALENT DEPOSITION | Li-Equivalent Deposition Rate | NUMERICAL UPPER BOUND f=1");
  for(int q:new int[]{9,45,54,99,297})label(m,"pg_a4d_h"+q,"05 | LI-EQUIVALENT DEPOSITION | Li-Equivalent Thickness | "+q+" C | NUMERICAL UPPER BOUND f=1");
  label(m,"pg_a4e_n2_current","06 | SPATIAL CO-LIMITATION | N2-Current Spatial Overlap | DIAGNOSTIC ONLY");
  label(m,"pg_a4e_donor_current","06 | SPATIAL CO-LIMITATION | Donor-Current Spatial Overlap | GENERIC DONOR | CALIBRATION REQUIRED");
  label(m,"pg_a4e_colim","06 | SPATIAL CO-LIMITATION | N2-Li+-Donor-Current Co-Limitation | DIAGNOSTIC ONLY");
  label(m,"pg_a4e_robust","06 | SPATIAL CO-LIMITATION | Classification Robustness | q=0.40/0.50/0.60");
  label(m,"pg_a4loss_phi","07 | ELECTRICAL LOSS | Electrolyte Ohmic Drop | NOT FULL-CELL VOLTAGE");
  label(m,"pg_a4loss_joule","07 | ELECTRICAL LOSS | Joule Heating Density | NO THERMAL FEEDBACK");
  m.param().set("M10A4_mesh_max_key_difference","0.0621400622644018","DERIVED mesh convergence maximum; PASS_WITH_LIMITATION; coarse warnings retained separately");
  m.param().set("M10A4_mesh_medium_elements","87892","DERIVED accepted medium element count");
  m.param().set("M10A4_mesh_coarse_elements","85016","DERIVED diagnostic coarse element count from hmin 1.05 attempt");
  m.label("LiNRR_M10A4_ionic_current_li_plating.mph");
  m.comments("M10A4 real-cell ionic current and Li-equivalent deposition scaffold. Numerical verification and diagnostic sensitivities only. NOT Li-NRR kinetics; does not predict FE or retained metallic-Li thickness; no SEI, Li3N, HER, HOR, Heat Transfer, or thermal feedback.");
  m.save(PRE);System.out.println("M10A4_FINAL_PRE_AUDIT_CHECKPOINT="+PRE);m.save(FINAL);System.out.println("M10A4_FINAL_ASSEMBLY="+FINAL+"|solve_triggered=FALSE");
 }finally{ModelUtil.remove("M10A4Final");}}
 private static void label(Model m,String tag,String value){if(m.result().hasTag(tag))m.result(tag).label(value);else throw new IllegalStateException("REQUIRED_RESULT_MISSING "+tag);}
}
