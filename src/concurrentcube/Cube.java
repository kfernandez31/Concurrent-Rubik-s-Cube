package concurrentcube;

import concurrentcube.Rotations.Rotation;

import java.util.Arrays;
import java.util.function.BiConsumer;

//ala ma kota

public class Cube {
    private final int size;
    private final Color[][] squares;
    private final BiConsumer<Integer, Integer> beforeRotation;
    private final BiConsumer<Integer, Integer> afterRotation;
    private final Runnable beforeShowing;
    private final Runnable afterShowing;
    private final ProcessManager pm;

    public int getSize() {
        return size;
    }
    public Color[][] getSquares() {
        return squares;
    }
    public Color getSquareColor(Side side, int row, int col) {
        return squares[side.intValue()][row * size + col];
    }
    public BiConsumer<Integer, Integer> getBeforeRotation() {
        return beforeRotation;
    }
    public BiConsumer<Integer, Integer> getAfterRotation() {
        return afterRotation;
    }
    public Runnable getBeforeShowing() {
        return beforeShowing;
    }
    public Runnable getAfterShowing() {
        return afterShowing;
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
                BiConsumer<Integer, Integer> beforeRotation,
                BiConsumer<Integer, Integer> afterRotation,
                Runnable beforeShowing,
                Runnable afterShowing) {

        // Cube initialization.
        assert(size > 0);
        this.size = size;
        this.beforeRotation = beforeRotation;
        this.afterRotation = afterRotation;
        this.beforeShowing = beforeShowing;
        this.afterShowing = afterShowing;
        this.squares = new Color[Side.SIDES.intValue()][size * size];
        this.pm = new ProcessManager(this);

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

        pm.entryProtocol();
        pm.writerWaitIfNecessary(me);
        pm.occupyLayer(me);
        pm.setWorkingGroup(me);
        pm.inviteParallelReaders();
        pm.writerEnterCriticalSection(me);
        pm.writerExitProtocol(me);
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
        String str;

        pm.entryProtocol();
        pm.readerWaitIfNecessary();
        pm.inviteParallelReaders();
        str = pm.readerEnterCriticalSection();
        pm.readerExitProtocol();

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

    /**
     * Asserts whether a cube is solved - checks if every face is colored in
     * the color with a corresponding enumeration constant.
     * @return whether the cube is solved
     */
    public boolean isSolved() {
        for (int side = Side.Top.intValue(); side < Side.SIDES.intValue(); side++) {
            Color color = Color.fromInt(side);
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    if (squares[side][row *  size + col] != color) {
                        return false;
                    }
                }
            }
        }
        return true;
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
        return Arrays.deepEquals(squares, other.getSquares());
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
