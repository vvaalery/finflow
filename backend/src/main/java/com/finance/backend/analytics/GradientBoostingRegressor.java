package com.finance.backend.analytics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class GradientBoostingRegressor {
    private final double init;
    private final double learningRate;
    private final List<RegressionTree> trees;

    private GradientBoostingRegressor(double init, double lr, List<RegressionTree> trees) {
        this.init = init; this.learningRate = lr; this.trees = trees;
    }

    static GradientBoostingRegressor fit(double[][] x, double[] y, int rounds, double learningRate, int depth, int minLeaf) {
        int n = y.length;
        double mean = Arrays.stream(y).average().orElse(0);
        double[] f = new double[n];
        Arrays.fill(f, mean);
        List<RegressionTree> trees = new ArrayList<>();
        for (int m = 0; m < rounds; m++) {
            double[] res = new double[n], abs = new double[n];
            for (int i = 0; i < n; i++) { res[i] = y[i] - f[i]; abs[i] = Math.abs(res[i]); }
            double delta = quantile(abs, 0.9);
            if (delta < 1e-9) break;
            double[] g = new double[n];
            for (int i = 0; i < n; i++) g[i] = Math.max(-delta, Math.min(delta, res[i]));
            RegressionTree tree = RegressionTree.fit(x, g, depth, minLeaf);
            for (int i = 0; i < n; i++) f[i] += learningRate * tree.predict(x[i]);
            trees.add(tree);
        }
        return new GradientBoostingRegressor(mean, learningRate, trees);
    }

    double predict(double[] features) {
        double v = init;
        for (RegressionTree t : trees) v += learningRate * t.predict(features);
        return v;
    }

    int size() { return trees.size(); }

    static double quantile(double[] values, double q) {
        if (values.length == 0) return 0;
        double[] s = values.clone();
        Arrays.sort(s);
        double pos = q * (s.length - 1);
        int lo = (int) Math.floor(pos), hi = (int) Math.ceil(pos);
        return s[lo] + (s[hi] - s[lo]) * (pos - lo);
    }
}
