import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** M10A4 A4C: postprocess-only real-cell current-crowding diagnostics. */
public final class LiNRR_M10A4_CurrentCrowding {
    private static final String ROOT = "F:\\LiNRR_COMSOL\\worktrees\\LiNRR_M10A4_INTEGRATED";
    private static final String RUN = ROOT + "\\runs\\M10A4\\20260820_121258";
    private static final String INPUT = RUN + "\\checkpoint_A4B_ionic_transport.mph";
    private static final String CHECKPOINT = RUN + "\\checkpoint_A4C_current_crowding.mph";
    private static final String TABLE = ROOT + "\\results\\tables\\M10A4_current_distribution_statistics.csv";
    private static final String PLANE_TABLE = ROOT + "\\results\\tables\\M10A4_A4C_reaction_plane_audit.csv";
    private static final String COMP = "comp_species_liq_real";
    private static final String GEOM = "geom_electrolyte_fluid1";
    private static final String CATHODE = "m10a3_sel_bnd_electrolyte_gde_top";
    private static final String ANODE = "m10a3_sel_bnd_electrolyte_gde_bottom";
    private static final String INLET = "m10a3_sel_bnd_electrolyte_inlet";
    private static final String OUTLET = "m10a3_sel_bnd_electrolyte_outlet";
    private static final String DATA_A4A = "dset_a4a_ohmic";
    private static final String DATA_A4B = "dset_a4b_ionic_en";
    private static int serial = 0;

    private LiNRR_M10A4_CurrentCrowding() {}

    public static void main(String[] args) throws Exception {
        Path checkpoint = Paths.get(CHECKPOINT);
        if (Files.exists(checkpoint)) throw new IllegalStateException("REFUSE_OVERWRITE_ACCEPTED_A4C " + checkpoint);
        Model m = ModelUtil.load("M10A4C", INPUT);
        try {
            require(m.result().dataset().hasTag(DATA_A4A), "A4A_DATASET_MISSING");
            require(m.result().dataset().hasTag(DATA_A4B), "A4B_DATASET_MISSING");
            String a4aSol = solver(m, "std_a4a_ohmic");
            String jout = "withsol('" + a4aSol + "',cd.nIl)";
            String jconv = "-(" + jout + ")";
            String jmag = "sqrt((" + jout + ")^2)";
            int[] cathIds = m.component(COMP).selection(CATHODE).entities(2);
            int[] anIds = m.component(COMP).selection(ANODE).entities(2);
            require(cathIds.length == 1, "AMBIGUOUS_CATHODE_REACTION_PLANE entities=" + cathIds.length);
            require(anIds.length == 1, "AMBIGUOUS_ANODE_REACTION_PLANE entities=" + anIds.length);

            double area = integral(m, DATA_A4B, CATHODE, "1", "m^2");
            double areaParam = m.param().evaluate("A_echem_cathode", "m^2");
            double areaRel = rel(area, areaParam);
            require(area > 0.0 && areaRel <= 1e-10, "REACTION_PLANE_AREA_MISMATCH rel=" + areaRel);
            double jmagMean = integral(m, DATA_A4B, CATHODE, jmag, "A") / area;

            defineVariables(m, jout, jconv, jmag);
            createResults(m, jmagMean);
            m.label("LiNRR_M10A4_ionic_current_li_plating_A4C.mph");
            m.comments("A4C postprocess-only area-weighted current-crowding diagnostics on the authoritative real-CAD cathode reaction plane. No kinetics added.");
            m.save(checkpoint.toString()); // immutable pre-audit checkpoint

            List<String[]> planeRows = new ArrayList<>();
            planeRows.add(new String[]{"cathode_reaction_plane", CATHODE, COMP, GEOM, ids(cathIds), f(area), f(area*1e6), f(area*1e4), "REAL_CAD", "PASS", "authoritative electrolyte/GDE interface"});
            planeRows.add(new String[]{"anode_reaction_plane", ANODE, COMP, GEOM, ids(anIds), f(integral(m, DATA_A4B, ANODE, "1", "m^2")), "", "", "REAL_CAD", "PASS", "paired real-CAD electrolyte/GDE interface"});
            planeRows.add(new String[]{"reference_capillary_mapping", "NOT_AVAILABLE", "comp_cell_physical", "NOT_MAPPED_TO_ELECTROLYTE_SURFACE", "", "", "", "", "LAB_MANUAL_GEOMETRY_ONLY", "NOT_CREATED_INSUFFICIENT_MAPPING_PROVENANCE", "manual capillary dimensions exist but no authoritative reaction-plane mapping/selection exists"});
            write(Paths.get(PLANE_TABLE), "role,selection,component,geometry,entity_ids,area_m2,area_mm2,area_cm2,source_class,status,notes", planeRows);

            List<String[]> rows = new ArrayList<>();
            double icath = integral(m, DATA_A4B, CATHODE, jout, "A");
            double ian = integral(m, DATA_A4B, ANODE, jout, "A");
            double closure = Math.abs(icath + ian) / Math.max(Math.max(Math.abs(icath), Math.abs(ian)), 1e-300);
            double expected = m.param().evaluate("I_A4A_sensitivity", "A");
            double cathVsA4A = rel(icath, expected);
            double signedMean = icath / area;
            double conventionalMean = -signedMean;
            double jmin = scalar(m, "MinSurface", DATA_A4B, CATHODE, jmag, "A/m^2");
            double jmax = scalar(m, "MaxSurface", DATA_A4B, CATHODE, jmag, "A/m^2");
            double variance = integral(m, DATA_A4B, CATHODE, "(" + jmag + "-(" + f(jmagMean) + "[A/m^2]))^2", "A^2/m^2") / area;
            double std = Math.sqrt(Math.max(variance, 0.0));
            double cv = std / Math.max(jmagMean, 1e-300);
            double p10 = weightedQuantile(m, jmag, area, jmin, jmax, 0.10);
            double p50 = weightedQuantile(m, jmag, area, jmin, jmax, 0.50);
            double p90 = weightedQuantile(m, jmag, area, jmin, jmax, 0.90);
            double negativeArea = integral(m, DATA_A4B, CATHODE, "if((" + jout + ")<0[A/m^2],1,0)", "m^2");
            double positiveArea = integral(m, DATA_A4B, CATHODE, "if((" + jout + ")>=0[A/m^2],1,0)", "m^2");

            add(rows,"actual_electrochemical_interface_area",area,"m^2","area integral",DATA_A4B,CATHODE,"REAL_CAD","PASS","one named real-CAD boundary; entity="+ids(cathIds));
            add(rows,"area_weight_sum",area,"m^2","COMSOL surface quadrature",DATA_A4B,CATHODE,"DERIVED_DIAGNOSTIC",areaRel<=1e-10?"PASS":"FAIL","positive weights; relative to A_echem_cathode="+f(areaRel));
            add(rows,"integrated_cathode_current",icath,"A","surface integral",DATA_A4B,CATHODE,"DERIVED_DIAGNOSTIC","PASS","electrolyte outward-normal sign retained");
            add(rows,"integrated_anode_current",ian,"A","surface integral",DATA_A4B,ANODE,"DERIVED_DIAGNOSTIC","PASS","electrolyte outward-normal sign retained");
            add(rows,"current_conservation_relative",closure,"1","integral closure",DATA_A4B,CATHODE+"+"+ANODE,"DERIVED_DIAGNOSTIC",closure<=1e-8?"PASS":"FAIL","abs(Ic+Ia)/max(abs(I))");
            add(rows,"cathode_current_vs_A4A_relative",cathVsA4A,"1","accepted parameter regression",DATA_A4B,CATHODE,"DERIVED_DIAGNOSTIC",cathVsA4A<=1e-8?"PASS":"FAIL","reference I_A4A_sensitivity");
            add(rows,"j_electrolyte_outward_mean",signedMean,"A/m^2","signed area weighted",DATA_A4B,CATHODE,"DERIVED_DIAGNOSTIC","PASS","positive accepted electrolyte outward convention");
            add(rows,"j_cathodic_conventional_mean",conventionalMean,"A/m^2","signed area weighted",DATA_A4B,CATHODE,"DERIVED_DIAGNOSTIC","PASS","explicit transform j_cathodic_conventional=-j_electrolyte_outward");
            add(rows,"j_surface_magnitude_mean",jmagMean,"A/m^2","area weighted",DATA_A4B,CATHODE,"DERIVED_DIAGNOSTIC","PASS","explicit sqrt(j_outward^2) diagnostic after sign audit; not sign repair");
            add(rows,"j_surface_magnitude_min",jmin,"A/m^2","surface extremum",DATA_A4B,CATHODE,"DERIVED_DIAGNOSTIC","PASS","nonnegative magnitude diagnostic");
            add(rows,"j_surface_magnitude_max",jmax,"A/m^2","surface extremum",DATA_A4B,CATHODE,"DERIVED_DIAGNOSTIC","PASS","nonnegative magnitude diagnostic");
            add(rows,"j_surface_magnitude_P10",p10,"A/m^2","area-weighted indicator-CDF bisection",DATA_A4B,CATHODE,"DERIVED_DIAGNOSTIC","PASS","CDF=IntSurface(if(j<=J,1,0))/area");
            add(rows,"j_surface_magnitude_P50",p50,"A/m^2","area-weighted indicator-CDF bisection",DATA_A4B,CATHODE,"DERIVED_DIAGNOSTIC","PASS","CDF=IntSurface(if(j<=J,1,0))/area");
            add(rows,"j_surface_magnitude_P90",p90,"A/m^2","area-weighted indicator-CDF bisection",DATA_A4B,CATHODE,"DERIVED_DIAGNOSTIC","PASS","CDF=IntSurface(if(j<=J,1,0))/area");
            add(rows,"j_surface_magnitude_std",std,"A/m^2","area weighted population std",DATA_A4B,CATHODE,"DERIVED_DIAGNOSTIC","PASS","sqrt(Int((j-mean)^2)/area)");
            add(rows,"j_surface_magnitude_CV",cv,"1","area weighted",DATA_A4B,CATHODE,"DERIVED_DIAGNOSTIC","PASS","std/mean");
            add(rows,"signed_negative_area_fraction",negativeArea/area,"1","area weighted sign audit",DATA_A4B,CATHODE,"DIAGNOSTIC_ONLY","DIAGNOSTIC","not clipped; local sign-reversal fraction retained");
            add(rows,"signed_nonnegative_area_fraction",positiveArea/area,"1","area weighted sign audit",DATA_A4B,CATHODE,"DIAGNOSTIC_ONLY","DIAGNOSTIC","partition closure with negative fraction");
            addRegionalDiagnostics(m, rows, jmag, jmagMean, area);

            boolean finite = finite(area,icath,ian,closure,signedMean,jmagMean,jmin,jmax,p10,p50,p90,std,cv);
            boolean ordered = jmin <= p10 && p10 <= p50 && p50 <= p90 && p90 <= jmax;
            boolean areaPartition = Math.abs(negativeArea + positiveArea - area) / area <= 1e-8;
            boolean pass = finite && ordered && areaPartition && closure <= 1e-8 && cathVsA4A <= 1e-8 && areaRel <= 1e-10 && jmin >= 0.0;
            write(Paths.get(TABLE), "quantity,value,unit,weighting,dataset,selection,source_class,scientific_status,notes", rows);
            System.out.println("M10A4C_REACTION_PLANE|selection="+CATHODE+"|entities="+ids(cathIds)+"|area_m2="+f(area)+"|area_mm2="+f(area*1e6)+"|area_cm2="+f(area*1e4)+"|area_relative="+f(areaRel));
            System.out.println("M10A4C_CURRENT_CLOSURE|I_cath_A="+f(icath)+"|I_anode_A="+f(ian)+"|relative="+f(closure)+"|cath_vs_A4A="+f(cathVsA4A));
            System.out.println("M10A4C_STATS|mean="+f(jmagMean)+"|min="+f(jmin)+"|max="+f(jmax)+"|P10="+f(p10)+"|P50="+f(p50)+"|P90="+f(p90)+"|std="+f(std)+"|CV="+f(cv)+"|negative_signed_area_fraction="+f(negativeArea/area));
            System.out.println("M10A4C_GATES|finite="+finite+"|ordered="+ordered+"|area_partition="+areaPartition+"|status="+(pass?"PASS":"FAIL"));
            if (!pass) throw new IllegalStateException("M10A4C_FORMAL_GATE_FAIL see " + TABLE);
            System.out.println("M10A4C_CURRENT_DISTRIBUTION=PASS");
            System.out.println("CHECKPOINT_A4C=" + CHECKPOINT);
        } finally {
            ModelUtil.remove("M10A4C");
        }
    }

    private static void defineVariables(Model m, String jout, String jconv, String jmag) {
        String tag="var_a4c";
        if(m.component(COMP).variable().hasTag(tag))m.component(COMP).variable().remove(tag);
        m.component(COMP).variable().create(tag);
        m.component(COMP).variable(tag).label("A4C explicit current sign and magnitude diagnostics");
        m.component(COMP).variable(tag).selection().named(CATHODE);
        m.component(COMP).variable(tag).set("j_electrolyte_outward_a4c",jout,"Accepted electrolyte outward-normal current density");
        m.component(COMP).variable(tag).set("j_cathodic_conventional_a4c",jconv,"Explicit conventional cathodic-negative transform");
        m.component(COMP).variable(tag).set("j_normal_magnitude_a4c",jmag,"Explicit nonnegative normal-current magnitude diagnostic after sign audit");
    }

    private static void createResults(Model m,double mean){
        surfacePlot(m,"pg_a4c_cathode_current","04 | CURRENT DISTRIBUTION | Cathode Current Density | electrolyte outward","j_electrolyte_outward_a4c","A/m^2",CATHODE);
        surfacePlot(m,"pg_a4c_anode_current","04 | CURRENT DISTRIBUTION | Anode Current Density | electrolyte outward","withsol('"+solver(m,"std_a4a_ohmic")+"',cd.nIl)","A/m^2",ANODE);
        surfacePlot(m,"pg_a4c_nonuniformity","04 | CURRENT DISTRIBUTION | Current Nonuniformity | normal-current magnitude","j_normal_magnitude_a4c","A/m^2",CATHODE);
        surfacePlot(m,"pg_a4c_crowding","04 | CURRENT DISTRIBUTION | Current-Crowding Diagnostic | magnitude over area mean","j_normal_magnitude_a4c/("+f(mean)+"[A/m^2])","1",CATHODE);
    }

    private static void addRegionalDiagnostics(Model m,List<String[]> rows,String jmag,double mean,double area){
        double xmin=scalar(m,"MinSurface",DATA_A4B,CATHODE,"x","m"),xmax=scalar(m,"MaxSurface",DATA_A4B,CATHODE,"x","m");
        double ymin=scalar(m,"MinSurface",DATA_A4B,CATHODE,"y","m"),ymax=scalar(m,"MaxSurface",DATA_A4B,CATHODE,"y","m");
        double band=0.10*Math.min(xmax-xmin,ymax-ymin);
        String edge="if((x<"+f(xmin+band)+"[m])||(x>"+f(xmax-band)+"[m])||(y<"+f(ymin+band)+"[m])||(y>"+f(ymax-band)+"[m]),1,0)";
        region(m,rows,"edge_corner_band",jmag,edge,"outer 10% coordinate band; SPATIAL_ASSOCIATION");
        double xin=scalar(m,"AvSurface",DATA_A4B,INLET,"x","m"),yin=scalar(m,"AvSurface",DATA_A4B,INLET,"y","m");
        double xout=scalar(m,"AvSurface",DATA_A4B,OUTLET,"x","m"),yout=scalar(m,"AvSurface",DATA_A4B,OUTLET,"y","m");
        String din="((x-("+f(xin)+"[m]))^2+(y-("+f(yin)+"[m]))^2)";
        String dout="((x-("+f(xout)+"[m]))^2+(y-("+f(yout)+"[m]))^2)";
        region(m,rows,"inlet_nearest_region",jmag,"if("+din+"<="+dout+",1,0)","nearest projected real inlet centroid; SPATIAL_ASSOCIATION");
        region(m,rows,"outlet_nearest_region",jmag,"if("+dout+"<"+din+",1,0)","nearest projected real outlet centroid; SPATIAL_ASSOCIATION");
        double cmean=integral(m,DATA_A4B,CATHODE,"cLi_a4b","mol/m")/area;
        double cvar=integral(m,DATA_A4B,CATHODE,"(cLi_a4b-("+f(cmean)+"[mol/m^3]))^2","mol^2/m^4")/area;
        double cov=integral(m,DATA_A4B,CATHODE,"("+jmag+"-("+f(mean)+"[A/m^2]))*(cLi_a4b-("+f(cmean)+"[mol/m^3]))","A*mol/m^3")/area;
        double jvar=integral(m,DATA_A4B,CATHODE,"("+jmag+"-("+f(mean)+"[A/m^2]))^2","A^2/m^2")/area;
        double corr=cov/Math.max(Math.sqrt(Math.max(jvar,0)*Math.max(cvar,0)),1e-300);
        add(rows,"Li_concentration_current_magnitude_correlation",corr,"1","area weighted Pearson",DATA_A4B,CATHODE,"DIAGNOSTIC_ONLY","SPATIAL_ASSOCIATION","Li field is A4B provisional sensitivity; no causality claim");
        add(rows,"electrolyte_path_length",0.010,"m","geometry diagnostic",DATA_A4B,CATHODE,"REAL_CAD","DIAGNOSTIC_ONLY","constant top-to-bottom reaction-plane separation; no spatial association identifiable");
        add(rows,"flow_field_footprint_area",area,"m^2","area weighted",DATA_A4B,CATHODE,"REAL_CAD","DIAGNOSTIC_ONLY","authoritative electrolyte reaction-plane footprint");
        rows.add(new String[]{"reference_port_spatial_association","","1","not evaluated",DATA_A4B,"NOT_AVAILABLE","LAB_MANUAL_GEOMETRY_ONLY","NOT_CREATED_INSUFFICIENT_MAPPING_PROVENANCE","no authoritative reference-capillary reaction-plane selection or mapping"});
    }

    private static void region(Model m,List<String[]> rows,String name,String jmag,String indicator,String note){
        double a=integral(m,DATA_A4B,CATHODE,indicator,"m^2");
        require(a>0,"EMPTY_REGION "+name);
        double v=integral(m,DATA_A4B,CATHODE,"("+jmag+")*("+indicator+")","A")/a;
        add(rows,name+"_area",a,"m^2","COMSOL indicator surface quadrature",DATA_A4B,CATHODE,"REAL_CAD","DIAGNOSTIC_ONLY",note);
        add(rows,name+"_j_magnitude_mean",v,"A/m^2","area weighted regional mean",DATA_A4B,CATHODE,"DERIVED_DIAGNOSTIC","SPATIAL_ASSOCIATION",note+"; no causality claim");
    }

    private static double weightedQuantile(Model m,String expr,double area,double lo,double hi,double q){
        for(int i=0;i<45;i++){
            double mid=0.5*(lo+hi);
            String cdf="if(("+expr+")<=("+f(mid)+"[A/m^2]),1,0)";
            double frac=integral(m,DATA_A4B,CATHODE,cdf,"m^2")/area;
            if(frac<q)lo=mid;else hi=mid;
        }
        return 0.5*(lo+hi);
    }

    private static void surfacePlot(Model m,String tag,String label,String expr,String unit,String sel){
        if(m.result().hasTag(tag))m.result().remove(tag);
        m.result().create(tag,"PlotGroup3D");m.result(tag).label(label);m.result(tag).set("data",DATA_A4B);
        m.result(tag).create("surf","Surface");m.result(tag).feature("surf").set("expr",expr);m.result(tag).feature("surf").set("unit",unit);
        m.result(tag).feature("surf").create("sel","Selection");m.result(tag).feature("surf").feature("sel").selection().named(sel);
    }

    private static double integral(Model m,String data,String sel,String expr,String unit){return scalar(m,"IntSurface",data,sel,expr,unit);}
    private static double scalar(Model m,String type,String data,String sel,String expr,String unit){
        String tag="m10a4c_ev_"+(++serial);m.result().numerical().create(tag,type);
        try{m.result().numerical(tag).set("data",data);m.result().numerical(tag).selection().geom(GEOM,2);m.result().numerical(tag).selection().set(m.component(COMP).selection(sel).entities(2));m.result().numerical(tag).set("expr",new String[]{expr});m.result().numerical(tag).set("unit",new String[]{unit});if(type.startsWith("Int")){m.result().numerical(tag).set("intorderactive",true);m.result().numerical(tag).set("intorder",8);}double[][]v=m.result().numerical(tag).getReal();require(v!=null&&v.length>0&&v[0].length>0,"EMPTY_EVAL "+expr);return v[0][v[0].length-1];}finally{m.result().numerical().remove(tag);}
    }
    private static String solver(Model m,String study){String[]s=m.study(study).getSolverSequences("SolverSequence");require(s.length>0,"NO_SOLVER "+study);return s[s.length-1];}
    private static void add(List<String[]>r,String q,double v,String u,String w,String d,String s,String c,String st,String n){r.add(new String[]{q,f(v),u,w,d,s,c,st,n});}
    private static boolean finite(double...x){for(double v:x)if(!Double.isFinite(v))return false;return true;}
    private static double rel(double a,double b){return Math.abs(a-b)/Math.max(Math.max(Math.abs(a),Math.abs(b)),1e-300);}
    private static String ids(int[]a){StringBuilder s=new StringBuilder();for(int i=0;i<a.length;i++){if(i>0)s.append(';');s.append(a[i]);}return s.toString();}
    private static String f(double x){return String.format(Locale.ROOT,"%.15g",x);}
    private static void require(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
    private static String csv(String s){return s.contains(",")||s.contains("\"")?"\""+s.replace("\"","\"\"")+"\"":s;}
    private static void write(Path p,String h,List<String[]>rows)throws Exception{Files.createDirectories(p.getParent());try(BufferedWriter w=Files.newBufferedWriter(p,StandardCharsets.UTF_8)){w.write(h);w.newLine();for(String[]r:rows){for(int i=0;i<r.length;i++){if(i>0)w.write(',');w.write(csv(r[i]));}w.newLine();}}}
}
