package concurrentcube.Rotations;

import concurrentcube.Cube;
import concurrentcube.AxisGroup;
import concurrentcube.Side;

public class BackRotation extends Rotation {

    public BackRotation(Cube cube, int layer) {
        super(cube, Side.Back, layer);
    }

    @Override
    protected AxisGroup assignGroup() {
        return AxisGroup.FrontBack;
    }

    @Override
    public int getLayerDisregardingOrientation() {
        return cube.getSize() - 1 - layer;
    }

    @Override
    public void applyRotation() {
        swapColumnAndRow(Side.Left, layer, Side.Bottom, cube.getSize() - 1 - layer);
        swapColumnAndRow(Side.Left, layer, Side.Top, layer);
        swapColumnAndRow(Side.Right, cube.getSize() - 1 - layer, Side.Top, layer);

        reverseColumn(Side.Left, layer);
        reverseColumn(Side.Right, cube.getSize() - 1 - layer);

        super.applyRotation();
    }

}
