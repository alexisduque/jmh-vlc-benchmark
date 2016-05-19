package fr.rtone.vlc.benchmark;

import org.uncommons.maths.Maths;

public class SampleClass {

    private static int x = 1;
    private static int y = 2;

    public static double foo(double a, double b) {
        return Math.hypot(a, b);
    }

    public static double bar(double a, double b) {
        return Maths.log(a, b);
    }

    public static int reps(int reps) {
        int s = 0;
        for (int i = 0; i < reps; i++) {
            s += (x + y);
        }
        return s;
    }


}
