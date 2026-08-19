import com.comsol.model.Model;
import com.comsol.model.MeshFeature;
import com.comsol.model.util.ModelUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Full synthetic five-parameter transfer integration using only the canonical resolved artifact. */
public final class LiNRR_M03A_4_ParameterTransfer {
    private static final String SOURCE_REL="models/generated/LiNRR_M03A_3_prescribed_current_coupling.mph";
    private static final String[] CASES={"IDENTITY_REFERENCE","KAPPA_PERTURBATION","HCELL_PERTURBATION","DEPTH_PERTURBATION","EIS_AREA_PERTURBATION","REPORT_AREA_PERTURBATION","COMBINED_NONIDENTITY"};
    private static final String[] QUANTITIES={"conductivity","electrode_spacing","out_of_plane_thickness","EIS_area","current_density_reporting_area"};
    private static final String[] TARGETS={"kappa_dry_run","Hcell_dry_run","depth_dry_run","A_EIS_dry_run","A_j_report_dry_run"};
    private static final String[] UNITS={"S/m","m","m","m^2","m^2"};
    private static final String[] FORBIDDEN={"SecondaryCurrentDistribution","TertiaryCurrentDistribution","ElectrodeReaction","ButlerVolmer","ExchangeCurrent","HOR","HER","LiPlating","LiStripping","SEI"};
    private LiNRR_M03A_4_ParameterTransfer() {}

    public static void main(String[] args) throws Exception {
        Path root=Paths.get(requireRunInput("LINRR_PROJECT_ROOT")).toAbsolutePath().normalize();
        Path source=root.resolve(SOURCE_REL).normalize();
        Path output=Paths.get(requireRunInput("M03A4_OUTPUT_MPH")).toAbsolutePath().normalize();
        Path resolved=Paths.get(requireRunInput("M03A4_RESOLVED_INPUTS")).toAbsolutePath().normalize();
        String expectedResolvedHash=requireRunInput("M03A4_RESOLVED_SHA256").toLowerCase(Locale.ROOT);
        if(!Files.isRegularFile(source)||Files.size(source)<=0) throw new IllegalStateException("Frozen M03A.3 source missing: "+source);
        if(!Files.isRegularFile(resolved)||Files.size(resolved)<=0) throw new IllegalStateException("Canonical resolved input missing: "+resolved);
        if(Files.exists(output)) throw new IllegalStateException("Refusing to overwrite staged MPH: "+output);
        if(source.equals(output)) throw new IllegalStateException("Derived output must differ from frozen source");
        String actualResolvedHash=sha256(resolved);
        if(!actualResolvedHash.equals(expectedResolvedHash)) throw new IllegalStateException("Resolved artifact SHA-256 mismatch");
        Map<String,Resolved> inputs=readResolved(resolved);
        for(String c:CASES) for(int i=0;i<QUANTITIES.length;i++) require(inputs,c,QUANTITIES[i],TARGETS[i],UNITS[i]);
        Resolved re=require(inputs,"SYN_DRY_001","R_electrolyte","R_electrolyte_dry_run","ohm");
        if(!(re.value>0.0)) throw new IllegalStateException("Resolved electrolyte resistance must be positive");

        Model model=ModelUtil.load("M034SyntheticTransfer",source.toString());
        String treeBefore=treeSignature(model);auditRequiredTree(model);auditForbidden(model);
        configureAccurateTransportFluxes(model);
        List<CaseResult> results=new ArrayList<>();
        for(String caseId:CASES){
            CaseInput in=caseInput(inputs,caseId);
            applyCase(model,in,re.value,actualResolvedHash);
            rebuildGeometryAndSelections(model,in);
            rebuildMesh(model,"COMBINED_NONIDENTITY".equals(caseId)?64:48,"COMBINED_NONIDENTITY".equals(caseId)?24:18);
            clearOldSolution(model);
            activateFullStudy(model);
            model.study("std_audit").run();
            CaseResult result=evaluate(model,in,re.value);
            results.add(result);
            emitCase(result);
            emitTransferRows(model,result);
            if("COMBINED_NONIDENTITY".equals(caseId)) emitCombinedEvidence(model,result);
        }
        emitResponses(results);
        CaseResult formal=find(results,"COMBINED_NONIDENTITY");
        if(!formal.fullPass) throw new IllegalStateException("Formal combined nonidentity full-coupled acceptance failed");
        if(!treeBefore.equals(treeSignature(model))) throw new IllegalStateException("M03A.3 physics tree changed during transfer");
        auditForbidden(model);
        model.param().set("I_total_M034",fmt(formal.current)+"[A]","Current-run integrated total current");
        model.param().set("j_reported_M034","I_total_M034/A_j_report_dry_run","Current-run total current divided by independent reporting area");
        model.param().set("M034_full_coupled_solve_marker","1","Current-run spf plus tds plus cd solve completed");
        model.label("LiNRR M03A.4 full synthetic coupled five-parameter transfer dry-run | not experimental calibration");
        model.save(output.toString());
        System.out.println("M03A4_PROGRESS|DERIVATIVE_MPH_SAVE|COMPLETE");
        System.out.println("M03A4_PROGRESS|FULL_SYNTHETIC_COUPLED_TRANSFER|COMPLETE");
    }

    private static void applyCase(Model m,CaseInput in,double r,String hash){
        for(Resolved resolved:in.resolved) setTransferredParameter(m,resolved,in.caseId,hash);
        m.param().set("R_electrolyte_dry_run",fmt(r)+"[ohm]","Canonical resolved de-embedded electrolyte resistance");
        m.param().set("H_EIS_inversion_M034",fmt(in.inversionH)+"[m]","Spacing used by the selected conductivity inversion case");
        m.param().set("kappa_HFR_audit_M034","H_EIS_inversion_M034/(A_EIS_dry_run*R_electrolyte_dry_run)","Fixed whitelist HFR inversion audit");
        m.param().set("Hcell","Hcell_dry_run","Transferred geometry height");
        m.param().set("Wcell","depth_dry_run","Transferred 2D-to-3D integration depth");
        m.param().set("kappa_M033","kappa_dry_run","Transferred Primary Current Distribution conductivity");
        m.param().set("DeltaPhi_M033","8e-5[V]","Synthetic low-conversion potential difference");
        m.param().set("FE_prescribed","1","Synthetic prescribed Faraday mapping not a prediction");
        m.param().set("Qliq","7.575757575757576e-5[m/s]*Hcell*Wcell","Synthetic low-conversion flow");
        m.param().set("M034_resolved_artifact_sha256","1","Full canonical resolved artifact SHA-256: "+hash);
    }

    private static void setTransferredParameter(Model m,Resolved r,String caseId,String resolvedHash){
        String description=String.join("|",
            "M03A4_META_V1",
            "target_parameter="+r.target,
            "applied_value="+r.valueText,
            "applied_unit="+r.unit,
            "data_origin="+r.origin,
            "source_sha256="+r.sourceHash,
            "resolved_sha256="+resolvedHash,
            "case_id="+caseId,
            "quantity_name="+r.quantity);
        m.param().set(r.target,r.valueText+"["+r.unit+"]",description);
    }

    private static void rebuildGeometryAndSelections(Model m,CaseInput in){
        m.component("comp1").geom("geom1").run();
        double lmm=m.param().evaluate("Lcell","mm"),hmm=m.param().evaluate("Hcell","mm");
        if(hasTag(m.result().dataset().tags(),"m033_vertical_mid"))m.result().dataset("m033_vertical_mid").set("genpoints",new double[][]{{0.5*lmm,0.0},{0.5*lmm,hmm}});
        if(hasTag(m.result().dataset().tags(),"m033_center_point")){m.result().dataset("m033_center_point").set("pointx",0.5*lmm);m.result().dataset("m033_center_point").set("pointy",0.5*hmm);}
        requireSelection(m,"sel_electrolyte",2,1);requireSelection(m,"sel_inlet",1,1);requireSelection(m,"sel_outlet",1,1);requireSelection(m,"sel_anode_wall",1,1);requireSelection(m,"sel_cathode_wall",1,1);
        System.out.println("M03A4_PROGRESS|"+in.caseId+"|GEOMETRY_REBUILT|PASS");
        System.out.println("M03A4_PROGRESS|"+in.caseId+"|NAMED_SELECTION_AUDIT|PASS");
    }

    private static void rebuildMesh(Model m,int nx,int ny){
        MeshFeature dx=m.component("comp1").mesh("mesh1").feature("map_channel").feature("dist_length");
        MeshFeature dy=m.component("comp1").mesh("mesh1").feature("map_channel").feature("dist_height");
        dx.set("type","predefined");dx.set("elemcount",nx);dx.set("elemratio",1.0);dx.set("reverse",false);
        dy.set("type","predefined");dy.set("elemcount",ny);dy.set("elemratio",1.0);dy.set("reverse",false);
        m.component("comp1").mesh("mesh1").run();
        System.out.println("M03A4_PROGRESS|MESH_REBUILT|PASS|nx="+nx+"|ny="+ny);
    }

    private static void clearOldSolution(Model m){
        for(String s:m.sol().tags())m.sol(s).clearSolutionData();
        System.out.println("M03A4_PROGRESS|OLD_SOLUTION_CLEARED|PASS");
    }

    private static void activateFullStudy(Model m){
        m.study("std_audit").feature("stat").activate("spf",true);
        m.study("std_audit").feature("stat").activate("tds",true);
        m.study("std_audit").feature("stat").activate("cd",true);
        m.study("std_audit").feature("stat").set("usestol",true);
        m.study("std_audit").feature("stat").set("stol",1.0e-10);
    }

    private static void configureAccurateTransportFluxes(Model m){
        m.component("comp1").physics("tds").prop("ShapeProperty")
            .set("boundaryFlux_concentration","1");
        m.component("comp1").physics("tds").prop("ShapeProperty")
            .set("boundaryFluxSmooth_concentration","0");
        String enabled=m.component("comp1").physics("tds").prop("ShapeProperty")
            .getString("boundaryFlux_concentration");
        if(!"1".equals(enabled))throw new IllegalStateException("TDS accurate boundary-flux computation is not enabled");
        System.out.println("M03A4_PROGRESS|TDS_ACCURATE_BOUNDARY_FLUX|PASS|smoothing=off");
    }

    private static CaseResult evaluate(Model m,CaseInput in,double re){
        CaseResult x=new CaseResult();x.in=in;
        x.anode=value(m,"m033_i_anode");x.current=value(m,"m033_i_cathode");x.left=value(m,"m033_i_left");x.right=value(m,"m033_i_right");
        x.phiAnode=value(m,"m033_phi_anode");x.phiCathode=value(m,"m033_phi_cathode");x.delta=x.phiAnode-x.phiCathode;
        double length=m.param().evaluate("Lcell","m"),area=length*in.depth;
        x.currentAnalytic=in.kappa*area*x.delta/in.h;x.resistanceAnalytic=in.h/(in.kappa*area);x.resistance=x.delta/x.current;
        x.currentError=LiNRR_M03A_4_Metrics.relative(x.current,x.currentAnalytic);x.resistanceError=LiNRR_M03A_4_Metrics.relative(x.resistance,x.resistanceAnalytic);
        x.currentBalance=LiNRR_M03A_4_Metrics.closure(x.anode+x.current+x.left+x.right,x.anode,x.current,x.left,x.right);
        double f=m.param().evaluate("F_const_M033","C/mol"),fe=m.param().evaluate("FE_prescribed");
        x.n2Expected=fe*x.current/(6.0*f);x.nh3Expected=fe*x.current/(3.0*f);
        x.n2Tds=value(m,"m033_n2_cath");x.nh3Tds=-value(m,"m033_nh3_cath");
        x.n2Imposed=value(m,"m033_n2_imposed_cath");x.nh3Imposed=value(m,"m033_nh3_imposed_cath");
        x.n2In=-value(m,"m033_n2_in");x.n2Out=value(m,"m033_n2_out");x.nh3In=-value(m,"m033_nh3_in");x.nh3Out=value(m,"m033_nh3_out");
        x.n2CurrentError=LiNRR_M03A_4_Metrics.relative(x.n2Tds,x.n2Expected);x.nh3CurrentError=LiNRR_M03A_4_Metrics.relative(x.nh3Tds,x.nh3Expected);x.ratioError=LiNRR_M03A_4_Metrics.relative(x.nh3Tds/x.n2Tds,2.0);
        x.n2Balance=LiNRR_M03A_4_Metrics.closure(x.n2In-x.n2Out-x.n2Tds,x.n2In,x.n2Out,x.n2Tds);
        x.nh3Balance=LiNRR_M03A_4_Metrics.closure(x.nh3Tds+x.nh3In-x.nh3Out,x.nh3Tds,x.nh3In,x.nh3Out);
        x.nitrogenBalance=LiNRR_M03A_4_Metrics.closure(2.0*(x.n2In-x.n2Out)-(x.nh3Out-x.nh3In),2.0*x.n2In,2.0*x.n2Out,x.nh3Out,x.nh3In);
        x.geometryHeight=value(m,"m033_len_inlet");x.reportedCurrentDensity=x.current/in.reportArea;
        x.inversionKappa=in.inversionExpected?in.inversionH/(in.eisArea*re):in.kappa;
        boolean finite=LiNRR_M03A_4_Metrics.finite(x.current,x.resistance,x.currentError,x.resistanceError,x.currentBalance,x.n2Tds,x.nh3Tds,x.n2In,x.n2Out,x.nh3In,x.nh3Out,x.n2CurrentError,x.nh3CurrentError,x.ratioError,x.n2Balance,x.nh3Balance,x.nitrogenBalance,x.geometryHeight,x.reportedCurrentDensity);
        x.fullPass=finite&&x.anode<0.0&&x.current>0.0&&x.currentError<=1e-3&&x.resistanceError<=1e-3&&x.currentBalance<=1e-6&&x.n2CurrentError<=1e-6&&x.nh3CurrentError<=1e-6&&x.ratioError<=1e-6&&x.n2Balance<=1e-4&&x.nh3Balance<=1e-4&&x.nitrogenBalance<=1e-4&&LiNRR_M03A_4_Metrics.relative(x.geometryHeight,in.h)<=1e-9&&(!in.inversionExpected||LiNRR_M03A_4_Metrics.relative(x.inversionKappa,in.kappa)<=1e-12);
        return x;
    }

    private static void emitCase(CaseResult x){
        System.out.println("M03A4_CASE|case_id|kappa_S_m|Hcell_m|depth_m|EIS_area_m2|report_area_m2|geometry_height_m|geometry_rebuilt|named_selections_pass|mesh_rebuilt|old_solution_cleared|spf_active|tds_active|cd_active|fresh_full_solve|analytical_current_A|COMSOL_current_A|current_relative_error|analytical_resistance_ohm|COMSOL_resistance_ohm|resistance_relative_error|reported_current_density_A_m2|status");
        System.out.println("M03A4_CASE|"+x.in.caseId+"|"+join(x.in.kappa,x.in.h,x.in.depth,x.in.eisArea,x.in.reportArea,x.geometryHeight,1,1,1,1,1,1,1,1,x.currentAnalytic,x.current,x.currentError,x.resistanceAnalytic,x.resistance,x.resistanceError,x.reportedCurrentDensity)+"|"+(x.fullPass?"PASS":"FAIL"));
    }

    private static void emitTransferRows(Model m,CaseResult x){
        System.out.println("M03A4_TRANSFER|case_id|source_quantity|source_value|source_unit|target_parameter|applied_value|applied_unit|consumer|expected_response|observed_response|relative_error|status|failure_reason");
        double[] src={x.in.kappa,x.in.h,x.in.depth,x.in.eisArea,x.in.reportArea};
        double[] applied={m.param().evaluate("kappa_dry_run","S/m"),m.param().evaluate("Hcell_dry_run","m"),m.param().evaluate("depth_dry_run","m"),m.param().evaluate("A_EIS_dry_run","m^2"),m.param().evaluate("A_j_report_dry_run","m^2")};
        String[] consumers={"cd/ice1 conductivity via kappa_M033","geom1 actual inlet height","all 2D-to-3D Wcell integrations","HFR conductivity inversion only","j_reported equals current-run I_total divided by A_j_report"};
        double[] expected={x.currentAnalytic,x.in.h,x.in.depth,x.in.kappa,x.current/x.in.reportArea};
        double[] observed={x.current,x.geometryHeight,m.param().evaluate("Wcell","m"),x.inversionKappa,x.reportedCurrentDensity};
        for(int i=0;i<src.length;i++){
            double err=LiNRR_M03A_4_Metrics.relative(expected[i],observed[i]);
            boolean pass=LiNRR_M03A_4_Metrics.relative(src[i],applied[i])<=1e-12&&err<=(i==0?1e-3:1e-9);
            System.out.println("M03A4_TRANSFER|"+x.in.caseId+"|"+QUANTITIES[i]+"|"+fmt(src[i])+"|"+UNITS[i]+"|"+TARGETS[i]+"|"+fmt(applied[i])+"|"+UNITS[i]+"|"+consumers[i]+"|"+fmt(expected[i])+"|"+fmt(observed[i])+"|"+fmt(err)+"|"+(pass?"PASS":"FAIL")+"|"+(pass?"":"consumer response mismatch"));
        }
    }

    private static void emitCombinedEvidence(Model m,CaseResult x){
        String status=x.fullPass?"SYNTHETIC_PARAMETER_TRANSFER_PASS":"FAIL";
        System.out.println("M03A4_CLOSURE|case_id|current_expected_N2_mol_s|actual_TDS_N2_cath_mol_s|current_to_TDS_N2_relative_error|current_expected_NH3_mol_s|actual_TDS_NH3_cath_mol_s|current_to_TDS_NH3_relative_error|NH3_N2_ratio_error|N2_inlet_mol_s|N2_outlet_mol_s|NH3_inlet_mol_s|NH3_outlet_mol_s|N2_balance|NH3_balance|nitrogen_balance|current_conservation|algebraic_imposed_N2_mol_s|algebraic_imposed_NH3_mol_s|algebraic_classification|transport_classification|status");
        System.out.println("M03A4_CLOSURE|"+x.in.caseId+"|"+join(x.n2Expected,x.n2Tds,x.n2CurrentError,x.nh3Expected,x.nh3Tds,x.nh3CurrentError,x.ratioError,x.n2In,x.n2Out,x.nh3In,x.nh3Out,x.n2Balance,x.nh3Balance,x.nitrogenBalance,x.currentBalance,x.n2Imposed,x.nh3Imposed)+"|ALGEBRAIC_STOICHIOMETRIC_MAPPING|ACTUAL_TDS_AND_INLET_OUTLET_BALANCE|"+status);
        System.out.println("M03A4_PROGRESS|COMBINED_NONIDENTITY|FRESH_FULL_COUPLED_STUDY|PASS");
        System.out.println("M03A4_PROGRESS|COMBINED_NONIDENTITY|SPF_ACTIVE|PASS");
        System.out.println("M03A4_PROGRESS|COMBINED_NONIDENTITY|TDS_ACTIVE|PASS");
        System.out.println("M03A4_PROGRESS|COMBINED_NONIDENTITY|CD_ACTIVE|PASS");
        System.out.println("M03A4_PROGRESS|COMBINED_NONIDENTITY|CURRENT_SOLUTION_CURRENT_RUN|PASS");
        System.out.println("M03A4_PROGRESS|COMBINED_NONIDENTITY|SPECIES_SOLUTION_CURRENT_RUN|PASS");
    }

    private static void emitResponses(List<CaseResult> r){
        System.out.println("M03A4_RESPONSE|case_id|metric|expected_ratio|observed_ratio|relative_error|status|failure_reason");
        CaseResult b=find(r,"IDENTITY_REFERENCE");
        response("KAPPA_PERTURBATION","current_vs_kappa",find(r,"KAPPA_PERTURBATION").in.kappa/b.in.kappa,find(r,"KAPPA_PERTURBATION").current/b.current,1e-3);
        response("KAPPA_PERTURBATION","resistance_vs_inverse_kappa",b.in.kappa/find(r,"KAPPA_PERTURBATION").in.kappa,find(r,"KAPPA_PERTURBATION").resistance/b.resistance,1e-3);
        response("HCELL_PERTURBATION","resistance_vs_Hcell",find(r,"HCELL_PERTURBATION").in.h/b.in.h,find(r,"HCELL_PERTURBATION").resistance/b.resistance,1e-3);
        response("HCELL_PERTURBATION","current_vs_inverse_Hcell",b.in.h/find(r,"HCELL_PERTURBATION").in.h,find(r,"HCELL_PERTURBATION").current/b.current,1e-3);
        response("DEPTH_PERTURBATION","current_vs_depth",find(r,"DEPTH_PERTURBATION").in.depth/b.in.depth,find(r,"DEPTH_PERTURBATION").current/b.current,1e-3);
        response("DEPTH_PERTURBATION","resistance_vs_inverse_depth",b.in.depth/find(r,"DEPTH_PERTURBATION").in.depth,find(r,"DEPTH_PERTURBATION").resistance/b.resistance,1e-3);
        response("EIS_AREA_PERTURBATION","kappa_vs_inverse_EIS_area",b.in.eisArea/find(r,"EIS_AREA_PERTURBATION").in.eisArea,find(r,"EIS_AREA_PERTURBATION").in.kappa/b.in.kappa,1e-12);
        response("EIS_AREA_PERTURBATION","current_changes_only_via_transferred_kappa",find(r,"EIS_AREA_PERTURBATION").in.kappa/b.in.kappa,find(r,"EIS_AREA_PERTURBATION").current/b.current,1e-3);
        response("REPORT_AREA_PERTURBATION","PDE_total_current_unchanged",1.0,find(r,"REPORT_AREA_PERTURBATION").current/b.current,1e-3);
        response("REPORT_AREA_PERTURBATION","PDE_resistance_unchanged",1.0,find(r,"REPORT_AREA_PERTURBATION").resistance/b.resistance,1e-3);
        response("REPORT_AREA_PERTURBATION","reported_j_vs_inverse_area",b.in.reportArea/find(r,"REPORT_AREA_PERTURBATION").in.reportArea,find(r,"REPORT_AREA_PERTURBATION").reportedCurrentDensity/b.reportedCurrentDensity,1e-3);
        CaseResult c=find(r,"COMBINED_NONIDENTITY");response("COMBINED_NONIDENTITY","current_vs_analytical",1.0,c.current/c.currentAnalytic,1e-3);response("COMBINED_NONIDENTITY","resistance_vs_analytical",1.0,c.resistance/c.resistanceAnalytic,1e-3);
    }

    private static void response(String c,String metric,double expected,double observed,double tol){double e=LiNRR_M03A_4_Metrics.relative(expected,observed);boolean p=e<=tol;System.out.println("M03A4_RESPONSE|"+c+"|"+metric+"|"+fmt(expected)+"|"+fmt(observed)+"|"+fmt(e)+"|"+(p?"PASS":"FAIL")+"|"+(p?"":"scaling response outside tolerance"));if(!p)throw new IllegalStateException("Parameter response failed: "+c+"/"+metric);}

    private static CaseInput caseInput(Map<String,Resolved> m,String id){
        CaseInput x=new CaseInput();x.caseId=id;x.resolved=new Resolved[QUANTITIES.length];for(int i=0;i<QUANTITIES.length;i++)x.resolved[i]=require(m,id,QUANTITIES[i],TARGETS[i],UNITS[i]);x.kappa=x.resolved[0].value;x.h=x.resolved[1].value;x.depth=x.resolved[2].value;x.eisArea=x.resolved[3].value;x.reportArea=x.resolved[4].value;
        Resolved k=require(m,id,QUANTITIES[0],TARGETS[0],UNITS[0]);x.inversionExpected=k.resolutionMethod.startsWith("HFR_")||"RESOLVED_HFR_CHAIN".equals(k.resolutionMethod);x.inversionH=x.h;
        if("HCELL_PERTURBATION".equals(id))x.inversionH=require(m,"IDENTITY_REFERENCE","electrode_spacing","Hcell_dry_run","m").value;
        for(double v:new double[]{x.kappa,x.h,x.depth,x.eisArea,x.reportArea})if(!(v>0.0))throw new IllegalStateException("Nonpositive case input: "+id);
        return x;
    }

    private static Map<String,Resolved> readResolved(Path p)throws Exception{
        byte[] bytes=Files.readAllBytes(p);for(byte b:bytes)if((b&0xff)>127)throw new IllegalStateException("Canonical resolved artifact must be ASCII");
        List<List<String>> rows=parseCsv(new String(bytes,StandardCharsets.US_ASCII));if(rows.size()<2)throw new IllegalStateException("Resolved artifact empty");
        String[] expected={"sample_id","quantity_name","target_parameter","value_SI","uncertainty_SI","unit","data_origin","evidence_status","resolution_method","source_table","source_rows","source_reference","source_sha256","status","failure_reason"};
        if(!rows.get(0).equals(Arrays.asList(expected)))throw new IllegalStateException("Resolved artifact header mismatch");
        Map<String,Resolved> out=new LinkedHashMap<>();
        for(int i=1;i<rows.size();i++){
            List<String> c=rows.get(i);if(c.size()!=expected.length)throw new IllegalStateException("Resolved column count mismatch row "+(i+1));
            Resolved r=new Resolved();r.sample=c.get(0);r.quantity=c.get(1);r.target=c.get(2);r.valueText=c.get(3);r.value=number(r.valueText,"value row "+(i+1));r.uncertainty=number(c.get(4),"uncertainty row "+(i+1));r.unit=c.get(5);r.origin=c.get(6);r.evidenceStatus=c.get(7);r.resolutionMethod=c.get(8);r.sourceTable=c.get(9);r.sourceRows=c.get(10);r.sourceReference=c.get(11);r.sourceHash=c.get(12).toLowerCase(Locale.ROOT);r.status=c.get(13);r.failure=c.get(14);
            if(!"SYNTHETIC".equals(r.origin)||!"PASS".equals(r.status)||!r.failure.isEmpty()||r.uncertainty<0.0||!r.sourceHash.matches("[0-9a-f]{64}")||r.resolutionMethod.isEmpty()||r.sourceTable.isEmpty()||r.sourceRows.isEmpty()||r.sourceReference.isEmpty())throw new IllegalStateException("Resolved provenance/status validation failed row "+(i+1));
            String key=r.sample+"|"+r.quantity;if(out.put(key,r)!=null)throw new IllegalStateException("Duplicate resolved key: "+key);
        }
        return out;
    }

    private static List<List<String>> parseCsv(String text){
        List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder f=new StringBuilder();boolean quoted=false;
        for(int i=0;i<text.length();i++){char ch=text.charAt(i);if(quoted){if(ch=='\"'){if(i+1<text.length()&&text.charAt(i+1)=='\"'){f.append('\"');i++;}else quoted=false;}else f.append(ch);}else if(ch=='\"'){quoted=true;}else if(ch==','){row.add(f.toString());f.setLength(0);}else if(ch=='\r'||ch=='\n'){if(ch=='\r'&&i+1<text.length()&&text.charAt(i+1)=='\n')i++;row.add(f.toString());f.setLength(0);if(!(row.size()==1&&row.get(0).isEmpty()))rows.add(row);row=new ArrayList<>();}else f.append(ch);}if(quoted)throw new IllegalStateException("Unclosed CSV quote");if(f.length()>0||!row.isEmpty()){row.add(f.toString());rows.add(row);}return rows;
    }

    private static Resolved require(Map<String,Resolved> m,String sample,String quantity,String target,String unit){Resolved r=m.get(sample+"|"+quantity);if(r==null)throw new IllegalStateException("Missing resolved input: "+sample+"/"+quantity);if(!target.equals(r.target)||!unit.equals(r.unit))throw new IllegalStateException("Resolved target/unit mismatch: "+sample+"/"+quantity);return r;}
    private static String sha256(Path p)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");byte[] b=Files.readAllBytes(p);byte[] h=d.digest(b);StringBuilder s=new StringBuilder();for(byte x:h)s.append(String.format(Locale.ROOT,"%02x",x&255));return s.toString();}
    private static String treeSignature(Model m){StringBuilder b=new StringBuilder();for(String p:m.component("comp1").physics().tags()){b.append(p).append(':').append(m.component("comp1").physics(p).getType()).append(';');for(String f:m.component("comp1").physics(p).feature().tags())b.append(p).append('/').append(f).append(':').append(m.component("comp1").physics(p).feature(f).getType()).append(';');}return b.toString();}
    private static void auditRequiredTree(Model m){requireTag(m.component("comp1").physics().tags(),"spf");requireTag(m.component("comp1").physics().tags(),"tds");requireTag(m.component("comp1").physics().tags(),"cd");if(!"PrimaryCurrentDistribution".equals(m.component("comp1").physics("cd").getType()))throw new IllegalStateException("cd type changed");}
    private static void auditForbidden(Model m){for(String p:m.component("comp1").physics().tags()){auditText(p);auditText(m.component("comp1").physics(p).getType());auditText(m.component("comp1").physics(p).label());for(String f:m.component("comp1").physics(p).feature().tags()){auditText(f);auditText(m.component("comp1").physics(p).feature(f).getType());auditText(m.component("comp1").physics(p).feature(f).label());}}}
    private static void auditText(String text){String x=text==null?"":text.toLowerCase(Locale.ROOT);for(String f:FORBIDDEN)if(x.contains(f.toLowerCase(Locale.ROOT)))throw new IllegalStateException("Forbidden physics text: "+f);}
    private static void requireSelection(Model m,String tag,int dim,int count){int n=m.component("comp1").selection(tag).entities(dim).length;if(n!=count)throw new IllegalStateException("Named selection audit failed: "+tag+" count="+n);}
    private static void requireTag(String[] tags,String wanted){if(!hasTag(tags,wanted))throw new IllegalStateException("Missing tag: "+wanted);}
    private static boolean hasTag(String[] tags,String wanted){for(String t:tags)if(wanted.equals(t))return true;return false;}
    private static double value(Model m,String tag){double[][] x=m.result().numerical(tag).getReal();if(x==null||x.length==0||x[0].length==0||!Double.isFinite(x[0][0]))throw new IllegalStateException("Invalid current-run numerical: "+tag);return x[0][0];}
    private static CaseResult find(List<CaseResult> r,String id){for(CaseResult x:r)if(id.equals(x.in.caseId))return x;throw new IllegalStateException("Case missing: "+id);}
    private static double number(String s,String name){try{double v=Double.parseDouble(s);if(!Double.isFinite(v))throw new NumberFormatException();return v;}catch(Exception e){throw new IllegalStateException("Invalid "+name);}}
    private static String join(Object... x){List<String> out=new ArrayList<>();for(Object v:x)out.add(v instanceof Number?fmt(((Number)v).doubleValue()):String.valueOf(v));return String.join("|",out);}
    private static String fmt(double x){return LiNRR_M03A_4_Metrics.fmt(x);}
    private static String requireRunInput(String name){try{Class<?> b=Class.forName("LiNRR_M03A_4_RunInputs");Object v=b.getMethod("get",String.class).invoke(null,name);if(v!=null&&!v.toString().trim().isEmpty())return v.toString();}catch(Throwable ignored){}String v=System.getenv(name);if(v!=null&&!v.trim().isEmpty())return v;throw new IllegalStateException(name+" is required");}
    private static final class Resolved{String sample,quantity,target,valueText,unit,origin,evidenceStatus,resolutionMethod,sourceTable,sourceRows,sourceReference,sourceHash,status,failure;double value,uncertainty;}
    private static final class CaseInput{String caseId;Resolved[] resolved;double kappa,h,depth,eisArea,reportArea,inversionH;boolean inversionExpected;}
    private static final class CaseResult{CaseInput in;double anode,current,left,right,phiAnode,phiCathode,delta,currentAnalytic,resistanceAnalytic,resistance,currentError,resistanceError,currentBalance,n2Expected,nh3Expected,n2Tds,nh3Tds,n2Imposed,nh3Imposed,n2In,n2Out,nh3In,nh3Out,n2CurrentError,nh3CurrentError,ratioError,n2Balance,nh3Balance,nitrogenBalance,geometryHeight,reportedCurrentDensity,inversionKappa;boolean fullPass;}
}
