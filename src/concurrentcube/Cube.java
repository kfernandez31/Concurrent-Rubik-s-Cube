package concurrentcube;

import concurrentcube.Rotations.Rotation;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

public class Cube {
    private final static int AXES = 3;

    private final int size;
    private final Color[][] squares;
    private final BiConsumer<Integer, Integer> beforeRotation;
    private final BiConsumer<Integer, Integer> afterRotation;
    private final Runnable beforeShowing;
    private final Runnable afterShowing;

    /* ------ Variables needed for process synchronization ------ */
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
    /* -----------------------------------------------------------*/

    public int getSize() {
        return size;
    }
    public Color[][] getSquares() {
        return squares;
    }

    public Color getSquareColor(Side side, int row, int col) {
        return squares[side.intValue()][row * size + col];
    }

    public void setSquareColor(Color color, Side side, int row, int col) {
        squares[side.intValue()][row * size + col] = color;
    }


    /**
     * Creates a Rubik's cube of size (number of rows/columns in a face) `size`.
     * Arguments of type BiConsumer<Integer, Integer> and Runnable are actions performed during
     * (right before and after) any altering operations on the cube.
     *
     * `beforeRotation` might, for example, start a mechanical arm manipulating the cube,
     * and `afterRotation` wait for the arm to finish manipulating a layer.
     *
     * @param size : size of the cube
     * @param beforeRotation : action performed before every rotation on the cube
     * @param afterRotation : action performed after every rotation on the cube
     * @param beforeShowing : action performed before every displaying of the cube
     * @param afterShowing : action performed after every displaying of the cube
     */
    public Cube(int size,
                BiConsumer<Integer, Integer> beforeRotation, BiConsumer<Integer, Integer> afterRotation,
                Runnable beforeShowing, Runnable afterShowing) {

        // Cube initialization.
        this.size = size;
        this.beforeRotation = beforeRotation;
        this.afterRotation = afterRotation;
        this.beforeShowing = beforeShowing;
        this.afterShowing = afterShowing;
        this.squares = new Color[Side.SIDES.intValue()][size * size];
        this.isOccupied = new boolean[AXES];

        solve();
    }


    /**
     * Commits a 90-degree rotation of the cube indicated by `side` and `layer` to the `RotationQueue`.
     * Performs the `beforeRotation` and `afterRotation` actions
     * right before and after gaining access to the cube respectively, passing them `side` and `layer` as arguments.
     * @param side : rotated face
     * @param layer : rotated layer
     */
    public void rotate(int side, int layer) throws InterruptedException {
        Rotation me = Rotation.newRotation(this, Side.fromInt(side), layer);

        /* --- Entry protocol --- */
        mutex.acquire();

        /* Wait if:
        - another process else requests (any) access to the cube,
        - the currently working group of processes collides with mine
        */
        if (waitingReaders > 0 || !readerInfoQueue.isEmpty() || isOccupied[me.getGroupno() % AXES] ||
                (!me.isParallel(currentSide) && currentSide != Side.NoSide)) {
            mutex.release();
            readerInfoQueue.add(me);
            writersQueue.acquire(); /* --- Critical section inheritance --- */
        }
        /* Occupy my corresponding layer */
        isOccupied[me.getGroupno() % AXES] = true;

        /* I'm the first process - set currently working group based on my side */
        if (activeWriters == 0) {
            currentSide = me.getSide();
        }
        activeWriters++;

        if (!readerInfoQueue.isEmpty()) {
            Rotation next = readerInfoQueue.peek();

            /* Wake up any non-colliding processes that'll work in parallel */
            if (me.isParallel(next.getSide()) && !isOccupied[next.getGroupno() & AXES]) {
                readerInfoQueue.poll();
                writersQueue.release(); /* --- Critical section transfer --- */
            } else {
                mutex.release();
            }
        } else {
            mutex.release();
        }

        /* --- Critical section --- */
        beforeRotation.accept(side, layer);
        me.applyRotation();
        afterRotation.accept(side, layer);

        /* --- Exit protocol --- */
        mutex.acquire();
        activeWriters--;
        /* Wake up any waiting process, prioritizing ones that request `show`.
            Note: Checking whether `detailsQueue` is empty corresponds to checking whether `rotationsQueue` is */
        if (waitingReaders > 0) {
            readersQueue.release();
        } else if (activeWriters == 0 && !readerInfoQueue.isEmpty()) {
            writersQueue.release();
        } else {
            mutex.release();
        }
    }

    /**
     * Returns a string representation of the cube composed of digits between 0 and 5.
     * Each color coded as digit `n` corresponds to the face indexed as `n`, being its initial color.
     * Squares of the cube are order firstly as they are in the `Side` enum,
     * then according to their row (from left to right) and finally, according to their column (from top to bottom).
     *
     * Performs the `beforeShowing` and `afterShowing` actions
     * right before and after gaining access to the cube respectively.
     * @return `this.toString()`
     */
    public String show() throws InterruptedException {

        /* --- Entry protocol --- */
        mutex.acquire();

        /* Wait if:
        - another process requests access to write to the cube
        */
        if (!readerInfoQueue.isEmpty()) {
            waitingReaders++;
            mutex.release();
            readersQueue.acquire(); /* --- Critical section inheritance --- */
            waitingReaders--;
        }
        activeReaders++;

        /* Wake up any non-colliding processes that'll work in parallel */
        if (waitingReaders > 0) {
            readersQueue.release();
        } else {
            mutex.release();
        }

        /* --- Critical section --- */
        beforeShowing.run();
        String str = toString();
        afterShowing.run();

        /* --- Exit protocol --- */
        mutex.acquire();
        activeReaders--;
        /* Wake up any waiting process, prioritizing ones that request `show`.
            Note: Checking whether `detailsQueue` is empty corresponds to checking whether `rotationsQueue` is */
        if (activeReaders == 0) {
            if (!readerInfoQueue.isEmpty()) {
                readerInfoQueue.poll();
                writersQueue.release();
            } else if (waitingReaders > 0) {
                readersQueue.release();
            } else {
                mutex.release();
            }
        } //TODO: i tu bez else'a ?
        return str;
    }

    /**
     * Solves/initializes the cube, setting each side to its corresponding enum value.
     */
    public void solve() {
        for (int side = Side.Top.intValue(); side < Side.SIDES.intValue(); side++) {
            Color color = Color.fromInt(side);
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    squares[side][row *  size + col] = color;
                }
            }
        }
    }

    /**
     * Shuffles/rearranges the cube.
     */
    public void shuffle() {
        for (int side = Side.Top.intValue(); side < Side.SIDES.intValue(); side++) {
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    squares[side][row *  size + col] = Color.randomColor();
                }
            }
        }
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Cube other = (Cube) o;
        if (size != other.getSize()) {
            return false;
        }

        return arrangementEquals(other.getSquares());
    }

    /**
     * Checks if the cube's squares match the provided arrangement.
     * @param arrangement: compared color arrangement
     * @return squares == this.squares
     */
    public boolean arrangementEquals(Color[][] arrangement) {
        if (arrangement.length != size || arrangement[0].length != size) {
            return false;
        }

        for (int side = Side.Top.intValue(); side < Side.SIDES.intValue(); side++) {
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    if (this.squares[side][row * size + col] != arrangement[side][row * side + col]) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Override
    public String toString() {
        char[] chars = new char[Side.SIDES.intValue() * size * size];

        for (int side = Side.Top.intValue(); side < Side.SIDES.intValue(); side++) {
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    chars[col + size * (row + size * side)] =
                            squares[side][row * size + col].charValue();
                }
            }
        }
        return new String(chars);
    }

}
