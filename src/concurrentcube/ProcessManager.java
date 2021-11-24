package concurrentcube;

import concurrentcube.Rotations.Rotation;

import java.util.concurrent.Semaphore;

/*TODO: should cleanup code be in finally blocks? */
public class ProcessManager {

    /* Mutual exclusion semaphore implemented as a binary semaphore. */
    private final Semaphore varMutex;
    /* Semaphore to hang awaiting `show` requests */
    private final Semaphore readerSem;
    /* Semaphores to guarantee mutual exclusion between rotations of the plane */
    private final Semaphore[] planeMutexes;
    /* Semaphores to hang rotations conflicting with the currently working axis */
    private final Semaphore[] axisSems;

    /* Number of processes that currently displaying the cube */
    private int activeReaders;
    /* Number of processes waiting to call `show` */
    private int waitingReaders;
    /* Number of processes waiting to call `rotate` */
    private int waitingWriters;
    /* Number of processes currently rotating the cube */
    private int activeWriters;

    /* Number of writers from each axis waiting for cube access */
    private final int[] waitingFromAxis;
    /* Status of a plane - whether a rotation is being performed on it */
    private final boolean[] planeIsOccupied;

    /* Contains the side ID of the first process that initiated any rotations in its group */
    private AxisGroup currentAxis;
    /* ID of the last group of writers that finished their work */
    private AxisGroup lastFinishedWritersAxis;

    /* Cube handled by the manager */
    private final Cube cube;

    private final Semaphore messageMutex = new Semaphore(1);
    private boolean DEBUG = true;
    public void printStatus(boolean writer, Rotation r, String status, int readerID) {
        if (DEBUG) {
            try {
                messageMutex.acquire();
                if (writer) {
                    System.out.printf("Process:   rotate(%d,%d), ID: %d\n", r.getSide().intValue(), r.getLayer(), r.ID);
                } else {
                    System.out.printf("Process:   reader, ID: %d\n", readerID);
                }
                /*System.out.println("Status:    " + status + "\n");*/
                messageMutex.release();
            } catch (InterruptedException ignored) {
            }
        }
    }

    public ProcessManager(Cube cube) {
        this.cube = cube;

        this.varMutex = new Semaphore(1);
        this.readerSem = new Semaphore(0, true);

        this.waitingFromAxis = new int[AxisGroup.NUM_AXES.intValue()];
        this.axisSems = new Semaphore[AxisGroup.NUM_AXES.intValue()];
        for (int axis = 0; axis < AxisGroup.NUM_AXES.intValue(); axis++) {
            this.axisSems[axis] = new Semaphore(0);
        }

        this.planeIsOccupied = new boolean[cube.getSize()];
        this.planeMutexes = new Semaphore[cube.getSize()];
        for (int plane = 0; plane < cube.getSize(); plane++) {
            this.planeMutexes[plane] = new Semaphore(1);
        }

        this.lastFinishedWritersAxis = AxisGroup.NO_AXIS;
    }

    private void findAndWakeNextWriterGroup(AxisGroup axis) {
        for (int i = 1; i <= AxisGroup.NUM_AXES.intValue(); i++) {
            int j = (axis.intValue() + i) % AxisGroup.NUM_AXES.intValue();
            if (waitingFromAxis[j] > 0) {
                axisSems[j].release();
            }
        }

        if (DEBUG) { /*System.out.printf("releasing mutex\n");*/ }
        varMutex.release();
    }

    /**
     * Enables a process to enter its entry protocol, waits if necessary.
     */
    public void readerEntryProtocol(int myID) throws InterruptedException {
        try {
            /*printStatus(false, null, "waiting for mutex.", myID);*/
            varMutex.acquire();
            /*printStatus(false, null, "acquired mutex.", myID);*/
        } catch (InterruptedException e) {
            varMutex.release();
            throw e;
        }
    }

    //TODO: zrobić jeden entry protocol
    /**
     * Enables a process to enter its entry protocol, waits if necessary.
     */
    public void writerEntryProtocol(Rotation writer) throws InterruptedException {
        try {
            printStatus(true, writer, "waiting for mutex.", -1);
            varMutex.acquire();
            printStatus(true, writer, "acquired mutex.", -1);
        } catch (InterruptedException e) {
            varMutex.release();
            throw e;
        }
    }

    /**
     * A writer must wait if readers or other colliding writers
     * are currently handling the cube.
     * @param writer : data of the requested writer
     * @return should the writer wait
     */
    private boolean writerWaitCondition(Rotation writer) {
        return activeReaders > 0 ||
                (activeWriters > 0 && (currentAxis != writer.getAxis() || planeIsOccupied[writer.getAxis().intValue()]));
    }

    private boolean readerWaitCondition() {
        return activeWriters > 0 || waitingWriters > 0;
    }

    /**
     * Halts a writer-type process before entering the critical section
     * if there are other active processes inside that would collide with it
     * (i.e. readers or non-parallel writers)
     * @param writer : data of the requested writer
     */
    public void writerWaitIfNecessary(Rotation writer) throws InterruptedException {
        try {
            if (writerWaitCondition(writer)) {
                waitingWriters++;
                waitingFromAxis[writer.getAxis().intValue()]++;

                if (DEBUG) { /*System.out.printf("releasing mutex\n");*/ }
                varMutex.release();

                printStatus(true, writer, "waiting for planeSem[" + writer.getAxis() + "]" + "[" + writer.getPlane() + "]", -1);
                axisSems[writer.getAxis().intValue()].acquire();
                printStatus(true, writer, "acquired planeSem[" + writer.getAxis() + "]" + "[" + writer.getPlane() + "]", -1);

                waitingWriters--;
                waitingFromAxis[writer.getAxis().intValue()]--;
            }
        } catch (InterruptedException e) {
            varMutex.acquireUninterruptibly(); //TODO: Niech mutex nie służy do samego obrotu
            waitingFromAxis[writer.getAxis().intValue()]--;
            varMutex.release();
            throw e;
        }
    }

    /**
     * Halts a reader before entering the critical section
     * if there are other active processes inside that would collide with it.
     * (i.e. any writers)
     */
    public void readerWaitIfNecessary(int myID) throws InterruptedException {
        try {
            if (readerWaitCondition()) {
                waitingReaders++;
                if (DEBUG) { /*System.out.printf("releasing mutex\n");*/ }
                varMutex.release();

                printStatus(false, null, "waiting for readerSem", myID);
                readerSem.acquire();
                printStatus(false, null, "acquired readerSem", myID);

                waitingReaders--;
            }
        } catch(InterruptedException e) {
            varMutex.acquireUninterruptibly();
            waitingReaders--;
            varMutex.release();
            throw e;
        }
    }

    /**
     * Sets a layer's status to occupied,
     * indicating that a rotation on it has begun.
     * @param writer : data of the working writer
     */
    public void occupyPlane(Rotation writer) {
        try {
            planeMutexes[writer.getPlane()].acquire();
        } catch (InterruptedException e) {
            planeMutexes[writer.getPlane()].release(); //TODO: czy na pewno???
        }

        planeIsOccupied[writer.getPlane()] = true;
        activeWriters++;

        if (activeWriters == 1) {
            currentAxis = writer.getAxis();
        }
    }

    /**
     * Wakes up other writers performing rotations on free layers around the same axis.
     * @param writer : data of the current writer
     */
    public void inviteParallelWriters(Rotation writer) {
        if (waitingFromAxis[writer.getAxis().intValue()] > 0) {
            axisSems[writer.getAxis().intValue()].release();
        } else {
            varMutex.release();
        }
    }

    /**
     * Wakes up other readers to read data from the cube concurrently.
     */
    public void inviteParallelReaders() {
        activeReaders++;

        if (waitingReaders > 0) {
            if (DEBUG) { /*System.out.printf("releasing readerSem\n");*/ }
            readerSem.release();
        } else {
            if (DEBUG) { /*System.out.printf("releasing mutex\n");*/ }
            varMutex.release();
        }
    }

    /**
     * Allows a writer to write to the cube.
     * @param writer : what is being written to the cube
     */
    public void writeToCube(Rotation writer) {
        if (cube.getBeforeRotation() != null) {
            cube.getBeforeRotation().accept(writer.getSide().intValue(), writer.getLayer());
        }
        writer.applyRotation();
        if (cube.getAfterRotation() != null) {
            cube.getAfterRotation().accept(writer.getSide().intValue(), writer.getLayer());
        }
    }

    /**
     * Allows a reader to read from the cube.
     */
    public String readFromCube() {
        if (cube.getBeforeShowing() != null) {
            cube.getBeforeShowing().run();
        }
        String str = cube.toString();
        if (cube.getAfterShowing() != null) {
            cube.getAfterShowing().run();
        }
        return str;
    }

    /**
     * Indicates that a writer is abandoning his critical section,
     * and allows other writers and readers to enter, prioritizing readers.
     * @param writer - data of the abandoning process
     */
    public void writerExitProtocol(Rotation writer) {
        planeMutexes[writer.getPlane()].release();

        printStatus(true, writer, "waiting for mutex.", -1);
        varMutex.acquireUninterruptibly();
        printStatus(true, writer, "acquired mutex uninterruptibly.", -1);
        activeWriters--;
        planeIsOccupied[writer.getPlane()] = false;

        if (activeWriters == 0) {
            lastFinishedWritersAxis = writer.getAxis();
            if (waitingReaders > 0) {
                if (DEBUG) { /*System.out.printf("releasing readerSem\n");*/ }
                readerSem.release();
            } else {
                findAndWakeNextWriterGroup(writer.getAxis());
            }
        } else {
            if (DEBUG) { /*System.out.printf("releasing mutex\n");*/ }
            varMutex.release();
        }
    }

    /**
     * Indicates that a reader is abandoning his critical section,
     * and allows other writers and readers to enter, prioritizing writers.
     */
    public void readerExitProtocol(int myID) {
        printStatus(false, null, "waiting for mutex", myID);
        varMutex.acquireUninterruptibly();
        printStatus(false, null, "acquired mutex uninterruptibly", myID);
        activeReaders--;

        if (activeReaders == 0) {
            if (waitingWriters > 0) {
                findAndWakeNextWriterGroup(lastFinishedWritersAxis);
            } else if (waitingReaders > 0) {
                if (DEBUG) { /*System.out.printf("releasing readerSem\n");*/ }
                readerSem.release();
            } else {
                if (DEBUG) { /*System.out.printf("releasing mutex\n");*/ }
                varMutex.release();
            }
        } else {
            if (DEBUG) { /*System.out.printf("releasing mutex\n");*/ }
            varMutex.release();
        }
    }
}
