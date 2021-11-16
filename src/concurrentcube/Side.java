package concurrentcube;

import java.util.Random;

public enum Side {
    Top(0),
    Left(1),
    Front(2),
    Right(3),
    Back(4),
    Bottom(5),

    NoSide(-1),
    SIDES(6);

    private static final Side[] values = values();
    private static final Random rand = new Random();
    private final int side;

    Side(int side) {
        this.side = side;
    }

    public int intValue() {
        return side;
    }

    public static Side fromInt(int ordinal) {
        return values[ordinal];
    }

    public static Side randomSide() {
        return fromInt(rand.nextInt(SIDES.intValue()));
    }

    public Side opposite() {
        switch (this) {
            case Top : return Bottom;
            case Left : return Right;
            case Front : return Back;
            case Right : return Left;
            case Back : return Front;
            case Bottom : return Top;
            default : throw new IndexOutOfBoundsException("Invalid side.");
        }
    }

}
