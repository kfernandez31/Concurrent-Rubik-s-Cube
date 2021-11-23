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
import java.util.concurrent.atomic.AtomicInteger;

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
    private static final int MAX_THREADS = 64;
    private static final Random rand = new Random();
    private static final Stopwatch stopwatch = new Stopwatch();

    //TODO: wtrynić wszędzie show'y
    //TODO: ujednolicić rozmiary

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

    private static boolean interruptWithProbability(Thread t, double prob) {
        if (rand.nextDouble() <= prob) {
            t.interrupt();
            return true;
        }
        return false;
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

    private static <T> List<List<T>> generateInterleavings(List<T> initialPermutation) {
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

    private static class WriterTask implements Runnable {
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
                sleep(100);
                cube.rotate(side, layer);
            } catch (InterruptedException e) {
                interruptCurrentThread();
            }
        }
    }

    private static class ReaderTask implements Runnable {
        private final Cube cube;

        public ReaderTask(Cube cube) {
            this.cube = cube;
        }

        @Override
        public void run() {
            try {
                sleep(100);
                cube.show();
            } catch (InterruptedException e) {
                interruptCurrentThread();
            }
        }
    }

    /* ------------------------ Concurrent tests ------------------------ */

    /**
     * Tests whether a composition of rotations produced by multi-threading is legal.
     */
    @Test
    public void testRotationCompositionResultConcurrent() {
        final int NUM_ROTATIONS = 8; //I advise you not to go higher than 9, since time will grow in O(n*n!)
        AtomicInteger counter = new AtomicInteger(0);
        Cube cube = new Cube(10,
                (x, y) -> { counter.incrementAndGet(); },
                (x, y) -> { counter.incrementAndGet(); },
                counter::incrementAndGet,
                counter::incrementAndGet
        );


        List<Rotation> rotations = new ArrayList<>();
        for (int i = 0; i < NUM_ROTATIONS; i++) {
            rotations.add(Rotation.newRotation(cube, Side.randomSide(), rand.nextInt(cube.getSize())));
        }

        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);
        List<Callable<Object>> tasks = new ArrayList<>(rotations.size());

        for (Rotation r : rotations) {
            tasks.add(Executors.callable(WriterTask.fromRotation(r)));
        }

        try {
            pool.invokeAll(tasks);
            Color[][] res = cube.getCopyOfSquares();

            List<List<Rotation>> interleavings = generateInterleavings(rotations);
            List<Color[][]> outcomes = new ArrayList<>();
            for (List<Rotation> seq : interleavings) {
                cube.applySequenceOfRotations(seq);
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
     * Tests how multiple readers interact.
     */
    @Test
    public void testManyReadersConcurrent() {
        AtomicInteger readersCounter = new AtomicInteger(0);
        Cube cube = new Cube(3,
                null, null,
                readersCounter::incrementAndGet,
                readersCounter::incrementAndGet
        );

        final int NUM_READERS = 100000;

        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);
        List<Callable<Object>> tasks = new ArrayList<>(NUM_READERS);

        for (int i = 0; i < NUM_READERS; i++) {
            tasks.add(Executors.callable(
                    new ReaderTask(cube)));
        }

        try {
            pool.invokeAll(tasks);
            assertThat(cube.isLegal() && readersCounter.intValue() == 2 * NUM_READERS);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Tests whether readers and writers don't collide.
     */
    @Test
    public void testSyncOfReadersAndWriters() {
        AtomicInteger readersCounter = new AtomicInteger(0);
        AtomicInteger writersCounter = new AtomicInteger(0);
        Cube cube = new Cube(3,
                (x, y) -> { writersCounter.incrementAndGet(); },
                (x, y) -> { writersCounter.incrementAndGet(); },
                readersCounter::incrementAndGet,
                readersCounter::incrementAndGet
        );

        final int NUM_WRITERS = 10000;
        final int NUM_READERS = 10000;

        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);
        List<Callable<Object>> tasks = new ArrayList<>(NUM_WRITERS + NUM_READERS);

        for (int i = 0; i < NUM_WRITERS; i++) {
            tasks.add(Executors.callable(
                    new WriterTask(cube, Side.randomSide().intValue(), rand.nextInt(cube.getSize()))));
        }
        for (int i = 0; i < NUM_READERS; i++) {
            tasks.add(Executors.callable(
                    new ReaderTask(cube)));
        }

        try {
            pool.invokeAll(tasks);
            assertThat(cube.isLegal() &&
                    readersCounter.intValue() == 2 * NUM_READERS && writersCounter.intValue() == 2 * NUM_WRITERS);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Tests how interrupted readers affect the entire synchronisation mechanism.
     */
    @Test
    public void testEffectsOfReaderInterruptions() {
        AtomicInteger readersCounter = new AtomicInteger(0);
        AtomicInteger writersCounter = new AtomicInteger(0);
        int interruptions = 0;

        Cube cube = new Cube(3,
                (x, y) -> { writersCounter.incrementAndGet(); },
                (x, y) -> { writersCounter.incrementAndGet(); },
                readersCounter::incrementAndGet,
                readersCounter::incrementAndGet
        );

        final int NUM_THREADS = 10000;
        Thread[] threads = new Thread[NUM_THREADS];

        /* Even indices - readers, odd - writers */
        for (int i = 1; i <= NUM_THREADS / 2; i++) {
            threads[2 * i - 2] = new Thread(new ReaderTask(cube));
            threads[2 * i - 1] = new Thread(WriterTask.fromRotation(Rotation.randomRotation(cube)));
        }

        for (int i = 0; i < NUM_THREADS; i++) {
            Thread t = threads[i];
            t.start();
            if (i % 2 == 0) {
                interruptWithProbability(t, 0.5);
            }
        }

        try {
            for (Thread t : threads) {
                t.join();
            }
            assert(cube.isLegal() &&
                    readersCounter.intValue() >= NUM_THREADS - interruptions &&
                    writersCounter.intValue() == NUM_THREADS);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    /**
     * Tests how interrupted writers affect the entire synchronisation mechanism.
     */
    @Test
    public void testEffectsOfWriterInterruptions() {
        AtomicInteger readersCounter = new AtomicInteger(0);
        AtomicInteger writersCounter = new AtomicInteger(0);
        int interruptions = 0;

        Cube cube = new Cube(3,
                (x, y) -> { writersCounter.incrementAndGet(); },
                (x, y) -> { writersCounter.incrementAndGet(); },
                readersCounter::incrementAndGet,
                readersCounter::incrementAndGet
        );

        final int NUM_THREADS = 10000;
        Thread[] threads = new Thread[NUM_THREADS];

        /* Even indices - readers, odd - writers */
        for (int i = 1; i <= NUM_THREADS / 2; i++) {
            threads[2 * i - 2] = new Thread(new ReaderTask(cube));
            threads[2 * i - 1] = new Thread(WriterTask.fromRotation(Rotation.randomRotation(cube)));
        }

        for (int i = 0; i < NUM_THREADS; i++) {
            Thread t = threads[i];
            t.start();
            if (i % 2 == 1) {
                interruptWithProbability(t, 0.5);
            }
        }

        try {
            for (Thread t : threads) {
                t.join();
            }
            assert(cube.isLegal() &&
                    readersCounter.intValue() >= NUM_THREADS - interruptions &&
                    writersCounter.intValue() == NUM_THREADS);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    /**
     * Tests how interrupted readers and writers affect the entire synchronisation mechanism.
     */
    @Test
    public void testEffectsOfReaderAndWriterInterruptions() {
        AtomicInteger readersCounter = new AtomicInteger(0);
        AtomicInteger writersCounter = new AtomicInteger(0);
        int readerInterruptions = 0, writerInterruptions = 0;

        Cube cube = new Cube(3,
                (x, y) -> { writersCounter.incrementAndGet(); },
                (x, y) -> { writersCounter.incrementAndGet(); },
                readersCounter::incrementAndGet,
                readersCounter::incrementAndGet
        );

        final int NUM_THREADS = 10000;
        Thread[] threads = new Thread[NUM_THREADS];

        /* Even indices - readers, odd - writers */
        for (int i = 1; i <= NUM_THREADS / 2; i++) {
            threads[2 * i - 2] = new Thread(new ReaderTask(cube));
            threads[2 * i - 1] = new Thread(WriterTask.fromRotation(Rotation.randomRotation(cube)));
        }

        for (int i = 0; i < NUM_THREADS; i++) {
            Thread t = threads[i];
            boolean interrupted = interruptWithProbability(t, 0.5);

            if (interrupted) {
                if (i % 2 == 0) {
                    readerInterruptions++;
                }
                else {
                    writerInterruptions++;
                }
            }
        }

        try {
            for (Thread t : threads) {
                t.join();
            }
            assert(cube.isLegal() &&
                    readersCounter.intValue() >= NUM_THREADS - readerInterruptions &&
                    writersCounter.intValue() >= NUM_THREADS - writerInterruptions);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }


    /**
     * Tests how the synchronization algorithm handles random rotations.
     */
    @Test
    public void testManyRandomRotationsConcurrent() {
        AtomicInteger counter = new AtomicInteger(0);
        Cube cube = new Cube(3,
                (x, y) -> { counter.incrementAndGet(); },
                (x, y) -> { counter.incrementAndGet(); },
                counter::incrementAndGet,
                counter::incrementAndGet
        );

        final int NUM_ROTATIONS = 20000;

        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);
        List<Callable<Object>> tasks = new ArrayList<>(NUM_ROTATIONS);

        for (int i = 0; i < NUM_ROTATIONS; i++) {
            tasks.add(Executors.callable(WriterTask.fromRotation(Rotation.randomRotation(cube))));
        }

        try {
            pool.invokeAll(tasks);
            assertThat(cube.isLegal() && counter.intValue() == 2 * NUM_ROTATIONS);
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
        AtomicInteger times_first_thread_started_rotating = new AtomicInteger(0);
        AtomicInteger times_someone_else_started_rotating = new AtomicInteger(0);

        Cube cube = new Cube(3,
                (side, __) -> {
                    if (side == Side.Top.intValue()) {
                        times_first_thread_started_rotating.incrementAndGet();
                        sleep(200); // give time for other threads to try and pass
                        assertThat(times_someone_else_started_rotating.intValue() == 0);
                    }
                    else {
                        times_someone_else_started_rotating.incrementAndGet();
                    }
                },
                null, null, null);

        WriterTask politeRunnable = new WriterTask(cube, Side.Top.intValue(), 0);
        Thread firstThread = new Thread(politeRunnable);

        Thread[] otherThreads = new Thread[] {
                new Thread(new WriterTask(cube,  Side.Left.intValue(), 0)),
                new Thread(new WriterTask(cube,  Side.Front.intValue(), 0)),
                new Thread(new WriterTask(cube,  Side.Right.intValue(), 0)),
                new Thread(new WriterTask(cube,  Side.Back.intValue(), 0)),
                new Thread(new WriterTask(cube,  Side.Bottom.intValue(), 2)),
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
            assertThat(times_first_thread_started_rotating.intValue() == 1);
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
        AtomicInteger counter = new AtomicInteger(0);
        Cube cube = new Cube(10,
                (x, y) -> { counter.incrementAndGet(); },
                (x, y) -> { counter.incrementAndGet(); },
                counter::incrementAndGet,
                counter::incrementAndGet
        );

        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);
        List<Callable<Object>> tasks = new ArrayList<>(2 * ROTATIONS_PER_TYPE);

        Side side = Side.randomSide();

        for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
            for (int layer = 0; layer < cube.getSize(); layer++) {
                tasks.add(Executors.callable(new WriterTask(cube, side.intValue(), layer)));
            }
        }

        try {
            pool.invokeAll(tasks);
            assertThat(cube.isSolved() && counter.intValue() == 2 * cube.getSize() * ROTATIONS_PER_TYPE);
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
        AtomicInteger counter = new AtomicInteger(0);
        Cube cube = new Cube(5,
                (x, y) -> { counter.incrementAndGet(); },
                (x, y) -> { counter.incrementAndGet(); },
                counter::incrementAndGet,
                counter::incrementAndGet
        );

        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);

        Side side = Side.randomSide();
        int layer = rand.nextInt(cube.getSize());

        List<Callable<Object>> tasks = new ArrayList<>(2 * ROTATIONS_PER_TYPE);
        for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
            tasks.add(Executors.callable(new WriterTask(cube, side.intValue(), layer)));
            tasks.add(Executors.callable(new WriterTask(cube, side.opposite().intValue(), cube.getSize() - 1 - layer)));
        }
        try {
            pool.invokeAll(tasks);

            assertThat(cube.isSolved() && counter.intValue() == 4 * ROTATIONS_PER_TYPE);

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
        final int NUM_PAIRS = 1000;
        AtomicInteger counter = new AtomicInteger(0);
        Cube cube = new Cube(3,
                (x, y) -> { counter.incrementAndGet(); },
                (x, y) -> { counter.incrementAndGet(); },
                counter::incrementAndGet,
                counter::incrementAndGet
        );

        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);
        List<Callable<Object>> tasks = new ArrayList<>(2 * NUM_PAIRS);

        Side side = Side.randomSide();
        for (int i = 0; i < NUM_PAIRS; i++) {
            for (int layer = 0; layer < cube.getSize(); layer++) {
                tasks.add(Executors.callable(new WriterTask(cube, side.intValue(), layer)));
                tasks.add(Executors.callable(new WriterTask(cube, side.opposite().intValue(), cube.getSize() - 1 - layer)));
            }
        }

        try {
            pool.invokeAll(tasks);
            assertThat(cube.isSolved() && counter.intValue() == 4 * cube.getSize() * NUM_PAIRS);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        } finally {
            pool.shutdown();
        }
    }

    /* ------------------------ Sequential tests ------------------------ */

    @Test
    public void testCyclesOfParallelRotationsSequential() {
        final int ROTATIONS_PER_TYPE = 4444;
        AtomicInteger counter = new AtomicInteger(0);
        Cube cube = new Cube(3,
                (x, y) -> { counter.incrementAndGet(); },
                (x, y) -> { counter.incrementAndGet(); },
                counter::incrementAndGet,
                counter::incrementAndGet
        );

        Side side = Side.randomSide();

        try {
            for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
                for (int layer = 0; layer < cube.getSize(); layer++) {
                    cube.rotate(side.intValue(), layer);
                }
            }
            assertThat(cube.isSolved() && counter.intValue() == 2 * cube.getSize() * ROTATIONS_PER_TYPE);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    @Test
    public void testTwoAntagonistRotationsSequential() {
        final int ROTATIONS_PER_TYPE = 1000;
        AtomicInteger counter = new AtomicInteger(0);
        Cube cube = new Cube(3,
                (x, y) -> { counter.incrementAndGet(); },
                (x, y) -> { counter.incrementAndGet(); },
                counter::incrementAndGet,
                counter::incrementAndGet
        );

        Side side = Side.randomSide();
        int layer = rand.nextInt(cube.getSize());

        try {
            for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
                cube.rotate(side.intValue(), layer);
                cube.rotate(side.opposite().intValue(), cube.getSize() - 1 - layer);
            }
            assertThat(cube.isSolved() && counter.intValue() == 4 * ROTATIONS_PER_TYPE);

        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    @Test
    public void testManyAntagonistRotationsSequential() {
        final int ROTATIONS_PER_TYPE = 1000;
        AtomicInteger counter = new AtomicInteger(0);
        Cube cube = new Cube(3,
                (x, y) -> { counter.incrementAndGet(); },
                (x, y) -> { counter.incrementAndGet(); },
                counter::incrementAndGet,
                counter::incrementAndGet
        );

        try {
            Side side = Side.randomSide();
            for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
                for (int layer = 0; layer < cube.getSize(); layer++) {
                    cube.rotate(side.intValue(), layer);
                    cube.rotate(side.opposite().intValue(), cube.getSize() - 1 - layer);
                }
            }
            assertThat(cube.isSolved() && counter.intValue() == 4 * cube.getSize() * ROTATIONS_PER_TYPE);
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
        AtomicInteger counter = new AtomicInteger(0);
        Cube cube = new Cube(3,
                (x, y) -> { counter.incrementAndGet(); },
                (x, y) -> { counter.incrementAndGet(); },
                counter::incrementAndGet,
                counter::incrementAndGet
        );

        try {
            for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
                cube.rotate(Side.Right.intValue(), 0);
                cube.rotate(Side.Top.intValue(),0);
                cube.rotate(Side.Right.opposite().intValue(),cube.getSize() - 1);
                cube.rotate(Side.Top.opposite().intValue(),cube.getSize() - 1);
            }

            assertThat(cube.isSolved() && counter.intValue() == 2 * 4 * ROTATIONS_PER_TYPE);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    @Test
    public void testManyRandomRotationsSequential() {
        AtomicInteger counter = new AtomicInteger(0);
        Cube cube = new Cube(3,
                (x, y) -> { counter.incrementAndGet(); },
                (x, y) -> { counter.incrementAndGet(); },
                counter::incrementAndGet,
                counter::incrementAndGet
        );

        final int NUM_ROTATIONS = 20000;

        try {
            for (int i = 0; i < NUM_ROTATIONS; i++) {
                cube.rotate(Side.randomSide().intValue(), rand.nextInt(cube.getSize()));
            }

            assertThat(cube.isLegal() && counter.intValue() == 2 * NUM_ROTATIONS);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    @Test
    public void testSimpleRotateTop0() {
        AtomicInteger counter = new AtomicInteger(0);
        Cube cube = new Cube(3,
                (x, y) -> { counter.incrementAndGet(); },
                (x, y) -> { counter.incrementAndGet(); },
                counter::incrementAndGet,
                counter::incrementAndGet
        );

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

        final Color[][] solvedArrangement =  {
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
        assertThat(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.intValue() == 4);
    }

    @Test
    public void testSimpleRotateLeft0() {
        AtomicInteger counter = new AtomicInteger(0);
        Cube cube = new Cube(3,
                (x, y) -> { counter.incrementAndGet(); },
                (x, y) -> { counter.incrementAndGet(); },
                counter::incrementAndGet,
                counter::incrementAndGet
        );

        try {
            cube.rotate(Side.Left.intValue(), 0);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }

        try {
            String str = cube.show();
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }

        final Color[][] solvedArrangement =  {
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

        assertThat(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.intValue() == 4);
    }

    @Test
    public void testSimpleRotateFront0() {
        AtomicInteger counter = new AtomicInteger(0);
        Cube cube = new Cube(3,
                (x, y) -> { counter.incrementAndGet(); },
                (x, y) -> { counter.incrementAndGet(); },
                counter::incrementAndGet,
                counter::incrementAndGet
        );

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

        final Color[][] solvedArrangement =  {
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

        assertThat(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.intValue() == 4);
    }

    @Test
    public void testSimpleRotateRight0() {
        AtomicInteger counter = new AtomicInteger(0);
        Cube cube = new Cube(3,
                (x, y) -> { counter.incrementAndGet(); },
                (x, y) -> { counter.incrementAndGet(); },
                counter::incrementAndGet,
                counter::incrementAndGet
        );

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

        final Color[][] solvedArrangement =  {
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

        assertThat(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.intValue() == 4);
    }

    @Test
    public void testSimpleRotateBack0() {
        AtomicInteger counter = new AtomicInteger(0);
        Cube cube = new Cube(3,
                (x, y) -> { counter.incrementAndGet(); },
                (x, y) -> { counter.incrementAndGet(); },
                counter::incrementAndGet,
                counter::incrementAndGet
        );

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

        final Color[][] solvedArrangement =  {
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

        assertThat(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.intValue() == 4);
    }

    @Test
    public void testSimpleRotateBottom0() {
        AtomicInteger counter = new AtomicInteger(0);
        Cube cube = new Cube(3,
                (x, y) -> { counter.incrementAndGet(); },
                (x, y) -> { counter.incrementAndGet(); },
                counter::incrementAndGet,
                counter::incrementAndGet
        );

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

        final Color[][] solvedArrangement =  {
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

        assertThat(Arrays.deepEquals(solvedArrangement, cube.getSquares()) && counter.intValue() == 4);
    }

    @Test
    public void testTimeOfConcurrentVsSequentialTests() {
        Duration seq_time, conc_time;
        int seq_wins = 0, conc_wins = 0;

        seq_time = stopwatch.runWithStopwatch(this::testCyclesOfParallelRotationsSequential);
        conc_time = stopwatch.runWithStopwatch(this::testCyclesOfParallelRotationsConcurrent);
        if (seq_time.toMillis() < conc_time.toMillis()) seq_wins++;
        else conc_wins++;

        seq_time = stopwatch.runWithStopwatch(this::testTwoAntagonistRotationsSequential);
        conc_time = stopwatch.runWithStopwatch(this::testTwoAntagonistRotationsConcurrent);
        if (seq_time.toMillis() < conc_time.toMillis()) seq_wins++;
        else conc_wins++;

        seq_time = stopwatch.runWithStopwatch(this::testManyAntagonistRotationsSequential);
        conc_time = stopwatch.runWithStopwatch(this::testManyAntagonistRotationsConcurrent);
        if (seq_time.toMillis() < conc_time.toMillis()) seq_wins++;
        else conc_wins++;

        seq_time = stopwatch.runWithStopwatch(this::testManyRandomRotationsSequential);
        conc_time = stopwatch.runWithStopwatch(this::testManyRandomRotationsConcurrent);
        if (seq_time.toMillis() < conc_time.toMillis()) seq_wins++;
        else conc_wins++;

        assertThat(conc_wins >= seq_wins);
    }

}
