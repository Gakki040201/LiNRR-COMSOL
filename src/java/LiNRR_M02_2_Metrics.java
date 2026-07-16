import java.util.Locale;

/** Shared numerical helpers for the COMSOL-compiled M02.2 verification entry. */
final class LiNRR_M02_2_Metrics {
    private LiNRR_M02_2_Metrics() {}

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
}
