import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * M02.2 conservative transport operator verification.
 *
 * This is a synthetic numerical verification only. It contains no electrode
 * kinetics and never clips a concentration. The conservative sign convention is
 *
 *   div(N_i) = R_i,    N_i = u*c_i - D_i*grad(c_i).
 *
 * COMSOL General Inward Flux is positive into the liquid. COMSOL ntflux is the
 * outward-normal physical total flux. Reported inlet rates are positive inward,
 * outlet rates positive outward, cathode N2 consumption positive, and cathode
 * NH3 generation positive.
 */
public final class LiNRR_M02_2_TransportVerification {
    private static final String PROVISIONAL =
        "PROVISIONAL - numerical verification only; not experimental validation";
    private static final int[][] OPERATOR_MESHES = {{40,20},{80,40},{160,80}};
    private static final int[][] WALL_MESHES = {{80,40},{160,80},{320,160}};
    private static final String[] MESH_NAMES = {"coarse","medium","fine"};
    private static final double[] PE_VALUES = {0.1,1.0,10.0,100.0,1000.0};
    private static final double[] DA_VALUES = {1e-4,1e-3,1e-2,1e-1,1.0};
    private static final double CONS_TOL = 1e-4;
    // Dirichlet-boundary physical flux is reconstructed from the FE gradient,
    // rather than a conservative boundary reaction. Keep this MMS truncation
    // diagnostic separate from the wall-model conservation tolerance.
    private static final double MMS_RECONSTRUCTED_FLUX_TOL = 2e-4;
    private static final double C_IN = 5.0;

    private LiNRR_M02_2_TransportVerification() {}

    public static void main(String[] args) throws Exception {
        String rootText = requireRunInput("LINRR_PROJECT_ROOT");
        String sourceText = requireRunInput("LINRR_M02_1_SOURCE");
        Path root = Paths.get(rootText).toAbsolutePath().normalize();
        Path source = Paths.get(sourceText).toAbsolutePath().normalize();
        if (!source.toFile().isFile()) {
            throw new IllegalStateException("Isolated frozen M02.1 source missing: " + source);
        }

        String resumeAfter=optionalRunInput("M02_2_RESUME_AFTER");
        Model operators;
        if("A".equals(resumeAfter)||"B".equals(resumeAfter)||"C".equals(resumeAfter)) {
            operators=resumeOperatorBenchmarks(root,resumeAfter);
        } else if("D".equals(resumeAfter)||"E".equals(resumeAfter)) {
            operators=ModelUtil.load("M022Mms",
                path(root,"models/generated/LiNRR_M02_2_operator_benchmarks.mph"));
        } else {
            System.out.println("M022_STAGE|A_UNIFORM|START");
            operators=buildOperatorBenchmarks(root);
        }
        operators.save(path(root,"models/generated/LiNRR_M02_2_operator_benchmarks.mph"));
        System.out.println("M022_STAGE|A_B_C_OPERATORS|SAVED");

        Model wall;
        if("D".equals(resumeAfter)||"E".equals(resumeAfter)) {
            wall=ModelUtil.load("M022Wall",
                path(root,"models/generated/LiNRR_M02_2_wall_reaction_audit.mph"));
        } else {
            System.out.println("M022_STAGE|D_LOW_DA|START");
            wall = ModelUtil.load("M022Wall", source.toString());
            wall.label("LiNRR M02.2 wall reaction audit | frozen M02.1 derivative | " + PROVISIONAL);
            verifyAndConfigureFrozenWallModel(wall);
            runLowDaAudit(wall, root);
            System.out.println("M02_2_PROGRESS|D_LOW_DA|COMPLETE");
            wall.save(path(root,"models/generated/LiNRR_M02_2_wall_reaction_audit.mph"));
        }
        if(!"E".equals(resumeAfter)) {
            System.out.println("M022_STAGE|E_PE_DA|START");
            runPeDaMap(wall);
            System.out.println("M02_2_PROGRESS|E_PE_DA|COMPLETE");
            wall.save(path(root,"models/generated/LiNRR_M02_2_wall_reaction_audit.mph"));
        }
        restoreLowDaFineAndExport(wall, root);
        wall.save(path(root,"models/generated/LiNRR_M02_2_wall_reaction_audit.mph"));
        wall.save(path(root,"models/generated/LiNRR_M02_2_transport_verification.mph"));
        System.out.println("M022_STAGE|D_E_WALL_MODEL|SAVED");
        System.out.println("M022_META|RUN_STATE|SYNTHETIC_SMOKE_TEST");
        System.out.println("M022_META|CALIBRATION_MODE|PROVISIONAL");
        System.out.println("M022_META|M03B_READY|FALSE");
    }

    private static Model buildOperatorBenchmarks(Path root) throws Exception {
        Model uniform = newOperatorModel("M022Uniform","uniform benchmark A");
        buildUniformComponent(uniform);
        runUniform(uniform, root);
        System.out.println("M022_STAGE|A_UNIFORM|PASS");
        System.out.println("M02_2_PROGRESS|A_UNIFORM|COMPLETE");
        uniform.save(path(root,"models/generated/LiNRR_M02_2_uniform_operator.mph"));

        Model diffusion = newOperatorModel("M022Diffusion","linear diffusion benchmark B");
        buildDiffusionComponent(diffusion);
        runDiffusion(diffusion, root);
        System.out.println("M022_STAGE|B_DIFFUSION|PASS");
        System.out.println("M02_2_PROGRESS|B_DIFFUSION|COMPLETE");
        diffusion.save(path(root,"models/generated/LiNRR_M02_2_diffusion_operator.mph"));

        Model mms = newOperatorModel("M022Mms","manufactured-solution benchmark C");
        buildMmsComponent(mms);
        runMms(mms);
        System.out.println("M022_STAGE|C_MMS|PASS");
        System.out.println("M02_2_PROGRESS|C_MMS|COMPLETE");
        return mms;
    }

    private static Model resumeOperatorBenchmarks(Path root,String resumeAfter) throws Exception {
        if("A".equals(resumeAfter)) {
            Model diffusion=newOperatorModel("M022Diffusion","linear diffusion benchmark B");
            buildDiffusionComponent(diffusion);
            runDiffusion(diffusion,root);
            System.out.println("M022_STAGE|B_DIFFUSION|PASS");
            System.out.println("M02_2_PROGRESS|B_DIFFUSION|COMPLETE");
            diffusion.save(path(root,"models/generated/LiNRR_M02_2_diffusion_operator.mph"));
            resumeAfter="B";
        }
        if("B".equals(resumeAfter)) {
            Model mms=newOperatorModel("M022Mms","manufactured-solution benchmark C");
            buildMmsComponent(mms);
            runMms(mms);
            System.out.println("M022_STAGE|C_MMS|PASS");
            System.out.println("M02_2_PROGRESS|C_MMS|COMPLETE");
            return mms;
        }
        return ModelUtil.load("M022Mms",
            path(root,"models/generated/LiNRR_M02_2_operator_benchmarks.mph"));
    }

    private static Model newOperatorModel(String tag,String benchmark) {
        Model model=ModelUtil.create(tag);
        model.label("LiNRR M02.2 "+benchmark+" | "+PROVISIONAL);
        defineOperatorParameters(model);
        return model;
    }

    private static void defineOperatorParameters(Model model) {
        model.param().set("Lcell","55[mm]","PROVISIONAL benchmark length");
        model.param().set("Hcell","4[mm]","PROVISIONAL benchmark height");
        model.param().set("Wcell","55[mm]","PROVISIONAL out-of-plane width");
        model.param().set("sel_tol","1e-6[mm]","Coordinate selection tolerance");
        model.param().set("cN2_in","5[mol/m^3]",PROVISIONAL);
        model.param().set("U0","1e-4[m/s]","Prescribed divergence-free benchmark velocity");
        model.param().set("D_u","2e-9[m^2/s]",PROVISIONAL);
        model.param().set("c_left","1[mol/m^3]","Diffusion benchmark left concentration");
        model.param().set("c_right","2[mol/m^3]","Diffusion benchmark right concentration");
        model.param().set("D_diff","2e-9[m^2/s]",PROVISIONAL);
        model.param().set("c_ref","5[mol/m^3]","Strictly positive MMS reference");
        model.param().set("a_mms","0.1","MMS sinusoidal amplitude");
        model.param().set("U_mms","2e-6[m/s]","MMS prescribed velocity");
        model.param().set("D_mms","2e-9[m^2/s]","MMS diffusion coefficient");
    }

    private static void buildUniformComponent(Model model) {
        buildRectangleAndSelections(model,"comp_u","geom_u");
        model.component("comp_u").physics().create("tds_u","DilutedSpecies","geom_u",
            new String[]{"cN2u","cNH3u"});
        model.component("comp_u").physics("tds_u").selection().named("sel_u_domain");
        model.component("comp_u").physics("tds_u").feature("cdm1")
            .set("D_cN2u_mat","userdef");
        model.component("comp_u").physics("tds_u").feature("cdm1").set("D_cN2u","D_u");
        model.component("comp_u").physics("tds_u").feature("cdm1")
            .set("D_cNH3u_mat","userdef");
        model.component("comp_u").physics("tds_u").feature("cdm1").set("D_cNH3u","D_u");
        model.component("comp_u").physics("tds_u").feature("cdm1")
            .set("u",new String[][]{{"U0"},{"0[m/s]"},{"0[m/s]"}});
        model.component("comp_u").physics("tds_u").feature("init1")
            .set("initc",new String[]{"cN2_in","0[mol/m^3]"});
        model.component("comp_u").physics("tds_u").create("inflow","Inflow",1);
        model.component("comp_u").physics("tds_u").feature("inflow")
            .selection().named("sel_u_inlet");
        model.component("comp_u").physics("tds_u").feature("inflow")
            .set("BoundaryConditionType","FluxDanckwerts");
        model.component("comp_u").physics("tds_u").feature("inflow")
            .set("c0",new String[]{"cN2_in","0[mol/m^3]"});
        namedBoundaryFeature(model,"comp_u","tds_u","outflow","Outflow","sel_u_outlet");
        namedBoundaryFeature(model,"comp_u","tds_u","noflux_top","NoFlux","sel_u_top");
        namedBoundaryFeature(model,"comp_u","tds_u","noflux_bottom","NoFlux","sel_u_bottom");
        conservativeQuadratic(model,"comp_u","tds_u");
        buildMappedMesh(model,"comp_u","geom_u","mesh_u",80,40);
        createStudy(model,"std_u","stat","tds_u",null);
    }

    private static void runUniform(Model model, Path root) throws Exception {
        String[] tags = runNewStudy(model,"std_u");
        String dset=tags[0];
        avg(model,"u_avg_n2",dset,"comp_u","cN2u","sel_u_domain","mol/m^3");
        avg(model,"u_avg_nh3",dset,"comp_u","cNH3u","sel_u_domain","mol/m^3");
        ext(model,"u_min_n2","MinSurface",dset,"comp_u","cN2u","sel_u_domain");
        ext(model,"u_max_n2","MaxSurface",dset,"comp_u","cN2u","sel_u_domain");
        ext(model,"u_min_nh3","MinSurface",dset,"comp_u","cNH3u","sel_u_domain");
        ext(model,"u_max_nh3","MaxSurface",dset,"comp_u","cNH3u","sel_u_domain");
        ext(model,"u_dev_n2","MaxSurface",dset,"comp_u","abs(cN2u-cN2_in)","sel_u_domain");
        ext(model,"u_dev_nh3","MaxSurface",dset,"comp_u","abs(cNH3u)","sel_u_domain");
        // Use the defining physical total flux explicitly. Custom physics tags
        // do not expose the same generated ntflux names as frozen tag "tds".
        String n2Flux="nx*(U0*cN2u-D_u*cN2ux)*Wcell-ny*D_u*cN2uy*Wcell";
        String nh3Flux="nx*(U0*cNH3u-D_u*cNH3ux)*Wcell-ny*D_u*cNH3uy*Wcell";
        integralLine(model,"u_n2_in",dset,"comp_u",n2Flux,"sel_u_inlet");
        integralLine(model,"u_n2_out",dset,"comp_u",n2Flux,"sel_u_outlet");
        integralLine(model,"u_nh3_in",dset,"comp_u",nh3Flux,"sel_u_inlet");
        integralLine(model,"u_nh3_out",dset,"comp_u",nh3Flux,"sel_u_outlet");
        double minN2=val(model,"u_min_n2"), maxN2=val(model,"u_max_n2");
        double minNh3=val(model,"u_min_nh3"), maxNh3=val(model,"u_max_nh3");
        double meanN2=val(model,"u_avg_n2"), meanNh3=val(model,"u_avg_nh3");
        double n2In=-val(model,"u_n2_in"), n2Out=val(model,"u_n2_out");
        double nh3In=-val(model,"u_nh3_in"), nh3Out=val(model,"u_nh3_out");
        double scale=model.param().evaluate("cN2_in*U0*Hcell*Wcell","mol/s");
        double n2Bal=Math.abs(n2In-n2Out)/Math.max(Math.abs(scale),1e-300);
        double nh3Bal=Math.abs(nh3In-nh3Out)/Math.max(Math.abs(scale),1e-300);
        double devN2=val(model,"u_dev_n2")/C_IN;
        double devNh3=val(model,"u_dev_nh3")/C_IN;
        boolean machine=devN2<=1e-13 && devNh3<=1e-13 && n2Bal<=1e-12 && nh3Bal<=1e-12;
        String status=machine?"MACHINE_PRECISION_CLOSURE":"PASS";
        if(devN2>1e-8||devNh3>1e-10||n2Bal>1e-6||nh3Bal>1e-6||
            minN2<-1e-12*C_IN||minNh3<-1e-12*C_IN)
            throw new IllegalStateException("Uniform transport acceptance failed.");
        System.out.println("M022_UNIFORM|case|min_cN2_mol_m3|max_cN2_mol_m3|mean_cN2_mol_m3|"+
            "min_cNH3_mol_m3|max_cNH3_mol_m3|mean_cNH3_mol_m3|N2_in_mol_s|N2_out_mol_s|"+
            "NH3_in_mol_s|NH3_out_mol_s|N2_balance_error|NH3_balance_error|"+
            "max_uniform_N2_relative_deviation|max_uniform_NH3_relative_deviation|min_concentration|status");
        System.out.println("M022_UNIFORM|uniform_80x40|"+join(minN2,maxN2,meanN2,minNh3,maxNh3,
            meanNh3,n2In,n2Out,nh3In,nh3Out,n2Bal,nh3Bal,devN2,devNh3,
            Math.min(minN2,minNh3))+"|"+status);
        model.result().create("pg_uniform","PlotGroup2D");
        model.result("pg_uniform").label("M02.2 uniform concentration - numerical verification");
        model.result("pg_uniform").set("data",dset);
        model.result("pg_uniform").create("surf","Surface");
        model.result("pg_uniform").feature("surf").set("expr","cN2u");
        model.result("pg_uniform").feature("surf").set("unit","mol/m^3");
        exportImage(model,"img_uniform","Image2D","pg_uniform",
            path(root,"results/figures/M02_2_uniform_concentration.png"));
    }

    private static void buildDiffusionComponent(Model model) {
        buildRectangleAndSelections(model,"comp_d","geom_d");
        model.component("comp_d").variable().create("var_exact");
        model.component("comp_d").variable("var_exact").selection().named("sel_d_domain");
        model.component("comp_d").variable("var_exact").set("c_diff_exact",
            "c_left+(c_right-c_left)*x/Lcell","Linear analytical solution");
        model.component("comp_d").physics().create("tds_d","DilutedSpecies","geom_d",
            new String[]{"cDiff"});
        model.component("comp_d").physics("tds_d").selection().named("sel_d_domain");
        model.component("comp_d").physics("tds_d").feature("cdm1")
            .set("D_cDiff_mat","userdef");
        model.component("comp_d").physics("tds_d").feature("cdm1").set("D_cDiff","D_diff");
        model.component("comp_d").physics("tds_d").feature("cdm1")
            .set("u",new String[][]{{"0[m/s]"},{"0[m/s]"},{"0[m/s]"}});
        concentration(model,"comp_d","tds_d","left_c","sel_d_inlet","c_left");
        concentration(model,"comp_d","tds_d","right_c","sel_d_outlet","c_right");
        namedBoundaryFeature(model,"comp_d","tds_d","noflux_top","NoFlux","sel_d_top");
        namedBoundaryFeature(model,"comp_d","tds_d","noflux_bottom","NoFlux","sel_d_bottom");
        conservativeQuadratic(model,"comp_d","tds_d");
        buildMappedMesh(model,"comp_d","geom_d","mesh_d",40,20);
        createStudy(model,"std_d","stat","tds_d");
    }

    private static void runDiffusion(Model model, Path root) throws Exception {
        String[] tags=runNewStudy(model,"std_d"); String dset=tags[0]; String sol=tags[1];
        integralSurface(model,"d_err2",dset,"comp_d","(cDiff-c_diff_exact)^2","sel_d_domain");
        integralSurface(model,"d_exact2",dset,"comp_d","c_diff_exact^2","sel_d_domain");
        ext(model,"d_linf","MaxSurface",dset,"comp_d","abs(cDiff-c_diff_exact)","sel_d_domain");
        ext(model,"d_min","MinSurface",dset,"comp_d","cDiff","sel_d_domain");
        ext(model,"d_max","MaxSurface",dset,"comp_d","cDiff","sel_d_domain");
        avg(model,"d_slope",dset,"comp_d","cDiffx","sel_d_domain","mol/m^4");
        String diffusionFlux="nx*(-D_diff*cDiffx)*Wcell+ny*(-D_diff*cDiffy)*Wcell";
        integralLine(model,"d_left_flux",dset,"comp_d",diffusionFlux,"sel_d_inlet");
        integralLine(model,"d_right_flux",dset,"comp_d",diffusionFlux,"sel_d_outlet");
        System.out.println("M022_DIFFUSION|mesh|n_length|n_height|cells|dof|L2_relative_error|"+
            "Linf_relative_error|left_outward_mol_s|right_outward_mol_s|flux_balance_error|"+
            "numerical_slope_mol_m4|analytic_slope_mol_m4|min_c_mol_m3|max_c_mol_m3|status");
        for(int i=0;i<OPERATOR_MESHES.length;i++){
            if(i>0){configureMesh(model,"comp_d","mesh_d",OPERATOR_MESHES[i][0],OPERATOR_MESHES[i][1]);model.study("std_d").run();}
            double l2=Math.sqrt(Math.abs(val(model,"d_err2"))/Math.max(Math.abs(val(model,"d_exact2")),1e-300));
            double linf=val(model,"d_linf")/2.0;
            double left=val(model,"d_left_flux"),right=val(model,"d_right_flux");
            double bal=Math.abs(left+right)/Math.max(Math.max(Math.abs(left),Math.abs(right)),1e-300);
            double slope=val(model,"d_slope");
            double analytic=model.param().evaluate("(c_right-c_left)/Lcell","mol/m^4");
            double min=val(model,"d_min"),max=val(model,"d_max");
            long dof=model.sol(sol).getU().length;
            String status=(l2<1e-12&&linf<1e-12)?"EXACT_POLYNOMIAL_REPRESENTATION":"PASS";
            System.out.println("M022_DIFFUSION|"+MESH_NAMES[i]+"|"+OPERATOR_MESHES[i][0]+"|"+
                OPERATOR_MESHES[i][1]+"|"+(OPERATOR_MESHES[i][0]*OPERATOR_MESHES[i][1])+"|"+dof+"|"+
                join(l2,linf,left,right,bal,slope,analytic,min,max)+"|"+status);
            if(i==2&&(l2>1e-4||linf>1e-3||bal>1e-6||min<1.0-1e-8))
                throw new IllegalStateException("Linear diffusion acceptance failed.");
        }
        double lmm=model.param().evaluate("Lcell","mm"),hmm=model.param().evaluate("Hcell","mm");
        model.result().dataset().create("cln_diff","CutLine2D");
        model.result().dataset("cln_diff").set("data",dset);
        model.result().dataset("cln_diff").set("genpoints",new double[][]{{0,0.5*hmm},{lmm,0.5*hmm}});
        model.result().create("pg_diff","PlotGroup1D");
        model.result("pg_diff").label("M02.2 linear diffusion - numerical verification");
        model.result("pg_diff").set("data","cln_diff");
        model.result("pg_diff").create("line_num","LineGraph");
        model.result("pg_diff").feature("line_num").set("expr","cDiff");
        model.result("pg_diff").feature("line_num").set("unit","mol/m^3");
        model.result("pg_diff").create("line_exact","LineGraph");
        model.result("pg_diff").feature("line_exact").set("expr","c_diff_exact");
        model.result("pg_diff").feature("line_exact").set("unit","mol/m^3");
        exportImage(model,"img_diff","Image1D","pg_diff",
            path(root,"results/figures/M02_2_linear_diffusion_profile.png"));
    }

    private static void buildMmsComponent(Model model) {
        buildRectangleAndSelections(model,"comp_m","geom_m");
        model.component("comp_m").variable().create("var_mms");
        model.component("comp_m").variable("var_mms").selection().named("sel_m_domain");
        model.component("comp_m").variable("var_mms").set("c_mms_exact",
            "c_ref*(1+a_mms*sin(pi*x/Lcell)*sin(pi*y/Hcell))",
            "Strictly positive manufactured solution");
        // div(-D grad(c_exact)+u c_exact) = -D laplacian(c_exact)+U dc_exact/dx.
        model.component("comp_m").variable("var_mms").set("R_mms_exact",
            "D_mms*c_ref*a_mms*((pi/Lcell)^2+(pi/Hcell)^2)*" +
            "sin(pi*x/Lcell)*sin(pi*y/Hcell)+" +
            "U_mms*c_ref*a_mms*(pi/Lcell)*cos(pi*x/Lcell)*sin(pi*y/Hcell)",
            "Manufactured source for div(N)=R");
        model.component("comp_m").physics().create("tds_m","DilutedSpecies","geom_m",
            new String[]{"cMms"});
        model.component("comp_m").physics("tds_m").selection().named("sel_m_domain");
        model.component("comp_m").physics("tds_m").feature("cdm1")
            .set("D_cMms_mat","userdef");
        model.component("comp_m").physics("tds_m").feature("cdm1").set("D_cMms","D_mms");
        model.component("comp_m").physics("tds_m").feature("cdm1")
            .set("u",new String[][]{{"U_mms"},{"0[m/s]"},{"0[m/s]"}});
        model.component("comp_m").physics("tds_m").create("source_mms","Reactions",2);
        model.component("comp_m").physics("tds_m").feature("source_mms")
            .selection().named("sel_m_domain");
        model.component("comp_m").physics("tds_m").feature("source_mms")
            .set("R_cMms",new String[]{"R_mms_exact"});
        concentration(model,"comp_m","tds_m","exact_boundary","sel_m_all","c_mms_exact");
        conservativeQuadratic(model,"comp_m","tds_m");
        buildMappedMesh(model,"comp_m","geom_m","mesh_m",40,20);
        createStudy(model,"std_m","stat","tds_m");
    }

    private static void runMms(Model model) throws Exception {
        String[] tags=runNewStudy(model,"std_m"); String dset=tags[0]; String sol=tags[1];
        integralSurface(model,"m_err2",dset,"comp_m","(cMms-c_mms_exact)^2","sel_m_domain");
        integralSurface(model,"m_exact2",dset,"comp_m","c_mms_exact^2","sel_m_domain");
        ext(model,"m_linf","MaxSurface",dset,"comp_m","abs(cMms-c_mms_exact)","sel_m_domain");
        ext(model,"m_min","MinSurface",dset,"comp_m","cMms","sel_m_domain");
        integralSurface(model,"m_source",dset,"comp_m","R_mms_exact*Wcell","sel_m_domain");
        integralLine(model,"m_flux",dset,"comp_m",
            "nx*(U_mms*cMms-D_mms*cMmsx)*Wcell-ny*D_mms*cMmsy*Wcell","sel_m_all");
        System.out.println("M022_MMS|mesh|n_length|n_height|cells|dof|L2_absolute_error_mol_m3|"+
            "L2_relative_error|Linf_error_mol_m3|source_mol_s|outward_flux_mol_s|"+
            "source_flux_balance_error|observed_order_from_previous|min_c_mol_m3|status");
        double previous=Double.NaN; double medium=Double.NaN; double fine=Double.NaN;
        for(int i=0;i<OPERATOR_MESHES.length;i++){
            if(i>0){configureMesh(model,"comp_m","mesh_m",OPERATOR_MESHES[i][0],OPERATOR_MESHES[i][1]);model.study("std_m").run();}
            double err2=Math.abs(val(model,"m_err2"));
            double area=model.param().evaluate("Lcell*Hcell","m^2");
            double l2abs=Math.sqrt(err2/area);
            double rel=Math.sqrt(err2/Math.max(Math.abs(val(model,"m_exact2")),1e-300));
            double linf=val(model,"m_linf"),source=val(model,"m_source"),flux=val(model,"m_flux");
            double balance=LiNRR_M02_2_Metrics.closure(source-flux,source,flux);
            double order=Double.isFinite(previous)&&rel>0?Math.log(previous/rel)/Math.log(2.0):Double.NaN;
            double minimum=val(model,"m_min");
            String status=rel<1e-12?"ROUND_OFF_LIMITED":"PASS";
            long dof=model.sol(sol).getU().length;
            System.out.println("M022_MMS|"+MESH_NAMES[i]+"|"+OPERATOR_MESHES[i][0]+"|"+
                OPERATOR_MESHES[i][1]+"|"+(OPERATOR_MESHES[i][0]*OPERATOR_MESHES[i][1])+"|"+dof+"|"+
                join(l2abs,rel,linf,source,flux,balance,order,minimum)+"|"+status);
            previous=rel; if(i==1)medium=rel; if(i==2)fine=rel;
            if(i==2&&(rel>1e-4||linf/C_IN>5e-4||balance>MMS_RECONSTRUCTED_FLUX_TOL))
                throw new IllegalStateException("MMS fine-grid acceptance failed.");
        }
        double orderMF=Math.log(medium/fine)/Math.log(2.0);
        if(fine>=1e-12&&orderMF<1.8)
            throw new IllegalStateException("MMS observed order below 1.8: "+orderMF);
    }

    private static void verifyAndConfigureFrozenWallModel(Model model) {
        requireOne(model,"comp1","sel_electrolyte",2);
        requireOne(model,"comp1","sel_inlet",1);
        requireOne(model,"comp1","sel_outlet",1);
        requireOne(model,"comp1","sel_anode_wall",1);
        requireOne(model,"comp1","sel_cathode_wall",1);
        String conv=model.component("comp1").physics("tds").prop("AdvancedSettings")
            .getString("ConvectiveTerm");
        String inlet=model.component("comp1").physics("tds").feature("inflow_audit")
            .getString("BoundaryConditionType");
        if(!"cons".equals(conv)||!"FluxDanckwerts".equals(inlet)||
            model.component("comp1").physics("tds").feature("conc_in").isActive())
            throw new IllegalStateException("Frozen M02.1 conservative/Danckwerts state mismatch.");
        model.component("comp1").physics("tds").prop("MassConsistentStabilization")
            .set("massStreamlineDiffusion",true);
        model.component("comp1").physics("tds").prop("MassConsistentStabilization")
            .set("massCrosswindDiffusion",true);
        model.component("comp1").physics("tds").prop("ShapeProperty")
            .set("order_concentration",2);
        model.param().set("Pe_H_target","10","M02.2 height-based Peclet target");
        model.param().set("Da_H_target","1e-3","M02.2 height-based Damkohler target");
        model.param().set("Umean_target","Pe_H_target*DN2/Hcell");
        model.param().set("Qliq","Umean_target*Hcell*Wcell");
        model.param().set("kN2","Da_H_target*DN2/Hcell");
        createWallNumerics(model);
    }

    private static void createWallNumerics(Model model) {
        String d="dset1";
        integralLine(model,"m022_n2_in",d,"comp1","tds.ntflux_cN2*Wcell","sel_inlet");
        integralLine(model,"m022_n2_out",d,"comp1","tds.ntflux_cN2*Wcell","sel_outlet");
        integralLine(model,"m022_nh3_in",d,"comp1","tds.ntflux_cNH3*Wcell","sel_inlet");
        integralLine(model,"m022_nh3_out",d,"comp1","tds.ntflux_cNH3*Wcell","sel_outlet");
        integralLine(model,"m022_n2_cons",d,"comp1","rN2*Wcell","sel_cathode_wall");
        integralLine(model,"m022_nh3_gen",d,"comp1","rNH3*Wcell","sel_cathode_wall");
        ext(model,"m022_min_n2","MinSurface",d,"comp1","cN2","sel_electrolyte");
        ext(model,"m022_max_n2","MaxSurface",d,"comp1","cN2","sel_electrolyte");
        ext(model,"m022_min_nh3","MinSurface",d,"comp1","cNH3","sel_electrolyte");
        ext(model,"m022_max_nh3","MaxSurface",d,"comp1","cNH3","sel_electrolyte");
    }

    private static void runLowDaAudit(Model model, Path root) throws Exception {
        System.out.println("M022_LOW_DA|mesh|n_length|n_height|cells|dof|Pe_H|Da_H|Umean_m_s|DN2_m2_s|"+
            "kN2_m_s|N2_in_mol_s|N2_out_mol_s|N2_cathode_consumption_mol_s|NH3_in_mol_s|"+
            "NH3_out_mol_s|NH3_cathode_generation_mol_s|N2_species_balance_error|"+
            "NH3_species_balance_error|nitrogen_atom_balance_error|stoichiometric_ratio|"+
            "stoichiometric_ratio_relative_error|min_cN2|max_cN2|min_cNH3|max_cNH3|"+
            "N2_conversion|NH3_outlet_rate_mol_s|change_N2_conversion|change_NH3_outlet|"+
            "change_N2_consumption|max_key_change|streamline|crosswind|concentration_order|status");
        double[][] rows=new double[3][];
        for(int i=0;i<WALL_MESHES.length;i++){
            configureWallMesh(model,WALL_MESHES[i][0],WALL_MESHES[i][1]);
            model.param().set("Pe_H_target","10"); model.param().set("Da_H_target","1e-3");
            model.study("std_audit").run();
            rows[i]=wallMetrics(model);
            double[] changes=i==0?new double[]{Double.NaN,Double.NaN,Double.NaN,Double.NaN}:
                keyChanges(rows[i],rows[i-1]);
            String status=wallPass(rows[i])?"PASS":"FAIL";
            long dof=model.sol("sol1").getU().length;
            System.out.println("M022_LOW_DA|"+MESH_NAMES[i]+"|"+WALL_MESHES[i][0]+"|"+
                WALL_MESHES[i][1]+"|"+(WALL_MESHES[i][0]*WALL_MESHES[i][1])+"|"+dof+"|"+
                wallMetricsForOutput(rows[i])+"|"+join(changes)+"|true|true|2|"+status);
            System.out.println("M022_MESH|low_da|"+MESH_NAMES[i]+"|"+WALL_MESHES[i][0]+"|"+
                WALL_MESHES[i][1]+"|"+dof+"|"+LiNRR_M02_2_Metrics.fmt(changes[3])+"|"+status);
        }
        double[] fine=rows[2]; double[] change=keyChanges(rows[2],rows[1]);
        if(!wallPass(fine)||change[3]>0.005)
            throw new IllegalStateException("Low-Da wall audit acceptance failed; max change="+change[3]);
    }

    private static void runPeDaMap(Model model) {
        configureWallMesh(model,160,80);
        System.out.println("M022_PE_DA|Pe_H|Da_H|Umean_m_s|DN2_m2_s|kN2_m_s|mesh|streamline|crosswind|"+
            "concentration_order|N2_species_balance_error|NH3_species_balance_error|"+
            "nitrogen_atom_balance_error|min_cN2|min_cNH3|N2_conversion|NH3_outlet_rate_mol_s|"+
            "classification|failure_reason");
        for(double pe:PE_VALUES)for(double da:DA_VALUES){
            model.param().set("Pe_H_target",LiNRR_M02_2_Metrics.fmt(pe));
            model.param().set("Da_H_target",LiNRR_M02_2_Metrics.fmt(da));
            try{
                model.study("std_audit").run();
                double[] m=wallMetrics(model);
                String classification=classifyMap(m);
                String reason=mapReason(classification);
                System.out.println("M022_PE_DA|"+join(pe,da,m[2],m[3],m[4])+"|160x80|true|true|2|"+
                    join(m[12],m[13],m[14],m[16],m[18],m[20],m[21])+"|"+classification+"|"+reason);
            }catch(Exception ex){
                double u=pe*model.param().evaluate("DN2","m^2/s")/
                    model.param().evaluate("Hcell","m");
                double k=da*model.param().evaluate("DN2","m^2/s")/
                    model.param().evaluate("Hcell","m");
                System.out.println("M022_PE_DA|"+join(pe,da,u,model.param().evaluate("DN2","m^2/s"),k)+
                    "|160x80|true|true|2|NaN|NaN|NaN|NaN|NaN|NaN|NaN|"+
                    "OUTSIDE_MODEL_APPLICABILITY|"+LiNRR_M02_2_Metrics.safe(ex));
            }
        }
    }

    private static void restoreLowDaFineAndExport(Model model, Path root) throws Exception {
        configureWallMesh(model,320,160);
        model.param().set("Pe_H_target","10"); model.param().set("Da_H_target","1e-3");
        model.study("std_audit").run();
        model.result().create("m022_pg_low_da","PlotGroup1D");
        model.result("m022_pg_low_da").label("M02.2 low-Da species - numerical verification");
        model.result("m022_pg_low_da").set("data","dset1");
        model.result("m022_pg_low_da").create("n2","LineGraph");
        model.result("m022_pg_low_da").feature("n2").selection().named("sel_cathode_wall");
        model.result("m022_pg_low_da").feature("n2").set("expr","cN2");
        model.result("m022_pg_low_da").feature("n2").set("unit","mol/m^3");
        model.result("m022_pg_low_da").create("nh3","LineGraph");
        model.result("m022_pg_low_da").feature("nh3").selection().named("sel_cathode_wall");
        model.result("m022_pg_low_da").feature("nh3").set("expr","cNH3");
        model.result("m022_pg_low_da").feature("nh3").set("unit","mol/m^3");
        exportImage(model,"m022_img_low_da","Image1D","m022_pg_low_da",
            path(root,"results/figures/M02_2_low_da_species.png"));
    }

    // wallMetrics indices: Pe,Da,U,D,k,N2in,N2out,N2cons,NH3in,NH3out,NH3gen,
    // stoich ratio,N2err,NH3err,Nerr,stoicherr,minN2,maxN2,minNH3,maxNH3,conversion,NH3out.
    private static double[] wallMetrics(Model model) {
        double[] m=new double[22];
        m[0]=model.param().evaluate("Umean_target*Hcell/DN2");
        m[1]=model.param().evaluate("kN2*Hcell/DN2");
        m[2]=model.param().evaluate("Umean_target","m/s");
        m[3]=model.param().evaluate("DN2","m^2/s");
        m[4]=model.param().evaluate("kN2","m/s");
        m[5]=-val(model,"m022_n2_in"); m[6]=val(model,"m022_n2_out");
        m[7]=val(model,"m022_n2_cons"); m[8]=-val(model,"m022_nh3_in");
        m[9]=val(model,"m022_nh3_out"); m[10]=val(model,"m022_nh3_gen");
        m[11]=m[10]/Math.max(2*m[7],1e-300);
        m[12]=Math.abs(m[5]-m[6]-m[7])/Math.max(Math.abs(m[5]),1e-300);
        m[13]=Math.abs(m[10]+m[8]-m[9])/
            Math.max(Math.max(Math.abs(m[10]),Math.abs(m[9])),1e-300);
        m[14]=Math.abs(2*(m[5]-m[6])-(m[9]-m[8]))/Math.max(Math.abs(2*m[5]),1e-300);
        m[15]=Math.abs(m[11]-1.0);
        m[16]=val(model,"m022_min_n2");m[17]=val(model,"m022_max_n2");
        m[18]=val(model,"m022_min_nh3");m[19]=val(model,"m022_max_nh3");
        m[20]=(m[5]-m[6])/Math.max(Math.abs(m[5]),1e-300);m[21]=m[9];
        return m;
    }

    private static boolean wallPass(double[] m) {
        return LiNRR_M02_2_Metrics.finite(m)&&m[12]<=CONS_TOL&&m[13]<=CONS_TOL&&
            m[14]<=CONS_TOL&&m[15]<=1e-4&&m[16]>=-1e-8*C_IN&&m[18]>=-1e-8*C_IN;
    }

    private static String wallMetricsForOutput(double[] m) {
        return join(m[0],m[1],m[2],m[3],m[4],m[5],m[6],m[7],m[8],m[9],m[10],
            m[12],m[13],m[14],m[11],m[15],m[16],m[17],m[18],m[19],m[20],m[21]);
    }

    private static double[] keyChanges(double[] current,double[] previous) {
        double a=LiNRR_M02_2_Metrics.relative(current[20],previous[20]);
        double b=LiNRR_M02_2_Metrics.relative(current[21],previous[21]);
        double c=LiNRR_M02_2_Metrics.relative(current[7],previous[7]);
        return new double[]{a,b,c,Math.max(a,Math.max(b,c))};
    }

    private static String classifyMap(double[] m) {
        if(!LiNRR_M02_2_Metrics.finite(m))return "OUTSIDE_MODEL_APPLICABILITY";
        if(m[12]>CONS_TOL||m[13]>CONS_TOL||m[14]>CONS_TOL)return "FAILED_CONSERVATION";
        double minimum=Math.min(m[16],m[18]);
        if(minimum<-1e-5*C_IN)return "FAILED_NEGATIVE_CONCENTRATION";
        if(minimum<-1e-8*C_IN)return "WARNING_NUMERICAL_OSCILLATION";
        return "PASS";
    }

    private static String mapReason(String classification) {
        if("PASS".equals(classification))return "finite_conservative_and_nonnegative";
        if("WARNING_NUMERICAL_OSCILLATION".equals(classification))return "conservative_small_unclipped_undershoot";
        if("FAILED_NEGATIVE_CONCENTRATION".equals(classification))return "unclipped_negative_concentration_numerical_failure";
        if("FAILED_CONSERVATION".equals(classification))return "one_or_more_species_or_nitrogen_balance_exceeds_1e-4";
        return "nonfinite_or_solver_failure";
    }

    private static void buildRectangleAndSelections(Model model,String comp,String geom) {
        model.component().create(comp,true);model.component(comp).geom().create(geom,2);
        model.component(comp).geom(geom).lengthUnit("mm");
        model.component(comp).geom(geom).create("rect","Rectangle");
        model.component(comp).geom(geom).feature("rect").set("size",new String[]{"Lcell","Hcell"});
        model.component(comp).geom(geom).run();
        String prefix="sel_"+comp.substring(comp.length()-1)+"_";
        box(model,comp,prefix+"domain",2,"-sel_tol","Lcell+sel_tol","-sel_tol","Hcell+sel_tol");
        box(model,comp,prefix+"inlet",1,"-sel_tol","sel_tol","-sel_tol","Hcell+sel_tol");
        box(model,comp,prefix+"outlet",1,"Lcell-sel_tol","Lcell+sel_tol","-sel_tol","Hcell+sel_tol");
        box(model,comp,prefix+"top",1,"-sel_tol","Lcell+sel_tol","Hcell-sel_tol","Hcell+sel_tol");
        box(model,comp,prefix+"bottom",1,"-sel_tol","Lcell+sel_tol","-sel_tol","sel_tol");
        box(model,comp,prefix+"all",1,"-sel_tol","Lcell+sel_tol","-sel_tol","Hcell+sel_tol");
    }

    private static void box(Model model,String comp,String tag,int dim,String xmin,String xmax,String ymin,String ymax){
        model.component(comp).selection().create(tag,"Box");
        model.component(comp).selection(tag).set("entitydim",dim);
        model.component(comp).selection(tag).set("condition","inside");
        model.component(comp).selection(tag).set("xmin",xmin);model.component(comp).selection(tag).set("xmax",xmax);
        model.component(comp).selection(tag).set("ymin",ymin);model.component(comp).selection(tag).set("ymax",ymax);
    }

    private static void buildMappedMesh(Model model,String comp,String geom,String mesh,int nx,int ny){
        model.component(comp).mesh().create(mesh);model.component(comp).mesh(mesh).create("map","Map");
        String prefix="sel_"+comp.substring(comp.length()-1)+"_";
        model.component(comp).mesh(mesh).feature("map").selection().named(prefix+"domain");
        model.component(comp).mesh(mesh).feature("map").create("dx","Distribution");
        model.component(comp).mesh(mesh).feature("map").feature("dx").selection().named(prefix+"bottom");
        model.component(comp).mesh(mesh).feature("map").create("dy","Distribution");
        model.component(comp).mesh(mesh).feature("map").feature("dy").selection().named(prefix+"inlet");
        configureMesh(model,comp,mesh,nx,ny);
    }

    private static void configureMesh(Model model,String comp,String mesh,int nx,int ny){
        model.component(comp).mesh(mesh).feature("map").feature("dx").set("type","number");
        model.component(comp).mesh(mesh).feature("map").feature("dx").set("numelem",nx);
        model.component(comp).mesh(mesh).feature("map").feature("dy").set("type","number");
        model.component(comp).mesh(mesh).feature("map").feature("dy").set("numelem",ny);
        model.component(comp).mesh(mesh).run();
    }

    private static void configureWallMesh(Model model,int nx,int ny){
        com.comsol.model.MeshFeature dx=model.component("comp1").mesh("mesh1")
            .feature("map_channel").feature("dist_length");
        com.comsol.model.MeshFeature dy=model.component("comp1").mesh("mesh1")
            .feature("map_channel").feature("dist_height");
        dx.set("type","predefined");dx.set("elemcount",nx);dx.set("elemratio",1.0);dx.set("reverse",false);
        dy.set("type","predefined");dy.set("elemcount",ny);dy.set("elemratio",1.0);dy.set("reverse",false);
        model.component("comp1").mesh("mesh1").run();
    }

    private static void namedBoundaryFeature(Model model,String comp,String physics,String tag,String type,String sel){
        model.component(comp).physics(physics).create(tag,type,1);
        model.component(comp).physics(physics).feature(tag).selection().named(sel);
    }

    private static void concentration(Model model,String comp,String physics,String tag,String sel,String value){
        namedBoundaryFeature(model,comp,physics,tag,"Concentration",sel);
        model.component(comp).physics(physics).feature(tag).set("species",new int[]{1});
        model.component(comp).physics(physics).feature(tag).set("c0",new String[]{value});
    }

    private static void conservativeQuadratic(Model model,String comp,String physics){
        model.component(comp).physics(physics).prop("AdvancedSettings").set("ConvectiveTerm","cons");
        model.component(comp).physics(physics).prop("MassConsistentStabilization")
            .set("massStreamlineDiffusion",true);
        model.component(comp).physics(physics).prop("MassConsistentStabilization")
            .set("massCrosswindDiffusion",true);
        model.component(comp).physics(physics).prop("ShapeProperty").set("order_concentration",2);
    }

    private static void createStudy(Model model,String study,String stat,String active,String... inactive){
        model.study().create(study);model.study(study).create(stat,"Stationary");
        model.study(study).feature(stat).activate(active,true);
        if(inactive!=null)for(String tag:inactive)model.study(study).feature(stat).activate(tag,false);
    }

    private static String[] runNewStudy(Model model,String study){
        String[] db=model.result().dataset().tags(),sb=model.sol().tags();model.study(study).run();
        return new String[]{newTag(db,model.result().dataset().tags()),newTag(sb,model.sol().tags())};
    }

    private static String newTag(String[] before,String[] after){
        for(String a:after){boolean found=false;for(String b:before)if(a.equals(b)){found=true;break;}if(!found)return a;}
        if(after.length==0)throw new IllegalStateException("COMSOL created no result tag.");return after[after.length-1];
    }

    private static void integralLine(Model model,String tag,String data,String comp,String expr,String sel){
        model.result().numerical().create(tag,"IntLine");model.result().numerical(tag).set("data",data);
        model.result().numerical(tag).selection().geom(
            model.component(comp).geom().tags()[0],1);
        model.result().numerical(tag).selection().set(
            model.component(comp).selection(sel).entities(1));
        model.result().numerical(tag).set("expr",new String[]{expr});
        model.result().numerical(tag).set("unit",new String[]{"mol/s"});
        model.result().numerical(tag).set("intorderactive",true);model.result().numerical(tag).set("intorder",8);
    }

    private static void integralSurface(Model model,String tag,String data,String comp,String expr,String sel){
        model.result().numerical().create(tag,"IntSurface");model.result().numerical(tag).set("data",data);
        model.result().numerical(tag).selection().geom(
            model.component(comp).geom().tags()[0],2);
        model.result().numerical(tag).selection().set(
            model.component(comp).selection(sel).entities(2));
        model.result().numerical(tag).set("expr",new String[]{expr});
        model.result().numerical(tag).set("intorderactive",true);model.result().numerical(tag).set("intorder",8);
    }

    private static void avg(Model model,String tag,String data,String comp,String expr,String sel,String unit){
        model.result().numerical().create(tag,"AvSurface");model.result().numerical(tag).set("data",data);
        model.result().numerical(tag).selection().geom(
            model.component(comp).geom().tags()[0],2);
        model.result().numerical(tag).selection().set(
            model.component(comp).selection(sel).entities(2));
        model.result().numerical(tag).set("expr",new String[]{expr});
        model.result().numerical(tag).set("unit",new String[]{unit});
    }

    private static void ext(Model model,String tag,String type,String data,String comp,String expr,String sel){
        model.result().numerical().create(tag,type);model.result().numerical(tag).set("data",data);
        model.result().numerical(tag).selection().geom(
            model.component(comp).geom().tags()[0],2);
        model.result().numerical(tag).selection().set(
            model.component(comp).selection(sel).entities(2));
        model.result().numerical(tag).set("expr",new String[]{expr});
        model.result().numerical(tag).set("unit",new String[]{"mol/m^3"});
    }

    private static double val(Model model,String tag){
        double[][] values=model.result().numerical(tag).getReal();
        if(values==null||values.length==0||values[0].length==0)
            throw new IllegalStateException("No numerical result for "+tag);
        return values[0][0];
    }

    private static void exportImage(Model model,String tag,String type,String plot,String filename){
        model.result().export().create(tag,type);model.result().export(tag).set("sourceobject",plot);
        model.result().export(tag).set("target","file");model.result().export(tag).set("filename",filename);
        model.result().export(tag).run();
    }

    private static void requireOne(Model model,String comp,String selection,int dim){
        int count=model.component(comp).selection(selection).entities(dim).length;
        if(count!=1)throw new IllegalStateException("FAILED_SELECTION_MAPPING: "+selection+" count="+count);
    }

    private static boolean hasTag(String[] tags,String wanted){
        for(String tag:tags)if(tag.equals(wanted))return true;
        return false;
    }

    private static String join(double... values){
        StringBuilder b=new StringBuilder();for(int i=0;i<values.length;i++){if(i>0)b.append('|');b.append(LiNRR_M02_2_Metrics.fmt(values[i]));}return b.toString();
    }

    private static String path(Path root,String relative){return root.resolve(relative.replace('/',File.separatorChar)).normalize().toString();}
    /**
     * COMSOL Runtime blocks getenv by default. The verification script therefore
     * supplies a transparent run-specific companion class through -classpathadd.
     * The environment fallback keeps direct execution under an ordinary JVM useful.
     */
    private static String requireRunInput(String name) {
        String value=optionalRunInput(name);
        if(value!=null&&!value.trim().isEmpty())return value;
        throw new IllegalStateException(name+" is required.");
    }

    private static String optionalRunInput(String name) {
        try {
            Class<?> inputs=Class.forName("LiNRR_M02_2_RunInputs");
            Object value=inputs.getMethod("get",String.class).invoke(null,name);
            if(value!=null&&!value.toString().trim().isEmpty())return value.toString();
        } catch (Throwable ignored) {
            // Fall through to the ordinary-JVM environment path.
        }
        try {
            String value=System.getenv(name);
            if(value!=null&&!value.trim().isEmpty())return value;
        } catch (SecurityException blocked) {
            throw new IllegalStateException(name+
                " is unavailable; run-specific input bridge was not loaded.",blocked);
        }
        return null;
    }
}
