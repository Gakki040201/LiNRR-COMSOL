import java.util.Locale;

/** Numerical helpers shared by the M03A.3 COMSOL builder and load check. */
final class LiNRR_M03A_3_Metrics {
    private LiNRR_M03A_3_Metrics() {}

    static double relative(double a, double b) {
        return Math.abs(a - b) /
            Math.max(Math.max(Math.abs(a), Math.abs(b)), 1.0e-300);
    }

    static double closure(double residual, double... scales) {
        double scale = 1.0e-300;
        for (double value : scales) scale = Math.max(scale, Math.abs(value));
        return Math.abs(residual) / scale;
    }

    static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    static String fmt(double value) {
        return String.format(Locale.ROOT, "%.12g", value);
    }

    static String safe(Throwable error) {
        String message = error.getClass().getSimpleName() + ":" + error.getMessage();
        return message.replace('|', '/').replace('\n', ' ').replace('\r', ' ');
    }

    static double[] linearFit(double[] x, double[] y) {
        if (x.length != y.length || x.length < 2) {
            throw new IllegalArgumentException("Linear fit requires equal arrays with at least two points.");
        }
        double sx = 0.0, sy = 0.0, sxx = 0.0, sxy = 0.0;
        for (int i = 0; i < x.length; i++) {
            sx += x[i]; sy += y[i]; sxx += x[i] * x[i]; sxy += x[i] * y[i];
        }
        double n = x.length;
        double denominator = n * sxx - sx * sx;
        if (denominator == 0.0) throw new IllegalArgumentException("Degenerate linear-fit input.");
        double slope = (n * sxy - sx * sy) / denominator;
        double intercept = (sy - slope * sx) / n;
        double mean = sy / n, ssTotal = 0.0, ssResidual = 0.0;
        for (int i = 0; i < x.length; i++) {
            double fitted = intercept + slope * x[i];
            ssTotal += (y[i] - mean) * (y[i] - mean);
            ssResidual += (y[i] - fitted) * (y[i] - fitted);
        }
        double r2 = ssTotal == 0.0 ? (ssResidual == 0.0 ? 1.0 : 0.0) :
            1.0 - ssResidual / ssTotal;
        return new double[] {slope, intercept, r2};
    }
}
