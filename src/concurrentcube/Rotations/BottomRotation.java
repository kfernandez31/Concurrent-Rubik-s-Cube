package concurrentcube.Rotations;

import concurrentcube.Cube;
import concurrentcube.AxisGroup;
import concurrentcube.Side;

public class BottomRotation extends Rotation {

    public BottomRotation(Cube cube, int layer) {
        super(cube, Side.Bottom, layer);
    }

    @Override
    protected AxisGroup assignGroup() {
        return AxisGroup.TopBottom;
    }

    @Override
    public int getLayerDisregardingOrientation() {
        return cube.getSize() - 1 - layer;
    }

    @Override
    public void applyRotation() {
        swapRowAndRow(Side.Left, cube.getSize() - 1 - layer, Side.Back, cube.getSize() - 1 - layer);
        swapRowAndRow(Side.Back, cube.getSize() - 1 - layer, Side.Right, cube.getSize() - 1 - layer);
        swapRowAndRow(Side.Right, cube.getSize() - 1 - layer, Side.Front, cube.getSize() - 1 - layer);

        super.applyRotation();
    }

}
