package concurrentcube.Rotations;

import concurrentcube.Cube;
import concurrentcube.WorkingGroup;
import concurrentcube.Side;

public class FrontRotation extends Rotation {

    public FrontRotation(Cube cube, int layer) {
        super(cube, Side.Front, layer);
    }

    @Override
    protected WorkingGroup assignGroup() {
        return WorkingGroup.FrontBack;
    }

    @Override
    public int getPlane() {
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
