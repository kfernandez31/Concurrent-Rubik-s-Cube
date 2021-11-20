package concurrentcube;


public enum AxisGroup {
    TopBottom(0),
    LeftRight(1),
    FrontBack(2),

    NUM_GROUPS(3);

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

}
