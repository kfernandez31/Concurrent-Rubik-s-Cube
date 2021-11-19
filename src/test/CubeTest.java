package test;

import concurrentcube.Cube;
import concurrentcube.Rotations.*;
import concurrentcube.Side;
import concurrentcube.Color;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.*;

/**
 * Klasa korzysta z JUNit5
 */
public class CubeTest {
    /**
     * Tests that utilize cube solving algorithms are in standard cubing notation,
     * however instead of writing "R'", we write "r".
     */

    private static final int TEST_MAX_CUBE_SIZE = 10;
    private static final int STANDARD_CUBE_SIZE = 3;
    private static final int NUM_THREADS = 64;
    private static final Random rand = new Random();

    //TODO: wtrynić wszędzie show'y
    //TODO: sprawdzanie counterów - gwarantuje bezpieczeństwo
    //TODO: Wywalić simpleRotate'y
    //TODO: dać wszędzie randomowe rozmiary

    /* ------------------------ Helper functions and structures ------------------------ */

    private static void interruptCurrentThread() {
        Thread t = Thread.currentThread();
        t.interrupt();
        System.err.println(t.getName() + " interrupted");
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

    private static void permute(List<Rotation> perm, int k, List<List<Rotation>> acc) {
        for(int i = k; i < perm.size(); i++){
            java.util.Collections.swap(perm, i, k);
            permute(perm, k+1, acc);
            java.util.Collections.swap(perm, k, i);
        }
        if (k == perm.size() -1){
            acc.add(perm);
        }
    }

    private static List<List<Rotation>> generateInterleavings(List<Rotation> initialPermutation) {
        List<List<Rotation>> outcomes = new ArrayList<>();
        permute(initialPermutation, 0, outcomes);
        return outcomes;
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
    public void testRotationCompositionResult() {
        final int NUM_ROTATIONS = 3;

        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(3,
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

            List<List<Rotation>> interleavings = generateInterleavings(rotations);
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


    /* ----------- Safety tests ----------- */

    @Test
    public void testCyclesOfParallelRotations() {
        final int ROTATIONS_PER_TYPE = 4444444;
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(3,
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

    @Test
    public void testTwoAntagonistRotations() {
        final int ROTATIONS_PER_TYPE = 3;
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(rand.nextInt(TEST_MAX_CUBE_SIZE),
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
            pool.invokeAll(tasks); //TODO: Wiesza się w debugu

            assertThat(cube.isSolved());

        } catch (InterruptedException e) {
            interruptCurrentThread();
        } finally {
            pool.shutdown();
        }
    }

    @Test
    //TODO: wiesza się
    public void testAntagonistRotationsConcurrent() {
        final int ROTATIONS_PER_TYPE = 2;
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(3,
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
            assertThat(cube.isSolved() && counter.value == 2 * cube.getSize() * ROTATIONS_PER_TYPE);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        } finally {
            pool.shutdown();
        }
    }

    /* ----------- Liveness tests ----------- */

    @Test
    public void testTimeOfIndependentProcesses() {

    }

    @Test
    public void testSizeOfWaitingGroups() {

    }


    /* ------------------------ Sequential tests ------------------------ */

    @Test
    public void testManyAntagonistRotationsSequential() {
        final int ROTATIONS_PER_TYPE = 100000;
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(rand.nextInt(TEST_MAX_CUBE_SIZE) +  1,
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        try {
            for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
                Side side = Side.randomSide();
                int layer = rand.nextInt(cube.getSize());

                cube.rotate(side.intValue(), layer);
                cube.show();

                cube.rotate(side.opposite().intValue(), cube.getSize() - 1 - layer);
                cube.show();
            }

            assertThat(cube.isSolved() && counter.value == 2 * 4 * ROTATIONS_PER_TYPE);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    @Test
    public void testCycleOfRUruSequential() {
        final int ROTATIONS_PER_TYPE = 2;
        /* This algorithm has a cycle of length 24. */
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(rand.nextInt(TEST_MAX_CUBE_SIZE),
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);

        //opcja 1: runnable
/*        for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
            pool.execute(new RotationTask(cube, 3, 0)); // R
            pool.execute(new RotationTask(cube, 0, 0)); // U
            pool.execute(new RotationTask(cube, 1, cube.getSize() - 1)); // r
            pool.execute(new RotationTask(cube, 5, cube.getSize() - 1)); // u
        }*/

        //opcja 2: callable
        List<Callable<Object>> tasks = new ArrayList<>(4 * ROTATIONS_PER_TYPE);
        for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
            tasks.add(Executors.callable(new RotationTask(cube, 3, 0))); // R
            tasks.add(Executors.callable(new RotationTask(cube, 0, 0))); // R
            tasks.add(Executors.callable(new RotationTask(cube, 1, cube.getSize() - 1))); // r
            tasks.add(Executors.callable(new RotationTask(cube, 5, cube.getSize() - 1))); // u
        }

        try {
            pool.invokeAll(tasks); //TODO: Wiesza się w debugu

            assertThat(cube.isSolved());

        } catch (InterruptedException e) {
            interruptCurrentThread();
        } finally {
            pool.shutdown();
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

    /** TODO: POMYSŁY
     * - sprawdzenie wszystkich n! przeplotów n procesów
     * - algorytmy zacyklające się (PROBLEM: nie da się ich dać na wykonaniu wspólbieżnym
     * - sprawdzanie czy kolejka danych pisarzy jest pusta (czy ich nie zagłodzono)
     * - Uruchomić w kilku wątkach niezależne od siebie operacje i zobaczyć czy czas trwania jest wystarczająco krótki
     */

}
