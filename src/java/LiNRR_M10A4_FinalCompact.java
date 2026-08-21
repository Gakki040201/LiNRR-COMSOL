import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

/**
 * Storage-only final compaction from the immutable pre-audit checkpoint.
 * Keeps final snapshots needed by M10A4 and clears only non-required cached solutions.
 * Never runs a study and never overwrites an accepted checkpoint.
 */
public final class LiNRR_M10A4_FinalCompact {
  private static final String INPUT="F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\runs\\M10A4\\20260820_121258\\checkpoint_A4_final_pre_audit.mph";
  private static final String CHECKPOINT="F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\runs\\M10A4\\20260820_121258\\checkpoint_A4_final_compact_attempt2.mph";
  private static final String OUTPUT="F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A4_ionic_current_li_plating_compact_attempt2.mph";
  private LiNRR_M10A4_FinalCompact(){}
  public static void main(String[]args)throws Exception{
    Model m=ModelUtil.load("M10A4Compact",INPUT);
    try{
      compactLast(m,"sol21");
      String[] keep={"sol15","sol19","sol20","sol21"};
      for(String s:m.sol().tags())if(!contains(keep,s)){m.sol(s).clearSolution();System.out.println("COMPACT_CLEAR_CACHE|solver="+s);}
      m.param().set("m10a4_compacted_storage","1","DERIVED_DIAGNOSTIC final archive retains only required M10A4 result-dependency solution data");
      m.param().set("m10a4_compaction_required_solvers","4","DERIVED_DIAGNOSTIC full sol15 N2/donor; sol19 A4A; sol20 real flow; sol21 A4B final state");
      m.save(CHECKPOINT);
      System.out.println("M10A4_COMPACT_CHECKPOINT_SAVED="+CHECKPOINT);
      m.save(OUTPUT);
      System.out.println("M10A4_COMPACT_OUTPUT_SAVED="+OUTPUT);
      System.out.println("M10A4_COMPACTION_SOLVE_TRIGGERED=FALSE");
    }finally{ModelUtil.remove("M10A4Compact");}
  }
  private static void compactLast(Model m,String sol){double[]p=m.sol(sol).getPVals();if(p==null||p.length==0)throw new IllegalStateException("REQUIRED_SOLUTION_EMPTY "+sol);int last=p.length;double time=p[last-1];double[]u=m.sol(sol).getU(last);m.sol(sol).clearSolution();m.sol(sol).setPVals(new double[]{time});m.sol(sol).setU(1,u);m.sol(sol).createSolution();if(m.sol(sol).getPVals().length!=1)throw new IllegalStateException("COMPACTION_COUNT_MISMATCH "+sol);System.out.println("COMPACT_LAST|solver="+sol+"|original_snapshots="+p.length+"|retained_snapshots=1|parameter="+time+"|dofs="+u.length);}
  private static boolean contains(String[]a,String x){for(String s:a)if(s.equals(x))return true;return false;}
}
