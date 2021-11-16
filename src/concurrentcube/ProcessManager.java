package concurrentcube;

import concurrentcube.Rotations.Rotation;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;

//todo: JavaDoc
//todo: zrobić z tego singleton
public class ProcessManager {

    /* Mutual exclusion semaphore. */
    private final Semaphore mutex = new Semaphore(1);
    /* Queue to hang awaiting rotations. */
    private final Semaphore writersQueue = new Semaphore(0, true);
    /* Queue to hang awaiting show requests. */
    private final Semaphore readersQueue = new Semaphore(0, true);
    /* Queue containing information about each process hanging on `writersQueue`. */
    private final Queue<Rotation> readerInfoQueue = new LinkedList<>();
    /* Number of processes waiting to call `show` */
    private int waitingReaders;
    /* Number of processes that are currently calling `show` */
    private int activeReaders ;
    /* Number of rotations currently taking place */
    private int activeWriters;
    /* Contains the side ID of the first process that initiated any rotations in its group. */
    private Side currentSide;
    /* Statuses of the cube's rings/layers - are they being rotated or not. */
    private final boolean[] isOccupied;

    /* Number of axes on a cube : Top-Bottom, Left-Right, Front-Back */
    private static final int AXES = 3;
    private final Cube cube;

    public ProcessManager(Cube cube) {
        this.cube = cube;
        this.isOccupied = new boolean[AXES * cube.getSize()]; //TODO: czy to mogłoby być rozmiaru `size
        this.currentSide = Side.NoSide;
    }

    /**
     * Enables a process to enter its entry protocol, waits if necessary.
     */
    public void entryProtocol() throws InterruptedException {
        mutex.acquire();
    }

    //TODO: lepsza jedna, przeładowana nazwa na wait'y i enter'y?

    /**
     * Halts a writer-type process before entering the critical section
     * if there are other active processes inside that would collide with it
     * (i.e. readers or non-parallel writers)
     * @param writer : data of the requested rotation
     */
    public void writerWaitIfNecessary(Rotation writer) throws InterruptedException {
        if (waitingReaders > 0 || !readerInfoQueue.isEmpty() ||
                isOccupied[writer.getGroupno() % AXES] || !writer.isParallel(currentSide)) {
            mutex.release();
            readerInfoQueue.add(writer);
            writersQueue.acquire(); /* --- Critical section inheritance --- */
        }
        activeWriters++;
    }

    /**
     * Halts a reader-type process before entering the critical section
     * if there are other active processes inside that would collide with it.
     * (i.e. any writers)
     */
    public void readerWaitIfNecessary() throws InterruptedException {
        if (!readerInfoQueue.isEmpty()) {
            waitingReaders++;
            mutex.release();
            readersQueue.acquire(); /* --- Critical section inheritance --- */
            waitingReaders--;
        }
        activeReaders++;
    }

    /**
     * Sets a layer's status to occupied,
     * indicating that a rotation has been approved on it.
     * @param writer : data of the requested rotation
     */
    public void occupyLayer(Rotation writer) {
        isOccupied[writer.getGroupno() % AXES] = true;
    }

    /**
     * Marks the cube as being rotated around an axis (by the first entering writer),
     * allowing only processes performing rotations around it.
     * @param writer : data of the requested rotation
     */
    public void setWorkingGroup(Rotation writer) {
        if (activeWriters == 1) {
            currentSide = writer.getSide();
        }
    }

    /**
     * Wakes up other writers performing rotations on free layers around the same axis.
     * @param writer : data of the requested rotation
     */
    public void inviteParallelWriters(Rotation writer) {
        if (!readerInfoQueue.isEmpty()) {
            Rotation next = readerInfoQueue.peek();

            /* Wake up any non-colliding processes that'll work in parallel */
            if (writer.isParallel(next.getSide()) && !isOccupied[next.getGroupno() & AXES]) {
                readerInfoQueue.poll();
                writersQueue.release(); /* --- Critical section transfer --- */
            } else {
                mutex.release();
            }
        } else {
            mutex.release();
        }
    }

    /**
     * Wakes up other readers to read data from the cube concurrently.
     */
    public void inviteParallelReaders() {
        if (waitingReaders > 0) {
            readersQueue.release();
        } else {
            mutex.release();
        }
    }

    /**
     * Allows a writer to enter the critical section.
     * @param writer : data of the requested rotation
     */
    public void writerEnterCriticalSection(Rotation writer) {
        if (cube.getBeforeRotation() != null) {
            cube.getBeforeRotation().accept(writer.getSide().intValue(), writer.getLayer());
        }
        writer.applyRotation();
        if (cube.getAfterRotation() != null) {
            cube.getAfterRotation().accept(writer.getSide().intValue(), writer.getLayer());
        }
    }

    /**
     * Allows a reader to enter the critical section.
     */
    public String readerEnterCriticalSection() {
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
     * Indicates that a writer is abandoning the critical section,
     * and allows other writers and readers to enter, prioritizing readers.
     * @param writer - data of the requested rotation
     */
    public void writerExitProtocol(Rotation writer) throws InterruptedException {
        mutex.acquire();
        activeWriters--;
        isOccupied[writer.getGroupno() % AXES] = false;
        /* Wake up any waiting process,
            prioritizing ones that request `show`. */
        if (waitingReaders > 0) {
            readersQueue.release();
        } else if (activeWriters == 0 && !readerInfoQueue.isEmpty()) {
            writersQueue.release();
        } else {
            currentSide = Side.NoSide;
            mutex.release();
        }
    }

    /**
     * Indicates that a reader is abandoning the critical section,
     * and allows other writers and readers to enter, prioritizing writers.
     */
    public void readerExitProtocol() throws InterruptedException {
          /* Wake up any waiting process,
            prioritizing ones that request `rotate`. */
        mutex.acquire();
        activeReaders--;
        if (activeReaders == 0) {
            if (!readerInfoQueue.isEmpty()) {
                readerInfoQueue.poll();
                writersQueue.release();
            } else if (waitingReaders > 0) {
                readersQueue.release();
            } else {
                mutex.release();
            }
        } //TODO: tu nie trzeba else'a ?
    }

}
