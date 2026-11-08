package com.finance.backend.analytics;

public final class Ols {
    public final int n;
    public final double slope, intercept, r2, tStat, residualStd;

    private Ols(int n, double slope, double intercept, double r2, double tStat, double residualStd) {
        this.n = n; this.slope = slope; this.intercept = intercept; this.r2 = r2; this.tStat = tStat; this.residualStd = residualStd;
    }

    public static Ols fit(double[] y, int from, int to) {
        int n = to - from;
        if (n <= 0) return new Ols(0, 0, 0, 0, 0, 0);
        if (n == 1) return new Ols(1, 0, y[from], 0, 0, 0);
        double sx = 0, sy = 0;
        for (int i = from; i < to; i++) { sx += i; sy += y[i]; }
        double mx = sx / n, my = sy / n, sxx = 0, sxy = 0, syy = 0;
        for (int i = from; i < to; i++) {
            double dx = i - mx, dy = y[i] - my;
            sxx += dx * dx; sxy += dx * dy; syy += dy * dy;
        }
        double slope = sxx == 0 ? 0 : sxy / sxx;
        double intercept = my - slope * mx;
        double sse = 0;
        for (int i = from; i < to; i++) { double e = y[i] - (intercept + slope * i); sse += e * e; }
        double r2 = syy == 0 ? 0 : Math.max(0, 1 - sse / syy);
        double resStd = n > 2 ? Math.sqrt(sse / (n - 2)) : 0;
        double t;
        if (n <= 2 || sxx == 0) t = 0;
        else if (resStd < 1e-9 * Math.max(1, Math.abs(my))) t = slope == 0 ? 0 : Math.copySign(1e9, slope);
        else t = slope / (resStd / Math.sqrt(sxx));
        return new Ols(n, slope, intercept, r2, t, resStd);
    }

    public double at(double x) { return intercept + slope * x; }
}
