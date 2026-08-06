import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.util.Locale;

/** M10A1 - stationary liquid and N2 flow in exact M10A0.4 CAD-derived domains. */
public final class LiNRR_M10A1_RealCAD_Flow {
    private static final String RUNTIME_CLASS="LiNRR_M10A1_RuntimeInputs";
    private static String currentApi="NONE",currentFeature="NONE";
    private static int evalSerial=0;
    private LiNRR_M10A1_RealCAD_Flow(){}

    public static Model run()throws Exception{
        System.out.println("M10A1_BOOT_START");
        try{
            final String input=rt("INPUT_MPH"),output=rt("OUTPUT_MPH"),figureDir=rt("FIGURE_DIR");
            rt("CC_SHA256");rt("CHAMBER_SHA256");
            System.out.println("M10A1_RUNTIME_INPUTS_PASS");
            api("ModelUtil.load","Model");final Model m=ModelUtil.load("Model",input);
            m.label("LiNRR_M10A1_real_cad_flow_solved.mph");
            m.comments("M10A1 numerical-path validation only. Exact CAD-derived fluid domains; all uncalibrated operating/fluid values are NUMERICAL_TEST_ONLY.");
            defineParameters(m);
            duplicateFlowComponents(m);
            addFlowSystem(m,"comp_electrolyte_flow","geom_electrolyte_fluid","spf_liq","mat_liq_test",
                "sel_dom_electrolyte_fluid","sel_bnd_electrolyte_inlet","sel_bnd_electrolyte_outlet",
                "sel_bnd_electrolyte_walls",new String[]{"sel_bnd_electrolyte_gde_top","sel_bnd_electrolyte_gde_bottom"},
                "Q_liq_test","A_liq_in_test","rho_liq_test","mu_liq_test","liq");
            addFlowSystem(m,"comp_n2_flow","geom_n2_channel_fluid","spf_n2","mat_n2_test",
                "sel_dom_n2_channel","sel_bnd_n2_inlet","sel_bnd_n2_outlet","sel_bnd_n2_walls",
                new String[]{"sel_bnd_n2_gde_interface"},"Q_n2_test","A_n2_in_test","rho_n2_test","mu_n2_test","n2");
            System.out.println("M10A1_PHYSICS_CREATE_PASS");

            buildMesh(m,"comp_electrolyte_flow","mesh_liq","sel_dom_electrolyte_fluid","sel_bnd_electrolyte_walls",6,"coarse");
            buildMesh(m,"comp_n2_flow","mesh_n2","sel_dom_n2_channel","sel_bnd_n2_walls",6,"coarse");
            System.out.println("M10A1_COARSE_MESH_PASS");
            createStudies(m);

            final long liqStart=System.nanoTime();solveWithRamp(m,"std_liq_stationary","flow_scale_liq_test");
            final double liqCoarseTime=(System.nanoTime()-liqStart)/1e9;
            final String liqSol=solverForStudy(m,"std_liq_stationary");
            final String liqData=solutionDataset(m,"dset_liq_solution",liqSol,"comp_electrolyte_flow");
            final double[] liqCoarse=metrics(m,"liquid","comp_electrolyte_flow","mesh_liq","spf_liq",liqData,liqSol,
                "sel_dom_electrolyte_fluid","sel_bnd_electrolyte_inlet","sel_bnd_electrolyte_outlet",
                "rho_liq_test","mu_liq_test","D_liq_in_test","Q_liq_test",liqCoarseTime);
            validateMetrics("liquid/coarse",liqCoarse,m.param().evaluate("Q_liq_test","m^3/s"));
            System.out.println("M10A1_LIQUID_COARSE_SOLVE_PASS");

            final long n2Start=System.nanoTime();solveWithRamp(m,"std_n2_stationary","flow_scale_n2_test");
            final double n2CoarseTime=(System.nanoTime()-n2Start)/1e9;
            final String n2Sol=solverForStudy(m,"std_n2_stationary");
            final String n2Data=solutionDataset(m,"dset_n2_solution",n2Sol,"comp_n2_flow");
            final double[] n2Coarse=metrics(m,"n2","comp_n2_flow","mesh_n2","spf_n2",n2Data,n2Sol,
                "sel_dom_n2_channel","sel_bnd_n2_inlet","sel_bnd_n2_outlet",
                "rho_n2_test","mu_n2_test","D_n2_in_test","Q_n2_test",n2CoarseTime);
            validateMetrics("n2/coarse",n2Coarse,m.param().evaluate("Q_n2_test","m^3/s"));
            System.out.println("M10A1_N2_COARSE_SOLVE_PASS");

            refineMesh(m,"comp_electrolyte_flow","mesh_liq",5,"normal");
            final long liqMedStart=System.nanoTime();solveWithRamp(m,"std_liq_stationary","flow_scale_liq_test");
            double[] liqMedium=metrics(m,"liquid","comp_electrolyte_flow","mesh_liq","spf_liq",liqData,liqSol,
                "sel_dom_electrolyte_fluid","sel_bnd_electrolyte_inlet","sel_bnd_electrolyte_outlet",
                "rho_liq_test","mu_liq_test","D_liq_in_test","Q_liq_test",(System.nanoTime()-liqMedStart)/1e9);
            validateMetrics("liquid/normal",liqMedium,m.param().evaluate("Q_liq_test","m^3/s"));
            validateRefinedMesh("liquid",liqCoarse,liqMedium);

            refineMesh(m,"comp_n2_flow","mesh_n2",5,"normal");
            final long n2MedStart=System.nanoTime();solveWithRamp(m,"std_n2_stationary","flow_scale_n2_test");
            double[] n2Medium=metrics(m,"n2","comp_n2_flow","mesh_n2","spf_n2",n2Data,n2Sol,
                "sel_dom_n2_channel","sel_bnd_n2_inlet","sel_bnd_n2_outlet",
                "rho_n2_test","mu_n2_test","D_n2_in_test","Q_n2_test",(System.nanoTime()-n2MedStart)/1e9);
            validateMetrics("n2/normal",n2Medium,m.param().evaluate("Q_n2_test","m^3/s"));
            validateRefinedMesh("n2",n2Coarse,n2Medium);
            final double liqDpDiff=relative(liqCoarse[3],liqMedium[3]),n2DpDiff=relative(n2Coarse[3],n2Medium[3]);
            if(liqDpDiff>0.10){refineMesh(m,"comp_electrolyte_flow","mesh_liq",4,"fine_local_review");long t=System.nanoTime();solveWithRamp(m,"std_liq_stationary","flow_scale_liq_test");liqMedium=metrics(m,"liquid","comp_electrolyte_flow","mesh_liq","spf_liq",liqData,liqSol,"sel_dom_electrolyte_fluid","sel_bnd_electrolyte_inlet","sel_bnd_electrolyte_outlet","rho_liq_test","mu_liq_test","D_liq_in_test","Q_liq_test",(System.nanoTime()-t)/1e9);validateMetrics("liquid/fine",liqMedium,m.param().evaluate("Q_liq_test","m^3/s"));}
            if(n2DpDiff>0.10){refineMesh(m,"comp_n2_flow","mesh_n2",4,"fine_local_review");long t=System.nanoTime();solveWithRamp(m,"std_n2_stationary","flow_scale_n2_test");n2Medium=metrics(m,"n2","comp_n2_flow","mesh_n2","spf_n2",n2Data,n2Sol,"sel_dom_n2_channel","sel_bnd_n2_inlet","sel_bnd_n2_outlet","rho_n2_test","mu_n2_test","D_n2_in_test","Q_n2_test",(System.nanoTime()-t)/1e9);validateMetrics("n2/fine",n2Medium,m.param().evaluate("Q_n2_test","m^3/s"));}
            System.out.println("M10A1_MESH_REVIEW|fluid=liquid|coarse_to_normal_delta_p_relative="+f(liqDpDiff)+"|status="+(liqDpDiff<=0.10?"PASS":"FINE_REVIEW_RUN"));
            System.out.println("M10A1_MESH_REVIEW|fluid=n2|coarse_to_normal_delta_p_relative="+f(n2DpDiff)+"|status="+(n2DpDiff<=0.10?"PASS":"FINE_REVIEW_RUN"));
            System.out.println("M10A1_MEDIUM_MESH_REVIEW_PASS");

            emitCsv("coarse",liqCoarse,"liquid");emitCsv("final",liqMedium,"liquid");
            emitCsv("coarse",n2Coarse,"n2");emitCsv("final",n2Medium,"n2");
            emitGeometryCsv(liqMedium,n2Medium);emitMeshCsv("coarse",liqCoarse,"liquid");emitMeshCsv("final",liqMedium,"liquid");emitMeshCsv("coarse",n2Coarse,"n2");emitMeshCsv("final",n2Medium,"n2");
            createResults(m,liqData,n2Data);
            api("model.save","Model");m.save(output);System.out.println("M10A1_SOLVED_MODEL_SAVE_PASS");
            exportFigures(m,figureDir);
            api("model.save(after_results)","Model");m.save(output);
            System.out.println("M10A1_FLOW_FIELDS_FINITE_PASS");
            System.out.println("M10A1_MASS_CONSERVATION_PASS");
            System.out.println("M10A1_REAL_CAD_FLOW_SOLVED=PASS");return m;
        }catch(Throwable e){System.err.println("M10A1_FAILURE_CONTEXT_BEGIN");System.err.println("M10A1_FIRST_FAILED_API="+currentApi);System.err.println("M10A1_FIRST_FAILED_FEATURE_TAG="+currentFeature);System.err.println("M10A1_EXCEPTION_CLASS="+e.getClass().getName());System.err.println("M10A1_EXCEPTION_MESSAGE="+String.valueOf(e.getMessage()));e.printStackTrace(System.err);System.err.println("M10A1_FAILURE_CONTEXT_END");if(e instanceof Exception)throw(Exception)e;throw(Error)e;}
    }

    private static void defineParameters(Model m){
        setp(m,"T_test","298.15[K]","NUMERICAL_TEST_ONLY temperature");
        setp(m,"Q_liq_test","1[cm^3/min]","NUMERICAL_TEST_ONLY liquid volume flow (1 mL/min; cm^3 used because this COMSOL unit database rejects mL)");
        setp(m,"rho_liq_test","889[kg/m^3]","NUMERICAL_TEST_ONLY provisional liquid density");
        setp(m,"mu_liq_test","0.46[mPa*s]","NUMERICAL_TEST_ONLY provisional liquid dynamic viscosity");
        setp(m,"Q_n2_test","10[cm^3/min]","NUMERICAL_TEST_ONLY N2 volume flow (10 mL/min; cm^3 used because this COMSOL unit database rejects mL)");
        setp(m,"rho_n2_test","1.145[kg/m^3]","NUMERICAL_TEST_ONLY N2 density at nominal condition");
        setp(m,"mu_n2_test","1.76e-5[Pa*s]","NUMERICAL_TEST_ONLY N2 dynamic viscosity");
        setp(m,"p_out_test","0[Pa]","NUMERICAL_TEST_ONLY gauge outlet pressure");
        setp(m,"A_liq_in_test","31.5391404487[mm^2]","NUMERICAL_TEST_ONLY measured CAD inlet area");
        setp(m,"A_n2_in_test","31.5391329804[mm^2]","NUMERICAL_TEST_ONLY measured CAD inlet area");
        setp(m,"D_liq_in_test","2*sqrt(A_liq_in_test/pi)","NUMERICAL_TEST_ONLY equivalent inlet diameter");
        setp(m,"D_n2_in_test","2*sqrt(A_n2_in_test/pi)","NUMERICAL_TEST_ONLY equivalent inlet diameter");
        setp(m,"flow_scale_liq_test","1","NUMERICAL_TEST_ONLY continuation factor");
        setp(m,"flow_scale_n2_test","1","NUMERICAL_TEST_ONLY continuation factor");
        System.out.println("M10A1_PARAMETER_UNIT_AUDIT|Q_liq_m3_s="+f(m.param().evaluate("Q_liq_test","m^3/s"))+"|Q_n2_m3_s="+f(m.param().evaluate("Q_n2_test","m^3/s")));
        System.out.println("M10A1_NUMERICAL_TEST_PARAMETERS_PASS");
    }
    private static void setp(Model m,String n,String v,String d){api("model.param.set",n);m.param().set(n,v,d);}

    private static void duplicateFlowComponents(Model m){api("component.tag(rename)","comp_electrolyte_flow");m.component("comp_electrolyte_fluid").tag("comp_electrolyte_flow");m.component("comp_electrolyte_flow").label("Electrolyte flow - exact CAD fluid domain");api("component.tag(rename)","comp_n2_flow");m.component("comp_n2_channel_fluid").tag("comp_n2_flow");m.component("comp_n2_flow").label("N2 flow - exact CAD channel domain");System.out.println("M10A1_FLOW_COMPONENTS_PASS");}

    private static void addFlowSystem(Model m,String c,String g,String spf,String mat,String dom,String inlet,String outlet,String walls,String[] gde,String q,String area,String rho,String mu,String stem){
        api("component.material().create",mat);m.component(c).material().create(mat,"Common");m.component(c).material(mat).label("NUMERICAL_TEST_ONLY fluid properties");m.component(c).material(mat).selection().named(dom);m.component(c).material(mat).propertyGroup("def").set("density",rho);m.component(c).material(mat).propertyGroup("def").set("dynamicviscosity",mu);
        api("component.physics().create(LaminarFlow)",spf);m.component(c).physics().create(spf,"LaminarFlow",g);m.component(c).physics(spf).label(("liq".equals(stem)?"Liquid":"N2")+" Stationary Laminar Flow | NUMERICAL_TEST_ONLY");m.component(c).physics(spf).selection().named(dom);
        m.component(c).physics(spf).feature("init1").set("u",new String[]{"0[m/s]","0[m/s]","0[m/s]"});m.component(c).physics(spf).feature("init1").set("p","p_out_test");
        m.component(c).physics(spf).create("inlet_"+stem,"Inlet",2);m.component(c).physics(spf).feature("inlet_"+stem).selection().named(inlet);m.component(c).physics(spf).feature("inlet_"+stem).label("Fully developed prescribed volume-flow inlet | NUMERICAL_TEST_ONLY");m.component(c).physics(spf).feature("inlet_"+stem).set("BoundaryCondition","LaminarInflow");m.component(c).physics(spf).feature("inlet_"+stem).set("LaminarInflowOption","V0");m.component(c).physics(spf).feature("inlet_"+stem).set("V0","flow_scale_"+stem+"_test*"+q);
        m.component(c).physics(spf).create("outlet_"+stem,"Outlet",2);m.component(c).physics(spf).feature("outlet_"+stem).selection().named(outlet);m.component(c).physics(spf).feature("outlet_"+stem).label("Fully developed zero gauge pressure outlet | NUMERICAL_TEST_ONLY");m.component(c).physics(spf).feature("outlet_"+stem).set("BoundaryCondition","LaminarOutflow");m.component(c).physics(spf).feature("outlet_"+stem).set("LaminarOutflowOption","p0_exit");m.component(c).physics(spf).feature("outlet_"+stem).set("p0_exit","p_out_test");
        m.component(c).physics(spf).create("walls_"+stem,"Wall",2);m.component(c).physics(spf).feature("walls_"+stem).selection().named(walls);m.component(c).physics(spf).feature("walls_"+stem).label("No Slip Walls");
        for(int i=0;i<gde.length;i++){String t="gde_wall_"+stem+"_"+(i+1);m.component(c).physics(spf).create(t,"Wall",2);m.component(c).physics(spf).feature(t).selection().named(gde[i]);m.component(c).physics(spf).feature(t).label("GDE interface "+(i+1)+" as No Slip Wall for M10A1");}
        api("component.cpl().create(Integration)","intop_"+stem+"_in");m.component(c).cpl().create("intop_"+stem+"_in","Integration");m.component(c).cpl("intop_"+stem+"_in").selection().named(inlet);
        m.component(c).cpl().create("intop_"+stem+"_out","Integration");m.component(c).cpl("intop_"+stem+"_out").selection().named(outlet);
        m.component(c).variable().create("var_"+stem+"_derived");m.component(c).variable("var_"+stem+"_derived").selection().named(dom);
        String uxv="n2".equals(stem)?"u2":"u",uyv="n2".equals(stem)?"v2":"v",uzv="n2".equals(stem)?"w2":"w";
        String tx=mu+"*(2*"+uxv+"x*nx+("+uxv+"y+"+uyv+"x)*ny+("+uxv+"z+"+uzv+"x)*nz)";String ty=mu+"*( ("+uyv+"x+"+uxv+"y)*nx+2*"+uyv+"y*ny+("+uyv+"z+"+uzv+"y)*nz)";String tz=mu+"*( ("+uzv+"x+"+uxv+"z)*nx+("+uzv+"y+"+uyv+"z)*ny+2*"+uzv+"z*nz)";String tn="("+tx+")*nx+("+ty+")*ny+("+tz+")*nz";
        m.component(c).variable("var_"+stem+"_derived").set("tauw_"+stem+"_test","sqrt(("+tx+"-("+tn+")*nx)^2+("+ty+"-("+tn+")*ny)^2+("+tz+"-("+tn+")*nz)^2)","Newtonian wall shear magnitude | NUMERICAL_TEST_ONLY properties");
    }

    private static void buildMesh(Model m,String c,String mesh,String dom,String walls,int level,String label){api("component.mesh().create",mesh);m.component(c).mesh().create(mesh);m.component(c).mesh(mesh).label(label+" Free Tetrahedral with 3-layer boundary-layer attempt");m.component(c).mesh(mesh).autoMeshSize(level);m.component(c).mesh(mesh).create("ftet1","FreeTet");m.component(c).mesh(mesh).feature("ftet1").selection().named(dom);m.component(c).mesh(mesh).create("bl1","BndLayer");m.component(c).mesh(mesh).feature("bl1").create("blp1","BndLayerProp");m.component(c).mesh(mesh).feature("bl1").feature("blp1").selection().set(m.component(c).selection(walls).entities(2));m.component(c).mesh(mesh).feature("bl1").feature("blp1").set("blnlayers",3);try{api("mesh.run(boundary_layer)",mesh);m.component(c).mesh(mesh).run();System.out.println("M10A1_BOUNDARY_LAYER_PASS|component="+c+"|layers=3");}catch(Throwable e){System.out.println("M10A1_BOUNDARY_LAYER_UNAVAILABLE|component="+c+"|class="+e.getClass().getName()+"|message="+clean(e.getMessage()));m.component(c).mesh(mesh).feature().remove("bl1");api("mesh.run(free_tet_fallback)",mesh);m.component(c).mesh(mesh).run();}validateMesh(m,c,mesh,dom,label);}
    private static void refineMesh(Model m,String c,String mesh,int level,String label){
        if(!m.component(c).mesh(mesh).feature().hasTag("size_review")){
            api("mesh.create(Size)","size_review");
            m.component(c).mesh(mesh).create("size_review","Size");
            m.component(c).mesh(mesh).feature().move("size_review",0);
        }
        m.component(c).mesh(mesh).feature("size_review").set("hauto",level);
        try{api("mesh.run(refine)",mesh);m.component(c).mesh(mesh).run();}catch(Throwable e){if(m.component(c).mesh(mesh).feature().hasTag("bl1")){System.out.println("M10A1_BOUNDARY_LAYER_UNAVAILABLE_ON_REFINEMENT|component="+c+"|message="+clean(e.getMessage()));m.component(c).mesh(mesh).feature().remove("bl1");m.component(c).mesh(mesh).run();}else throw e;}String dom="comp_electrolyte_flow".equals(c)?"sel_dom_electrolyte_fluid":"sel_dom_n2_channel";validateMesh(m,c,mesh,dom,label);}
    private static void validateMesh(Model m,String c,String mesh,String dom,String level){int n=m.component(c).mesh(mesh).getNumElem();double q=m.component(c).mesh(mesh).getMinQuality(),v=m.component(c).mesh(mesh).getMinVolume();int[]required=m.component(c).selection(dom).entities(3);boolean covered=true;String[]types={"tet","prism","pyr","hex"};for(int d:required){boolean found=false;for(String type:types){try{int[]ids=m.component(c).mesh(mesh).getGeomEntities(type);for(int id:ids)if(id==d){found=true;break;}}catch(Throwable ignored){}if(found)break;}if(!found){covered=false;break;}}String[]problems=m.component(c).mesh(mesh).problems();if(n<1||!Double.isFinite(q)||q<=0||!Double.isFinite(v)||v<=0||!covered)throw new IllegalStateException("MESH_AUDIT_INVALID: "+c+" elements="+n+" minq="+q+" minvol="+v+" selected_domains_covered="+covered);System.out.println("M10A1_MESH_AUDIT|level="+level+"|component="+c+"|elements="+n+"|minimum_quality="+f(q)+"|minimum_volume_m3="+f(v)+"|selected_domains_covered=true|whole_component_complete="+m.component(c).mesh(mesh).isComplete()+"|problem_tags="+problems.length);}

    private static void createStudies(Model m){api("model.study().create","std_liq_stationary");m.study().create("std_liq_stationary");m.study("std_liq_stationary").label("Liquid Stationary Flow | NUMERICAL_TEST_ONLY");m.study("std_liq_stationary").create("stat","Stationary");m.study("std_liq_stationary").feature("stat").activate("spf_liq",true);m.study("std_liq_stationary").feature("stat").activate("spf_n2",false);api("model.study().create","std_n2_stationary");m.study().create("std_n2_stationary");m.study("std_n2_stationary").label("N2 Stationary Flow | NUMERICAL_TEST_ONLY");m.study("std_n2_stationary").create("stat","Stationary");m.study("std_n2_stationary").feature("stat").activate("spf_liq",false);m.study("std_n2_stationary").feature("stat").activate("spf_n2",true);System.out.println("M10A1_STUDIES_CREATE_PASS");}
    private static void solveWithRamp(Model m,String study,String scale){
        m.param().set(scale,"1");m.study(study).showAutoSequences("sol");String sol=solverTag(m,study);configureNonlinearSolver(m,sol,"hnlin");
        try{api("solver.runAll(highly_nonlinear_direct)",sol);m.sol(sol).runAll();System.out.println("M10A1_DIRECT_SOLVE_PASS|study="+study+"|nonlinear_method=hnlin");return;}
        catch(Throwable direct){System.out.println("M10A1_DIRECT_SOLVE_UNAVAILABLE|study="+study+"|class="+direct.getClass().getName()+"|message="+clean(direct.getMessage()));}
        final String comp=scale.contains("liq")?"comp_electrolyte_flow":"comp_n2_flow",spf=scale.contains("liq")?"spf_liq":"spf_n2",initSol=scale.contains("liq")?"solinit_liq":"solinit_n2";
        m.sol(sol).clearSolutionData();m.component(comp).physics(spf).prop("PhysicalModelProperty").set("StokesFlowProp",true);m.study(study).showAutoSequences("sol");sol=solverTag(m,study);api("solver.runAll(Stokes_initialization_only)",sol);m.sol(sol).runAll();validateInitializationFlow(m,sol,comp,scale.contains("liq")?"sel_bnd_electrolyte_inlet":"sel_bnd_n2_inlet",scale.contains("liq")?"sel_bnd_electrolyte_outlet":"sel_bnd_n2_outlet",scale.contains("liq")?"Q_liq_test":"Q_n2_test",scale.contains("liq")?"dset_init_liq":"dset_init_n2");System.out.println("M10A1_STOKES_INITIALIZATION_PASS|study="+study+"|final_model_stokes=false");
        for(String t:m.sol().tags())if(initSol.equals(t)){m.sol().remove(t);break;}m.sol(sol).copySolution(initSol);m.component(comp).physics(spf).prop("PhysicalModelProperty").set("StokesFlowProp",false);m.sol(sol).clearSolutionData();m.study(study).showAutoSequences("sol");sol=solverTag(m,study);configureInitialSolution(m,sol,initSol);configureNonlinearSolver(m,sol,"hnlin");
        try{api("solver.runAll(Navier_Stokes_from_Stokes_initialization)",sol);m.sol(sol).runAll();System.out.println("M10A1_NAVIER_STOKES_FROM_STOKES_PASS|study="+study);return;}
        catch(Throwable initialized){System.out.println("M10A1_NS_FROM_STOKES_UNAVAILABLE|study="+study+"|class="+initialized.getClass().getName()+"|message="+clean(initialized.getMessage()));}
        m.sol(sol).clearSolutionData();
        if(!m.study(study).feature().hasTag("param_ramp")){m.study(study).create("param_ramp","Parametric");m.study(study).feature().move("param_ramp",0);}
        m.study(study).feature("param_ramp").set("pname",new String[]{scale});m.study(study).feature("param_ramp").set("plistarr",new String[]{"0.1 0.25 0.5 0.75 1"});m.study(study).feature("param_ramp").set("punit",new String[]{""});m.study(study).feature("param_ramp").set("sweeptype","sparse");m.study(study).feature("param_ramp").set("keepsol","last");
        m.study(study).showAutoSequences("sol");sol=solverTag(m,study);configureInitialSolution(m,sol,initSol);configureNonlinearSolver(m,sol,"hnlin");
        try{api("solver.runAll(highly_nonlinear_ramp)",sol);m.sol(sol).runAll();System.out.println("M10A1_FLOW_RAMP_PASS|study="+study+"|scales=0.1,0.25,0.5,0.75,1|nonlinear_method=hnlin");System.out.println("M10A1_FLOW_RAMP_RECOVERY_PASS|study="+study);return;}
        catch(Throwable ramp){System.out.println("M10A1_HNLIN_RAMP_UNAVAILABLE|study="+study+"|class="+ramp.getClass().getName()+"|message="+clean(ramp.getMessage()));}
        m.sol(sol).clearSolutionData();configureNonlinearSolver(m,sol,"pseudo_time");api("solver.runAll(pseudo_time_ramp)",sol);m.sol(sol).runAll();System.out.println("M10A1_FLOW_RAMP_PASS|study="+study+"|scales=0.1,0.25,0.5,0.75,1|nonlinear_method=pseudo_time");System.out.println("M10A1_FLOW_RAMP_RECOVERY_PASS|study="+study);
    }
    private static String solverTag(Model m,String study){String[]s=m.study(study).getSolverSequences("SolverSequence");if(s.length>0)return s[0];for(String t:m.sol().tags())if(study.equals(m.sol(t).study()))return t;throw new IllegalStateException("NO_SOLVER_SEQUENCE: "+study);}
    private static void configureInitialSolution(Model m,String sol,String initSol){for(String a:m.sol(sol).feature().tags())if("Variables".equals(m.sol(sol).feature(a).getType())){m.sol(sol).feature(a).set("initmethod","sol");m.sol(sol).feature(a).set("initsol",initSol);m.sol(sol).feature(a).set("initsoluse","current");m.sol(sol).feature(a).set("solnum","last");System.out.println("M10A1_INITIAL_SOLUTION_CONFIGURATION|solver="+sol+"|source="+initSol);return;}throw new IllegalStateException("VARIABLES_SOLVER_FEATURE_NOT_FOUND: "+sol);}
    private static void configureNonlinearSolver(Model m,String sol,String mode){for(String a:m.sol(sol).feature().tags())if("Stationary".equals(m.sol(sol).feature(a).getType()))for(String b:m.sol(sol).feature(a).feature().tags())if("FullyCoupled".equals(m.sol(sol).feature(a).feature(b).getType())){if("hnlin".equals(mode)){m.sol(sol).feature(a).feature(b).set("dtech","hnlin");m.sol(sol).feature(a).feature(b).set("initsteph","1e-4");m.sol(sol).feature(a).feature(b).set("minsteph","1e-8");m.sol(sol).feature(a).feature(b).set("maxiter",200);}else{m.sol(sol).feature(a).feature(b).set("dtech","bcktrack");m.sol(sol).feature(a).feature(b).set("stabacc","cflcmp");m.sol(sol).feature(a).feature(b).set("initcfl","1");m.sol(sol).feature(a).feature(b).set("mincfl","1e4");m.sol(sol).feature(a).feature(b).set("maxiter",300);}System.out.println("M10A1_SOLVER_CONFIGURATION|solver="+sol+"|stationary="+a+"|coupled="+b+"|mode="+mode);return;}throw new IllegalStateException("FULLY_COUPLED_SOLVER_FEATURE_NOT_FOUND: "+sol);}
    private static String solutionDataset(Model m,String tag,String sol,String comp){if(!m.result().dataset().hasTag(tag))m.result().dataset().create(tag,"Solution");m.result().dataset(tag).set("solution",sol);m.result().dataset(tag).set("comp",comp);System.out.println("M10A1_DATASET|tag="+tag+"|solution="+sol+"|component="+comp);return tag;}
    private static void validateInitializationFlow(Model m,String sol,String comp,String inlet,String outlet,String target,String data){solutionDataset(m,data,sol,comp);String flux="comp_n2_flow".equals(comp)?"u2*nx+v2*ny+w2*nz":"u*nx+v*ny+w*nz";double qi=Math.abs(eval(m,"IntSurface",data,inlet,flux,"m^3/s")),qo=Math.abs(eval(m,"IntSurface",data,outlet,flux,"m^3/s")),qt=m.param().evaluate(target,"m^3/s"),ei=Math.abs(qi-qt)/qt,mb=Math.abs(qi-qo)/Math.max(Math.max(qi,qo),1e-30);System.out.println("M10A1_STOKES_FLOW_AUDIT|component="+comp+"|Q_in_m3_s="+f(qi)+"|Q_out_m3_s="+f(qo)+"|target_m3_s="+f(qt)+"|inlet_relative_error="+f(ei)+"|mass_balance_relative="+f(mb));if(ei>0.02||mb>0.01)throw new IllegalStateException("STOKES_INITIALIZATION_FLOW_AUDIT_INVALID: component="+comp+" inlet_error="+ei+" mass_balance="+mb);}
    private static String solverForStudy(Model m,String study){String[]s=m.study(study).getSolverSequences("SolverSequence");if(s.length<1)throw new IllegalStateException("NO_SOLVER_SEQUENCE: "+study);int[]z=m.sol(s[0]).getSize();System.out.println("M10A1_SOLVER|study="+study+"|solver="+s[0]+"|dof="+(z.length>0?z[0]:-1));return s[0];}

    private static double[] metrics(Model m,String fluid,String c,String mesh,String spf,String data,String sol,String dom,String inlet,String outlet,String rho,String mu,String diam,String target,double time){boolean n2="n2".equals(fluid);String ux=n2?"u2":"u",uy=n2?"v2":"v",uz=n2?"w2":"w",pv=n2?"p2":"p",flux=ux+"*nx+"+uy+"*ny+"+uz+"*nz",speed="sqrt("+ux+"^2+"+uy+"^2+"+uz+"^2)";double qin=Math.abs(eval(m,"IntSurface",data,inlet,flux,"m^3/s"));double qout=Math.abs(eval(m,"IntSurface",data,outlet,flux,"m^3/s"));double pin=eval(m,"AvSurface",data,inlet,pv,"Pa"),pout=eval(m,"AvSurface",data,outlet,pv,"Pa");double umean=eval(m,"AvVolume",data,dom,speed,"m/s"),umax=eval(m,"MaxVolume",data,dom,speed,"m/s");double mb=Math.abs(qin-qout)/Math.max(Math.max(qin,qout),1e-30);double re=m.param().evaluate(rho,"kg/m^3")*umean*m.param().evaluate(diam,"m")/m.param().evaluate(mu,"Pa*s");double vol=volume(m,c,dom);int elem=m.component(c).mesh(mesh).getNumElem();double minq=m.component(c).mesh(mesh).getMinQuality();int[]sz=m.sol(sol).getSize();double dof=sz.length>0?sz[0]:-1;return new double[]{qin,qout,mb,pin-pout,umean,umax,re,vol,elem,minq,time,pin,pout,dof};}
    private static double eval(Model m,String type,String data,String sel,String expr,String unit){String tag="ev_m10a1_"+(++evalSerial);m.result().numerical().create(tag,type);m.result().numerical(tag).set("data",data);m.result().numerical(tag).selection().named(sel);m.result().numerical(tag).set("expr",new String[]{expr});m.result().numerical(tag).set("unit",new String[]{unit});double[][]v=m.result().numerical(tag).getReal();m.result().numerical().remove(tag);if(v==null||v.length<1||v[0].length<1)return Double.NaN;return v[0][0];}
    private static double volume(Model m,String c,String dom){int[]d=m.component(c).selection(dom).entities(3);m.component(c).measure().selection().geom(3);m.component(c).measure().selection().set(d);return m.component(c).measure().getVolume();}
    private static void validateMetrics(String label,double[]a,double target){for(double x:a)if(!Double.isFinite(x))throw new IllegalStateException("NONFINITE_FLOW_METRIC: "+label);if(a[2]>0.01)throw new IllegalStateException("MASS_BALANCE_EXCEEDS_1_PERCENT: "+label+" value="+a[2]);if(a[4]<=0||a[5]<=0||a[3]<=0)throw new IllegalStateException("NONPHYSICAL_FLOW_RESULT: "+label+" dp="+a[3]+" umean="+a[4]+" umax="+a[5]);if(Math.abs(a[0]-target)/target>0.02)throw new IllegalStateException("INLET_FLOW_MISMATCH: "+label+" actual="+a[0]+" target="+target);}
    private static void validateRefinedMesh(String fluid,double[]coarse,double[]refined){if(refined[8]<=coarse[8])throw new IllegalStateException("MEDIUM_MESH_NOT_REFINED: fluid="+fluid+" coarse_elements="+(long)coarse[8]+" medium_elements="+(long)refined[8]);System.out.println("M10A1_MEDIUM_MESH_REFINED|fluid="+fluid+"|coarse_elements="+(long)coarse[8]+"|medium_elements="+(long)refined[8]);}
    private static double relative(double a,double b){return Math.abs(a-b)/Math.max(Math.max(Math.abs(a),Math.abs(b)),1e-30);}

    private static void emitCsv(String level,double[]a,String fluid){System.out.println("M10A1_FLOW_CSV|mesh_level,fluid,Q_in_m3_s,Q_out_m3_s,mass_balance_relative,delta_p_Pa,u_mean_m_s,u_max_m_s,Re_estimate,fluid_volume_mm3,mesh_elements,mesh_min_quality,solver_time_s,dof");System.out.println("M10A1_FLOW_CSV|"+level+","+fluid+","+f(a[0])+","+f(a[1])+","+f(a[2])+","+f(a[3])+","+f(a[4])+","+f(a[5])+","+f(a[6])+","+f(a[7])+","+(long)a[8]+","+f(a[9])+","+f(a[10])+","+(long)a[13]);}
    private static void emitGeometryCsv(double[]l,double[]n){System.out.println("M10A1_GEOMETRY_CSV|fluid,volume_mm3,inlet_area_mm2,outlet_area_mm2,connected_components,source_geometry");System.out.println("M10A1_GEOMETRY_CSV|liquid,"+f(l[7])+",31.5391404487,31.5391401711,1,exact_chamber_STEP_boolean_difference");System.out.println("M10A1_GEOMETRY_CSV|n2,"+f(n[7])+",31.5391329804,31.5391329804,1,exact_collector_STEP_channel_domain_3");}
    private static void emitMeshCsv(String level,double[]a,String fluid){System.out.println("M10A1_MESH_CSV|mesh_level,fluid,elements,minimum_quality,dof");System.out.println("M10A1_MESH_CSV|"+level+","+fluid+","+(long)a[8]+","+f(a[9])+","+(long)a[13]);}

    private static void createResults(Model m,String liqData,String n2Data){createFluidResults(m,"liq","Liquid",liqData,"spf_liq","sel_dom_electrolyte_fluid","sel_bnd_electrolyte_inlet","sel_bnd_electrolyte_walls");createFluidResults(m,"n2","N2",n2Data,"spf_n2","sel_dom_n2_channel","sel_bnd_n2_inlet","sel_bnd_n2_walls");System.out.println("M10A1_RESULT_NODES_PASS");}
    private static void createFluidResults(Model m,String stem,String label,String data,String spf,String dom,String inlet,String walls){boolean n2="n2".equals(stem);String ux=n2?"u2":"u",uy=n2?"v2":"v",uz=n2?"w2":"w",pv=n2?"p2":"p",speed="sqrt("+ux+"^2+"+uy+"^2+"+uz+"^2)";String v="pg_"+stem+"_velocity",p="pg_"+stem+"_pressure",s="pg_"+stem+"_streamlines",w="pg_"+stem+"_wall_shear";m.result().create(v,"PlotGroup3D");m.result(v).label(label+" Velocity Magnitude");m.result(v).set("data",data);m.result(v).create("surf","Surface");m.result(v).feature("surf").set("expr",speed);m.result(v).create("slice","Slice");m.result(v).feature("slice").set("expr",speed);m.result().create(p,"PlotGroup3D");m.result(p).label(label+" Pressure");m.result(p).set("data",data);m.result(p).create("surf","Surface");m.result(p).feature("surf").set("expr",pv);m.result(p).create("slice","Slice");m.result(p).feature("slice").set("expr",pv);m.result().create(s,"PlotGroup3D");m.result(s).label(label+" Streamlines");m.result(s).set("data",data);m.result(s).create("str","Streamline");m.result(s).feature("str").selection().named(inlet);m.result(s).feature("str").set("expr",new String[]{ux,uy,uz});m.result().create(w,"PlotGroup3D");m.result(w).label(label+" Wall Shear Stress");m.result(w).set("data",data);m.result(w).create("surf","Surface");m.result(w).feature("surf").create("sel","Selection");m.result(w).feature("surf").feature("sel").selection().named(walls);m.result(w).feature("surf").set("expr","tauw_"+stem+"_test");String dp="eval_"+stem+"_pressure_drop";m.result().numerical().create(dp,"AvSurface");m.result().numerical(dp).label(label+" Pressure-Drop Evaluation (inlet pressure)");m.result().numerical(dp).set("data",data);m.result().numerical(dp).selection().named(inlet);m.result().numerical(dp).set("expr",new String[]{pv});m.result().numerical(dp).set("unit",new String[]{"Pa"});}
    private static void exportFigures(Model m,String dir){String sep=dir.endsWith("\\")?"":"\\";String[][]x={{"img_liq_velocity","pg_liq_velocity","01_liquid_velocity.png"},{"img_liq_pressure","pg_liq_pressure","02_liquid_pressure.png"},{"img_liq_stream","pg_liq_streamlines","03_liquid_streamlines.png"},{"img_n2_velocity","pg_n2_velocity","04_n2_velocity.png"},{"img_n2_pressure","pg_n2_pressure","05_n2_pressure.png"},{"img_n2_stream","pg_n2_streamlines","06_n2_streamlines.png"}};try{for(String[]a:x){m.result().export().create(a[0],"Image3D");m.result().export(a[0]).set("sourceobject",a[1]);m.result().export(a[0]).set("target","file");m.result().export(a[0]).set("filename",dir+sep+a[2]);m.result().export(a[0]).set("width",1200);m.result().export(a[0]).set("height",900);m.result().export(a[0]).run();}System.out.println("M10A1_IMAGE_EXPORT_PASS");}catch(Throwable e){System.out.println("IMAGE_EXPORT_UNAVAILABLE|class="+e.getClass().getName()+"|message="+clean(e.getMessage()));}}

    private static String clean(String s){return s==null?"":s.replace('\n',' ').replace('\r',' ').replace('|','/');}
    private static String f(double x){return String.format(Locale.ROOT,"%.12g",x);}
    private static void api(String a,String t){currentApi=a;currentFeature=t;System.out.println("M10A1_API_BEGIN|api="+a+"|feature="+t);}
    private static String rt(String n){try{return((String)Class.forName(RUNTIME_CLASS).getField(n).get(null)).trim();}catch(Exception e){throw new IllegalStateException("RUNTIME_INPUT_MISSING: "+n,e);}}
    public static void main(String[]a)throws Exception{run();}
}
