package concurrentcube.Rotations;

import concurrentcube.Cube;
import concurrentcube.WorkingGroup;
import concurrentcube.Side;

public class RightRotation extends Rotation {

    public RightRotation(Cube cube, int layer) {
        super(cube, Side.Right, layer);
    }

    @Override
    protected WorkingGroup assignGroup() {
        return WorkingGroup.LeftRight;
    }

    @Override
    public int getPlane() {
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
