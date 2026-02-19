package com.intuit.karate.core;

import java.util.Arrays;

/**
 * Utility for flagging branch coverage and printing it.
 */
public class DIYCoverageTracker {
    public static boolean[] branches = new boolean[22];

    public static void reportCoverage() {
        int coveredCount = 0;
        int notCoveredCount = 0;

        System.out.println("DIY branch coverage report");
        for (int i = 0; i < branches.length; i++) {
            if (branches[i]) {
                System.out.println("Branch ID " + i + ": COVERED");
                coveredCount++;
            } else {
                System.out.println("Branch ID " + i + ": NOT COVERED");
                notCoveredCount++;
            }
        }
        double percentage = ((double) coveredCount / branches.length) * 100;

        System.out.println("Total branches covered: " + coveredCount);
        System.out.println("Total branches not covered: " + notCoveredCount);
        System.out.println("Percentage of branches covered: " + percentage + "%");
    }
}