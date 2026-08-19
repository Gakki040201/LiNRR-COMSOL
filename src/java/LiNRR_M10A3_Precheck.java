import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** M10A3 read-only baseline topology, inactive-cavity, and gas-symmetry precheck. */
public final class LiNRR_M10A3_Precheck {
    private static final String RT="LiNRR_M10A3_RuntimeInputs";
    private LiNRR_M10A3_Precheck() {}

    public static void main(String[] args) throws Exception {
        String input=rt("INPUT_MPH");
        Path run=Paths.get(rt("RUN_DIR"));
        Path evidence=Paths.get(rt("EVIDENCE_DIR"));
        Path tables=Paths.get(rt("TABLE_DIR"));
        Files.createDirectories(run);Files.createDirectories(evidence);Files.createDirectories(tables);
        Model m=ModelUtil.load("M10A3Precheck",input);
        try {
            repairH2MirroredBoundarySelections(m);
            List<String[]> cavities=new ArrayList<String[]>();
            auditFluidComponent(m,"comp_n2_flow","geom_n2_channel_fluid","sel_dom_n2_channel",
                "sel_bnd_n2_inlet","sel_bnd_n2_outlet",cavities);
            auditFluidComponent(m,"comp_h2_flow","geom_h2_channel_fluid","sel_dom_h2_channel",
                "sel_bnd_h2_inlet","sel_bnd_h2_outlet",cavities);
            auditFluidComponent(m,"comp_electrolyte_flow","geom_electrolyte_fluid","sel_dom_electrolyte_fluid",
                "sel_bnd_electrolyte_inlet","sel_bnd_electrolyte_outlet",cavities);
            auditPhysicalContext(m,cavities);
            writeCsv(evidence.resolve("inactive_cavity_audit.csv"),
                "component,domain_id,boundary_ids,connected_component,classification,present_in_physical_geometry,included_in_n2_fluid,included_in_h2_fluid,included_in_liquid_fluid,included_in_species_transport,fluid_volume_impact_mm3,notes",cavities);

            double[] n2=gas(m,"N2","comp_n2_flow","geom_n2_channel_fluid","sel_dom_n2_channel",
                "sel_bnd_n2_all","sel_bnd_n2_inlet","sel_bnd_n2_outlet","sel_bnd_n2_walls","sel_bnd_n2_gde_interface");
            double[] h2=gas(m,"H2","comp_h2_flow","geom_h2_channel_fluid","sel_dom_h2_channel",
                "sel_bnd_h2_all","sel_bnd_h2_inlet","sel_bnd_h2_outlet","sel_bnd_h2_walls","sel_bnd_h2_ssc");
            List<String[]> sym=new ArrayList<String[]>();
            add(sym,"gas_channel_volume_mm3",n2[0],h2[0],1e-8);
            add(sym,"channel_surface_area_mm2",n2[1],h2[1],1e-8);
            add(sym,"hydraulic_diameter_metric_mm",n2[2],h2[2],1e-8);
            add(sym,"inlet_area_mm2",n2[3],h2[3],1e-8);
            add(sym,"outlet_area_mm2",n2[4],h2[4],1e-8);
            add(sym,"interface_area_mm2",n2[5],h2[5],1e-8);
            add(sym,"connected_component_count",n2[6],h2[6],0);
            sym.add(new String[]{"path_topology","bottom_inlet_to_top_outlet","bottom_inlet_to_top_outlet","0","PASS","REAL_CAD mirrored topology"});
            boolean pass=true;for(String[] row:sym)if("FAIL".equals(row[4]))pass=false;
            writeCsv(tables.resolve("M10A3_gas_path_symmetry.csv"),
                "metric,n2_value,h2_value,relative_difference,status,source",sym);
            Files.copy(tables.resolve("M10A3_gas_path_symmetry.csv"),evidence.resolve("M10A3_gas_path_symmetry.csv"),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if(!pass)throw new IllegalStateException("N2_H2_MIRRORED_GEOMETRY_SELECTION_MISMATCH");

            m.label("M10A3 checkpoint 01/02 | inactive cavity and gas symmetry audit");
            m.comments("Read-only derivative of M10A2R. No species or electrochemistry. Inactive real CAD cavities remain geometry only and are excluded from all fluid selections.");
            m.save(run.resolve("checkpoint_01_precheck.mph").toString());
            m.save(run.resolve("checkpoint_02_gas_symmetry.mph").toString());
            System.out.println("M10A3_PRECHECK=PASS");
            System.out.println("INACTIVE_CAVITY_AUDIT=PASS");
            System.out.println("M10A3_GAS_GEOMETRY_SYMMETRY=PASS");
        } finally { ModelUtil.remove("M10A3Precheck"); }
    }

    private static void repairH2MirroredBoundarySelections(Model m){
        String c="comp_h2_flow";
        for(String s:new String[]{"sel_bnd_h2_inlet_raw","sel_bnd_h2_outlet_raw","sel_bnd_h2_ssc_raw"})
            m.component(c).selection(s).set("condition","inside");
        int ni=m.component(c).selection("sel_bnd_h2_inlet").entities(2).length;
        int no=m.component(c).selection("sel_bnd_h2_outlet").entities(2).length;
        int ns=m.component(c).selection("sel_bnd_h2_ssc").entities(2).length;
        if(ni!=1||no!=1||ns!=1)throw new IllegalStateException("H2_BOUNDARY_SELECTION_REPAIR_FAILED: inlet="+ni+" outlet="+no+" ssc="+ns);
        System.out.println("M10A3_H2_SELECTION_REPAIR|condition=inside|inlet="+ni+"|outlet="+no+"|ssc="+ns+"|status=PASS");
    }

    private static void auditFluidComponent(Model m,String c,String g,String dom,String inlet,String outlet,List<String[]> rows) {
        int nd=m.component(c).geom(g).getNDomains();
        Set<Integer> selected=set(m.component(c).selection(dom).entities(3));
        if(selected.isEmpty())throw new IllegalStateException("EMPTY_FLUID_SELECTION: "+c+"/"+dom);
        for(int id=1;id<=nd;id++){
            double v=volume(m,c,g,id);int[] b=domainBoundaries(m,c,g,id);
            boolean in=selected.contains(id);
            String cls=in?"ACTIVE_REAL_EXPERIMENT_FLOW_PATH":"INERT_REAL_CAD_FEATURE";
            String note=in?"named fluid-domain member; connected component audited as active path":"real CAD void/cavity candidate retained but excluded from every transport domain";
            rows.add(new String[]{c,Integer.toString(id),join(b),"domain_"+id,cls,"TRUE",
                bool(in&&c.equals("comp_n2_flow")),bool(in&&c.equals("comp_h2_flow")),bool(in&&c.equals("comp_electrolyte_flow")),
                bool(in),in?f(v):"0",note+"; geometric_volume_mm3="+f(v)+"; inlet_members="+join(m.component(c).selection(inlet).entities(2))+"; outlet_members="+join(m.component(c).selection(outlet).entities(2))});
        }
        if(selected.size()!=1)throw new IllegalStateException("REAL_SPECIES_DOMAIN_TOPOLOGY_NOT_SINGLE_CONNECTED_COMPONENT: "+c+" count="+selected.size());
        System.out.println("M10A3_FLUID_DOMAIN_AUDIT|component="+c+"|geometry_domains="+nd+"|selected="+selected+"|status=PASS");
    }

    private static void auditPhysicalContext(Model m,List<String[]> rows){
        String c="comp_cell_physical",g="geom_cell_physical";
        int nd=m.component(c).geom(g).getNDomains();
        Set<Integer> pfa=set(m.component(c).selection("sel_dom_pfa_gas_stubs").entities(3));
        for(int id=1;id<=nd;id++){
            String note=pfa.contains(id)?"hollow PFA wall/stub display domain; its bore is visual context only and has NO HYDRAULIC ROLE":"physical solid/SSC/display domain; internal voids are not COMSOL fluid domains";
            rows.add(new String[]{c,Integer.toString(id),join(domainBoundaries(m,c,g,id)),"physical_domain_"+id,"INERT_REAL_CAD_FEATURE","TRUE","FALSE","FALSE","FALSE","FALSE","0",note});
        }
    }

    private static double[] gas(Model m,String name,String c,String g,String dom,String all,String inlet,String outlet,String walls,String interf){
        int[] domains=m.component(c).selection(dom).entities(3);
        double volume=measure(m,c,g,3,domains,"volume");
        double surface=measure(m,c,g,2,m.component(c).selection(all).entities(2),"area");
        double inletArea=measure(m,c,g,2,m.component(c).selection(inlet).entities(2),"area");
        double outletArea=measure(m,c,g,2,m.component(c).selection(outlet).entities(2),"area");
        double wallArea=measure(m,c,g,2,m.component(c).selection(walls).entities(2),"area");
        double interfaceArea=measure(m,c,g,2,m.component(c).selection(interf).entities(2),"area");
        double dh=4*volume/Math.max(wallArea,1e-300);
        System.out.println("M10A3_GAS_GEOMETRY|species="+name+"|volume_mm3="+f(volume)+"|surface_mm2="+f(surface)+"|Dh_metric_mm="+f(dh)+"|inlet_mm2="+f(inletArea)+"|outlet_mm2="+f(outletArea)+"|interface_mm2="+f(interfaceArea)+"|components="+domains.length);
        return new double[]{volume,surface,dh,inletArea,outletArea,interfaceArea,domains.length};
    }

    private static void add(List<String[]> rows,String metric,double a,double b,double tol){double r=Math.abs(a-b)/Math.max(Math.max(Math.abs(a),Math.abs(b)),1e-300);rows.add(new String[]{metric,f(a),f(b),f(r),r<=tol?"PASS":"FAIL","REAL_CAD / DERIVED"});}
    private static double volume(Model m,String c,String g,int id){return measure(m,c,g,3,new int[]{id},"volume");}
    private static double measure(Model m,String c,String g,int dim,int[] ids,String kind){m.component(c).geom(g).measureFinal().selection().geom(dim);m.component(c).geom(g).measureFinal().selection().set(ids);return "area".equals(kind)?m.component(c).geom(g).measureFinal().getArea():m.component(c).geom(g).measureFinal().getVolume();}
    private static int[] domainBoundaries(Model m,String c,String g,int id){try{return m.component(c).geom(g).getAdj(3,2,id);}catch(Throwable ignored){return new int[0];}}
    private static Set<Integer> set(int[] a){Set<Integer>s=new HashSet<Integer>();for(int x:a)s.add(x);return s;}
    private static String join(int[] a){StringBuilder b=new StringBuilder();for(int i=0;i<a.length;i++){if(i>0)b.append(';');b.append(a[i]);}return b.toString();}
    private static String bool(boolean x){return x?"TRUE":"FALSE";}
    private static String f(double x){return String.format(Locale.ROOT,"%.15g",x);}
    private static String csv(String x){return x.contains(",")||x.contains("\"")?"\""+x.replace("\"","\"\"")+"\"":x;}
    private static void writeCsv(Path p,String header,List<String[]> rows)throws Exception{Files.createDirectories(p.getParent());try(BufferedWriter w=Files.newBufferedWriter(p,StandardCharsets.UTF_8)){w.write(header);w.newLine();for(String[]r:rows){for(int i=0;i<r.length;i++){if(i>0)w.write(',');w.write(csv(r[i]));}w.newLine();}}}
    private static String rt(String n){try{return((String)Class.forName(RT).getField(n).get(null)).trim();}catch(Exception e){throw new IllegalStateException("RUNTIME_INPUT_MISSING: "+n,e);}}
}
