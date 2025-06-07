package org.example;

import java.util.Objects;

import org.locationtech.jts.geom.Coordinate;

class CoordKey {
    final double x, y;

    // A utility class used to define a unique key for a coordinate based on its x
    // and y values,
    // rounded to 7 decimal places to avoid floating-point precision errors.
    CoordKey(Coordinate c) {
        this.x = round7(c.x);
        this.y = round7(c.y);
    }

    // Override equals to compare coordinates accurately using rounded values.
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CoordKey))
            return false;
        CoordKey other = (CoordKey) o;
        return Double.compare(x, other.x) == 0 && Double.compare(y, other.y) == 0;
    }

    // Generates a hash code based on x and y to use CoordKey as a key in hash-based
    // collections
    @Override
    public int hashCode() {
        return Objects.hash(x, y);
        // or for faster prformance
        // long bitsX = Double.doubleToLongBits(x);
        // long bitsY = Double.doubleToLongBits(y);
        // return (int) (bitsX ^ (bitsX >>> 32)) * 31 + (int) (bitsY ^ (bitsY >>> 32));
    }

    private static double round7(double val) {
        return Math.round(val * 1e7) / 1e7;
    }
}
