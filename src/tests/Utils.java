package test;

import concurrentcube.Cube;
import concurrentcube.Rotations.Rotation;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Utils {

    public static final Random rand = new Random();


    /**
     * Displays a message for the current thread.
     */
    public static void logWithThreadName(String message) {
        System.out.println("[" + Thread.currentThread().getName() + "] " + message);
    }

    /**
     * Performs and logs an interruption.
     */
    public static void interruptCurrentThread() {
        logWithThreadName("interrupted");
        Thread.currentThread().interrupt();
    }

    /**
     * Makes a thread sleep for a specific amount of time.
     * @param millis : sleep period/delay
     */
    public static void sleep(int millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException ignored) {
            //we ignore this exception on purpose
        }
    }

    /**
     * Simple assertion.
     * @param condition : what should be true
     */
    public static void assertThat(boolean condition) {
        if (!condition) {
            throw new AssertionError();
        }
    }

    /**
     * Interrupts a thread based on an RNG's result.
     * @param t : thread
     * @param prob : probability
     * @return was the thread interrrupted
     */
    public static boolean interruptWithProbability(Thread t, double prob) {
        if (rand.nextDouble() <= prob) {
            t.interrupt();
            return true;
        }
        return false;
    }

    /**
     * Gets all permutations of `perm` and adds them to `acc`.
     * @param perm : initial permutation
     * @param k : depth of recursion (0...`perm.size()`)
     * @param acc : result accumulator
     */
    public static <T> void addPermutations(List<T> perm, int k, List<List<T>> acc) {
        for (int i = k; i < perm.size(); i++){
            java.util.Collections.swap(perm, i, k);
            addPermutations(perm, k+1, acc);
            java.util.Collections.swap(perm, k, i);
        }
        if (k == perm.size() - 1){
            acc.add(new ArrayList<>(perm));
        }
    }

    /**
     * Generates all permutations of a list.
     * @param initialPermutation : the initial permutation
     * @return all permutations of list
     */
    public static <T> List<List<T>> generatePermutations(List<T> initialPermutation) {
        List<List<T>> result = new ArrayList<>();
        addPermutations(initialPermutation, 0, result);
        return result;
    }

    /**
     * Allows measuring time of a runnable's execution.
     */
    public static class Stopwatch {
        private Instant start;

        public void start() {
            start = Instant.now();
        }

        public Duration stop() {
            Duration duration = Duration.between(start, Instant.now());
            start = null;
            return duration;
        }

        public Duration runWithStopwatch(Runnable runnable) {
            start();
            runnable.run();
            return stop();
        }
    }

    /**
     * Runnable using the Cube::rotate method
     */
    public static class WriterTask implements Runnable {
        private final Cube cube;
        private final int side;
        private final int layer;

        public WriterTask(Cube cube, int side, int layer) {
            this.cube = cube;
            this.side = side;
            this.layer = layer;
        }

        public static WriterTask fromRotation(Rotation rotation) {
            return new WriterTask(rotation.getCube(), rotation.getSide().intValue(), rotation.getLayer());
        }

        @Override
        public void run() {
            try {
                cube.rotate(side, layer);
            } catch (InterruptedException e) {
                interruptCurrentThread();
            }
        }
    }

    /**
     * Runnable using the Cube::show method
     */
    public static class ReaderTask implements Runnable {
        private final Cube cube;

        public ReaderTask(Cube cube) {
            this.cube = cube;
        }

        @Override
        public void run() {
            try {
                cube.show();
            } catch (InterruptedException e) {
                interruptCurrentThread();
            }
        }
    }

}
