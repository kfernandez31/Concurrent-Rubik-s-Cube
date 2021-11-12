package concurrentcube;

public enum Side {

    NoSide(-1),

    Top(0),
    Left(1),
    Front(2),
    Right(3),
    Back(4),
    Bottom(5),

    SIDES(6);

    private static final Side[] values = values();

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

    public Side opposite() {
        return switch (this) {
            case Top -> Bottom;
            case Left -> Right;
            case Front -> Back;
            case Right -> Left;
            case Back -> Front;
            case Bottom -> Top;
            default -> throw new IndexOutOfBoundsException("Invalid side.");
        };
    }

}
