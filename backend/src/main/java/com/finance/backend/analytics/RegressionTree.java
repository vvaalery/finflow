package com.finance.backend.analytics;

import java.util.Arrays;

final class RegressionTree {
    private static final class Node {
        int feature = -1; double threshold; double value; Node left, right;
    }

    private final Node root;

    private RegressionTree(Node root) { this.root = root; }

    static RegressionTree fit(double[][] x, double[] y, int maxDepth, int minLeaf) {
        int[] idx = new int[y.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        return new RegressionTree(build(x, y, idx, 0, maxDepth, Math.max(1, minLeaf)));
    }

    double predict(double[] f) {
        Node n = root;
        while (n.feature >= 0) n = f[n.feature] <= n.threshold ? n.left : n.right;
        return n.value;
    }

    private static Node build(double[][] x, double[] y, int[] idx, int depth, int maxDepth, int minLeaf) {
        Node node = new Node();
        double sum = 0;
        for (int i : idx) sum += y[i];
        node.value = idx.length == 0 ? 0 : sum / idx.length;
        if (depth >= maxDepth || idx.length < 2 * minLeaf) return node;

        int features = x[0].length;
        double bestGain = 1e-12; int bestF = -1; double bestT = 0;
        double parentScore = sum * sum / idx.length;
        for (int f = 0; f < features; f++) {
            final int ff = f;
            Integer[] order = new Integer[idx.length];
            for (int i = 0; i < idx.length; i++) order[i] = idx[i];
            Arrays.sort(order, (a, b) -> Double.compare(x[a][ff], x[b][ff]));
            double leftSum = 0;
            for (int k = 0; k < order.length - 1; k++) {
                leftSum += y[order[k]];
                int nl = k + 1, nr = order.length - nl;
                double v1 = x[order[k]][f], v2 = x[order[k + 1]][f];
                if (v1 == v2 || nl < minLeaf || nr < minLeaf) continue;
                double rightSum = sum - leftSum;
                double gain = leftSum * leftSum / nl + rightSum * rightSum / nr - parentScore;
                if (gain > bestGain) { bestGain = gain; bestF = f; bestT = (v1 + v2) / 2; }
            }
        }
        if (bestF < 0) return node;
        final int splitFeature = bestF;
        final double splitValue = bestT;
        int[] l = Arrays.stream(idx).filter(i -> x[i][splitFeature] <= splitValue).toArray();
        int[] r = Arrays.stream(idx).filter(i -> x[i][splitFeature] > splitValue).toArray();
        node.feature = splitFeature; node.threshold = splitValue;
        node.left = build(x, y, l, depth + 1, maxDepth, minLeaf);
        node.right = build(x, y, r, depth + 1, maxDepth, minLeaf);
        return node;
    }
}
