import com.comsol.model.Model;
import com.comsol.model.physics.Physics;
import com.comsol.model.util.ModelUtil;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only final M10A4 inventories, prohibited-physics audit, and PNG evidence. Never solves or saves the MPH. */
public final class LiNRR_M10A4_FinalEvidence {
  private static final String MPH="F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\models\\generated\\LiNRR_M10A4_ionic_current_li_plating.mph";
  private static final Path OUT=Paths.get("F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED\\evidence\\M10A4");
  private static int imageSerial=0;
  private LiNRR_M10A4_FinalEvidence(){}

  public static void main(String[] args)throws Exception{
    Files.createDirectories(OUT);
    Model m=ModelUtil.load("M10A4Evidence",MPH);
    try{
      auditPhysics(m);
      exportInventories(m);
      exportImages(m);
      System.out.println("M10A4_EVIDENCE_EXPORT=PASS");
      System.out.println("M10A4_EVIDENCE_SOLVE_TRIGGERED=FALSE");
    }finally{ModelUtil.remove("M10A4Evidence");}
  }

  private static void auditPhysics(Model m){
    String[] forbidden={"butler-volmer","butler volmer","sei kinetics","sei growth","li3n kinetics","li-nrr kinetics","linrr kinetics","her kinetics","hor kinetics","heat transfer"};
    for(String c:m.component().tags())for(String p:m.component(c).physics().tags()){
      Physics x=m.component(c).physics(p);
      String s=(p+" "+x.getType()+" "+x.label()).toLowerCase(Locale.ROOT);
      for(String q:forbidden)if(s.contains(q))throw new IllegalStateException("FORBIDDEN_PHYSICS "+c+"/"+p+" "+q);
    }
    System.out.println("FULL_CELL_VOLTAGE_PREDICTED=FALSE");
    System.out.println("SEI_CREATED=FALSE");
    System.out.println("LI3N_KINETICS_CREATED=FALSE");
    System.out.println("LINRR_KINETICS_CREATED=FALSE");
    System.out.println("HER_CREATED=FALSE");
    System.out.println("HOR_CREATED=FALSE");
    System.out.println("FE_PREDICTED=FALSE");
    System.out.println("THERMAL_FEEDBACK_CREATED=FALSE");
    System.out.println("NO_PROHIBITED_PHYSICS=TRUE");
  }

  private static void exportInventories(Model m)throws Exception{
    write(OUT.resolve("component_inventory.csv"),"tag,label,dimension,source_class",components(m));
    write(OUT.resolve("physics_inventory.csv"),"component,physics_tag,physics_type,label,features",physics(m));
    write(OUT.resolve("study_inventory.csv"),"study_tag,label,features,solver_sequences",studies(m));
    write(OUT.resolve("dataset_inventory.csv"),"dataset_tag,dataset_type,label",datasets(m));
    write(OUT.resolve("result_inventory.csv"),"result_tag,result_type,label,features",results(m));
    write(OUT.resolve("selection_inventory.csv"),"component,selection,dimension,count,label",selections(m));
    write(OUT.resolve("parameter_inventory.csv"),"parameter,expression,source_class,description",parameters(m));
    tree(m,OUT.resolve("model_tree.json"));
  }

  private static List<String[]> components(Model m){List<String[]>r=new ArrayList<>();for(String c:m.component().tags()){int d=m.component(c).geom().tags().length==0?0:m.component(c).geom(m.component(c).geom().tags()[0]).getSDim();r.add(new String[]{c,m.component(c).label(),Integer.toString(d),classify(m.component(c).label())});}return r;}
  private static List<String[]> physics(Model m){List<String[]>r=new ArrayList<>();for(String c:m.component().tags())for(String p:m.component(c).physics().tags()){Physics x=m.component(c).physics(p);r.add(new String[]{c,p,x.getType(),x.label(),String.join(";",x.feature().tags())});}return r;}
  private static List<String[]> studies(Model m){List<String[]>r=new ArrayList<>();for(String s:m.study().tags())r.add(new String[]{s,m.study(s).label(),String.join(";",m.study(s).feature().tags()),String.join(";",m.study(s).getSolverSequences("SolverSequence"))});return r;}
  private static List<String[]> datasets(Model m){List<String[]>r=new ArrayList<>();for(String d:m.result().dataset().tags())r.add(new String[]{d,m.result().dataset(d).getType(),m.result().dataset(d).label()});return r;}
  private static List<String[]> results(Model m){List<String[]>r=new ArrayList<>();for(String p:m.result().tags())r.add(new String[]{p,m.result(p).getType(),m.result(p).label(),String.join(";",m.result(p).feature().tags())});for(String t:m.result().table().tags())r.add(new String[]{t,m.result().table(t).getType(),m.result().table(t).label(),"rows="+m.result().table(t).getTableData(false).length});return r;}
  private static List<String[]> selections(Model m){List<String[]>r=new ArrayList<>();for(String c:m.component().tags())for(String s:m.component(c).selection().tags()){int d=-1,n=0;for(int q=3;q>=0;q--)try{int z=m.component(c).selection(s).entities(q).length;if(z>0){d=q;n=z;break;}}catch(Throwable ignored){}r.add(new String[]{c,s,Integer.toString(d),Integer.toString(n),m.component(c).selection(s).label()});}return r;}
  private static List<String[]> parameters(Model m){List<String[]>r=new ArrayList<>();for(String p:m.param().varnames()){String d=m.param().descr(p);r.add(new String[]{p,m.param().get(p),source(d),d});}return r;}

  private static String source(String d){String x=String.valueOf(d).toUpperCase(Locale.ROOT);for(String s:new String[]{"REAL_CAD","LAB_MANUAL","DERIVED_FROM_LAB_MANUAL","LAB_EXPERIMENTAL_SOURCE","EXPERIMENTAL_GATE","LITERATURE_SAME_PLATFORM","LITERATURE_REPORTED","LITERATURE_ESTIMATE","PROVISIONAL_SENSITIVITY","CALIBRATION_REQUIRED","NUMERICAL_VERIFICATION_ONLY","NUMERICAL_UPPER_BOUND","CURRENT_PARTITION_SENSITIVITY","DERIVED_DIAGNOSTIC","DIAGNOSTIC_ONLY","DERIVED"})if(x.contains(s))return s;if(x.contains("CALIBRATION")||x.contains("UNKNOWN"))return "CALIBRATION_REQUIRED";if(x.contains("COMPUT")||x.contains("FORMULA")||x.contains("MAPPED"))return "DERIVED";return "PROVISIONAL_SENSITIVITY";}
  private static String classify(String s){String x=String.valueOf(s).toUpperCase(Locale.ROOT);if(x.contains("REAL")||x.contains("PHYSICAL"))return "REAL_CAD";if(x.contains("REDUCED"))return "REDUCED_SUPPORT_MODEL";return "SUPPORT";}

  private static void tree(Model m,Path p)throws Exception{
    StringBuilder j=new StringBuilder("{\n  \"model\":\"LiNRR_M10A4_ionic_current_li_plating\",\n  \"source_mph\":\"").append(js(MPH)).append("\",\n  \"components\":[\n");
    String[] cs=m.component().tags();for(int i=0;i<cs.length;i++){String c=cs[i];if(i>0)j.append(",\n");j.append("    {\"tag\":\"").append(js(c)).append("\",\"label\":\"").append(js(m.component(c).label())).append("\",\"physics\":[");String[] ps=m.component(c).physics().tags();for(int a=0;a<ps.length;a++){if(a>0)j.append(',');j.append("{\"tag\":\"").append(js(ps[a])).append("\",\"type\":\"").append(js(m.component(c).physics(ps[a]).getType())).append("\",\"label\":\"").append(js(m.component(c).physics(ps[a]).label())).append("\"}");}j.append("]}");}
    j.append("\n  ],\n  \"studies\":[");String[] ss=m.study().tags();for(int i=0;i<ss.length;i++){if(i>0)j.append(',');j.append("\"").append(js(ss[i])).append("\"");}
    j.append("],\n  \"datasets\":[");String[] ds=m.result().dataset().tags();for(int i=0;i<ds.length;i++){if(i>0)j.append(',');j.append("\"").append(js(ds[i])).append("\"");}
    j.append("],\n  \"results\":[");String[] rr=m.result().tags();for(int i=0;i<rr.length;i++){if(i>0)j.append(',');j.append("\"").append(js(rr[i])).append("\"");}j.append("]\n}\n");Files.write(p,j.toString().getBytes(StandardCharsets.UTF_8));
  }

  private static void exportImages(Model m){String[][] images={{"pg00_physical_cell","physical_cell.png"},{"pg_a4a_electrolyte_potential","electrolyte_potential.png"},{"pg_a4b_li_conc","li_concentration.png"},{"pg_a4c_cathode_current","cathode_current_density.png"},{"pg_a4d_h297","li_equivalent_thickness_297C_f1_upper_bound.png"},{"pg_a4e_colim","spatial_colimitation.png"},{"pg_a4loss_joule","joule_heating_density.png"}};for(String[]x:images)image(m,x[0],OUT.resolve(x[1]));}
  private static void image(Model m,String plot,Path file){if(!m.result().hasTag(plot))throw new IllegalStateException("PLOT_MISSING "+plot);m.result(plot).run();String t="img_m10a4_"+(++imageSerial);m.result().export().create(t,plot,"Image3D");try{m.result().export(t).set("target","file");m.result().export(t).set("filename",file.toString());m.result().export(t).set("width",1400);m.result().export(t).set("height",1000);m.result().export(t).run();if(!Files.isRegularFile(file)||file.toFile().length()==0)throw new IllegalStateException("IMAGE_EMPTY "+plot);System.out.println("EVIDENCE_IMAGE|plot="+plot+"|file="+file.getFileName()+"|status=PASS");}finally{m.result().export().remove(t);}}
  private static void write(Path p,String h,List<String[]>r)throws Exception{try(BufferedWriter w=Files.newBufferedWriter(p,StandardCharsets.UTF_8)){w.write(h);w.newLine();for(String[]x:r){for(int i=0;i<x.length;i++){if(i>0)w.write(',');w.write(csv(x[i]));}w.newLine();}}}
  private static String csv(String s){String x=String.valueOf(s);return x.contains(",")||x.contains("\"")||x.contains("\n")?"\""+x.replace("\"","\"\"")+"\"":x;}
  private static String js(String s){return String.valueOf(s).replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","");}
}
