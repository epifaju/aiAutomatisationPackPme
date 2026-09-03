package com.aipack.lead;

public final class LeadScoreBands {

    private LeadScoreBands() {}

    public static String label(int score, int lowMax, int mediumMax, int highMax) {
        if (score <= lowMax) {
            return "faible";
        }
        if (score <= mediumMax) {
            return "moyen";
        }
        if (score <= highMax) {
            return "intéressant";
        }
        return "prioritaire";
    }
}
