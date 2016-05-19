package fr.rtone.vlc.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.uncommons.maths.Maths;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class SampleBenchmark {

    @State(Scope.Thread)
    public static class Point {

        private static final double MAX_VALUE = 10_000;
        public final double a, b;
        public int sum;

        public Point() {
            a = ThreadLocalRandom.current().nextDouble(MAX_VALUE);
            b = ThreadLocalRandom.current().nextDouble(MAX_VALUE);
        }

        @Setup(Level.Trial)
        public void doSetup() {
            sum = 0;
            System.out.println("Do Setup");
        }

        @TearDown(Level.Trial)
        public void doTearDown() {
            System.out.println("Do TearDown");
        }
    }

    @Benchmark
     public double measureHypot_baseline(Point p) {
        return Math.sqrt(p.a * p.a + p.b * p.b);
    }

    @Benchmark
    public double measureHypot_direct(Point p) {
        return Math.hypot(p.a, p.b);
    }

    @Benchmark
    public double measureHypot_wrapped(Point p) {
        return SampleClass.foo(p.a, p.b);
    }

    @Benchmark
    public double measureLog_direct(Point p) {
        return Maths.log(p.a, p.b);
    }

    @Benchmark
    public double measureLog_wrapped(Point p) {
        return SampleClass.bar(p.a, p.b);
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public int measureWrong_100() {
        return SampleClass.reps(100);
    }

}
