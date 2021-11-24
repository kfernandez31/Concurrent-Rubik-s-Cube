package concurrentcube.Rotations;

import concurrentcube.Cube;
import concurrentcube.AxisGroup;
import concurrentcube.Side;

public class LeftRotation extends Rotation {

    public LeftRotation(Cube cube, int layer) {
        super(cube, Side.Left, layer);
    }

    @Override
    protected AxisGroup assignGroup() {
        return AxisGroup.LeftRight;
    }

    @Override
    public int getPlane() {
        return layer;
    }

    @Override
    public void applyRotation() {
        swapColumnAndColumn(Side.Bottom, layer, Side.Front, layer);
        swapColumnAndColumn(Side.Front, layer, Side.Top, layer);
        swapColumnAndColumn(Side.Top, layer, Side.Back, cube.getSize() - 1 - layer);

        reverseColumn(Side.Top, layer);
        reverseColumn(Side.Back, cube.getSize() - 1 - layer);

        super.applyRotation();
    }

}
