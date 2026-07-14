import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * M02.1: independent total-flux, boundary-condition, positivity, and
 * stabilization audit of the provisional M02 N2/NH3 transport model.
 *
 * No concentration clipping is used. No electrochemistry is added.
 */
public final class LiNRR_M02_1_TransportAudit {
    private static final String PROVISIONAL =
        "PROVISIONAL \u2014 numerical audit only; not experimentally validated";
    private static final double CONS_TOL = 1e-4;
    private static final double NEG_REL_TOL = 1e-6;
    private static final double C_IN = 5.0;
    private static final double[] Q_SCAN = {0.25, 0.5, 1.0, 2.0, 4.0};
    private static final double[] K_SCAN = {1e-6, 3e-6, 1e-5, 3e-5, 1e-4};
    private static final String MPH =
        "models/generated/LiNRR_M02_1_transport_audit.mph";
    private static final String MPH_ABSOLUTE =
        "F:/LiNRR_COMSOL/LiNRR_COMSOL_Codex_Starter/models/generated/"+
        "LiNRR_M02_1_transport_audit.mph";
    private static final boolean LOAD_ONLY = false;

    private LiNRR_M02_1_TransportAudit() {}

    private enum InletMode { CONCENTRATION, INFLOW_CONSTRAINT, DANCKWERTS }

    private static final class Config {
        final String name;
        final InletMode inlet;
        final boolean streamline;
        final boolean crosswind;
        final int order;
        final int nx;
        final int ny;
        final double xRatio;
        final boolean xReverse;
        final double yRatio;
        final boolean yReverse;
        final boolean continuation;

        Config(String name, InletMode inlet, boolean streamline, boolean crosswind,
               int order, int nx, int ny, double xRatio, boolean xReverse,
               double yRatio, boolean yReverse, boolean continuation) {
            this.name=name; this.inlet=inlet; this.streamline=streamline;
            this.crosswind=crosswind; this.order=order; this.nx=nx; this.ny=ny;
            this.xRatio=xRatio; this.xReverse=xReverse; this.yRatio=yRatio;
            this.yReverse=yReverse; this.continuation=continuation;
        }
    }

    private static final class Metrics {
        String config;
        double q;
        double k;
        long dof;
        double seconds;
        double n2InTotal, n2OutTotal, nh3InTotal, nh3OutTotal;
        double n2InAdv, n2OutAdv, nh3InAdv, nh3OutAdv;
        double n2InDiff, n2OutDiff, nh3InDiff, nh3OutDiff;
        double n2Consumed, nh3Generated;
        double n2CathTotal, nh3CathTotal;
        double n2AnodeTotal, nh3AnodeTotal;
        double n2AllOutTotal, nh3AllOutTotal;
        double n2AllOutExplicit, nh3AllOutExplicit;
        double minN2, minNH3, minCathN2, avgCathN2;
        double conversion, n2Error, nh3Error, nBoundaryError, nOverallError;
        double nh3BackDiffusion;
        double jReaction, jLimiting, reactionToLimit;
        double physicalChange;
        String classification;
        String reason;
    }

    public static Model run() throws Exception {
        Model model = ModelUtil.create("M021");
        model.label("LiNRR M02.1 transport audit | " + PROVISIONAL);
        defineParameters(model);
        buildGeometry(model);
        System.out.println("M021_STAGE|geometry");
        createSelections(model);
        System.out.println("M021_STAGE|selections");
        assignMaterials(model);
        addFlowPhysics(model);
        addSpeciesTransport(model);
        System.out.println("M021_STAGE|physics");
        buildMesh(model);
        System.out.println("M021_STAGE|mesh");
        createStudy(model);
        Config initial = cfg("INITIALIZE_EVALUATIONS",InletMode.CONCENTRATION,
            true,true,1,100,200,1,false,1,false,false);
        configure(model,initial);
        model.param().set("kN2","1e-6[m/s]");
        model.study("std_audit").run();
        createNumericalEvaluations(model);
        System.out.println("M021_STAGE|evaluations");

        printFluxHeader();
        Config a = cfg("A_CONCENTRATION_OUTFLOW", InletMode.CONCENTRATION,
            true,true,1,100,200,1,false,1,false,true);
        Config b = cfg("B_DANCKWERTS_OUTFLOW", InletMode.DANCKWERTS,
            true,true,1,100,200,1,false,1,false,true);
        Config c = cfg("C_INFLOW_CONSTRAINT_OUTFLOW", InletMode.INFLOW_CONSTRAINT,
            true,true,1,100,200,1,false,1,false,true);
        Metrics ma = solve(model,a,1.0,1e-5);
        Metrics mb = solve(model,b,1.0,1e-5);
        Metrics mc = solve(model,c,1.0,1e-5);
        printFlux(ma); printFlux(mb); printFlux(mc);

        printStabilizationHeader();
        List<Config> variants = new ArrayList<Config>();
        variants.add(cfg("CURRENT_A_BOTH_LINEAR",InletMode.CONCENTRATION,true,true,1,
            100,200,1,false,1,false,true));
        variants.add(cfg("B_NONE_LINEAR",InletMode.DANCKWERTS,false,false,1,
            100,200,1,false,1,false,false));
        variants.add(cfg("B_STREAMLINE_LINEAR",InletMode.DANCKWERTS,true,false,1,
            100,200,1,false,1,false,false));
        variants.add(cfg("B_CROSSWIND_LINEAR",InletMode.DANCKWERTS,false,true,1,
            100,200,1,false,1,false,false));
        variants.add(cfg("B_BOTH_LINEAR_CONTINUATION",InletMode.DANCKWERTS,true,true,1,
            100,200,1,false,1,false,true));
        variants.add(cfg("B_BOTH_QUADRATIC",InletMode.DANCKWERTS,true,true,2,
            100,200,1,false,1,false,false));
        // Distribution ratio is last/first element size. yRatio>1 refines the
        // first end of the inlet edge (the coordinate-selected cathode at y=0).
        variants.add(cfg("B_CATHODE_REFINED",InletMode.DANCKWERTS,true,true,1,
            100,240,1,false,20,false,false));
        // Reverse the length-edge distribution to place smaller elements at x=L.
        variants.add(cfg("B_OUTLET_REFINED",InletMode.DANCKWERTS,true,true,1,
            140,200,10,true,1,false,false));
        variants.add(cfg("B_BOTH_LINEAR_DIRECT_K",InletMode.DANCKWERTS,true,true,1,
            100,200,1,false,1,false,false));
        variants.add(cfg("B_NX350_QUADRATIC_SELECTED",InletMode.DANCKWERTS,true,true,2,
            350,200,1,false,1,false,false));

        List<Metrics> stabilization = new ArrayList<Metrics>();
        for (Config v : variants) {
            try {
                Metrics m=solve(model,v,1.0,1e-5);
                m.physicalChange=physicalChange(ma,m);
                classify(m);
                stabilization.add(m);
                printStabilization(m,v);
            } catch (Exception ex) {
                printStabilizationFailure(v,ex);
            }
        }

        Config selected = chooseSelected(variants, stabilization);
        System.out.println("M021_SELECTED|"+selected.name+"|"+
            "minimum-negative eligible conservative configuration; scan uses FluxDanckwerts+Outflow");

        printScanHeader();
        Config scanConfig=cfg(selected.name,selected.inlet,selected.streamline,selected.crosswind,
            selected.order,selected.nx,selected.ny,selected.xRatio,selected.xReverse,
            selected.yRatio,selected.yReverse,false);
        for (double q : Q_SCAN) {
            for (double k : K_SCAN) {
                try {
                    Metrics m=solve(model,scanConfig,q,k);
                    m.physicalChange=physicalChange(ma,m);
                    classify(m);
                    printScan(m);
                } catch (Exception ex) {
                    printScanFailure(scanConfig,q,k,ex);
                }
            }
        }

        // Restore and verify the selected base solution in the editable MPH.
        Metrics finalBase=solve(model,selected,1.0,1e-5);
        finalBase.physicalChange=physicalChange(ma,finalBase);
        classify(finalBase);
        enforceBaseAcceptance(finalBase);
        model.label("LiNRR M02.1 transport audit | selected="+selected.name+" | "+PROVISIONAL);
        model.save(MPH);
        ModelUtil.remove("M021");
        Model loaded=ModelUtil.load("M021Reload",MPH_ABSOLUTE);
        if (!hasTag(loaded.component().tags(),"comp1") ||
            !hasTag(loaded.component("comp1").physics().tags(),"tds") ||
            !hasTag(loaded.study().tags(),"std_audit")) {
            throw new IllegalStateException("Reloaded MPH is missing stable M02.1 tags");
        }
        System.out.println("M021_LOAD|PASS|"+MPH);
        return loaded;
    }

    private static Config cfg(String name, InletMode inlet, boolean sd, boolean cw,
                              int order, int nx, int ny, double xr, boolean xrev,
                              double yr, boolean yrev, boolean continuation) {
        return new Config(name,inlet,sd,cw,order,nx,ny,xr,xrev,yr,yrev,continuation);
    }

    private static void defineParameters(Model model) {
        model.param().set("Lcell","55[mm]","Specified channel length");
        model.param().set("Hcell","4[mm]","Specified liquid layer thickness");
        model.param().set("Wcell","55[mm]","Specified out-of-plane width");
        model.param().set("Qliq","1[cm^3/min]","Base liquid flow");
        model.param().set("rho_el","900[kg/m^3]",PROVISIONAL);
        model.param().set("mu_el","3[mPa*s]",PROVISIONAL);
        model.param().set("DN2","2e-9[m^2/s]",PROVISIONAL);
        model.param().set("DNH3","2e-9[m^2/s]",PROVISIONAL);
        model.param().set("cN2_in","5[mol/m^3]",PROVISIONAL);
        model.param().set("cNH3_in","0[mol/m^3]",PROVISIONAL);
        model.param().set("kN2","1e-5[m/s]",PROVISIONAL+"; phenomenological wall law");
        model.param().set("reaction_on","1","Audit reaction switch");
        model.param().set("uin","Qliq/(Hcell*Wcell)","Mean inlet velocity");
        model.param().set("sel_tol","1e-6[mm]","Coordinate selection tolerance");
        model.param().set("Acath","Lcell*Wcell","Cathode area represented by 2D edge");
    }

    private static void buildGeometry(Model model) {
        model.component().create("comp1",true);
        model.component("comp1").geom().create("geom1",2);
        model.component("comp1").geom("geom1").lengthUnit("mm");
        model.component("comp1").geom("geom1").create("r_channel","Rectangle");
        model.component("comp1").geom("geom1").feature("r_channel")
            .set("size",new String[]{"Lcell","Hcell"});
        model.component("comp1").geom("geom1").run();
    }

    private static void createSelections(Model model) {
        box(model,"sel_electrolyte","Electrolyte domain",2,
            "-sel_tol","Lcell+sel_tol","-sel_tol","Hcell+sel_tol");
        box(model,"sel_inlet","Inlet boundary",1,
            "-sel_tol","sel_tol","-sel_tol","Hcell+sel_tol");
        box(model,"sel_outlet","Outlet boundary",1,
            "Lcell-sel_tol","Lcell+sel_tol","-sel_tol","Hcell+sel_tol");
        box(model,"sel_anode_wall","Upper no-flux wall",1,
            "-sel_tol","Lcell+sel_tol","Hcell-sel_tol","Hcell+sel_tol");
        box(model,"sel_cathode_wall","Lower reactive wall",1,
            "-sel_tol","Lcell+sel_tol","-sel_tol","sel_tol");
        box(model,"sel_all_boundaries","All boundaries for closure",1,
            "-sel_tol","Lcell+sel_tol","-sel_tol","Hcell+sel_tol");
    }

    private static void box(Model model,String tag,String label,int dim,
                            String xmin,String xmax,String ymin,String ymax) {
        model.component("comp1").selection().create(tag,"Box");
        model.component("comp1").selection(tag).label(label);
        model.component("comp1").selection(tag).set("entitydim",dim);
        model.component("comp1").selection(tag).set("condition","inside");
        model.component("comp1").selection(tag).set("xmin",xmin);
        model.component("comp1").selection(tag).set("xmax",xmax);
        model.component("comp1").selection(tag).set("ymin",ymin);
        model.component("comp1").selection(tag).set("ymax",ymax);
    }

    private static void assignMaterials(Model model) {
        model.component("comp1").material().create("mat_electrolyte","Common");
        model.component("comp1").material("mat_electrolyte").selection().named("sel_electrolyte");
        model.component("comp1").material("mat_electrolyte").propertyGroup("def")
            .set("density","rho_el");
        model.component("comp1").material("mat_electrolyte").propertyGroup("def")
            .set("dynamicviscosity","mu_el");
    }

    private static void addFlowPhysics(Model model) {
        model.component("comp1").physics().create("spf","LaminarFlow","geom1");
        model.component("comp1").physics("spf").selection().named("sel_electrolyte");
        model.component("comp1").physics("spf").create("wall_anode","Wall",1);
        model.component("comp1").physics("spf").feature("wall_anode").selection().named("sel_anode_wall");
        model.component("comp1").physics("spf").create("wall_cathode","Wall",1);
        model.component("comp1").physics("spf").feature("wall_cathode").selection().named("sel_cathode_wall");
        model.component("comp1").physics("spf").create("inlet","Inlet",1);
        model.component("comp1").physics("spf").feature("inlet").selection().named("sel_inlet");
        model.component("comp1").physics("spf").feature("inlet").set("BoundaryCondition","LaminarInflow");
        model.component("comp1").physics("spf").feature("inlet").set("Uav","uin");
        model.component("comp1").physics("spf").create("outlet","Outlet",1);
        model.component("comp1").physics("spf").feature("outlet").selection().named("sel_outlet");
        model.component("comp1").physics("spf").feature("outlet").set("p0","0[Pa]");
    }

    private static void addSpeciesTransport(Model model) {
        model.component("comp1").physics().create("tds","DilutedSpecies","geom1",
            new String[]{"cN2","cNH3"});
        model.component("comp1").physics("tds").label("M02.1 N2/NH3 transport | "+PROVISIONAL);
        model.component("comp1").physics("tds").selection().named("sel_electrolyte");
        model.component("comp1").physics("tds").feature("cdm1").set("D_cN2_mat","userdef");
        model.component("comp1").physics("tds").feature("cdm1").set("D_cN2","DN2");
        model.component("comp1").physics("tds").feature("cdm1").set("D_cNH3_mat","userdef");
        model.component("comp1").physics("tds").feature("cdm1").set("D_cNH3","DNH3");
        model.component("comp1").physics("tds").feature("cdm1")
            .set("u",new String[][]{{"u"},{"v"},{"0"}});
        model.component("comp1").physics("tds").feature("init1")
            .set("initc",new String[]{"cN2_in","cNH3_in"});

        model.component("comp1").physics("tds").create("conc_in","Concentration",1);
        model.component("comp1").physics("tds").feature("conc_in").selection().named("sel_inlet");
        model.component("comp1").physics("tds").feature("conc_in").set("species",new int[]{1,1});
        model.component("comp1").physics("tds").feature("conc_in")
            .set("c0",new String[]{"cN2_in","cNH3_in"});

        // COMSOL 6.4 application-library models confirm the Inflow feature and
        // the API value FluxDanckwerts for BoundaryConditionType.
        model.component("comp1").physics("tds").create("inflow_audit","Inflow",1);
        model.component("comp1").physics("tds").feature("inflow_audit").selection().named("sel_inlet");
        model.component("comp1").physics("tds").feature("inflow_audit")
            .set("c0",new String[]{"cN2_in","cNH3_in"});
        model.component("comp1").physics("tds").feature("inflow_audit").active(false);

        model.component("comp1").physics("tds").create("outflow","Outflow",1);
        model.component("comp1").physics("tds").feature("outflow").selection().named("sel_outlet");
        model.component("comp1").physics("tds").create("noflux_anode","NoFlux",1);
        model.component("comp1").physics("tds").feature("noflux_anode").selection().named("sel_anode_wall");

        model.component("comp1").variable().create("var_rxn");
        model.component("comp1").variable("var_rxn").selection().named("sel_cathode_wall");
        model.component("comp1").variable("var_rxn").set("rN2","kN2*cN2",
            "Positive phenomenological N2 consumption flux");
        model.component("comp1").variable("var_rxn").set("rNH3","2*rN2",
            "NH3 generation flux; nitrogen-atom stoichiometry");
        model.component("comp1").physics("tds").create("flux_cathode","FluxBoundary",1);
        model.component("comp1").physics("tds").feature("flux_cathode").selection().named("sel_cathode_wall");
        model.component("comp1").physics("tds").feature("flux_cathode").set("species",new int[]{1,1});
        model.component("comp1").physics("tds").feature("flux_cathode")
            .set("FluxType","GeneralInwardFlux");
        model.component("comp1").physics("tds").feature("flux_cathode")
            .set("J0",new String[]{"-reaction_on*rN2","reaction_on*rNH3"});
    }

    private static void buildMesh(Model model) {
        model.component("comp1").mesh().create("mesh1");
        model.component("comp1").mesh("mesh1").create("map_channel","Map");
        model.component("comp1").mesh("mesh1").feature("map_channel").selection().named("sel_electrolyte");
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .create("dist_height","Distribution");
        model.component("comp1").mesh("mesh1").feature("map_channel").feature("dist_height")
            .selection().named("sel_inlet");
        model.component("comp1").mesh("mesh1").feature("map_channel")
            .create("dist_length","Distribution");
        model.component("comp1").mesh("mesh1").feature("map_channel").feature("dist_length")
            .selection().named("sel_cathode_wall");
        configureMesh(model,100,200,1,false,1,false);
    }

    private static void configureMesh(Model model,int nx,int ny,double xr,boolean xrev,
                                      double yr,boolean yrev) {
        com.comsol.model.MeshFeature dx=model.component("comp1").mesh("mesh1")
            .feature("map_channel").feature("dist_length");
        com.comsol.model.MeshFeature dy=model.component("comp1").mesh("mesh1")
            .feature("map_channel").feature("dist_height");
        dx.set("type","predefined"); dx.set("elemcount",nx);
        dx.set("elemratio",xr); dx.set("reverse",xrev);
        dy.set("type","predefined"); dy.set("elemcount",ny);
        dy.set("elemratio",yr); dy.set("reverse",yrev);
        model.component("comp1").mesh("mesh1").run();
    }

    private static void createStudy(Model model) {
        model.study().create("std_audit");
        model.study("std_audit").label("M02.1 stationary transport audit");
        model.study("std_audit").create("stat","Stationary");
        model.study("std_audit").feature("stat").activate("spf",true);
        model.study("std_audit").feature("stat").activate("tds",true);
    }

    private static void createNumericalEvaluations(Model model) {
        // ntflux variables are confirmed from the generated COMSOL model tree and
        // official COMSOL 6.4 application-library models. They are outward normal
        // total fluxes, including convection and diffusion.
        integral(model,"n2_in_nt","tds.ntflux_cN2*Wcell","sel_inlet","mol/s");
        integral(model,"n2_out_nt","tds.ntflux_cN2*Wcell","sel_outlet","mol/s");
        integral(model,"nh3_in_nt","tds.ntflux_cNH3*Wcell","sel_inlet","mol/s");
        integral(model,"nh3_out_nt","tds.ntflux_cNH3*Wcell","sel_outlet","mol/s");
        integral(model,"n2_cath_nt","tds.ntflux_cN2*Wcell","sel_cathode_wall","mol/s");
        integral(model,"nh3_cath_nt","tds.ntflux_cNH3*Wcell","sel_cathode_wall","mol/s");
        integral(model,"n2_anode_nt","tds.ntflux_cN2*Wcell","sel_anode_wall","mol/s");
        integral(model,"nh3_anode_nt","tds.ntflux_cNH3*Wcell","sel_anode_wall","mol/s");
        integral(model,"n2_all_nt","tds.ntflux_cN2*Wcell","sel_all_boundaries","mol/s");
        integral(model,"nh3_all_nt","tds.ntflux_cNH3*Wcell","sel_all_boundaries","mol/s");

        String n2adv="(u*cN2*nx+v*cN2*ny)*Wcell";
        String n2diff="-DN2*(nx*cN2x+ny*cN2y)*Wcell";
        String nh3adv="(u*cNH3*nx+v*cNH3*ny)*Wcell";
        String nh3diff="-DNH3*(nx*cNH3x+ny*cNH3y)*Wcell";
        for (String s : new String[]{"inlet","outlet"}) {
            String sel=s.equals("inlet")?"sel_inlet":"sel_outlet";
            integral(model,"n2_"+s+"_adv",n2adv,sel,"mol/s");
            integral(model,"n2_"+s+"_diff",n2diff,sel,"mol/s");
            integral(model,"nh3_"+s+"_adv",nh3adv,sel,"mol/s");
            integral(model,"nh3_"+s+"_diff",nh3diff,sel,"mol/s");
        }
        integral(model,"n2_all_exp","("+n2adv+")+(("+n2diff+"))","sel_all_boundaries","mol/s");
        integral(model,"nh3_all_exp","("+nh3adv+")+(("+nh3diff+"))","sel_all_boundaries","mol/s");
        integral(model,"n2_consumed","reaction_on*rN2*Wcell","sel_cathode_wall","mol/s");
        integral(model,"nh3_generated","reaction_on*rNH3*Wcell","sel_cathode_wall","mol/s");
        extremum(model,"min_n2","MinSurface","cN2","sel_electrolyte");
        extremum(model,"min_nh3","MinSurface","cNH3","sel_electrolyte");
        extremum(model,"min_cath_n2","MinLine","cN2","sel_cathode_wall");
        average(model,"avg_cath_n2","cN2","sel_cathode_wall");
    }

    private static void integral(Model model,String tag,String expr,String sel,String unit) {
        model.result().numerical().create(tag,"IntLine");
        model.result().numerical(tag).selection().named(sel);
        model.result().numerical(tag).set("expr",new String[]{expr});
        model.result().numerical(tag).set("unit",new String[]{unit});
        model.result().numerical(tag).set("intorderactive",true);
        model.result().numerical(tag).set("intorder",8);
    }

    private static void extremum(Model model,String tag,String type,String expr,String sel) {
        model.result().numerical().create(tag,type);
        model.result().numerical(tag).selection().named(sel);
        model.result().numerical(tag).set("expr",new String[]{expr});
        model.result().numerical(tag).set("unit",new String[]{"mol/m^3"});
    }

    private static void average(Model model,String tag,String expr,String sel) {
        model.result().numerical().create(tag,"AvLine");
        model.result().numerical(tag).selection().named(sel);
        model.result().numerical(tag).set("expr",new String[]{expr});
        model.result().numerical(tag).set("unit",new String[]{"mol/m^3"});
        model.result().numerical(tag).set("intorderactive",true);
        model.result().numerical(tag).set("intorder",8);
    }

    private static Metrics solve(Model model,Config cfg,double q,double k) {
        configure(model,cfg);
        model.param().set("Qliq",fmt(q)+"[cm^3/min]");
        long start=System.nanoTime();
        if (cfg.continuation) {
            for (double step : continuation(k)) {
                model.param().set("kN2",fmt(step)+"[m/s]");
                model.study("std_audit").run();
            }
        } else {
            model.param().set("kN2",fmt(k)+"[m/s]");
            model.study("std_audit").run();
        }
        double seconds=(System.nanoTime()-start)/1e9;
        model.param().set("kN2",fmt(k)+"[m/s]");
        setDataset(model,"dset1");
        Metrics m=evaluate(model,cfg.name,q,k);
        m.seconds=seconds;
        m.dof=model.sol("sol1").getU().length;
        return m;
    }

    private static void configure(Model model,Config cfg) {
        configureMesh(model,cfg.nx,cfg.ny,cfg.xRatio,cfg.xReverse,cfg.yRatio,cfg.yReverse);
        model.component("comp1").physics("tds").feature("conc_in")
            .active(cfg.inlet==InletMode.CONCENTRATION);
        model.component("comp1").physics("tds").feature("inflow_audit")
            .active(cfg.inlet!=InletMode.CONCENTRATION);
        if (cfg.inlet!=InletMode.CONCENTRATION) {
            model.component("comp1").physics("tds").feature("inflow_audit")
                .set("BoundaryConditionType",cfg.inlet==InletMode.DANCKWERTS ?
                    "FluxDanckwerts" : "ConcentrationConstraint");
        }
        model.component("comp1").physics("tds").prop("MassConsistentStabilization")
            .set("massStreamlineDiffusion",cfg.streamline);
        model.component("comp1").physics("tds").prop("MassConsistentStabilization")
            .set("massCrosswindDiffusion",cfg.crosswind);
        // The audited total-flux equation requires divergence (conservative)
        // convection. Keep A in the current nonconservative M02 form, and use
        // COMSOL's confirmed API value "cons" for the conservative alternatives.
        model.component("comp1").physics("tds").prop("AdvancedSettings")
            .set("ConvectiveTerm",cfg.inlet==InletMode.CONCENTRATION ? "noncons" : "cons");
        model.component("comp1").physics("tds").prop("ShapeProperty")
            .set("order_concentration",cfg.order);
    }

    private static double[] continuation(double target) {
        if(target<=1e-6)return new double[]{target};
        if(target<=3e-6)return new double[]{1e-6,target};
        if(target<=1e-5)return new double[]{1e-6,3e-6,target};
        if(target<=3e-5)return new double[]{1e-6,3e-6,1e-5,target};
        return new double[]{1e-6,3e-6,1e-5,3e-5,target};
    }

    private static Metrics evaluate(Model model,String config,double q,double k) {
        Metrics m=new Metrics(); m.config=config; m.q=q; m.k=k;
        m.n2InTotal=-val(model,"n2_in_nt"); m.n2OutTotal=val(model,"n2_out_nt");
        m.nh3InTotal=-val(model,"nh3_in_nt"); m.nh3OutTotal=val(model,"nh3_out_nt");
        m.n2InAdv=-val(model,"n2_inlet_adv"); m.n2OutAdv=val(model,"n2_outlet_adv");
        m.nh3InAdv=-val(model,"nh3_inlet_adv"); m.nh3OutAdv=val(model,"nh3_outlet_adv");
        m.n2InDiff=-val(model,"n2_inlet_diff"); m.n2OutDiff=val(model,"n2_outlet_diff");
        m.nh3InDiff=-val(model,"nh3_inlet_diff"); m.nh3OutDiff=val(model,"nh3_outlet_diff");
        m.n2Consumed=val(model,"n2_consumed"); m.nh3Generated=val(model,"nh3_generated");
        m.n2CathTotal=val(model,"n2_cath_nt"); m.nh3CathTotal=val(model,"nh3_cath_nt");
        m.n2AnodeTotal=val(model,"n2_anode_nt"); m.nh3AnodeTotal=val(model,"nh3_anode_nt");
        m.n2AllOutTotal=val(model,"n2_all_nt"); m.nh3AllOutTotal=val(model,"nh3_all_nt");
        m.n2AllOutExplicit=val(model,"n2_all_exp"); m.nh3AllOutExplicit=val(model,"nh3_all_exp");
        m.minN2=val(model,"min_n2"); m.minNH3=val(model,"min_nh3");
        m.minCathN2=val(model,"min_cath_n2"); m.avgCathN2=val(model,"avg_cath_n2");
        m.n2Error=Math.abs(m.n2InTotal-m.n2OutTotal-m.n2Consumed)/
            Math.max(Math.abs(m.n2InTotal),1e-30);
        m.nh3Error=Math.abs(m.nh3Generated+m.nh3InTotal-m.nh3OutTotal)/
            Math.max(Math.abs(m.nh3Generated),1e-30);
        m.nBoundaryError=Math.abs(2*m.n2Consumed-m.nh3Generated)/
            Math.max(Math.abs(m.nh3Generated),1e-30);
        m.nOverallError=Math.abs(2*(m.n2InTotal-m.n2OutTotal)-
            (m.nh3OutTotal-m.nh3InTotal))/Math.max(Math.abs(2*m.n2InTotal),1e-30);
        m.conversion=(m.n2InTotal-m.n2OutTotal)/Math.max(Math.abs(m.n2InTotal),1e-30);
        // Positive means NH3 diffuses out of the domain through the inlet.
        m.nh3BackDiffusion=Math.max(-m.nh3InDiff,0.0);
        double area=model.param().evaluate("Acath");
        m.jReaction=m.n2Consumed/area;
        double depletion=C_IN-m.avgCathN2;
        if(m.jReaction>0 && depletion>1e-15) m.jLimiting=m.jReaction*C_IN/depletion;
        else m.jLimiting=Double.POSITIVE_INFINITY;
        m.reactionToLimit=Double.isFinite(m.jLimiting)?m.jReaction/m.jLimiting:0.0;
        return m;
    }

    private static void setDataset(Model model,String dset) {
        for(String tag:model.result().numerical().tags())model.result().numerical(tag).set("data",dset);
    }

    private static double val(Model model,String tag) {
        double[][] v=model.result().numerical(tag).getReal();
        if(v==null||v.length==0||v[0].length==0)throw new IllegalStateException("No value: "+tag);
        return v[0][0];
    }

    private static double physicalChange(Metrics ref,Metrics m) {
        double a=Math.abs(m.conversion-ref.conversion)/Math.max(Math.abs(ref.conversion),1e-30);
        double b=Math.abs(m.nh3Generated-ref.nh3Generated)/Math.max(Math.abs(ref.nh3Generated),1e-30);
        return Math.max(a,b);
    }

    private static void classify(Metrics m) {
        if(!finite(m)){m.classification="FAILED_SOLVER";m.reason="nonfinite_result";return;}
        if(m.n2Error>CONS_TOL||m.nh3Error>CONS_TOL||m.nBoundaryError>CONS_TOL||m.nOverallError>CONS_TOL){
            m.classification="FAILED_MASS_BALANCE";
            m.reason="total_flux_or_nitrogen_balance_exceeds_1e-4";return;
        }
        double worst=Math.min(m.minN2,m.minNH3);
        boolean distorted=m.conversion<0||m.conversion>1.0001||m.nh3Generated<0;
        if(worst< -NEG_REL_TOL*C_IN||distorted){
            m.classification="FAILED_NEGATIVE_CONCENTRATION";
            if(m.reactionToLimit>=0.95||m.minCathN2<=0)
                m.reason="near_depletion_plus_numerical_oscillation_not_a_physical-limit_proof";
            else m.reason="numerical_oscillation_or_insufficient_mesh_stabilization";
            return;
        }
        if(worst<0){m.classification="WARNING_UNDERSHOOT";m.reason="negative_but_abs_cmin_over_cN2in_le_1e-6";}
        else {m.classification="PASS_CONSERVATIVE";m.reason="conservative_and_nonnegative";}
    }

    private static boolean finite(Metrics m) {
        return Double.isFinite(m.n2Error)&&Double.isFinite(m.nh3Error)&&
            Double.isFinite(m.minN2)&&Double.isFinite(m.minNH3)&&Double.isFinite(m.conversion);
    }

    private static Config chooseSelected(List<Config> configs,List<Metrics> metrics) {
        Config best=null; double bestMin=-Double.MAX_VALUE;
        for(Metrics m:metrics){
            boolean eligible=!m.classification.startsWith("FAILED")&&m.config.startsWith("B_");
            if(eligible&&Math.min(m.minN2,m.minNH3)>bestMin){
                bestMin=Math.min(m.minN2,m.minNH3);
                for(Config c:configs)if(c.name.equals(m.config)){best=c;break;}
            }
        }
        if(best==null)throw new IllegalStateException("No conservative nonfailed B configuration");
        return best;
    }

    private static void enforceBaseAcceptance(Metrics m) {
        classify(m);
        if(m.n2Error>CONS_TOL||m.nh3Error>CONS_TOL||m.nBoundaryError>CONS_TOL||
            m.nOverallError>CONS_TOL||m.classification.startsWith("FAILED"))
            throw new IllegalStateException("Selected M02.1 base fails acceptance: "+m.classification+
                ", N2="+m.n2Error+", NH3="+m.nh3Error+", Noverall="+m.nOverallError+
                ", minNH3="+m.minNH3);
    }

    private static void printFluxHeader() {
        System.out.println("M021_FLUX|config|Qliq_cm3_min|kN2_m_s|N2_in_total|N2_out_total|"+
            "N2_in_adv|N2_out_adv|N2_in_diff|N2_out_diff|N2_consumed|N2_cathode_out|"+
            "N2_anode_out|N2_all_boundary_out_ntflux|N2_all_boundary_out_explicit|N2_species_error|"+
            "NH3_in_total|NH3_out_total|NH3_in_adv|NH3_out_adv|NH3_in_diff|NH3_out_diff|"+
            "NH3_generated|NH3_cathode_out|NH3_anode_out|NH3_all_boundary_out_ntflux|"+
            "NH3_all_boundary_out_explicit|NH3_species_error|NH3_inlet_backdiff_out|"+
            "N_boundary_error|N_overall_error|min_cN2|min_cNH3|conversion|dof|solve_s");
    }

    private static void printFlux(Metrics m) {
        System.out.println(String.format(Locale.ROOT,
            "M021_FLUX|%s|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|"+
            "%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|"+
            "%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%d|%.12g",
            m.config,m.q,m.k,m.n2InTotal,m.n2OutTotal,m.n2InAdv,m.n2OutAdv,m.n2InDiff,m.n2OutDiff,
            m.n2Consumed,m.n2CathTotal,m.n2AnodeTotal,m.n2AllOutTotal,m.n2AllOutExplicit,m.n2Error,
            m.nh3InTotal,m.nh3OutTotal,m.nh3InAdv,m.nh3OutAdv,m.nh3InDiff,m.nh3OutDiff,
            m.nh3Generated,m.nh3CathTotal,m.nh3AnodeTotal,m.nh3AllOutTotal,m.nh3AllOutExplicit,
            m.nh3Error,m.nh3BackDiffusion,m.nBoundaryError,m.nOverallError,m.minN2,m.minNH3,
            m.conversion,m.dof,m.seconds));
    }

    private static void printStabilizationHeader() {
        System.out.println("M021_STAB|config|inlet|convective_form|streamline|crosswind|order|nx|ny|dof|solve_s|"+
            "min_cN2|min_cNH3|N2_species_error|NH3_species_error|N_boundary_error|N_overall_error|"+
            "N2_conversion|NH3_generation|physical_change_gt_1pct|physical_change_fraction|classification|reason");
    }

    private static void printStabilization(Metrics m,Config c) {
        System.out.println(String.format(Locale.ROOT,
            "M021_STAB|%s|%s|%s|%s|%s|%d|%d|%d|%d|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|"+
            "%.12g|%.12g|%.12g|%s|%.12g|%s|%s",c.name,c.inlet,
            c.inlet==InletMode.CONCENTRATION?"noncons":"cons",c.streamline,c.crosswind,c.order,
            c.nx,c.ny,m.dof,m.seconds,m.minN2,m.minNH3,m.n2Error,m.nh3Error,m.nBoundaryError,
            m.nOverallError,m.conversion,m.nh3Generated,m.physicalChange>0.01,m.physicalChange,
            m.classification,m.reason));
    }

    private static void printStabilizationFailure(Config c,Exception ex) {
        System.out.println("M021_STAB|"+c.name+"|"+c.inlet+"|"+
            (c.inlet==InletMode.CONCENTRATION?"noncons":"cons")+"|"+c.streamline+"|"+c.crosswind+"|"+
            c.order+"|"+c.nx+"|"+c.ny+"|NaN|NaN|NaN|NaN|NaN|NaN|NaN|NaN|NaN|NaN|true|NaN|"+
            "FAILED_SOLVER|"+safe(ex));
    }

    private static void printScanHeader() {
        System.out.println("M021_SCAN|config|Qliq_cm3_min|kN2_m_s|N2_in_total|N2_out_total|N2_in_adv|"+
            "N2_out_adv|N2_in_diff|N2_out_diff|NH3_in_total|NH3_out_total|NH3_in_adv|NH3_out_adv|"+
            "NH3_in_diff|NH3_out_diff|NH3_inlet_backdiff_out|N2_species_error|NH3_species_error|"+
            "N_boundary_error|N_overall_error|N2_conversion|NH3_generation|min_cathode_cN2|"+
            "avg_cathode_cN2|min_cN2|min_cNH3|reaction_flux|limiting_mass_transfer_flux|"+
            "reaction_to_limiting_flux|classification|reason|dof|solve_s");
    }

    private static void printScan(Metrics m) {
        System.out.println(String.format(Locale.ROOT,
            "M021_SCAN|%s|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|"+
            "%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|"+
            "%.12g|%.12g|%.12g|%.12g|%.12g|%.12g|%s|%s|%d|%.12g",m.config,m.q,m.k,
            m.n2InTotal,m.n2OutTotal,m.n2InAdv,m.n2OutAdv,m.n2InDiff,m.n2OutDiff,
            m.nh3InTotal,m.nh3OutTotal,m.nh3InAdv,m.nh3OutAdv,m.nh3InDiff,m.nh3OutDiff,
            m.nh3BackDiffusion,m.n2Error,m.nh3Error,m.nBoundaryError,m.nOverallError,m.conversion,
            m.nh3Generated,m.minCathN2,m.avgCathN2,m.minN2,m.minNH3,m.jReaction,m.jLimiting,
            m.reactionToLimit,m.classification,m.reason,m.dof,m.seconds));
    }

    private static void printScanFailure(Config c,double q,double k,Exception ex) {
        StringBuilder b=new StringBuilder("M021_SCAN|").append(c.name).append('|').append(fmt(q))
            .append('|').append(fmt(k));
        for(int i=0;i<26;i++)b.append("|NaN");
        b.append("|FAILED_SOLVER|").append(safe(ex)).append("|NaN|NaN");
        System.out.println(b.toString());
    }

    private static boolean hasTag(String[] tags,String wanted) {
        for(String tag:tags)if(tag.equals(wanted))return true;
        return false;
    }

    private static String safe(Exception ex) {
        String s=ex.getClass().getSimpleName()+":"+ex.getMessage();
        return s.replace('|','/').replace('\n',' ').replace('\r',' ');
    }

    private static String fmt(double x) {return String.format(Locale.ROOT,"%.12g",x);}

    public static void main(String[] args) throws Exception {
        if(LOAD_ONLY){
            Model loaded=ModelUtil.load("M021Reload",MPH_ABSOLUTE);
            if (!hasTag(loaded.component().tags(),"comp1") ||
                !hasTag(loaded.component("comp1").physics().tags(),"tds") ||
                !hasTag(loaded.study().tags(),"std_audit"))
                throw new IllegalStateException("Reloaded MPH is missing stable M02.1 tags");
            System.out.println("M021_LOAD|PASS|"+MPH);
            return;
        }
        run();
    }
}
