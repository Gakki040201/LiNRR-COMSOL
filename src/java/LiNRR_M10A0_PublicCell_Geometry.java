import com.comsol.model.Model;
import com.comsol.model.util.ModelUtil;
import java.io.IOException;

/**
 * M10A0: public-reference 3D Li-NRR flow-cell geometry.
 *
 * Purpose:
 *   1) create a full-assembly visualization component;
 *   2) create a separate physics-ready active-zone component;
 *   3) save an editable COMSOL MPH for later flow/transport/current modules.
 *
 * IMPORTANT SCIENTIFIC BOUNDARY:
 *   This is a PUBLIC_REFERENCE geometry, not a claim that every unpublished
 *   internal dimension of the DTU/Shaofeng Li cell is known. Public anchors,
 *   geometry inferences, and provisional visualization dimensions are kept
 *   as separate parameters and documented in config/M10A0_public_cell_parameters.csv.
 *
 * Public anchors used here:
 *   - overall cell: 10.7 cm x 10.7 cm x 5.1 cm;
 *   - effective flow-field/electrode area: 25 cm^2;
 *   - gas-flow-channel characteristic size: 1 mm;
 *   - central electrolyte chamber: 4 mm baseline;
 *   - baseline chamber shown/tested with 4 elongated spacers.
 *
 * No physics, solver, or experimental calibration is executed in M10A0.
 */
public final class LiNRR_M10A0_PublicCell_Geometry {

    private static final String OUTPUT = "models/generated/LiNRR_M10A0_public_cell_geometry.mph";

    private LiNRR_M10A0_PublicCell_Geometry() {
        // Utility class.
    }

    public static Model run() throws IOException {
        Model model = ModelUtil.create("Model");
        model.label("LiNRR_M10A0_public_cell_geometry.mph");

        defineParameters(model);
        buildAssemblyVisualization(model);
        buildPhysicsReadyActiveZone(model);

        model.save(OUTPUT);
        return model;
    }

    private static void defineParameters(Model model) {
        // ------------------------------------------------------------------
        // Public hard anchors.
        // ------------------------------------------------------------------
        model.param().set("Lcell", "107[mm]", "PUBLIC: overall cell length");
        model.param().set("Wcell", "107[mm]", "PUBLIC: overall cell width");
        model.param().set("Hcell", "51[mm]", "PUBLIC: overall assembled thickness");
        model.param().set("Aactive", "25[cm^2]", "PUBLIC: effective flow-field/electrode area");
        model.param().set("h_electrolyte", "4[mm]", "PUBLIC baseline: central electrolyte chamber thickness");
        model.param().set("d_gas_public", "1[mm]", "PUBLIC: reported gas-flow-channel characteristic size");

        // ------------------------------------------------------------------
        // Geometry inference from public area.
        // The paper/patent gives 25 cm^2, not an exact 50 x 50 mm CAD drawing.
        // ------------------------------------------------------------------
        model.param().set("Lactive", "50[mm]", "INFERRED: square-equivalent side for 25 cm^2 area");
        model.param().set("Wactive", "50[mm]", "INFERRED: square-equivalent side for 25 cm^2 area");
        model.param().set("m_active_x", "(Lcell-Lactive)/2", "Derived active-area centering margin");
        model.param().set("m_active_y", "(Wcell-Wactive)/2", "Derived active-area centering margin");

        // ------------------------------------------------------------------
        // Same-platform/public-family dimension used for the mesh electrode.
        // Keep editable when exact hardware is measured.
        // ------------------------------------------------------------------
        model.param().set("t_gde", "0.03[mm]", "PUBLIC SAME-PLATFORM: SSC thickness 30 um");

        // ------------------------------------------------------------------
        // Modeling interpretation of the reported 1 mm gas-channel size.
        // The publication does not uniquely specify both width and depth.
        // ------------------------------------------------------------------
        model.param().set("w_gas_ch", "d_gas_public", "INFERRED: map 1 mm characteristic size to channel width");
        model.param().set("h_gas_ch", "d_gas_public", "INFERRED: map 1 mm characteristic size to channel depth");
        model.param().set("w_gas_rib", "1[mm]", "PROVISIONAL: gas-flow-field rib width");

        // ------------------------------------------------------------------
        // Electrolyte-chamber spacer model.
        // Patent Figure 4 / Example 5 describes a 4 elongated-bar baseline.
        // Bar width itself is not reported.
        // ------------------------------------------------------------------
        model.param().set("n_spacer", "4", "PUBLIC configuration count used in patent baseline");
        model.param().set("w_spacer", "1[mm]", "PROVISIONAL: spacer bar width");
        model.param().set("w_liq_lane", "(Wactive-4*w_spacer)/5", "Derived width of each of five liquid lanes");

        // ------------------------------------------------------------------
        // Active-zone inlet/outlet header lengths for a physics-ready geometry.
        // These are not claimed as exact hardware dimensions.
        // ------------------------------------------------------------------
        model.param().set("L_header", "10[mm]", "PROVISIONAL: numerical inlet/outlet header extension");

        // ------------------------------------------------------------------
        // Full-assembly visualization-only layer thicknesses.
        // The exact internal metal-stack dimensions are not public. They are
        // selected only so the overall public 51 mm thickness is respected.
        // ------------------------------------------------------------------
        model.param().set("t_flow_plate", "10[mm]", "PROVISIONAL VISUAL: gas manifold/current-collector envelope");
        model.param().set("t_outer_plate", "(Hcell-2*t_flow_plate-2*t_gde-h_electrolyte)/2", "Derived visual end-plate thickness to close 51 mm stack");
        model.param().set("r_bolt", "3[mm]", "PROVISIONAL VISUAL: bolt envelope radius");
        model.param().set("m_bolt", "10[mm]", "PROVISIONAL VISUAL: bolt center margin from cell edge");

        // Stack z positions for assembly component.
        model.param().set("z_cath_end", "0[mm]", "Assembly z: cathode end plate");
        model.param().set("z_cath_flow", "t_outer_plate", "Assembly z: cathode gas/current-collector plate");
        model.param().set("z_cath_gde", "t_outer_plate+t_flow_plate", "Assembly z: cathode SSC");
        model.param().set("z_chamber", "z_cath_gde+t_gde", "Assembly z: electrolyte chamber/frame");
        model.param().set("z_an_gde", "z_chamber+h_electrolyte", "Assembly z: anode PtAu/SSC");
        model.param().set("z_an_flow", "z_an_gde+t_gde", "Assembly z: anode gas/current-collector plate");
        model.param().set("z_an_end", "z_an_flow+t_flow_plate", "Assembly z: anode end plate");

        // Stack z positions for active-zone component.
        model.param().set("z_h2", "0[mm]", "Active zone z: H2 flow-field layer");
        model.param().set("z_anode", "h_gas_ch", "Active zone z: PtAu/SSC anode");
        model.param().set("z_liq", "h_gas_ch+t_gde", "Active zone z: electrolyte chamber");
        model.param().set("z_cathode", "z_liq+h_electrolyte", "Active zone z: SSC cathode");
        model.param().set("z_n2", "z_cathode+t_gde", "Active zone z: N2 flow-field layer");
        model.param().set("Hactive", "2*h_gas_ch+2*t_gde+h_electrolyte", "Derived active-zone stack height");
    }

    private static void buildAssemblyVisualization(Model model) {
        final String comp = "comp_assembly";
        final String geom = "geom_assembly";

        model.component().create(comp, true);
        model.component(comp).label("Public cell - full assembly visualization");
        model.component(comp).geom().create(geom, 3);
        model.component(comp).geom(geom).lengthUnit("mm");

        // Metal envelope layers.
        addBlock(model, comp, geom, "ep_c", "Cathode end plate - provisional visual envelope",
                new String[] {"Lcell", "Wcell", "t_outer_plate"},
                new String[] {"0", "0", "z_cath_end"});

        addBlock(model, comp, geom, "fp_c", "Cathode gas manifold/current-collector plate - provisional",
                new String[] {"Lcell", "Wcell", "t_flow_plate"},
                new String[] {"0", "0", "z_cath_flow"});

        addBlock(model, comp, geom, "gde_c", "Cathode SSC / GDE active sheet",
                new String[] {"Lactive", "Wactive", "t_gde"},
                new String[] {"m_active_x", "m_active_y", "z_cath_gde"});

        // PEEK-like central frame: four strips around the 25 cm^2 square-equivalent opening.
        addBlock(model, comp, geom, "fr_l", "Electrolyte chamber frame - left",
                new String[] {"m_active_x", "Wcell", "h_electrolyte"},
                new String[] {"0", "0", "z_chamber"});
        addBlock(model, comp, geom, "fr_r", "Electrolyte chamber frame - right",
                new String[] {"m_active_x", "Wcell", "h_electrolyte"},
                new String[] {"m_active_x+Lactive", "0", "z_chamber"});
        addBlock(model, comp, geom, "fr_b", "Electrolyte chamber frame - bottom",
                new String[] {"Lactive", "m_active_y", "h_electrolyte"},
                new String[] {"m_active_x", "0", "z_chamber"});
        addBlock(model, comp, geom, "fr_t", "Electrolyte chamber frame - top",
                new String[] {"Lactive", "m_active_y", "h_electrolyte"},
                new String[] {"m_active_x", "m_active_y+Wactive", "z_chamber"});

        addBlock(model, comp, geom, "liq_vis", "Electrolyte cavity - public 4 mm baseline",
                new String[] {"Lactive", "Wactive", "h_electrolyte"},
                new String[] {"m_active_x", "m_active_y", "z_chamber"});

        addBlock(model, comp, geom, "gde_a", "Anode PtAu/SSC active sheet",
                new String[] {"Lactive", "Wactive", "t_gde"},
                new String[] {"m_active_x", "m_active_y", "z_an_gde"});

        addBlock(model, comp, geom, "fp_a", "Anode gas manifold/current-collector plate - provisional",
                new String[] {"Lcell", "Wcell", "t_flow_plate"},
                new String[] {"0", "0", "z_an_flow"});

        addBlock(model, comp, geom, "ep_a", "Anode end plate - provisional visual envelope",
                new String[] {"Lcell", "Wcell", "t_outer_plate"},
                new String[] {"0", "0", "z_an_end"});

        // Eight bolt envelopes. Count is visually consistent with the public assembled cell,
        // but exact coordinates/radius are not claimed as published CAD data.
        addBolt(model, comp, geom, "bolt01", "m_bolt", "m_bolt");
        addBolt(model, comp, geom, "bolt02", "Lcell/2", "m_bolt");
        addBolt(model, comp, geom, "bolt03", "Lcell-m_bolt", "m_bolt");
        addBolt(model, comp, geom, "bolt04", "m_bolt", "Wcell/2");
        addBolt(model, comp, geom, "bolt05", "Lcell-m_bolt", "Wcell/2");
        addBolt(model, comp, geom, "bolt06", "m_bolt", "Wcell-m_bolt");
        addBolt(model, comp, geom, "bolt07", "Lcell/2", "Wcell-m_bolt");
        addBolt(model, comp, geom, "bolt08", "Lcell-m_bolt", "Wcell-m_bolt");

        model.component(comp).geom(geom).run();
    }

    private static void buildPhysicsReadyActiveZone(Model model) {
        final String comp = "comp_active";
        final String geom = "geom_active";

        model.component().create(comp, true);
        model.component(comp).label("Public cell - physics-ready active zone");
        model.component(comp).geom().create(geom, 3);
        model.component(comp).geom(geom).lengthUnit("mm");

        // ------------------------------------------------------------------
        // H2-side flow field: 25 x 1 mm parallel channels with 1 mm ribs.
        // The reported 1 mm gas-channel size is public; the exact parallel
        // arrangement and rib width are a documented modeling interpretation.
        // ------------------------------------------------------------------
        addBlock(model, comp, geom, "h2_hdr_in", "H2 inlet header",
                new String[] {"L_header", "Wactive", "h_gas_ch"},
                new String[] {"-L_header", "0", "z_h2"});
        addBlock(model, comp, geom, "h2_hdr_out", "H2 outlet header",
                new String[] {"L_header", "Wactive", "h_gas_ch"},
                new String[] {"Lactive", "0", "z_h2"});

        for (int i = 0; i < 25; i++) {
            String suffix = String.format("%02d", i + 1);
            String yChannel = String.format("%d*(w_gas_ch+w_gas_rib)", i);
            String yRib = String.format("%d*(w_gas_ch+w_gas_rib)+w_gas_ch", i);

            addBlock(model, comp, geom, "h2c" + suffix, "H2 gas channel " + suffix,
                    new String[] {"Lactive", "w_gas_ch", "h_gas_ch"},
                    new String[] {"0", yChannel, "z_h2"});
            addBlock(model, comp, geom, "h2r" + suffix, "H2 flow-field rib " + suffix,
                    new String[] {"Lactive", "w_gas_rib", "h_gas_ch"},
                    new String[] {"0", yRib, "z_h2"});
        }

        // PtAu/SSC anode.
        addBlock(model, comp, geom, "anode", "PtAu/SSC anode GDE",
                new String[] {"Lactive", "Wactive", "t_gde"},
                new String[] {"0", "0", "z_anode"});

        // ------------------------------------------------------------------
        // Central electrolyte chamber.
        // Four full-height elongated spacer bars define five flow lanes.
        // This reproduces the public patent baseline topology without claiming
        // the unpublished spacer width or exact CAD profile.
        // ------------------------------------------------------------------
        addBlock(model, comp, geom, "liq_hdr_in", "Electrolyte inlet header",
                new String[] {"L_header", "Wactive", "h_electrolyte"},
                new String[] {"-L_header", "0", "z_liq"});
        addBlock(model, comp, geom, "liq_hdr_out", "Electrolyte outlet header",
                new String[] {"L_header", "Wactive", "h_electrolyte"},
                new String[] {"Lactive", "0", "z_liq"});

        for (int lane = 0; lane < 5; lane++) {
            String suffix = String.format("%02d", lane + 1);
            String yLane = String.format("%d*w_liq_lane+%d*w_spacer", lane, lane);
            addBlock(model, comp, geom, "liq" + suffix, "Electrolyte flow lane " + suffix,
                    new String[] {"Lactive", "w_liq_lane", "h_electrolyte"},
                    new String[] {"0", yLane, "z_liq"});
        }

        for (int s = 0; s < 4; s++) {
            String suffix = String.format("%02d", s + 1);
            String ySpacer = String.format("%d*w_liq_lane+%d*w_spacer", s + 1, s);
            addBlock(model, comp, geom, "sp" + suffix, "Electrolyte chamber spacer " + suffix,
                    new String[] {"Lactive", "w_spacer", "h_electrolyte"},
                    new String[] {"0", ySpacer, "z_liq"});
        }

        // SSC cathode.
        addBlock(model, comp, geom, "cathode", "SSC cathode GDE",
                new String[] {"Lactive", "Wactive", "t_gde"},
                new String[] {"0", "0", "z_cathode"});

        // ------------------------------------------------------------------
        // N2-side flow field, mirrored above the cathode.
        // ------------------------------------------------------------------
        addBlock(model, comp, geom, "n2_hdr_in", "N2 inlet header",
                new String[] {"L_header", "Wactive", "h_gas_ch"},
                new String[] {"-L_header", "0", "z_n2"});
        addBlock(model, comp, geom, "n2_hdr_out", "N2 outlet header",
                new String[] {"L_header", "Wactive", "h_gas_ch"},
                new String[] {"Lactive", "0", "z_n2"});

        for (int i = 0; i < 25; i++) {
            String suffix = String.format("%02d", i + 1);
            String yChannel = String.format("%d*(w_gas_ch+w_gas_rib)", i);
            String yRib = String.format("%d*(w_gas_ch+w_gas_rib)+w_gas_ch", i);

            addBlock(model, comp, geom, "n2c" + suffix, "N2 gas channel " + suffix,
                    new String[] {"Lactive", "w_gas_ch", "h_gas_ch"},
                    new String[] {"0", yChannel, "z_n2"});
            addBlock(model, comp, geom, "n2r" + suffix, "N2 flow-field rib " + suffix,
                    new String[] {"Lactive", "w_gas_rib", "h_gas_ch"},
                    new String[] {"0", yRib, "z_n2"});
        }

        model.component(comp).geom(geom).run();
    }

    private static void addBlock(
            Model model,
            String comp,
            String geom,
            String tag,
            String label,
            String[] size,
            String[] pos) {

        model.component(comp).geom(geom).create(tag, "Block");
        model.component(comp).geom(geom).feature(tag).label(label);
        model.component(comp).geom(geom).feature(tag).set("base", "corner");
        model.component(comp).geom(geom).feature(tag).set("size", size);
        model.component(comp).geom(geom).feature(tag).set("pos", pos);
        model.component(comp).geom(geom).feature(tag).set("selresult", "on");
        model.component(comp).geom(geom).feature(tag).set("selresultshow", "dom");
    }

    private static void addBolt(
            Model model,
            String comp,
            String geom,
            String tag,
            String x,
            String y) {

        model.component(comp).geom(geom).create(tag, "Cylinder");
        model.component(comp).geom(geom).feature(tag).label(tag + " - visual bolt envelope");
        model.component(comp).geom(geom).feature(tag).set("r", "r_bolt");
        model.component(comp).geom(geom).feature(tag).set("h", "Hcell");
        model.component(comp).geom(geom).feature(tag).set("pos", new String[] {x, y, "0"});
        model.component(comp).geom(geom).feature(tag).set("selresult", "on");
        model.component(comp).geom(geom).feature(tag).set("selresultshow", "dom");
    }

    public static void main(String[] args) throws IOException {
        run();
    }
}
