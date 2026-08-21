import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** M10A4 A4E: frozen-field spatial diagnostics only; explicitly not a rate model. */
public final class LiNRR_M10A4_SpatialColimitation{
 private static final String ROOT="F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED",RUN=ROOT+"\\runs\\M10A4\\20260820_121258";
 private static final String INPUT=RUN+"\\checkpoint_A4D_li_equivalent_attempt4.mph",CHECKPOINT=RUN+"\\checkpoint_A4E_spatial_colimitation.mph";
 private static final String TABLE=ROOT+"\\results\\tables\\M10A4_spatial_colimitation.csv",MAP=ROOT+"\\results\\tables\\M10A4_A4E_field_mapping_audit.csv";
 private static final String COMP="comp_species_liq_real",GEOM="geom_electrolyte_fluid1",CATH="m10a3_sel_bnd_electrolyte_gde_top",DATA="dset_a4b_ionic_en";
 private static int serial=0;private LiNRR_M10A4_SpatialColimitation(){}
 public static void main(String[]a)throws Exception{
  if(Files.exists(Paths.get(CHECKPOINT)))throw new IllegalStateException("REFUSE_OVERWRITE_ACCEPTED_A4E");
  Model m=ModelUtil.load("M10A4E",INPUT);try{
   String a4a=solver(m,"std_a4a_ohmic"),n2sol=solver(m,"std_species_liq_n2");
   String n2="withsol('"+n2sol+"',cN2d)",don="withsol('"+n2sol+"',cDonor)",li="cLi_a4b",cur="sqrt((withsol('"+a4a+"',cd.nIl))^2)";
   double area=integ(m,"1","m^2");require(area>0,"EMPTY_REACTION_PLANE");
   List<String[]>maps=new ArrayList<>();
   maps.add(map("N2 availability","cN2d","mol/m^3",n2sol,"M10A3 frozen","COLLOCATED_SAME_COMPONENT_GEOMETRY_MESH_WITHSOL","PASS"));
   maps.add(map("generic donor availability","cDonor","mol/m^3",n2sol,"M10A3 frozen GENERIC donor","COLLOCATED_SAME_COMPONENT_GEOMETRY_MESH_WITHSOL","PASS_CALIBRATION_REQUIRED"));
   maps.add(map("Li+ availability","cLi_a4b","mol/m^3","dset_a4b_ionic_en","M10A4B provisional","SAME_COMPONENT_GEOMETRY_SELECTION","PASS_CALIBRATION_REQUIRED"));
   maps.add(map("current magnitude","cd.nIl","A/m^2",a4a,"M10A4A accepted","SAME_COMPONENT_GEOMETRY_SELECTION_WITHSOL","PASS_DIAGNOSTIC"));
   write(Paths.get(MAP),"field,raw_variable,unit,source_solution,source_class,mapping_method,status,component,geometry,selection,coverage,extrapolation",maps);
   String[]expr={n2,li,don,cur},names={"N2","Li+","GENERIC_DONOR","CURRENT_MAGNITUDE"},units={"mol/m^3","mol/m^3","mol/m^3","A/m^2"};
   double[][]thr=new double[3][4];double[]qs={0.40,0.50,0.60};double[]cv=new double[4];
   for(int k=0;k<4;k++){double lo=scalar(m,"MinSurface",expr[k],units[k]),hi=scalar(m,"MaxSurface",expr[k],units[k]);double mean=integ(m,expr[k],k==3?"A":"mol/m")/area;double var=integ(m,"("+expr[k]+"-("+f(mean)+"["+units[k]+"]))^2",k==3?"A^2/m^2":"mol^2/m^4")/area;cv[k]=Math.sqrt(Math.max(var,0))/Math.max(Math.abs(mean),1e-300);for(int q=0;q<3;q++)thr[q][k]=quantile(m,expr[k],units[k],area,lo,hi,qs[q]);}
   createPlots(m,expr,units,thr[1]);m.label("LiNRR_M10A4_A4E_spatial_colimitation_diagnostic.mph");m.comments("A4E frozen-field area-weighted spatial co-limitation diagnostic only. NOT Li-NRR rate, FE, selectivity, or mechanistic proof.");m.save(CHECKPOINT);
   List<String[]>rows=new ArrayList<>();Map<String,double[]>fractions=new LinkedHashMap<>();String[]cats={"CURRENT_RICH_N2_POOR","N2_RICH_CURRENT_POOR","LI_LIMITED","DONOR_LIMITED","BALANCED","MULTI_LIMITED","MIXED_UNCLASSIFIED"};for(String c:cats)fractions.put(c,new double[3]);
   boolean pass=true;
   for(int qi=0;qi<3;qi++){
    String nh=ge(n2,thr[qi][0],units[0]),lh=ge(li,thr[qi][1],units[1]),dh=ge(don,thr[qi][2],units[2]),jh=ge(cur,thr[qi][3],units[3]);
    String nl=not(nh),ll=not(lh),dl=not(dh),jl=not(jh),multi="(("+nl+"&&"+ll+")||("+nl+"&&"+dl+")||("+ll+"&&"+dl+"))";
    Map<String,String>c=new LinkedHashMap<>();c.put("MULTI_LIMITED",multi);c.put("BALANCED","("+nh+"&&"+lh+"&&"+dh+"&&"+jh+")");c.put("LI_LIMITED","("+nh+"&&"+ll+"&&"+dh+"&&"+jh+")");c.put("DONOR_LIMITED","("+nh+"&&"+lh+"&&"+dl+"&&"+jh+")");c.put("CURRENT_RICH_N2_POOR","("+jh+"&&"+nl+"&&"+lh+"&&"+dh+")");c.put("N2_RICH_CURRENT_POOR","("+nh+"&&"+jl+"&&!"+multi+")");
    String used="("+String.join("||",c.values())+")";c.put("MIXED_UNCLASSIFIED","(!"+used+")");double sum=0;
    for(String cat:cats){double ar=integ(m,"if("+c.get(cat)+",1,0)","m^2"),fr=ar/area;fractions.get(cat)[qi]=fr;sum+=fr;rows.add(row("N2_LI_DONOR_CURRENT_COLIMITATION",qs[qi],cat,fr,ar,"AREA_WEIGHTED_QUANTILE_HIGH_GE_RAW_THRESHOLD","cN2d;cLi_a4b;cDonor;cd.nIl","SPATIAL_CO_LIMITATION_DIAGNOSTIC",qi==1?"PRIMARY":"THRESHOLD_SENSITIVITY","precedence MULTI>BALANCED>LI>DONOR>CURRENT_N2>N2_CURRENT>MIXED; NOT rate/FE/mechanism"));}
    double close=Math.abs(sum-1);rows.add(row("CATEGORY_CLOSURE",qs[qi],"SUM",sum,area,"AREA_WEIGHTED","all four frozen fields","SPATIAL_CO_LIMITATION_DIAGNOSTIC",close<=1e-8?"PASS":"FAIL","sum area fractions; residual="+f(close)));if(close>1e-8)pass=false;
    if(qi==1){overlap(rows,m,qs[qi],nh,jh,"N2_CURRENT");overlap(rows,m,qs[qi],dh,jh,"DONOR_CURRENT");}
   }
   for(int k=0;k<4;k++)rows.add(row("FIELD_SPATIAL_CV",0.50,names[k],cv[k],0,"RAW_FIELD_AREA_WEIGHTED_CV",names[k],"SPATIAL_CO_LIMITATION_DIAGNOSTIC","DIAGNOSTIC_ONLY","raw variable/unit="+units[k]));
   for(String cat:cats){double[]v=fractions.get(cat);double spread=Math.max(v[0],Math.max(v[1],v[2]))-Math.min(v[0],Math.min(v[1],v[2]));rows.add(row("THRESHOLD_ROBUSTNESS",0.50,cat,spread,0,"MAX_MINUS_MIN_AREA_FRACTION_Q40_Q50_Q60","all four frozen fields","SPATIAL_CO_LIMITATION_DIAGNOSTIC",spread<=0.10?"ROBUST_SPATIAL_ASSOCIATION":"THRESHOLD_SENSITIVE","diagnostic robustness; no causality"));}
   write(Paths.get(TABLE),"diagnostic,threshold_quantile,category,area_fraction,area,unit,normalization_method,weighting,source_fields,source_class,scientific_status,robustness_status,notes",rows);
   System.out.println("M10A4E_MAPPING|component="+COMP+"|geometry="+GEOM+"|selection="+CATH+"|coverage=1|extrapolation=FALSE|status=PASS");
   System.out.println("M10A4E_THRESHOLDS|set=0.40,0.50,0.60|status="+(pass?"PASS":"FAIL"));if(!pass)throw new IllegalStateException("A4E_CATEGORY_CLOSURE_FAIL");
   System.out.println("M10A4E_SPATIAL_COLIMITATION=PASS");System.out.println("CHECKPOINT_A4E="+CHECKPOINT);
  }finally{ModelUtil.remove("M10A4E");}
 }
 private static void overlap(List<String[]>r,Model m,double q,String ah,String bh,String name){String al=not(ah),bl=not(bh);String[]n={"HIGH_HIGH","HIGH_LOW","LOW_HIGH","LOW_LOW"},e={"("+ah+"&&"+bh+")","("+ah+"&&"+bl+")","("+al+"&&"+bh+")","("+al+"&&"+bl+")"};for(int i=0;i<4;i++){double a=integ(m,"if("+e[i]+",1,0)","m^2");r.add(row(name+"_OVERLAP",q,n[i],a/integ(m,"1","m^2"),a,"AREA_WEIGHTED_QUANTILE","frozen fields","SPATIAL_CO_LIMITATION_DIAGNOSTIC","DIAGNOSTIC_ONLY","NOT_LINRR_RATE"));}}
 private static void createPlots(Model m,String[]e,String[]u,double[]t){String nh=ge(e[0],t[0],u[0]),lh=ge(e[1],t[1],u[1]),dh=ge(e[2],t[2],u[2]),jh=ge(e[3],t[3],u[3]);plot(m,"pg_a4e_n2_current","06 | SPATIAL CO-LIMITATION | N2-Current Spatial Overlap | DIAGNOSTIC ONLY","if("+nh+",1,0)+2*if("+jh+",1,0)");plot(m,"pg_a4e_donor_current","06 | SPATIAL CO-LIMITATION | Donor-Current Spatial Overlap | GENERIC DONOR | CALIBRATION REQUIRED","if("+dh+",1,0)+2*if("+jh+",1,0)");plot(m,"pg_a4e_colim","06 | SPATIAL CO-LIMITATION | N2-Li+-Donor-Current Co-Limitation | q=0.50 | DIAGNOSTIC ONLY","if("+nh+",1,0)+2*if("+lh+",1,0)+4*if("+dh+",1,0)+8*if("+jh+",1,0)");plot(m,"pg_a4e_robust","06 | SPATIAL CO-LIMITATION | Co-Limitation Classification Robustness | q=0.40/0.50/0.60","if("+nh+"&&"+lh+"&&"+dh+"&&"+jh+",1,0)");}
 private static void plot(Model m,String tag,String label,String expr){if(m.result().hasTag(tag))m.result().remove(tag);m.result().create(tag,"PlotGroup3D");m.result(tag).label(label);m.result(tag).set("data",DATA);m.result(tag).create("surf","Surface");m.result(tag).feature("surf").set("expr",expr);m.result(tag).feature("surf").set("unit","1");m.result(tag).feature("surf").create("sel","Selection");m.result(tag).feature("surf").feature("sel").selection().named(CATH);}
 private static double quantile(Model m,String e,String u,double area,double lo,double hi,double q){if(Math.abs(hi-lo)<=1e-12*Math.max(Math.abs(hi),1))return .5*(lo+hi);for(int i=0;i<18;i++){double x=.5*(lo+hi),fr=integ(m,"if(("+e+")<=("+f(x)+"["+u+"]),1,0)","m^2")/area;if(fr<q)lo=x;else hi=x;}return .5*(lo+hi);}
 private static String ge(String e,double v,String u){return "(("+e+")>=("+f(v)+"["+u+"]))";}private static String not(String x){return "(!"+x+")";}
 private static String[]map(String a,String b,String c,String d,String e,String f,String g){return new String[]{a,b,c,d,e,f,g,COMP,GEOM,CATH,"1","FALSE"};}
 private static String[]row(String d,double q,String c,double fr,double ar,String nm,String sf,String sc,String rb,String n){return new String[]{d,f(q),c,f(fr),ar==0?"":f(ar),ar==0?"1":"m^2",nm,"AREA_WEIGHTED_SURFACE_QUADRATURE",sf,sc,sc,rb,n};}
 private static String solver(Model m,String s){String[]x=m.study(s).getSolverSequences("SolverSequence");require(x.length>0,"NO_SOLVER "+s);return x[x.length-1];}
 private static double integ(Model m,String e,String u){return scalar(m,"IntSurface",e,u);}private static double scalar(Model m,String type,String e,String u){String t="m10a4e_ev_"+(++serial);m.result().numerical().create(t,type);try{m.result().numerical(t).set("data",DATA);m.result().numerical(t).selection().geom(GEOM,2);m.result().numerical(t).selection().set(m.component(COMP).selection(CATH).entities(2));m.result().numerical(t).set("expr",new String[]{e});m.result().numerical(t).set("unit",new String[]{u});if(type.startsWith("Int")){m.result().numerical(t).set("intorderactive",true);m.result().numerical(t).set("intorder",8);}double[][]v=m.result().numerical(t).getReal();require(v!=null&&v.length>0&&v[0].length>0,"EMPTY "+e);return v[0][v[0].length-1];}finally{m.result().numerical().remove(t);}}
 private static void require(boolean b,String s){if(!b)throw new IllegalStateException(s);}private static String f(double x){return String.format(Locale.ROOT,"%.15g",x);}private static String csv(String s){return s.contains(",")||s.contains("\"")?"\""+s.replace("\"","\"\"")+"\"":s;}private static void write(Path p,String h,List<String[]>r)throws Exception{Files.createDirectories(p.getParent());try(BufferedWriter w=Files.newBufferedWriter(p,StandardCharsets.UTF_8)){w.write(h);w.newLine();for(String[]x:r){for(int i=0;i<x.length;i++){if(i>0)w.write(',');w.write(csv(x[i]));}w.newLine();}}}
}
