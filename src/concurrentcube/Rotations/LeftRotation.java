package concurrentcube.Rotations;

import concurrentcube.Cube;
import concurrentcube.Side;

public class LeftRotation extends Rotation {

    public LeftRotation(Cube cube, int layer) {
        super(cube, Side.Left, layer);
    }


    @Override
    protected int calculateGroup() {
        return side.intValue() * layer;
    }

    @Override
    public void applyRotation() {
        swapColumnAndColumn(Side.Bottom, layer, Side.Front, layer);
        swapColumnAndColumn(Side.Front, layer, Side.Top, layer);
        swapColumnAndColumn(Side.Top, layer, Side.Back, cube.getSize() - 1 - layer);

        reverseColumn(Side.Back, cube.getSize() - 1 - layer);

        super.applyRotation();
    }

}
