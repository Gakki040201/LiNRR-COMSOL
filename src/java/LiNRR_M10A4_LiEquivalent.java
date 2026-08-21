import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** M10A4 A4D: Li-equivalent Faraday upper-bound/current-partition scaffold only. */
public final class LiNRR_M10A4_LiEquivalent {
    private static final String ROOT="F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED";
    private static final String RUN=ROOT+"\\runs\\M10A4\\20260820_121258";
    private static final String INPUT=RUN+"\\checkpoint_A4C_current_crowding.mph";
    private static final String CHECKPOINT=RUN+"\\checkpoint_A4D_li_equivalent_attempt4.mph";
    private static final String STATS=ROOT+"\\results\\tables\\M10A4_current_distribution_statistics.csv";
    private static final String LEDGER=ROOT+"\\results\\tables\\M10A4_li_faraday_ledger.csv";
    private static final String COMP="comp_species_liq_real",GEOM="geom_electrolyte_fluid1";
    private static final String CATHODE="m10a3_sel_bnd_electrolyte_gde_top",DOM="m10a3_sel_dom_electrolyte_fluid",DATA="dset_a4b_ionic_en";
    private static int serial=0;
    private LiNRR_M10A4_LiEquivalent(){}

    public static void main(String[]args)throws Exception{
        Path checkpoint=Paths.get(CHECKPOINT);if(Files.exists(checkpoint))throw new IllegalStateException("REFUSE_OVERWRITE_ACCEPTED_A4D "+checkpoint);
        Map<String,Double>s=metrics(Paths.get(STATS));
        require(s.get("current_conservation_relative")<=1e-8,"A4C_REGRESSION_FAIL");
        Model m=ModelUtil.load("M10A4D",INPUT);
        try{
            double area=s.get("actual_electrochemical_interface_area");
            double icath=s.get("integrated_cathode_current");
            double jmean=s.get("j_surface_magnitude_mean");
            require(icath>0&&area>0&&jmean>0,"A4D_SIGN_OR_SCALE_INVALID");
            defineParameters(m,icath);
            String a4aSol=solver(m,"std_a4a_ohmic");
            if(m.component(COMP).variable().hasTag("var_a4c"))m.component(COMP).variable().remove("var_a4c");
            String jout="withsol('"+a4aSol+"',cd.nIl)";
            String jmag="sqrt(("+jout+")^2)";
            String jdep="("+f(icath)+"[A])/("+f(area)+"[m^2]*("+f(jmean)+"[A/m^2]))*("+jmag+")";
            String jLi="-("+jdep+")";
            String nLiExpr="-("+jLi+")/F_const";
            String dh="("+nLiExpr+")*(6.941e-3[kg/mol])/(534[kg/m^3])";
            repairA4CResults(m,jout,jmag,jmean);
            createResults(m,dh,icath);
            m.label("LiNRR_M10A4_ionic_current_li_plating_A4D_li_equivalent.mph");
            m.comments("A4D Li-EQUIVALENT Faraday scaffold only. f=1 is NUMERICAL UPPER BOUND; f<1 are CURRENT-PARTITION SENSITIVITY. No Li plating kinetics and no claim of retained metallic Li.");
            m.save(checkpoint.toString()); // preserve before formal audit

            double jLiMax=scalar(m,"MaxSurface",jLi,"A/m^2");
            double nMin=scalar(m,"MinSurface",nLiExpr,"mol/(m^2*s)");
            double dhMin=scalar(m,"MinSurface",dh,"m/s");
            boolean signPass=jLiMax<=0&&nMin>=0&&dhMin>=0;
            List<String[]>rows=new ArrayList<>();
            double F=m.param().evaluate("F_const","C/mol"),M=m.param().evaluate("M_Li_a4d","kg/mol"),rho=m.param().evaluate("rho_Li_a4d","kg/m^3");
            double[]charges={9,45,54,99,297},fractions={0.25,0.50,0.75,1.00};
            double maxElectron=0,maxLi=0;
            for(double Q:charges)for(double f:fractions){
                double ne=Q/F,qLi=f*Q,nLi=qLi/F,mLi=nLi*M,vLi=mLi/rho;
                double mean=vLi/area;
                double shapeScale=mean/jmean;
                double min=shapeScale*s.get("j_surface_magnitude_min");
                double max=shapeScale*s.get("j_surface_magnitude_max");
                double p10=shapeScale*s.get("j_surface_magnitude_P10");
                double p50=shapeScale*s.get("j_surface_magnitude_P50");
                double p90=shapeScale*s.get("j_surface_magnitude_P90");
                double std=shapeScale*s.get("j_surface_magnitude_std");
                double cv=s.get("j_surface_magnitude_CV");
                double er=Math.abs(ne*F-Q)/Math.max(Math.abs(Q),1e-300);
                double lr=Math.abs(nLi*F-qLi)/Math.max(Math.abs(qLi),1e-300);
                maxElectron=Math.max(maxElectron,er);maxLi=Math.max(maxLi,lr);
                boolean finite=finite(ne,qLi,nLi,mLi,vLi,mean,min,max,p10,p50,p90,std,cv,er,lr);
                boolean ordered=min>=0&&min<=p10&&p10<=p50&&p50<=p90&&p90<=max;
                boolean pass=finite&&ordered&&er<=1e-10&&lr<=1e-10&&signPass;
                String cls=f==1.0?"NUMERICAL_UPPER_BOUND":"CURRENT_PARTITION_SENSITIVITY";
                rows.add(new String[]{f(Q),f(f),cls,f(ne),f(qLi),f(nLi),f(mLi),f(vLi),f(mean),f(min),f(max),f(p10),f(p50),f(p90),f(std),f(cv),f(er),f(lr),pass?"PASS":"FAIL","Li-EQUIVALENT; not predicted retained metallic Li; area-weighted scaled A4C magnitude distribution"});
                if(!pass)throw new IllegalStateException("A4D_ROW_FAIL Q="+Q+" f="+f);
            }
            write(Paths.get(LEDGER),"Q_C,f_Li_current,classification,n_e_mol,Q_Li_equiv_C,n_Li_equiv_mol,m_Li_equiv_kg,V_Li_equiv_m3,mean_thickness_m,minimum_thickness_m,maximum_thickness_m,P10_thickness_m,P50_thickness_m,P90_thickness_m,std_thickness_m,CV_thickness,electron_ledger_relative,Li_equivalent_ledger_relative,status,notes",rows);
            boolean pass=signPass&&maxElectron<=1e-10&&maxLi<=1e-10&&rows.size()==20;
            System.out.println("M10A4D_SIGN_AUDIT|j_Li_conventional_max="+f(jLiMax)+"|N_Li_equiv_min="+f(nMin)+"|dh_min="+f(dhMin)+"|status="+(signPass?"PASS":"FAIL"));
            System.out.println("M10A4D_FARADAY_LEDGER|rows="+rows.size()+"|electron_max_relative="+f(maxElectron)+"|Li_equivalent_max_relative="+f(maxLi)+"|status="+(pass?"PASS":"FAIL"));
            if(!pass)throw new IllegalStateException("M10A4D_FORMAL_GATE_FAIL");
            System.out.println("M10A4D_LI_EQUIVALENT_PLATING=PASS");
            System.out.println("CHECKPOINT_A4D="+CHECKPOINT);
        }finally{ModelUtil.remove("M10A4D");}
    }

    private static void defineParameters(Model m,double icath){
        p(m,"M_Li_a4d","6.941e-3[kg/mol]","LITERATURE_CONSTANT lithium molar mass");
        p(m,"rho_Li_a4d","534[kg/m^3]","LITERATURE_CONSTANT solid lithium density for equivalent volume only");
        p(m,"f_Li_current","1","NUMERICAL_UPPER_BOUND default; sweep 0.25,0.50,0.75,1.00 is CURRENT_PARTITION_SENSITIVITY");
        p(m,"I_A4D_total",f(icath)+"[A]","DERIVED accepted A4C positive electrolyte-outward cathode integral");
        for(int q:new int[]{9,45,54,99,297}){
            p(m,"Q_A4D_"+q,""+q+"[C]","LAB_EXPERIMENTAL_SOURCE frozen program charge checkpoint");
            p(m,"t_equiv_A4D_"+q,"Q_A4D_"+q+"/I_A4D_total","DERIVED equivalent time from charge and accepted current; Q is not used directly as time");
        }
    }

    private static void repairA4CResults(Model m,String jout,String jmag,double mean){
        if(m.result().hasTag("pg_a4c_cathode_current"))m.result("pg_a4c_cathode_current").feature("surf").set("expr",jout);
        if(m.result().hasTag("pg_a4c_anode_current"))m.result("pg_a4c_anode_current").feature("surf").set("expr",jout);
        if(m.result().hasTag("pg_a4c_nonuniformity"))m.result("pg_a4c_nonuniformity").feature("surf").set("expr",jmag);
        if(m.result().hasTag("pg_a4c_crowding"))m.result("pg_a4c_crowding").feature("surf").set("expr","("+jmag+")/("+f(mean)+"[A/m^2])");
    }
    private static void createResults(Model m,String dh,double icath){
        plot(m,"pg_a4d_rate","05 | LI-EQUIVALENT DEPOSITION | Li-Equivalent Deposition Rate | NUMERICAL UPPER BOUND f=1",dh,"m/s");
        for(int q:new int[]{9,45,54,99,297})plot(m,"pg_a4d_h"+q,"05 | LI-EQUIVALENT DEPOSITION | Li-Equivalent Thickness | "+q+" C | NUMERICAL UPPER BOUND f=1","("+dh+")*("+f(q/icath)+"[s])","m");
    }
    private static void plot(Model m,String tag,String label,String expr,String unit){if(m.result().hasTag(tag))m.result().remove(tag);m.result().create(tag,"PlotGroup3D");m.result(tag).label(label);m.result(tag).set("data",DATA);m.result(tag).create("surf","Surface");m.result(tag).feature("surf").set("expr",expr);m.result(tag).feature("surf").set("unit",unit);m.result(tag).feature("surf").create("sel","Selection");m.result(tag).feature("surf").feature("sel").selection().named(CATHODE);}
    private static double scalar(Model m,String type,String expr,String unit){String t="m10a4d_ev_"+(++serial);m.result().numerical().create(t,type);try{m.result().numerical(t).set("data",DATA);m.result().numerical(t).selection().geom(GEOM,2);m.result().numerical(t).selection().set(m.component(COMP).selection(CATHODE).entities(2));m.result().numerical(t).set("expr",new String[]{expr});m.result().numerical(t).set("unit",new String[]{unit});double[][]v=m.result().numerical(t).getReal();require(v!=null&&v.length>0&&v[0].length>0,"EMPTY_EVAL "+expr);return v[0][v[0].length-1];}finally{m.result().numerical().remove(t);}}
    private static Map<String,Double>metrics(Path p)throws Exception{Map<String,Double>m=new HashMap<>();for(String line:Files.readAllLines(p,StandardCharsets.UTF_8)){int k=line.indexOf(',');if(k<0)continue;int k2=line.indexOf(',',k+1);if(k2<0)continue;String key=line.substring(0,k),v=line.substring(k+1,k2);if(v.isEmpty())continue;try{m.put(key,Double.parseDouble(v));}catch(NumberFormatException ignored){}}return m;}
    private static String solver(Model m,String study){String[]s=m.study(study).getSolverSequences("SolverSequence");require(s.length>0,"NO_SOLVER "+study);return s[s.length-1];}
    private static void p(Model m,String n,String v,String d){m.param().set(n,v,d);}
    private static boolean finite(double...a){for(double x:a)if(!Double.isFinite(x))return false;return true;}
    private static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
    private static String f(double x){return String.format(Locale.ROOT,"%.15g",x);}
    private static String csv(String s){return s.contains(",")||s.contains("\"")?"\""+s.replace("\"","\"\"")+"\"":s;}
    private static void write(Path p,String h,List<String[]>r)throws Exception{Files.createDirectories(p.getParent());try(BufferedWriter w=Files.newBufferedWriter(p,StandardCharsets.UTF_8)){w.write(h);w.newLine();for(String[]x:r){for(int i=0;i<x.length;i++){if(i>0)w.write(',');w.write(csv(x[i]));}w.newLine();}}}
}
