package test;

import concurrentcube.Cube;
import concurrentcube.Side;
import concurrentcube.Color;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

import static org.junit.Assert.assertEquals;

/**
 * Klasa korzysta z JUNit5
 */
public class CubeTest {
    /**
     * Tests that utilize cube solving algorithms are in standard cubing notation,
     * however instead of writing "R'", we write "r".
     */

    private static final int TEST_MAX_CUBE_SIZE = 100;
    private static final int STANDARD_CUBE_SIZE = 3;
    private static final int NUM_THREADS = 64;
    private static final Random rand = new Random();

    //TODO: zdecydować się między Runnable a Callable
    //TODO: sprawdzanie counterów - gwarantuje bezpieczeństwo
    public static class RotationTask implements Runnable {
        private final Cube cube;
        private final int side;
        private final int layer;

        public RotationTask(Cube cube, int side, int layer) {
            this.cube = cube;
            this.side = side;
            this.layer = layer;
        }

        @Override
        public void run() {
            try {
                cube.rotate(side, layer);
            } catch (InterruptedException e) {
                interruptCurrentThread();
            }
        }

        public Callable<Void> toCallable() {
            return () -> {
                this.run();
                return null;
            };
        }
    }

    public static class RotationCallable implements Callable<Void> {
        private final Cube cube;
        private final int side;
        private final int layer;

        public RotationCallable(Cube cube, int side, int layer) {
            this.cube = cube;
            this.side = side;
            this.layer = layer;
        }

        @Override
        public Void call() {
            try {
                cube.rotate(side, layer);
            } catch (InterruptedException e) {
                interruptCurrentThread();
            }
            return null;
        }
    }


    private static void interruptCurrentThread() {
        Thread t = Thread.currentThread();
        t.interrupt();
        System.err.println(t.getName() + " interrupted");
    }


/*
    Color[][] solved6x3x3Arrangement =  {
            {       // Top
                    Color.White, Color.White, Color.White,
                    Color.White, Color.White, Color.White,
                    Color.White, Color.White, Color.White
            },
            {       // Left
                    Color.Green, Color.Green, Color.Green,
                    Color.Green, Color.Green, Color.Green,
                    Color.Green, Color.Green, Color.Green
            },
            {       // Front
                    Color.Red, Color.Red, Color.Red,
                    Color.Red, Color.Red, Color.Red,
                    Color.Red, Color.Red, Color.Red
            },
            {       // Right
                    Color.Blue, Color.Blue, Color.Blue,
                    Color.Blue, Color.Blue, Color.Blue,
                    Color.Blue, Color.Blue, Color.Blue
            },
            {       // Back
                    Color.Orange, Color.Orange, Color.Orange,
                    Color.Orange, Color.Orange, Color.Orange,
                    Color.Orange, Color.Orange, Color.Orange
            },
            {       // Bottom
                    Color.Yellow, Color.Yellow, Color.Yellow,
                    Color.Yellow, Color.Yellow, Color.Yellow,
                    Color.Yellow, Color.Yellow, Color.Yellow
            }
    };
*/

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
        assert(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.value == 4);
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

        assert(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.value == 4);
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

        assert(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.value == 4);
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
                        Color.Orange, Color.Orange, Color.White,
                        Color.Orange, Color.Orange, Color.White,
                        Color.Orange, Color.Orange, Color.White
                },
                {       /* Bottom */
                        Color.Yellow, Color.Yellow, Color.Orange,
                        Color.Yellow, Color.Yellow, Color.Orange,
                        Color.Yellow, Color.Yellow, Color.Orange
                }
        };

        assert(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.value == 4);
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

        assert(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.value == 4);
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

        assert(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.value == 4);
    }

    @Test
    public void testCycleOfOneRotation() {
        /* This tests a cycle of 4. */
        //0mod4 watkow w jedna
    }

    @Test
    public void testTwoAntagonistRotations() {
        final int ROTATIONS_PER_TYPE = 2;
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(rand.nextInt(TEST_MAX_CUBE_SIZE),
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);

        Side side = Side.randomSide();

        List<Callable<Void>> tasks = new ArrayList<>(2 * ROTATIONS_PER_TYPE);
        for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {

            tasks.add(new RotationTask(cube, side.intValue(), 0).toCallable());
            tasks.add(new RotationTask(cube, side.opposite().intValue(), cube.getSize() - 1).toCallable());
        }

        try {
            ArrayList<Future<Void>> results = (ArrayList<Future<Void>>) pool.invokeAll(tasks); //TODO: Wiesza się w debugu

            assert(cube.isSolved());

        } catch (InterruptedException e) {
            interruptCurrentThread();
        } finally {
            pool.shutdown();
        }
    }

    @Test
    public void testManyAntagonistRotations() {
        final int ROTATIONS_PER_TYPE = 2;
        var counter = new Object() { int value = 0; };
        Cube cube = new Cube(rand.nextInt(TEST_MAX_CUBE_SIZE),
                (x, y) -> { ++counter.value; },
                (x, y) -> { ++counter.value; },
                () -> { ++counter.value; },
                () -> { ++counter.value; });

        ExecutorService pool = Executors.newFixedThreadPool(NUM_THREADS);

        List<Callable<Void>> tasks = new ArrayList<>(2 * ROTATIONS_PER_TYPE);
        for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
            Side side = Side.randomSide();

            tasks.add(new RotationTask(cube, side.intValue(), 0).toCallable());
            tasks.add(new RotationTask(cube, side.opposite().intValue(), cube.getSize() - 1).toCallable());
        }

        try {
            ArrayList<Future<Void>> results = (ArrayList<Future<Void>>) pool.invokeAll(tasks); //TODO: Wiesza się w debugu

            assert(cube.isSolved());

        } catch (InterruptedException e) {
            interruptCurrentThread();
        } finally {
            pool.shutdown();
        }
    }

    @Test
    public void testCycleOfRUru() {
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
        List<Callable<Void>> tasks = new ArrayList<>(4 * ROTATIONS_PER_TYPE);
        for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
            tasks.add(new RotationTask(cube, 3, 0).toCallable()); // R
            tasks.add(new RotationTask(cube, 0, 0).toCallable()); // R
            tasks.add(new RotationTask(cube, 1, cube.getSize() - 1).toCallable()); // r
            tasks.add(new RotationTask(cube, 5, cube.getSize() - 1).toCallable()); // u
        }

        try {
            ArrayList<Future<Void>> results = (ArrayList<Future<Void>>) pool.invokeAll(tasks); //TODO: Wiesza się w debugu

            assert(cube.isSolved());

        } catch (InterruptedException e) {
            interruptCurrentThread();
        } finally {
            pool.shutdown();
        }
    }

    /** TODO: POMYSŁY
     * - są tez algorytmy które jak wykonasz np. 16 razy to wracają do stanu początkowego i składają sie z 3 ruchów
     * - Uruchomić w kilku wątkach niezależne od siebie operacje i zobaczyć czy czas trwania jest wystarczająco krótki
     */

}
