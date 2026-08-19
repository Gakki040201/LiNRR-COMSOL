import com.comsol.model.Model;
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

/** Independent exact value/unit/origin/hash and five-consumer reload audit. */
public final class LiNRR_M03A_4_LoadCheck {
    private static final String CASE="COMBINED_NONIDENTITY";
    private static final String[] Q={"conductivity","electrode_spacing","out_of_plane_thickness","EIS_area","current_density_reporting_area"};
    private static final String[] P={"kappa_dry_run","Hcell_dry_run","depth_dry_run","A_EIS_dry_run","A_j_report_dry_run"};
    private static final String[] U={"S/m","m","m","m^2","m^2"};
    private static final String[] FORBIDDEN={"SecondaryCurrentDistribution","TertiaryCurrentDistribution","ElectrodeReaction","ButlerVolmer","ExchangeCurrent","HOR","HER","LiPlating","LiStripping","SEI"};
    private LiNRR_M03A_4_LoadCheck(){}
    public static void main(String[] args)throws Exception{
        Path mph=Paths.get(input("M03A4_RELOAD_MPH")).toAbsolutePath().normalize();Path csv=Paths.get(input("M03A4_RESOLVED_INPUTS")).toAbsolutePath().normalize();String expectedHash=input("M03A4_RESOLVED_SHA256").toLowerCase(Locale.ROOT);
        if(!Files.isRegularFile(mph)||Files.size(mph)<=0)throw new IllegalStateException("Derived MPH missing: "+mph);if(!sha(csv).equals(expectedHash))throw new IllegalStateException("Reload resolved SHA-256 mismatch");
        Map<String,R> rows=read(csv);Model m=ModelUtil.load("M034ReloadAudit",mph.toString());
        for(String s:new String[]{"sel_electrolyte","sel_inlet","sel_outlet","sel_anode_wall","sel_cathode_wall"}){int d=s.equals("sel_electrolyte")?2:1;if(m.component("comp1").selection(s).entities(d).length!=1)throw new IllegalStateException("Selection mapping failed: "+s);}
        System.out.println("M03A4_RELOAD_PARAM|expected_target_parameter|stored_target_parameter|expected_value|stored_value|actual_value|expected_unit|stored_unit|expected_origin|stored_origin|expected_source_sha256|stored_source_sha256|expected_resolved_sha256|stored_resolved_sha256|expected_case_id|stored_case_id|expected_quantity_name|stored_quantity_name|status|failure_reason");
        for(int i=0;i<Q.length;i++){
            R r=rows.get(CASE+"|"+Q[i]);
            if(r==null||!P[i].equals(r.target)||!U[i].equals(r.unit)||!"SYNTHETIC".equals(r.origin)||!"PASS".equals(r.status))throw new IllegalStateException("Resolved expected row invalid: "+Q[i]);
            Map<String,String> meta=metadata(m.param().descr(P[i]));
            String expression=m.param().get(P[i]).replace(" ","");
            double actual=m.param().evaluate(P[i],U[i]);
            boolean exactValue=Double.doubleToLongBits(r.value)==Double.doubleToLongBits(actual)&&r.valueText.equals(meta.get("applied_value"))&&expression.equals(r.valueText+"["+r.unit+"]");
            boolean pass=exactValue&&P[i].equals(meta.get("target_parameter"))&&r.unit.equals(meta.get("applied_unit"))&&r.origin.equals(meta.get("data_origin"))&&r.sourceHash.equals(meta.get("source_sha256"))&&expectedHash.equals(meta.get("resolved_sha256"))&&CASE.equals(meta.get("case_id"))&&Q[i].equals(meta.get("quantity_name"));
            if(!pass)throw new IllegalStateException("Reload exact metadata round-trip failed: "+P[i]);
            System.out.println("M03A4_RELOAD_PARAM|"+P[i]+"|"+meta.get("target_parameter")+"|"+r.valueText+"|"+meta.get("applied_value")+"|"+LiNRR_M03A_4_Metrics.fmt(actual)+"|"+r.unit+"|"+meta.get("applied_unit")+"|"+r.origin+"|"+meta.get("data_origin")+"|"+r.sourceHash+"|"+meta.get("source_sha256")+"|"+expectedHash+"|"+meta.get("resolved_sha256")+"|"+CASE+"|"+meta.get("case_id")+"|"+Q[i]+"|"+meta.get("quantity_name")+"|PASS|");
        }
        requireTag(m.component("comp1").physics().tags(),"spf");requireTag(m.component("comp1").physics().tags(),"tds");requireTag(m.component("comp1").physics().tags(),"cd");if(!"PrimaryCurrentDistribution".equals(m.component("comp1").physics("cd").getType()))throw new IllegalStateException("Primary current type changed");
        String sigma=m.component("comp1").physics("cd").feature("ice1").getString("sigmal");if(!"kappa_M033".equals(sigma)||LiNRR_M03A_4_Metrics.relative(m.param().evaluate("kappa_M033","S/m"),m.param().evaluate("kappa_dry_run","S/m"))>1e-12)throw new IllegalStateException("Conductivity consumer audit failed");
        double h=value(m,"m033_len_inlet"),hexp=m.param().evaluate("Hcell_dry_run","m");if(LiNRR_M03A_4_Metrics.relative(h,hexp)>1e-9)throw new IllegalStateException("Actual geometry height consumer failed");
        for(String n:new String[]{"m033_i_anode","m033_i_cathode","m033_n2_in","m033_n2_out","m033_nh3_in","m033_nh3_out","m033_n2_cath","m033_nh3_cath"}){String e=m.result().numerical(n).getStringArray("expr")[0];if(!e.contains("Wcell"))throw new IllegalStateException("Integration depth consumer missing Wcell: "+n);value(m,n);}
        if(LiNRR_M03A_4_Metrics.relative(m.param().evaluate("Wcell","m"),m.param().evaluate("depth_dry_run","m"))>1e-12)throw new IllegalStateException("Depth parameter consumer failed");
        if(LiNRR_M03A_4_Metrics.relative(m.param().evaluate("kappa_HFR_audit_M034","S/m"),m.param().evaluate("kappa_dry_run","S/m"))>1e-12)throw new IllegalStateException("EIS inversion area consumer failed");
        double current=m.param().evaluate("I_total_M034","A"),area=m.param().evaluate("A_j_report_dry_run","m^2"),j=m.param().evaluate("j_reported_M034","A/m^2");if(LiNRR_M03A_4_Metrics.relative(j,current/area)>1e-12)throw new IllegalStateException("Reporting area consumer failed");
        if(m.param().evaluate("M034_full_coupled_solve_marker")!=1.0)throw new IllegalStateException("Full coupled solution marker missing");
        for(String p:m.component("comp1").physics().tags()){audit(p);audit(m.component("comp1").physics(p).getType());audit(m.component("comp1").physics(p).label());for(String f:m.component("comp1").physics(p).feature().tags()){audit(f);audit(m.component("comp1").physics(p).feature(f).getType());audit(m.component("comp1").physics(p).feature(f).label());}}
        System.out.println("M03A4_RELOAD_CONSUMER|conductivity_feature|PASS");System.out.println("M03A4_RELOAD_CONSUMER|actual_geometry_height|PASS");System.out.println("M03A4_RELOAD_CONSUMER|integration_depth|PASS");System.out.println("M03A4_RELOAD_CONSUMER|conductivity_inversion_area|PASS");System.out.println("M03A4_RELOAD_CONSUMER|reported_current_density|PASS");
        System.out.println("M03A4_RELOAD|PASS|exact canonical values units origins targets source hashes and five actual consumers verified in independent process");
    }
    private static Map<String,R> read(Path p)throws Exception{byte[] b=Files.readAllBytes(p);for(byte x:b)if((x&255)>127)throw new IllegalStateException("resolved artifact not ASCII");List<List<String>> rows=parse(new String(b,StandardCharsets.US_ASCII));String[] h={"sample_id","quantity_name","target_parameter","value_SI","uncertainty_SI","unit","data_origin","evidence_status","resolution_method","source_table","source_rows","source_reference","source_sha256","status","failure_reason"};if(rows.size()<2||!rows.get(0).equals(Arrays.asList(h)))throw new IllegalStateException("resolved header failed");Map<String,R> out=new LinkedHashMap<>();for(int i=1;i<rows.size();i++){List<String> c=rows.get(i);if(c.size()!=h.length)throw new IllegalStateException("resolved columns failed");R r=new R();r.target=c.get(2);r.valueText=c.get(3);r.value=Double.parseDouble(r.valueText);r.unit=c.get(5);r.origin=c.get(6);r.sourceHash=c.get(12).toLowerCase(Locale.ROOT);r.status=c.get(13);String k=c.get(0)+"|"+c.get(1);if(out.put(k,r)!=null)throw new IllegalStateException("duplicate resolved key");}return out;}
    private static Map<String,String> metadata(String description){if(description==null||!description.startsWith("M03A4_META_V1|"))throw new IllegalStateException("MPH parameter metadata missing");Map<String,String> out=new LinkedHashMap<>();String[] fields=description.split("\\|",-1);for(int i=1;i<fields.length;i++){int equals=fields[i].indexOf('=');if(equals<=0||out.put(fields[i].substring(0,equals),fields[i].substring(equals+1))!=null)throw new IllegalStateException("Malformed MPH parameter metadata");}return out;}
    private static List<List<String>> parse(String t){List<List<String>> rows=new ArrayList<>();List<String> row=new ArrayList<>();StringBuilder f=new StringBuilder();boolean q=false;for(int i=0;i<t.length();i++){char c=t.charAt(i);if(q){if(c=='\"'){if(i+1<t.length()&&t.charAt(i+1)=='\"'){f.append('\"');i++;}else q=false;}else f.append(c);}else if(c=='\"')q=true;else if(c==','){row.add(f.toString());f.setLength(0);}else if(c=='\r'||c=='\n'){if(c=='\r'&&i+1<t.length()&&t.charAt(i+1)=='\n')i++;row.add(f.toString());f.setLength(0);if(!(row.size()==1&&row.get(0).isEmpty()))rows.add(row);row=new ArrayList<>();}else f.append(c);}if(q)throw new IllegalStateException("unclosed quote");if(f.length()>0||!row.isEmpty()){row.add(f.toString());rows.add(row);}return rows;}
    private static double value(Model m,String n){double[][] v=m.result().numerical(n).getReal();if(v==null||v.length==0||v[0].length==0||!Double.isFinite(v[0][0]))throw new IllegalStateException("Current-run result missing: "+n);return v[0][0];}
    private static String sha(Path p)throws Exception{byte[] h=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p));StringBuilder s=new StringBuilder();for(byte x:h)s.append(String.format(Locale.ROOT,"%02x",x&255));return s.toString();}
    private static void audit(String text){String x=text==null?"":text.toLowerCase(Locale.ROOT);for(String f:FORBIDDEN)if(x.contains(f.toLowerCase(Locale.ROOT)))throw new IllegalStateException("Forbidden physics text: "+f);}
    private static void requireTag(String[] tags,String wanted){for(String t:tags)if(wanted.equals(t))return;throw new IllegalStateException("Missing tag: "+wanted);}
    private static String input(String n){try{Class<?> b=Class.forName("LiNRR_M03A_4_RunInputs");Object v=b.getMethod("get",String.class).invoke(null,n);if(v!=null&&!v.toString().trim().isEmpty())return v.toString();}catch(Throwable ignored){}String v=System.getenv(n);if(v!=null&&!v.trim().isEmpty())return v;throw new IllegalStateException(n+" is required");}
    private static final class R{String target,valueText,unit,origin,sourceHash,status;double value;}
}
