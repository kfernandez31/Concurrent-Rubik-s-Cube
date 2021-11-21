package test;

import concurrentcube.Cube;
import concurrentcube.Rotations.*;
import concurrentcube.Side;
import concurrentcube.Color;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Klasa korzysta z JUNit5
 */
public class CubeTest {
    /**
     * Tests that utilize cube solving algorithms are in standard cubing notation,
     * however anti-clockwise turns are written in lowercase instead of the traditional apostrophe (ex. R' = r).
     */

    private static final int TEST_MAX_CUBE_SIZE = 25;
    private static final int STANDARD_CUBE_SIZE = 3;
    private static final int NUM_THREADS = 64;
    private static final Random rand = new Random();
    private static final Stopwatch stopwatch = new Stopwatch();

    //TODO: wtrynić wszędzie show'y
    //TODO: Wywalić simpleRotate'y (?)

    /* ------------------------ Helper functions and structures ------------------------ */

    private static void interruptCurrentThread() {
        Thread t = Thread.currentThread();
        t.interrupt();
        System.err.println(t.getName() + " interrupted");
    }

    private static void sleep(int millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }

    private static void assertThat(boolean condition) {
        if (!condition) {
            throw new AssertionError();
        }
    }

    private static void applySequenceOfRotations(Cube cube, List<Rotation> rotations) {
        cube.solve();
        for (Rotation rot : rotations) {
            rot.applyRotation();
        }
    }

    private static <T> void addPermutations(List<T> perm, int k, List<List<T>> acc) {
        for(int i = k; i < perm.size(); i++){
            java.util.Collections.swap(perm, i, k);
            addPermutations(perm, k+1, acc);
            java.util.Collections.swap(perm, k, i);
        }
        if (k == perm.size() - 1){
            acc.add(new ArrayList<>(perm));
        }
    }

    private static <T> List<List<T>> generateInterlacings(List<T> initialPermutation) {
        List<List<T>> outcomes = new ArrayList<>();
        addPermutations(initialPermutation, 0, outcomes);
        return outcomes;
    }

    private static class Stopwatch {
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


    private static class RotationTask implements Runnable {
        private final Cube cube;
        private final int side;
        private final int layer;

        public RotationTask(Cube cube, int side, int layer) {
            this.cube = cube;
            this.side = side;
            this.layer = layer;
        }

        public static RotationTask fromRotation(Rotation rotation) {
            return new RotationTask(rotation.getCube(), rotation.getSide().intValue(), rotation.getLayer());
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

    private static class ShowTask implements Runnable {
        private final Cube cube;

        public ShowTask(Cube cube) {
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

    /* ------------------------ Concurrent tests ------------------------ */

    @Test
    public void testDelegateManyRandomRotations() {
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(TEST_MAX_CUBE_SIZE,
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        final int NUM_ROTATIONS = 100;

        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);
        List<Callable<Object>> tasks = new ArrayList<>(NUM_ROTATIONS);

        for (int i = 0; i < NUM_ROTATIONS; i++) {
            tasks.add(Executors.callable(
                    new RotationTask(cube, Side.randomSide().intValue(), 1 + rand.nextInt(cube.getSize()))));
        }

        try {
            pool.invokeAll(tasks);
            assertThat(cube.isLegal() && counter.value == 2 * NUM_ROTATIONS);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Tests whether a composition of rotations produced by multi-threading is legal.
     */
    @Test
    public void testRotationCompositionResult() {
        final int NUM_ROTATIONS = 8; //I advise you not to go higher than 9, since time will grow in O(n*n!)
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(TEST_MAX_CUBE_SIZE,
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });


        List<Rotation> rotations = new ArrayList<>();
        for (int i = 0; i < NUM_ROTATIONS; i++) {
            rotations.add(Rotation.newRotation(cube, Side.randomSide(), rand.nextInt(cube.getSize())));
        }

        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);
        List<Callable<Object>> tasks = new ArrayList<>(rotations.size());

        for (Rotation r : rotations) {
            tasks.add(Executors.callable(RotationTask.fromRotation(r)));
        }

        try {
            pool.invokeAll(tasks);
            Color[][] res = cube.getCopyOfSquares();

            List<List<Rotation>> interleavings = generateInterlacings(rotations);
            List<Color[][]> outcomes = new ArrayList<>();
            for (List<Rotation> sequence : interleavings) {
                applySequenceOfRotations(cube, sequence);
                Color[][] outcomeArr = cube.getCopyOfSquares();
                outcomes.add(outcomeArr);
            }

            assertThat(outcomes.stream().anyMatch(arr -> Arrays.deepEquals(arr, res)));
        } catch (InterruptedException e) {
            interruptCurrentThread();
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Tests whether conflicting rotations are actually stopped on their axis' semaphores
     * and whether only one can rotate the cube at a time.
     */
    @Test
    public void testLetAnotherRotationPass() {
        var counter = new Object() {
            int times_first_thread_started_rotating = 0;
            int times_someone_else_started_rotating = 0;
        };
        Cube cube = new Cube(3,
                (side, __) -> {
                    if (side == Side.Top.intValue()) {
                        ++counter.times_first_thread_started_rotating;
                        sleep(200); // give time for other threads to try and pass
                        assertThat(counter.times_someone_else_started_rotating == 0);
                    }
                    else {
                        ++counter.times_someone_else_started_rotating;
                    }
                },
                null, null, null);

        RotationTask politeRunnable = new RotationTask(cube, Side.Top.intValue(), 0);
        Thread firstThread = new Thread(politeRunnable);

        Thread[] otherThreads = new Thread[] {
                new Thread(new RotationTask(cube,  Side.Left.intValue(), 0)),
                new Thread(new RotationTask(cube,  Side.Front.intValue(), 0)),
                new Thread(new RotationTask(cube,  Side.Right.intValue(), 0)),
                new Thread(new RotationTask(cube,  Side.Back.intValue(), 0)),
                new Thread(new RotationTask(cube,  Side.Bottom.intValue(), 2)),
        };

        firstThread.start();
        sleep(100); //TODO: this is to ensure `firstThread` will start first
        for (Thread t : otherThreads) {
            t.start();
        }
        try {
            firstThread.join();
            for (Thread t : otherThreads) {
                t.join();
            }
            assertThat(counter.times_first_thread_started_rotating == 1);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    /**
     * Tests whether each rotation has a cycle of length 4.
     */
    @Test
    public void testCyclesOfParallelRotationsConcurrent() {
        final int ROTATIONS_PER_TYPE = 4444;
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(10,
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);
        List<Callable<Object>> tasks = new ArrayList<>(2 * ROTATIONS_PER_TYPE);

        Side side = Side.randomSide();

        for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
            for (int layer = 0; layer < cube.getSize(); layer++) {
                tasks.add(Executors.callable(new RotationTask(cube, side.intValue(), layer)));
            }
        }

        try {
            pool.invokeAll(tasks);
            assertThat(cube.isSolved() && counter.value == 2 * cube.getSize() * ROTATIONS_PER_TYPE);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Tests whether a processes of two antagonist groups will cancel out.
     */
    @Test
    public void testTwoAntagonistRotationsConcurrent() {
        final int ROTATIONS_PER_TYPE = 1000;
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(5,
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);

        Side side = Side.randomSide();
        int layer = rand.nextInt(cube.getSize());

        List<Callable<Object>> tasks = new ArrayList<>(2 * ROTATIONS_PER_TYPE);
        for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
            tasks.add(Executors.callable(new RotationTask(cube, side.intValue(), layer)));
            tasks.add(Executors.callable(new RotationTask(cube, side.opposite().intValue(), cube.getSize() - 1 - layer)));
        }
        try {
            pool.invokeAll(tasks);

            assertThat(cube.isSolved() && counter.value == 4 * ROTATIONS_PER_TYPE);

        } catch (InterruptedException e) {
            interruptCurrentThread();
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Tests whether a processes of many antagonist groups will cancel out.
     */
    @Test
    public void testManyAntagonistRotationsConcurrent() {
        final int ROTATIONS_PER_TYPE = 1000;
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(1 + rand.nextInt(TEST_MAX_CUBE_SIZE),
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);
        List<Callable<Object>> tasks = new ArrayList<>(2 * ROTATIONS_PER_TYPE);

        Side side = Side.randomSide();
        for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
            for (int layer = 0; layer < cube.getSize(); layer++) {
                tasks.add(Executors.callable(new RotationTask(cube, side.intValue(), layer)));
                tasks.add(Executors.callable(new RotationTask(cube, side.opposite().intValue(), cube.getSize() - 1 - layer)));
            }
        }

        try {
            pool.invokeAll(tasks);
            assertThat(cube.isSolved() && counter.value == 4 * cube.getSize() * ROTATIONS_PER_TYPE);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        } finally {
            pool.shutdown();
        }
    }

    //TODO
    @Test
    public void testTimeOfIndependentProcesses() {

    }



    /* ------------------------ Sequential tests ------------------------ */

    @Test
    public void testCyclesOfParallelRotationsSequential() {
        final int ROTATIONS_PER_TYPE = 4444;
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(10,
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        Side side = Side.randomSide();

        try {
            for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
                for (int layer = 0; layer < cube.getSize(); layer++) {
                    cube.rotate(side.intValue(), layer);
                }
            }
            assertThat(cube.isSolved() && counter.value == 2 * cube.getSize() * ROTATIONS_PER_TYPE);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    @Test
    public void testTwoAntagonistRotationsSequential() {
        final int ROTATIONS_PER_TYPE = 1000;
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(5,
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        Side side = Side.randomSide();
        int layer = rand.nextInt(cube.getSize());

        try {
            for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
                cube.rotate(side.intValue(), layer);
                cube.rotate(side.opposite().intValue(), cube.getSize() - 1 - layer);
            }
            assertThat(cube.isSolved() && counter.value == 4 * ROTATIONS_PER_TYPE);

        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    @Test
    public void testManyAntagonistRotationsSequential() {
        final int ROTATIONS_PER_TYPE = 1000;
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(1 + rand.nextInt(TEST_MAX_CUBE_SIZE),
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        try {
            Side side = Side.randomSide();
            for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
                for (int layer = 0; layer < cube.getSize(); layer++) {
                    cube.rotate(side.intValue(), layer);
                    cube.rotate(side.opposite().intValue(), cube.getSize() - 1 - layer);
                }
            }
            assertThat(cube.isSolved() && counter.value == 4 * cube.getSize() * ROTATIONS_PER_TYPE);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    /**
     * Tests a popular algorithm with cycle of length 24.
     */
    @Test
    public void testCycleOfRUruSequential() {
        final int ROTATIONS_PER_TYPE = 666;
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(3,
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        try {
            for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
                cube.rotate(Side.Right.intValue(), 0);
                cube.rotate(Side.Top.intValue(),0);
                cube.rotate(Side.Right.opposite().intValue(),cube.getSize() - 1);
                cube.rotate(Side.Top.opposite().intValue(),cube.getSize() - 1);
            }

            assertThat(cube.isSolved() && counter.value == 2 * 4 * ROTATIONS_PER_TYPE);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    @Test
    public void testSimpleRotateTop0() {
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(STANDARD_CUBE_SIZE,
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        try {
            cube.rotate(Side.Top.intValue(), 0);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }

        try {
            String str = cube.show();
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }

        Color[][] solvedArrangement =  {
                {       /* Top */
                        Color.White, Color.White, Color.White,
                        Color.White, Color.White, Color.White,
                        Color.White, Color.White, Color.White
                },
                {       /* Left */
                        Color.Red, Color.Red, Color.Red,
                        Color.Green, Color.Green, Color.Green,
                        Color.Green, Color.Green, Color.Green
                },
                {       /* Front */
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Red, Color.Red, Color.Red,
                        Color.Red, Color.Red, Color.Red
                },
                {       /* Right */
                        Color.Orange, Color.Orange, Color.Orange,
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Blue, Color.Blue, Color.Blue
                },
                {       /* Back */
                        Color.Green, Color.Green, Color.Green,
                        Color.Orange, Color.Orange, Color.Orange,
                        Color.Orange, Color.Orange, Color.Orange
                },
                {       /* Bottom */
                        Color.Yellow, Color.Yellow, Color.Yellow,
                        Color.Yellow, Color.Yellow, Color.Yellow,
                        Color.Yellow, Color.Yellow, Color.Yellow
                }
        };
        assertThat(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.value == 4);
    }

    @Test
    public void testSimpleRotateLeft0() {
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(STANDARD_CUBE_SIZE,
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        try {
            cube.rotate(Side.Left.intValue(), 0);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }

        try { //TOP jest yellow/white/orange (5/0/4), reszta ok
            String str = cube.show();
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }

        Color[][] solvedArrangement =  {
                {       /* Top */
                        Color.Orange, Color.White, Color.White,
                        Color.Orange, Color.White, Color.White,
                        Color.Orange, Color.White, Color.White
                },
                {       /* Left */
                        Color.Green, Color.Green, Color.Green,
                        Color.Green, Color.Green, Color.Green,
                        Color.Green, Color.Green, Color.Green
                },
                {       /* Front */
                        Color.White, Color.Red, Color.Red,
                        Color.White, Color.Red, Color.Red,
                        Color.White, Color.Red, Color.Red
                },
                {       /* Right */
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Blue, Color.Blue, Color.Blue
                },
                {       /* Back */
                        Color.Orange, Color.Orange, Color.Yellow,
                        Color.Orange, Color.Orange, Color.Yellow,
                        Color.Orange, Color.Orange, Color.Yellow
                },
                {       /* Bottom */
                        Color.Red, Color.Yellow, Color.Yellow,
                        Color.Red, Color.Yellow, Color.Yellow,
                        Color.Red, Color.Yellow, Color.Yellow
                }
        };

        assertThat(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.value == 4);
    }

    @Test
    public void testSimpleRotateFront0() {
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(STANDARD_CUBE_SIZE,
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        try {
            cube.rotate(Side.Front.intValue(), 0);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }

        try {
            String str = cube.show();
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }

        Color[][] solvedArrangement =  {
                {       /* Top */
                        Color.White, Color.White, Color.White,
                        Color.White, Color.White, Color.White,
                        Color.Green, Color.Green, Color.Green
                },
                {       /* Left */
                        Color.Green, Color.Green, Color.Yellow,
                        Color.Green, Color.Green, Color.Yellow,
                        Color.Green, Color.Green, Color.Yellow
                },
                {       /* Front */
                        Color.Red, Color.Red, Color.Red,
                        Color.Red, Color.Red, Color.Red,
                        Color.Red, Color.Red, Color.Red
                },
                {       /* Right */
                        Color.White, Color.Blue, Color.Blue,
                        Color.White, Color.Blue, Color.Blue,
                        Color.White, Color.Blue, Color.Blue
                },
                {       /* Back */
                        Color.Orange, Color.Orange, Color.Orange,
                        Color.Orange, Color.Orange, Color.Orange,
                        Color.Orange, Color.Orange, Color.Orange
                },
                {       /* Bottom */
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Yellow, Color.Yellow, Color.Yellow,
                        Color.Yellow, Color.Yellow, Color.Yellow
                }
        };

        assertThat(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.value == 4);
    }

    @Test
    public void testSimpleRotateRight0() {
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(STANDARD_CUBE_SIZE,
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        try {
            cube.rotate(Side.Right.intValue(), 0);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }

        try {
            String str = cube.show();
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }

        Color[][] solvedArrangement =  {
                {       /* Top */
                        Color.White, Color.White, Color.Red,
                        Color.White, Color.White, Color.Red,
                        Color.White, Color.White, Color.Red
                },
                {       /* Left */
                        Color.Green, Color.Green, Color.Green,
                        Color.Green, Color.Green, Color.Green,
                        Color.Green, Color.Green, Color.Green
                },
                {       /* Front */
                        Color.Red, Color.Red, Color.Yellow,
                        Color.Red, Color.Red, Color.Yellow,
                        Color.Red, Color.Red, Color.Yellow
                },
                {       /* Right */
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Blue, Color.Blue, Color.Blue
                },
                {       /* Back */
                        Color.White, Color.Orange, Color.Orange,
                        Color.White, Color.Orange, Color.Orange,
                        Color.White, Color.Orange, Color.Orange
                },
                {       /* Bottom */
                        Color.Yellow, Color.Yellow, Color.Orange,
                        Color.Yellow, Color.Yellow, Color.Orange,
                        Color.Yellow, Color.Yellow, Color.Orange
                }
        };

        assertThat(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.value == 4);
    }

    @Test
    public void testSimpleRotateBack0() {
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(STANDARD_CUBE_SIZE,
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        try {
            cube.rotate(Side.Back.intValue(), 0);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }

        try {
            String str = cube.show();
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }

        Color[][] solvedArrangement =  {
                {       /* Top */
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.White, Color.White, Color.White,
                        Color.White, Color.White, Color.White
                },
                {       /* Left */
                        Color.White, Color.Green, Color.Green,
                        Color.White, Color.Green, Color.Green,
                        Color.White, Color.Green, Color.Green
                },
                {       /* Front */
                        Color.Red, Color.Red, Color.Red,
                        Color.Red, Color.Red, Color.Red,
                        Color.Red, Color.Red, Color.Red
                },
                {       /* Right */
                        Color.Blue, Color.Blue, Color.Yellow,
                        Color.Blue, Color.Blue, Color.Yellow,
                        Color.Blue, Color.Blue, Color.Yellow
                },
                {       /* Back */
                        Color.Orange, Color.Orange, Color.Orange,
                        Color.Orange, Color.Orange, Color.Orange,
                        Color.Orange, Color.Orange, Color.Orange
                },
                {       /* Bottom */
                        Color.Yellow, Color.Yellow, Color.Yellow,
                        Color.Yellow, Color.Yellow, Color.Yellow,
                        Color.Green, Color.Green, Color.Green
                }
        };

        assertThat(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.value == 4);
    }

    @Test
    public void testSimpleRotateBottom0() {
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(STANDARD_CUBE_SIZE,
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        try {
            cube.rotate(Side.Bottom.intValue(), 0);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }

        try {
            String str = cube.show();
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }

        Color[][] solvedArrangement =  {
                {       /* Top */
                        Color.White, Color.White, Color.White,
                        Color.White, Color.White, Color.White,
                        Color.White, Color.White, Color.White
                },
                {       /* Left */
                        Color.Green, Color.Green, Color.Green,
                        Color.Green, Color.Green, Color.Green,
                        Color.Orange, Color.Orange, Color.Orange
                },
                {       /* Front */
                        Color.Red, Color.Red, Color.Red,
                        Color.Red, Color.Red, Color.Red,
                        Color.Green, Color.Green, Color.Green
                },
                {       /* Right */
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Red, Color.Red, Color.Red
                },
                {       /* Back */
                        Color.Orange, Color.Orange, Color.Orange,
                        Color.Orange, Color.Orange, Color.Orange,
                        Color.Blue, Color.Blue, Color.Blue
                },
                {       /* Bottom */
                        Color.Yellow, Color.Yellow, Color.Yellow,
                        Color.Yellow, Color.Yellow, Color.Yellow,
                        Color.Yellow, Color.Yellow, Color.Yellow
                }
        };

        assertThat(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.value == 4);
    }

    @Test
    public void testTimeOfConcurrentVsSequentialTests() {
        Duration seq_time, conc_time;
        int seq_wins = 0, conc_wins = 0;

        seq_time = stopwatch.runWithStopwatch(this::testCyclesOfParallelRotationsSequential); //rekord: 1s631ms
        conc_time = stopwatch.runWithStopwatch(this::testCyclesOfParallelRotationsConcurrent);
        if (seq_time.toMillis() < conc_time.toMillis()) {
            seq_wins++;
        }
        else {
            conc_wins++;
        }

        seq_time = stopwatch.runWithStopwatch(this::testTwoAntagonistRotationsSequential);
        conc_time = stopwatch.runWithStopwatch(this::testTwoAntagonistRotationsConcurrent);
        if (seq_time.toMillis() < conc_time.toMillis()) {
            seq_wins++;
        }
        else {
            conc_wins++;
        }

        seq_time = stopwatch.runWithStopwatch(this::testManyAntagonistRotationsSequential);
        conc_time = stopwatch.runWithStopwatch(this::testManyAntagonistRotationsConcurrent);
        if (seq_time.toMillis() < conc_time.toMillis()) {
            seq_wins++;
        }
        else {
            conc_wins++;
        }

        assertThat(conc_wins >= seq_wins);
    }

    /** TODO: POMYSŁY
     * - sprawdzanie czy kolejka danych pisarzy jest pusta (czy ich nie zagłodzono)
     * - Uruchomić w kilku wątkach niezależne od siebie operacje i zobaczyć czy czas trwania jest wystarczająco krótki
     */

}
