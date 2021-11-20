package concurrentcube.Rotations;

import concurrentcube.Cube;
import concurrentcube.AxisGroup;
import concurrentcube.Side;

public class RightRotation extends Rotation {

    public RightRotation(Cube cube, int layer) {
        super(cube, Side.Right, layer);
    }

    @Override
    protected AxisGroup assignGroup() {
        return AxisGroup.LeftRight;
    }

    @Override
    public int getLayerDisregardingOrientation() {
        return cube.getSize() - 1 - layer;
    }

    @Override
    public void applyRotation() {
        swapColumnAndColumn(Side.Top, cube.getSize() - 1 - layer, Side.Front, cube.getSize() - 1 - layer);
        swapColumnAndColumn(Side.Front, cube.getSize() - 1 - layer, Side.Bottom, cube.getSize() - 1 - layer);
        swapColumnAndColumn(Side.Bottom, cube.getSize() - 1 - layer, Side.Back, layer);

        reverseColumn(Side.Bottom, cube.getSize() - 1 - layer);
        reverseColumn(Side.Back, layer);

        super.applyRotation();
    }

}
