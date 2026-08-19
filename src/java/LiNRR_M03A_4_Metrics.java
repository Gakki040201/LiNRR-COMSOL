import java.util.Locale;

/** Numerical helpers for the M03A.4 synthetic parameter-transfer dry-run. */
final class LiNRR_M03A_4_Metrics {
    private LiNRR_M03A_4_Metrics() {}
    static double relative(double a, double b) {
        return Math.abs(a-b)/Math.max(Math.max(Math.abs(a),Math.abs(b)),1.0e-300);
    }
    static double closure(double residual, double... scales) {
        double scale=1.0e-300; for(double v:scales) scale=Math.max(scale,Math.abs(v));
        return Math.abs(residual)/scale;
    }
    static boolean finite(double... values) {
        for(double v:values) if(!Double.isFinite(v)) return false; return true;
    }
    static String fmt(double value) { return String.format(Locale.ROOT,"%.17g",value); }
}
