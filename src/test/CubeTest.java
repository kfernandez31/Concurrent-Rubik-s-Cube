package test;

import concurrentcube.Cube;
import concurrentcube.Side;
import concurrentcube.Color;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * Klasa korzysta z JUNit5
 */
public class CubeTest {

    private static final int TEST_MAX_CUBE_SIZE = 3;
    private static final int STANDARD_CUBE_SIZE = 3;
    private static final Random rand = new Random();

    private void assertIsSolved(Cube cube) {
        Cube solved = new Cube(cube.getSize(), null,null, null, null);
        assert(cube.equals(solved));
    }

    @Test
    public void printSolved() {
        int size = rand.nextInt(TEST_MAX_CUBE_SIZE);
        Cube solved = new Cube(size, null,null, null, null);
        String str = solved.toString();
        System.out.println(str);
    }

    @Test
    public void testIsInitiallySolved() {
        int size = rand.nextInt(TEST_MAX_CUBE_SIZE);
        Cube solved = new Cube(size, null,null, null, null);
        assertIsSolved(solved);
    }

/*
    Color[][] solved6x3x3Arrangement =  {
            {       // Top
                    Color.White, Color.White, Color.White,
                    Color.White, Color.White, Color.White,
                    Color.White, Color.White, Color.White
            },
            {       // Left
                    Color.Green, Color.Green, Color.Green,
                    Color.Green, Color.Green, Color.Green,
                    Color.Green, Color.Green, Color.Green
            },
            {       // Front
                    Color.Red, Color.Red, Color.Red,
                    Color.Red, Color.Red, Color.Red,
                    Color.Red, Color.Red, Color.Red
            },
            {       // Right
                    Color.Blue, Color.Blue, Color.Blue,
                    Color.Blue, Color.Blue, Color.Blue,
                    Color.Blue, Color.Blue, Color.Blue
            },
            {       // Back
                    Color.Orange, Color.Orange, Color.Orange,
                    Color.Orange, Color.Orange, Color.Orange,
                    Color.Orange, Color.Orange, Color.Orange
            },
            {       // Bottom
                    Color.Yellow, Color.Yellow, Color.Yellow,
                    Color.Yellow, Color.Yellow, Color.Yellow,
                    Color.Yellow, Color.Yellow, Color.Yellow
            }
    };
*/

    @Test
    public void testRotateTop1() {
        Cube cube = new Cube(STANDARD_CUBE_SIZE, null,null, null, null);

        try {
            cube.rotate(Side.Top.intValue(), 1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Color[][] solvedArrangement =  {
                {       /* Top */
                        Color.White, Color.White, Color.White,
                        Color.White, Color.White, Color.White,
                        Color.White, Color.White, Color.White
                },
                {       /* Left */
                        Color.Red, Color.Red, Color.Red,
                        Color.Green, Color.Green, Color.Green,
                        Color.Green, Color.Green, Color.Green
                },
                {       /* Front */
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Red, Color.Red, Color.Red,
                        Color.Red, Color.Red, Color.Red
                },
                {       /* Right */
                        Color.Orange, Color.Orange, Color.Orange,
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Blue, Color.Blue, Color.Blue
                },
                {       /* Back */
                        Color.Green, Color.Green, Color.Green,
                        Color.Orange, Color.Orange, Color.Orange,
                        Color.Orange, Color.Orange, Color.Orange
                },
                {       /* Bottom */
                        Color.Yellow, Color.Yellow, Color.Yellow,
                        Color.Yellow, Color.Yellow, Color.Yellow,
                        Color.Yellow, Color.Yellow, Color.Yellow
                }
        };

        assert(cube.arrangementEquals(solvedArrangement));
    }

    @Test
    public void testRotateLeft1() {
        Cube cube = new Cube(STANDARD_CUBE_SIZE, null,null, null, null);

        try {
            cube.rotate(Side.Left.intValue(), 1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Color[][] solvedArrangement =  {
                {       /* Top */
                        Color.Orange, Color.White, Color.White,
                        Color.Orange, Color.White, Color.White,
                        Color.Orange, Color.White, Color.White
                },
                {       /* Left */
                        Color.Green, Color.Green, Color.Green,
                        Color.Green, Color.Green, Color.Green,
                        Color.Green, Color.Green, Color.Green
                },
                {       /* Front */
                        Color.White, Color.Red, Color.Red,
                        Color.White, Color.Red, Color.Red,
                        Color.White, Color.Red, Color.Red
                },
                {       /* Right */
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Blue, Color.Blue, Color.Blue
                },
                {       /* Back */
                        Color.Orange, Color.Orange, Color.Yellow,
                        Color.Orange, Color.Orange, Color.Yellow,
                        Color.Orange, Color.Orange, Color.Yellow
                },
                {       /* Bottom */
                        Color.Red, Color.Yellow, Color.Yellow,
                        Color.Red, Color.Yellow, Color.Yellow,
                        Color.Red, Color.Yellow, Color.Yellow
                }
        };

        assert(cube.arrangementEquals(solvedArrangement));
    }

    @Test
    public void testRotateFront1() {
        Cube cube = new Cube(STANDARD_CUBE_SIZE, null,null, null, null);

        try {
            cube.rotate(Side.Front.intValue(), 1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Color[][] solvedArrangement =  {
                {       /* Top */
                        Color.White, Color.White, Color.White,
                        Color.White, Color.White, Color.White,
                        Color.Green, Color.Green, Color.Green
                },
                {       /* Left */
                        Color.Green, Color.Green, Color.Yellow,
                        Color.Green, Color.Green, Color.Yellow,
                        Color.Green, Color.Green, Color.Yellow
                },
                {       /* Front */
                        Color.Red, Color.Red, Color.Red,
                        Color.Red, Color.Red, Color.Red,
                        Color.Red, Color.Red, Color.Red
                },
                {       /* Right */
                        Color.White, Color.Blue, Color.Blue,
                        Color.White, Color.Blue, Color.Blue,
                        Color.White, Color.Blue, Color.Blue
                },
                {       /* Back */
                        Color.Orange, Color.Orange, Color.Orange,
                        Color.Orange, Color.Orange, Color.Orange,
                        Color.Orange, Color.Orange, Color.Orange
                },
                {       /* Bottom */
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Yellow, Color.Yellow, Color.Yellow,
                        Color.Yellow, Color.Yellow, Color.Yellow
                }
        };

        assert(cube.arrangementEquals(solvedArrangement));
    }

    @Test
    public void testRotateRight1() {
        Cube cube = new Cube(STANDARD_CUBE_SIZE, null,null, null, null);

        try {
            cube.rotate(Side.Right.intValue(), 1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Color[][] solvedArrangement =  {
                {       /* Top */
                        Color.White, Color.White, Color.Red,
                        Color.White, Color.White, Color.Red,
                        Color.White, Color.White, Color.Red
                },
                {       /* Left */
                        Color.Green, Color.Green, Color.Green,
                        Color.Green, Color.Green, Color.Green,
                        Color.Green, Color.Green, Color.Green
                },
                {       /* Front */
                        Color.Red, Color.Red, Color.Yellow,
                        Color.Red, Color.Red, Color.Yellow,
                        Color.Red, Color.Red, Color.Yellow
                },
                {       /* Right */
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Blue, Color.Blue, Color.Blue
                },
                {       /* Back */
                        Color.Orange, Color.Orange, Color.White,
                        Color.Orange, Color.Orange, Color.White,
                        Color.Orange, Color.Orange, Color.White
                },
                {       /* Bottom */
                        Color.Yellow, Color.Yellow, Color.Orange,
                        Color.Yellow, Color.Yellow, Color.Orange,
                        Color.Yellow, Color.Yellow, Color.Orange
                }
        };

        assert(cube.arrangementEquals(solvedArrangement));
    }

    @Test
    public void testRotateBack1() {
        Cube cube = new Cube(STANDARD_CUBE_SIZE, null,null, null, null);

        try {
            cube.rotate(Side.Back.intValue(), 1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Color[][] solvedArrangement =  {
                {       /* Top */
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.White, Color.White, Color.White,
                        Color.White, Color.White, Color.White
                },
                {       /* Left */
                        Color.White, Color.Green, Color.Green,
                        Color.White, Color.Green, Color.Green,
                        Color.White, Color.Green, Color.Green
                },
                {       /* Front */
                        Color.Red, Color.Red, Color.Red,
                        Color.Red, Color.Red, Color.Red,
                        Color.Red, Color.Red, Color.Red
                },
                {       /* Right */
                        Color.Blue, Color.Blue, Color.Yellow,
                        Color.Blue, Color.Blue, Color.Yellow,
                        Color.Blue, Color.Blue, Color.Yellow
                },
                {       /* Back */
                        Color.Orange, Color.Orange, Color.Orange,
                        Color.Orange, Color.Orange, Color.Orange,
                        Color.Orange, Color.Orange, Color.Orange
                },
                {       /* Bottom */
                        Color.Yellow, Color.Yellow, Color.Yellow,
                        Color.Yellow, Color.Yellow, Color.Yellow,
                        Color.Green, Color.Green, Color.Green
                }
        };

        assert(cube.arrangementEquals(solvedArrangement));
    }

    @Test
    public void testRotateBottom1() {
        Cube cube = new Cube(STANDARD_CUBE_SIZE, null,null, null, null);

        try {
            cube.rotate(Side.Bottom.intValue(), 1);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Color[][] solvedArrangement =  {
                {       /* Top */
                        Color.White, Color.White, Color.White,
                        Color.White, Color.White, Color.White,
                        Color.White, Color.White, Color.White
                },
                {       /* Left */
                        Color.Green, Color.Green, Color.Green,
                        Color.Green, Color.Green, Color.Green,
                        Color.Orange, Color.Orange, Color.Orange
                },
                {       /* Front */
                        Color.Red, Color.Red, Color.Red,
                        Color.Red, Color.Red, Color.Red,
                        Color.Green, Color.Green, Color.Green
                },
                {       /* Right */
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Blue, Color.Blue, Color.Blue,
                        Color.Red, Color.Red, Color.Red
                },
                {       /* Back */
                        Color.Orange, Color.Orange, Color.Orange,
                        Color.Orange, Color.Orange, Color.Orange,
                        Color.Blue, Color.Blue, Color.Blue
                },
                {       /* Bottom */
                        Color.Yellow, Color.Yellow, Color.Yellow,
                        Color.Yellow, Color.Yellow, Color.Yellow,
                        Color.Yellow, Color.Yellow, Color.Yellow
                }
        };

        assert(cube.arrangementEquals(solvedArrangement));
    }

    @Test
    public void test() {
        assert(true);
    }


}
