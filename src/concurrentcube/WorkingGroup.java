package concurrentcube;

/*
IDs of groups handling the cube
 */
public enum WorkingGroup {
    TopBottom(0),
    LeftRight(1),
    FrontBack(2),

    NUM_AXES(3),
    Readers(-1);


    private static final WorkingGroup[] values = values();
    private final int id;

    WorkingGroup(int id) {
        this.id = id;
    }

    public int intValue() {
        return id;
    }

    public static WorkingGroup fromSide(Side side) {
        switch (side) {
            case Top : return TopBottom;
            case Left : return LeftRight;
            case Front : return FrontBack;
            default : return fromSide(side.opposite());
        }
    }

}
