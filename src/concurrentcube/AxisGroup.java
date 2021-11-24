package concurrentcube;


public enum AxisGroup {
    TopBottom(0),
    LeftRight(1),
    FrontBack(2),

    NUM_AXES(3),
    NO_AXIS(-1);


    private static final AxisGroup[] values = values();
    private final int id;

    AxisGroup(int id) {
        this.id = id;
    }

    public int intValue() {
        return id;
    }

    public static AxisGroup fromInt(int ordinal) {
        return values[ordinal];
    }

    public static AxisGroup fromSide(Side side) {
        switch (side) {
            case Top : return TopBottom;
            case Left : return LeftRight;
            case Front : return FrontBack;
            default : return fromSide(side.opposite());
        }
    }

}
