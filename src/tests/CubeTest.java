package tests;

import org.junit.Test;

import concurrentcube.WorkingGroup;
import concurrentcube.Cube;
import concurrentcube.Rotations.*;
import concurrentcube.Side;
import concurrentcube.Color;

import static test.Utils.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/* This class uses JUnit 5 */
public class CubeTest {
    /**
     * Tests that utilize cube solving algorithms are in standard cubing notation,
     * however anti-clockwise turns are written in lowercase instead of the traditional apostrophe (ex. R' = r).
     */

    private static final int STANDARD_CUBE_SIZE = 3;
    private static final int TEST_CUBE_SIZE = 10;
    private static final int MAX_THREADS = 64;

    private static final Stopwatch stopwatch = new Stopwatch();

    private static final Semaphore varProtection = new Semaphore(1);
    private static final int[] rotationsOnAxis = new int[WorkingGroup.NUM_AXES.intValue()];
    private static int activeReaders = 0;
    private static int activeWriters = 0;
    private static int performedRotations = 0;
    private static int performedShows = 0;
    private static int[] rotationsOnPlane;

    /* ------------------------ Helper functions ------------------------ */

    private static void resetSyncVars(int size) {
        varProtection.drainPermits();
        varProtection.release(1);

        activeReaders = 0;
        activeWriters = 0;
        performedRotations = 0;
        performedShows = 0;

        for (int i = 0; i < WorkingGroup.NUM_AXES.intValue(); i++) {
            rotationsOnAxis[i] = 0;
        }
        rotationsOnPlane = new int[size];
    }

    private static BiConsumer<Integer, Integer> defaultBeforeRotation(int size, int msDelay) {
        return (side, layer) -> {
            try {
                varProtection.acquire();
                WorkingGroup axis = WorkingGroup.fromSide(Side.fromInt(side));
                int plane = Rotation.getPlane(size, side, layer);
                activeWriters++;
                rotationsOnAxis[axis.intValue()]++;
                rotationsOnPlane[plane]++;

                assertThat(activeReaders == 0 && rotationsOnPlane[plane] == 1 &&
                        rotationsOnAxis[(axis.intValue() + 1) % WorkingGroup.NUM_AXES.intValue()] == 0 &&
                        rotationsOnAxis[(axis.intValue() + 2) % WorkingGroup.NUM_AXES.intValue()] == 0
                );
                varProtection.release();
                sleep(msDelay);
            } catch (InterruptedException e) {
                logWithThreadName("interrupted during beforeRotation");
            }
        };
    }

    private static BiConsumer<Integer, Integer> defaultAfterRotation(int size, int msDelay) {
        return (side, layer) -> {
            try {
                varProtection.acquire();
                WorkingGroup axis = WorkingGroup.fromSide(Side.fromInt(side));
                int plane = Rotation.getPlane(size, side, layer);
                activeWriters--;
                rotationsOnAxis[axis.intValue()]--;
                rotationsOnPlane[plane]--;
                performedRotations++;

                assertThat(activeReaders == 0 && rotationsOnPlane[plane] == 0 &&
                        rotationsOnAxis[(axis.intValue() + 1) % WorkingGroup.NUM_AXES.intValue()] == 0 &&
                        rotationsOnAxis[(axis.intValue() + 2) % WorkingGroup.NUM_AXES.intValue()] == 0
                );
                varProtection.release();
                sleep(msDelay);
            } catch (InterruptedException e) {
                logWithThreadName("interrupted during afterRotation");
            }
        };
    }

    private static Runnable defaultBeforeShowing(int msDelay) {
        return () -> {
            try {
                varProtection.acquire();
                activeReaders++;
                assertThat(activeWriters == 0);
                varProtection.release();
                sleep(msDelay);
            } catch (InterruptedException e) {
                logWithThreadName("interrupted during beforeShowing");
            }
        };
    }

    private static Runnable defaultAfterShowing(int msDelay) {
        return () -> {
            try {
                varProtection.acquire();
                activeReaders--;
                performedShows++;
                assertThat(activeWriters == 0);
                varProtection.release();
                sleep(msDelay);
            } catch (InterruptedException e) {
                logWithThreadName("interrupted during afterShowing");
            }
        };
    }

    /* ------------------------ Concurrent tests ------------------------ */

    /**
     * Tests whether a composition of rotations produced by multi-threading is legal.
     */
    @Test
    public void testRotationCompositionResultConcurrent() {
        int size = 25;
        resetSyncVars(size);
        Cube cube = new Cube(size,
                defaultBeforeRotation(size, 0),
                defaultAfterRotation(size, 0),
                defaultBeforeShowing(0),
                defaultAfterShowing(0)
        );

        final int NUM_ROTATIONS = 7; //Don't put big numbers here as the time complexity will be O(n*n!) !!!

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

            List<List<Rotation>> interleavings = generatePermutations(rotations);
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
    public void testSyncManyReadersConcurrent() {
        int size = 25;
        resetSyncVars(size);
        Cube cube = new Cube(size,
                defaultBeforeRotation(size, 0),
                defaultAfterRotation(size, 0),
                defaultBeforeShowing(0),
                defaultAfterShowing(0)
        );

        final int NUM_READERS = 10000;

        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);
        List<Callable<Object>> tasks = new ArrayList<>(NUM_READERS);

        for (int i = 0; i < NUM_READERS; i++) {
            tasks.add(Executors.callable(new ReaderTask(cube)));
        }

        try {
            pool.invokeAll(tasks);
            assertThat(cube.isLegal() && performedShows == NUM_READERS);
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
        int size = 10;
        resetSyncVars(size);
        Cube cube = new Cube(size,
                defaultBeforeRotation(size, 0),
                defaultAfterRotation(size, 0),
                defaultBeforeShowing(0),
                defaultAfterShowing(0)
        );

        final int NUM_WRITERS = 100000;
        final int NUM_READERS = 100000;

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
            assertThat(cube.isLegal() && performedRotations == NUM_READERS && performedShows == NUM_WRITERS);
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
        int size = 10;
        resetSyncVars(size);
        Cube cube = new Cube(size,
                defaultBeforeRotation(size, 0),
                defaultAfterRotation(size, 0),
                defaultBeforeShowing(0),
                defaultAfterShowing(0)
        );

        final int NUM_THREADS = 100000;
        Thread[] threads = new Thread[NUM_THREADS];

        //Even indices - readers, odd - writers
        for (int i = 1; i <= NUM_THREADS / 2; i++) {
            threads[2 * i - 2] = new Thread(new ReaderTask(cube));
            threads[2 * i - 1] = new Thread(WriterTask.fromRotation(Rotation.randomRotation(cube)));
        }

        Timer timer = new Timer();

        for (int i = 0; i < NUM_THREADS; i++) {
            if (i % 2 == 0) {
                Thread t = threads[i];
                t.start();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        interruptWithProbability(t, 0.5);
                    }
                }, 10);
            }
        }

        try {
            for (Thread t : threads) {
                t.join();
            }
            assert(cube.isLegal());
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    /**
     * Tests how interrupted writers affect the entire synchronisation mechanism.
     */
    @Test
    public void testEffectsOfWriterInterruptions() {
        int size = 10;
        resetSyncVars(size);
        Cube cube = new Cube(size,
                defaultBeforeRotation(size, 0),
                defaultAfterRotation(size, 0),
                defaultBeforeShowing(0),
                defaultAfterShowing(0)
        );

        final int NUM_THREADS = 5000;
        Thread[] threads = new Thread[NUM_THREADS];

        //Even indices - readers, odd - writers
        for (int i = 1; i <= NUM_THREADS / 2; i++) {
            threads[2 * i - 2] = new Thread(new ReaderTask(cube));
            threads[2 * i - 1] = new Thread(WriterTask.fromRotation(Rotation.randomRotation(cube)));
        }

        Timer timer = new Timer();

        for (int i = 0; i < NUM_THREADS; i++) {
            if (i % 2 == 1) {
                Thread t = threads[i];
                t.start();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        interruptWithProbability(t, 0.5);
                    }
                }, 10);
            }
        }

        try {
            for (Thread t : threads) {
                t.join();
            }
            assert(cube.isLegal());
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    /**
     * Tests how interrupted readers and writers affect the entire synchronisation mechanism.
     */
    @Test
    public void testEffectsOfReaderAndWriterInterruptions() {
        int size = 10;
        resetSyncVars(size);
        Cube cube = new Cube(size,
                defaultBeforeRotation(size, 0),
                defaultAfterRotation(size, 0),
                defaultBeforeShowing(0),
                defaultAfterShowing(0)
        );

        final int NUM_THREADS = 20000;
        Thread[] threads = new Thread[NUM_THREADS];

        //Even indices - readers, odd - writers
        for (int i = 1; i <= NUM_THREADS / 2; i++) {
            threads[2 * i - 2] = new Thread(new ReaderTask(cube));
            threads[2 * i - 1] = new Thread(WriterTask.fromRotation(Rotation.randomRotation(cube)));
        }

        Timer timer = new Timer();

        for (int i = 0; i < NUM_THREADS; i++) {
            Thread t = threads[i];
            t.start();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    interruptWithProbability(t, 0.5);

                }
            }, 10);
        }

        try {
            for (Thread t : threads) {
                t.join();
            }
            assert(cube.isLegal());
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    /**
     * Tests how the synchronization algorithm handles random rotations.
     */
    @Test
    public void testManyRandomRotationsConcurrent() {
        int size = 10;
        resetSyncVars(size);
        Cube cube = new Cube(size,
                defaultBeforeRotation(size, 0),
                defaultAfterRotation(size, 0),
                defaultBeforeShowing(0),
                defaultAfterShowing(0)
        );

        final int NUM_ROTATIONS = 200000;

        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);
        List<Callable<Object>> tasks = new ArrayList<>(NUM_ROTATIONS);

        for (int i = 0; i < NUM_ROTATIONS; i++) {
            tasks.add(Executors.callable(WriterTask.fromRotation(Rotation.randomRotation(cube))));
        }

        try {
            pool.invokeAll(tasks);
            assertThat(cube.isLegal() && performedRotations == NUM_ROTATIONS);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        } finally {
            pool.shutdown();
        }
    }

    /**
     * Tests whether an interruption on a process that's about to be awakened halts other waiters.
     */
    @Test
    public void testScenario_whatHappensWhenWhoImWakingIsInterrupted() {
        ;
    }

    /**
     * Tests whether conflicting rotations are actually stopped on their axis' semaphores
     * and whether only one can rotate the cube at a time.
     */
    @Test
    public void testScenario_letAnotherRotationPass() {
        AtomicInteger times_first_thread_started_rotating = new AtomicInteger(0);
        AtomicInteger times_someone_else_started_rotating = new AtomicInteger(0);

        Cube cube = new Cube(3,
                (side, __) -> {
                    if (side == Side.Top.intValue()) {
                        int x = times_someone_else_started_rotating.intValue();
                        times_first_thread_started_rotating.incrementAndGet();
                        sleep(50); // give time for other threads to try and pass
                        assertThat(times_someone_else_started_rotating.intValue() == x);
                    } else {
                        times_someone_else_started_rotating.incrementAndGet();
                    }
                },
                null, null, null);


        Thread politeThread = new Thread(() -> {
            WriterTask writer = new WriterTask(cube, Side.Top.intValue(), 0);
            sleep(5);
            writer.run();
        });

        Thread[] otherThreads = new Thread[] {
                new Thread(new WriterTask(cube,  Side.Left.intValue(), 0)),
                new Thread(new WriterTask(cube,  Side.Front.intValue(), 0)),
                new Thread(new WriterTask(cube,  Side.Right.intValue(), 0)),
                new Thread(new WriterTask(cube,  Side.Back.intValue(), 0)),
                new Thread(new WriterTask(cube,  Side.Bottom.intValue(), 2)),
        };

        politeThread.start();
        for (Thread t : otherThreads) {
            t.start();
        }
        try {
            politeThread.join();
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
        int size = 10;
        resetSyncVars(size);
        Cube cube = new Cube(size,
                defaultBeforeRotation(size, 10),
                defaultAfterRotation(size, 10),
                defaultBeforeShowing(0),
                defaultAfterShowing(0)
        );

        final int ROTATIONS_PER_TYPE = 20000;

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
            assertThat(cube.isSolved() && performedRotations == ROTATIONS_PER_TYPE * cube.getSize());
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
        int size = 20;
        resetSyncVars(size);
        Cube cube = new Cube(size,
                defaultBeforeRotation(size,     10),
                defaultAfterRotation(size,  10),
                defaultBeforeShowing(0),
                defaultAfterShowing(0)
        );

        final int ROTATION_PAIRS = 50000;

        ExecutorService pool = Executors.newFixedThreadPool(MAX_THREADS);

        Side side = Side.randomSide();
        int layer = rand.nextInt(cube.getSize());

        List<Callable<Object>> tasks = new ArrayList<>(2 * ROTATION_PAIRS);
        for (int i = 0; i < ROTATION_PAIRS; i++) {
            tasks.add(Executors.callable(new WriterTask(cube, side.intValue(), layer)));
            tasks.add(Executors.callable(new WriterTask(cube, side.opposite().intValue(), cube.getSize() - 1 - layer)));
        }
        try {
            pool.invokeAll(tasks);
            assertThat(cube.isSolved() && performedRotations == 2 * ROTATION_PAIRS);
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
        int size = 10;
        resetSyncVars(size);
        Cube cube = new Cube(size,
                defaultBeforeRotation(size, 0),
                defaultAfterRotation(size, 0),
                defaultBeforeShowing(0),
                defaultAfterShowing(0)
        );

        final int NUM_PAIRS = 7500;

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
            assertThat(cube.isSolved() && performedRotations == 2 * cube.getSize() * NUM_PAIRS);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        } finally {
            pool.shutdown();
        }
    }

    /* ------------------------ Sequential tests ------------------------ */

    @Test
    public void testCyclesOfParallelRotationsSequential() {
        int size = 10;
        resetSyncVars(size);
        Cube cube = new Cube(size,
                defaultBeforeRotation(size, 10),
                defaultAfterRotation(size, 10),
                defaultBeforeShowing(0),
                defaultAfterShowing(0)
        );

        final int ROTATIONS_PER_TYPE = 10000;
        Side side = Side.randomSide();

        try {
            for (int i = 0; i < ROTATIONS_PER_TYPE; i++) {
                for (int layer = 0; layer < cube.getSize(); layer++) {
                    cube.rotate(side.intValue(), layer);
                }
            }
            assertThat(cube.isSolved() && performedRotations == ROTATIONS_PER_TYPE * cube.getSize());
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    @Test
    public void testTwoAntagonistRotationsSequential() {
        int size = 20;
        resetSyncVars(size);
        Cube cube = new Cube(size,
                defaultBeforeRotation(size, 0),
                defaultAfterRotation(size, 0),
                defaultBeforeShowing(0),
                defaultAfterShowing(0)
        );

        final int ROTATION_PAIRS = 1000;
        Side side = Side.randomSide();
        int layer = rand.nextInt(cube.getSize());

        try {
            for (int i = 0; i < ROTATION_PAIRS; i++) {
                cube.rotate(side.intValue(), layer);
                cube.rotate(side.opposite().intValue(), cube.getSize() - 1 - layer);
            }
            assertThat(cube.isSolved() && performedShows == 2 * ROTATION_PAIRS);

        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    @Test
    public void testManyAntagonistRotationsSequential() {
        int size = 10;
        resetSyncVars(size);
        Cube cube = new Cube(size,
                defaultBeforeRotation(size, 10),
                defaultAfterRotation(size, 10),
                defaultBeforeShowing(0),
                defaultAfterShowing(0)
        );

        final int NUM_PAIRS = 10000;

        try {
            Side side = Side.randomSide();
            for (int i = 0; i < NUM_PAIRS; i++) {
                for (int layer = 0; layer < cube.getSize(); layer++) {
                    cube.rotate(side.intValue(), layer);
                    cube.rotate(side.opposite().intValue(), cube.getSize() - 1 - layer);
                }
            }
            assertThat(cube.isSolved() && performedRotations == 2 * cube.getSize() * NUM_PAIRS);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    /**
     * Tests a popular algorithm with cycle of length 24.
     */
    @Test
    public void testCycleOfRUruSequential() {
        int size = 10;
        resetSyncVars(size);
        Cube cube = new Cube(size,
                defaultBeforeRotation(size, 0),
                defaultAfterRotation(size, 0),
                defaultBeforeShowing(0),
                defaultAfterShowing(0)
        );

        final int LOOPS = 66666;
        try {
            for (int i = 0; i < LOOPS; i++) {
                cube.rotate(Side.Right.intValue(), 0);
                cube.rotate(Side.Top.intValue(),0);
                cube.rotate(Side.Right.opposite().intValue(),cube.getSize() - 1);
                cube.rotate(Side.Top.opposite().intValue(),cube.getSize() - 1);
            }

            assertThat(cube.isSolved() && performedRotations == 4 * LOOPS);
        } catch (InterruptedException e) {
            interruptCurrentThread();
        }
    }

    @Test
    public void testManyRandomRotationsSequential() {
        int size = 10;
        resetSyncVars(size);
        Cube cube = new Cube(size,
                defaultBeforeRotation(size, 0),
                defaultAfterRotation(size, 0),
                defaultBeforeShowing(0),
                defaultAfterShowing(0)
        );

        final int NUM_ROTATIONS = 20000;

        try {
            for (int i = 0; i < NUM_ROTATIONS; i++) {
                cube.rotate(Side.randomSide().intValue(), rand.nextInt(cube.getSize()));
            }

            assertThat(cube.isLegal() && performedRotations == NUM_ROTATIONS);
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


    /**
     * This compares whether concurrency boosts speed.
     */
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
