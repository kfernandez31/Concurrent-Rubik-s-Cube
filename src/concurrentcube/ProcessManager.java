package concurrentcube;

import concurrentcube.Rotations.Rotation;

import java.util.concurrent.Semaphore;

/*TODO: should cleanup code in finally blocks? */
public class ProcessManager {

    /* Mutual exclusion semaphore. */
    private final Semaphore mutex;
    /* Queue to hang awaiting show requests */
    private final Semaphore readerSem;
    /* Number of processes waiting to call `show` */
    private int waitingReaders;
    /* Number of processes that are currently calling `show` */
    private int activeReaders;
    /* Number of rotations currently taking place */
    private int activeWriters;
    /* Number of processes waiting to call `rotate` */
    private int waitingWriters;
    /* Number of processes waiting to call `rotate` from a specific group */
    private final int[] waitingWritersFromGivenGroup;
    /* Contains the side ID of the first process that initiated any rotations in its group */
    private AxisGroup currentGroup;
    /* ID of the last group of writers that finished their work */
    private int lastFinishedWriterGroupId;
    /* Statuses of the cube's rings/layers - are they being rotated or not */
    private final boolean[] layerIsOccupied;
    /* Semaphores for writers on each axis */
    private final Semaphore[] writerSems;

    private final Cube cube;

    private final Semaphore messageMutex = new Semaphore(1);
    public void printStatus(Rotation r, String status) {
        try {
            messageMutex.acquire();
            System.out.printf("Process:   rotate(%d,%d), ID: %d\n", r.getSide().intValue(), r.getLayer(), r.ID);
            System.out.println("Status:    " + status + "\n");
            messageMutex.release();
        } catch (InterruptedException ignored) {

        }
    }

    public ProcessManager(Cube cube) {
        this.cube = cube;
        this.lastFinishedWriterGroupId = -1;
        this.mutex = new Semaphore(1);
        this.readerSem = new Semaphore(0, true);
        this.waitingWritersFromGivenGroup = new int[AxisGroup.NUM_GROUPS.intValue()];

        this.writerSems = new Semaphore[AxisGroup.NUM_GROUPS.intValue()];
        for (int i = 0; i < AxisGroup.NUM_GROUPS.intValue(); i++) {
            this.writerSems[i] = new Semaphore(0);
        }

        this.layerIsOccupied = new boolean[cube.getSize()];
    }

    /**
     * Enables a process to enter its entry protocol, waits if necessary.
     */
    public void entryProtocol() throws InterruptedException {
        try {
            mutex.acquire();
        } catch (InterruptedException e) {
            mutex.release();
            throw e;
        }
    }


    /**
     * Enables a process to enter its entry protocol, waits if necessary.
     */
    public void writerEntryProtocol(Rotation writer) throws InterruptedException {
        try {
            //printStatus(writer, "waiting for mutex.");
            mutex.acquire();
            //printStatus(writer, "acquired mutex.");
        } catch (InterruptedException e) {
            mutex.release();
            throw e;
        }
    }

    /**
     * Condition that, when met, halts a writer on its axis' semaphore
     * @param writer : data of the requested writer
     * @return should the writer wait
     */
    private boolean writerWaitCondition(Rotation writer) {
        return activeReaders > 0 || waitingReaders > 0 || (activeWriters > 0
                && (currentGroup != writer.getGroup() || layerIsOccupied[writer.getLayerDisregardingOrientation()]));
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
                waitingWritersFromGivenGroup[writer.getGroup().intValue()]++;
                waitingWriters++;
                //printStatus(writer, "released mutex.");
                mutex.release();
                //printStatus(writer, "waiting for writerSems[" + writer.getLayerDisregardingOrientation() + "]");
                writerSems[writer.getGroup().intValue()].acquire();
                //printStatus(writer, "acquired writerSems[" + writer.getLayerDisregardingOrientation() + "]");
                waitingWritersFromGivenGroup[writer.getGroup().intValue()]--;
                waitingWriters--;
            }
        } catch (InterruptedException e) {
            mutex.acquireUninterruptibly();
            waitingWritersFromGivenGroup[writer.getGroup().intValue()]--;
            waitingWriters--;
            mutex.release();
            throw e;
        }
    }

    /**
     * Halts a reader before entering the critical section
     * if there are other active processes inside that would collide with it.
     * (i.e. any writers)
     */
    public void readerWaitIfNecessary() throws InterruptedException {
        try {
            if (readerWaitCondition()) {
                waitingReaders++;
                mutex.release();
                readerSem.acquire();
                waitingReaders--;
            }
        } catch(InterruptedException e) {
            mutex.acquireUninterruptibly();
            waitingReaders--;
            mutex.release();
            throw e;
        }
    }

    /**
     * Sets a layer's status to occupied,
     * indicating that a rotation on it has begun.
     * @param writer : data of the working writer
     */
    public void occupyLayer(Rotation writer) {
        activeWriters++;
        if (activeWriters == 1) {
            currentGroup = writer.getGroup();
        }
        layerIsOccupied[writer.getLayerDisregardingOrientation()] = true;
    }

    /**
     * Wakes up other writers performing rotations on free layers around the same axis.
     * @param writer : data of the current writer
     */
    public void inviteParallelWriters(Rotation writer) {
        if (waitingWritersFromGivenGroup[writer.getGroup().intValue()] > 0) {
            writerSems[writer.getGroup().intValue()].release();
        } else {
            mutex.release();
        }
    }

    /**
     * Wakes up other readers to read data from the cube concurrently.
     */
    public void inviteParallelReaders() {
        activeReaders++;

        if (waitingReaders > 0) {
            readerSem.release();
        } else {
            mutex.release();
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
        //printStatus(writer, "waiting for mutex.");
        mutex.acquireUninterruptibly();
        //printStatus(writer, "acquired mutex.");
        activeWriters--;
        layerIsOccupied[writer.getLayerDisregardingOrientation()] = false;

        if (activeWriters == 0) {
            lastFinishedWriterGroupId = writer.getGroup().intValue();
            if (waitingReaders > 0) {
                readerSem.release();
            } else if (waitingWriters > 0) {
                for (int i = 1; i <= AxisGroup.NUM_GROUPS.intValue(); i++) {
                    if (waitingWritersFromGivenGroup[(writer.getGroup().intValue() + i) % AxisGroup.NUM_GROUPS.intValue()] > 0) {
                        //printStatus(writer, "released writerSems[" + i + "].");
                        writerSems[(writer.getGroup().intValue() + i) % AxisGroup.NUM_GROUPS.intValue()].release();
                        break;
                    }
                }
            } else {
                //printStatus(writer, "released mutex.");
                mutex.release();
            }
        } else {
            //printStatus(writer, "released mutex.");
            mutex.release();
        }
    }

    /**
     * Indicates that a reader is abandoning his critical section,
     * and allows other writers and readers to enter, prioritizing writers.
     */
    public void readerExitProtocol() {
        mutex.acquireUninterruptibly();
        activeReaders--;

        if (activeReaders == 0) {
            if (waitingWriters > 0) {
                for (int i = 1; i <= AxisGroup.NUM_GROUPS.intValue(); i++) {
                    if (waitingWritersFromGivenGroup[(lastFinishedWriterGroupId + i) % AxisGroup.NUM_GROUPS.intValue()] > 0) {
                        writerSems[(lastFinishedWriterGroupId+ i) % AxisGroup.NUM_GROUPS.intValue()].release();
                        break;
                    }
                }
            } else if (waitingReaders > 0) {
                readerSem.release();
            } else {
                mutex.release();
            }
        } else {
            mutex.release();
        }
    }
}
