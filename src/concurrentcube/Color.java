package concurrentcube;

import java.util.Random;

/**
 * Enumeration of the cube's colors
 */
public enum Color {

    White(0),
    Green(1),
    Red(2),
    Blue(3),
    Orange(4),
    Yellow(5),

    COLORS(6);

    private static final Color[] values = values();

    private final int color;

    private static final Random rand = new Random();

    Color(int color) {
        this.color = color;
    }

    public int intValue() {
        return color;
    }

    public static Color fromInt(int ordinal) {
        return values[ordinal];
    }

    public static Color randomColor() {
        return fromInt(rand.nextInt(COLORS.intValue()));
    }

    public char charValue() {
        return (char)(color + '0');
    }

}
