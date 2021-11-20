package concurrentcube.Rotations;

import concurrentcube.Cube;
import concurrentcube.AxisGroup;
import concurrentcube.Side;

public class FrontRotation extends Rotation {

    public FrontRotation(Cube cube, int layer) {
        super(cube, Side.Front, layer);
    }

    @Override
    protected AxisGroup assignGroup() {
        return AxisGroup.FrontBack;
    }

    @Override
    public int getLayerDisregardingOrientation() {
        return layer;
    }

    @Override
    public void applyRotation() {
        swapColumnAndRow(Side.Left, cube.getSize() - 1 - layer, Side.Top, cube.getSize() - 1 - layer);
        swapColumnAndRow(Side.Left, cube.getSize() - 1 - layer, Side.Bottom, layer);
        swapColumnAndRow(Side.Right, layer, Side.Bottom, layer);

        reverseRow(Side.Top, cube.getSize() - 1 - layer);
        reverseRow(Side.Bottom, layer);

        super.applyRotation();
    }

}
