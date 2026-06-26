import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConvexHull {

    // -------------------------------------------------------------------------
    // Point — immutable 2D point with its original CSV index
    // -------------------------------------------------------------------------
    static class Point {
        final int    index; // original 0-based row index from the CSV
        final double x;
        final double y;

        Point(int index, double x, double y) {
            this.index = index;
            this.x     = x;
            this.y     = y;
        }

        @Override
        public String toString() {
            return String.format("Point{index=%d, x=%s, y=%s}", index, x, y);
        }
    }

    // -------------------------------------------------------------------------
    // Hull — ordered list of Point references in counter-clockwise order
    // -------------------------------------------------------------------------
    static class Hull {
        // Package-accessible so Merger can iterate directly when needed,
        // but all geometric logic should go through the public API below.
        final List<Point> points;

        /**
         * Constructs a Hull from a CCW-ordered list of Points.
         * The caller is responsible for ensuring CCW order.
         */
        Hull(List<Point> points) {
            this.points = new ArrayList<>(points); // defensive copy
        }

        /**
         * Returns the number of vertices on this hull.
         */
        int size() {
            return points.size();
        }

        /**
         * Returns the vertex at position i, with wrap-around support.
         *
         * Examples (hull of size 5):
         *   get(0)  → points[0]
         *   get(5)  → points[0]   (wraps forward)
         *   get(-1) → points[4]   (wraps backward)
         *
         * This allows tangent-walking loops to step freely without
         * manually computing modular indices at each call site.
         */
        Point get(int i) {
            int n = points.size();
            // Java's % can return negative values for negative i,
            // so we add n before taking mod to guarantee a positive result.
            return points.get(((i % n) + n) % n);
        }

        /**
         * Returns the position of Point p in the hull's list, or -1 if absent.
         * Uses reference equality (==), not coordinate equality.
         */
        int indexOf(Point p) {
            for (int i = 0; i < points.size(); i++) {
                if (points.get(i) == p) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Hull[");
            for (int i = 0; i < points.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(points.get(i).index);
            }
            sb.append("]");
            return sb.toString();
        }
    }

    // -------------------------------------------------------------------------
    // InputParser — reads a CSV file and produces a Point[] with original indices
    // -------------------------------------------------------------------------
    static class InputParser {

        /**
         * Reads a CSV file where each non-empty line has the form:
         *   x,y
         * and returns a Point[] in original file order.
         *
         * The returned array satisfies: result[i].index == i for all i.
         *
         * @param filename path to the input CSV file
         * @return array of Point objects in file order
         * @throws IOException if the file cannot be read
         */
        static Point[] parse(String filename) throws IOException {
            List<Point> result = new ArrayList<>();
            int index = 0;

            try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue; // skip blank lines
                    }

                    String[] parts = line.split(",");
                    double x = Double.parseDouble(parts[0].trim());
                    double y = Double.parseDouble(parts[1].trim());
                    result.add(new Point(index, x, y));
                    index++;
                }
            }

            return result.toArray(new Point[0]);
        }
    }

    // -------------------------------------------------------------------------
    // OutputWriter — writes hull vertex indices to a file, one per line
    // -------------------------------------------------------------------------
    static class OutputWriter {

        /**
         * Writes the original 0-based indices of the hull's vertices to a file,
         * one index per line, in CCW order as stored in the Hull.
         *
         * @param hull     the final convex hull
         * @param filename path to the output file (created or overwritten)
         * @throws IOException if the file cannot be written
         */
        static void write(Hull hull, String filename) throws IOException {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename))) {
                for (int i = 0; i < hull.size(); i++) {
                    bw.write(Integer.toString(hull.get(i).index));
                    bw.newLine();
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // BaseCase — produces a valid Hull for 1 or 2 points
    // -------------------------------------------------------------------------
    static class BaseCase {

        /**
         * Computes the convex hull of 1 or 2 points.
         *
         * The input subarray is already sorted by x (per the global input
         * assumption), so no geometric computation is needed — ordering is
         * purely positional.
         *
         * 1 point  → Hull containing just that point.
         * 2 points → Hull containing [points[lo], points[hi]] in CCW order.
         *            With only two points any ordering is trivially valid,
         *            and left-to-right matches the CCW convention used by
         *            the rest of the algorithm (leftmost point first).
         *
         * @param points the full sorted array
         * @param lo     inclusive start index of the subproblem
         * @param hi     inclusive end index of the subproblem (lo <= hi <= lo+1)
         * @return a Hull of the points in points[lo..hi]
         */
        static Hull compute(Point[] points, int lo, int hi) {
            List<Point> hull = new ArrayList<>();
            for (int i = lo; i <= hi; i++) {
                hull.add(points[i]);
            }
            return new Hull(hull);
        }
    }

    // -------------------------------------------------------------------------
    // TangentFinder — finds the upper and lower tangents between two hulls
    // -------------------------------------------------------------------------
    static class TangentFinder {

        /**
         * Returns the signed area of the triangle O->A->B, scaled by 2.
         *
         *   cross(O, A, B) > 0  →  A->B makes a CCW (left) turn at O
         *   cross(O, A, B) < 0  →  A->B makes a CW  (right) turn at O
         *
         * Never returns 0 during tangent search because no three input
         * points are collinear (per global assumption).
         */
        static double cross(Point O, Point A, Point B) {
            return (A.x - O.x) * (B.y - O.y)
                 - (A.y - O.y) * (B.x - O.x);
        }

        /**
         * Finds the upper tangent between the left and right hulls.
         *
         * The upper tangent is the line segment (left.get(l), right.get(r))
         * such that both hulls lie entirely below (or on) the line.
         *
         * Walking rule:
         *   - Advance l CCW on left  while right.get(r) is to the LEFT  of
         *     the current tangent line (i.e. cross > 0), meaning the tangent
         *     can still be raised by moving l.
         *   - Advance r CW  on right while left.get(l)  is to the RIGHT of
         *     the current tangent line (i.e. cross < 0), meaning the tangent
         *     can still be raised by moving r.
         *
         * @return int[]{l, r} — positions into left.points and right.points
         */
        static int[] findUpperTangent(Hull left, Hull right) {
            int l = left.size() - 1;  // start at rightmost point of left hull
            int r = 0;                // start at leftmost point of right hull

            boolean changed = true;
            while (changed) {
                changed = false;

                // Advance l CCW (decreasing index) on left hull while improving
                while (cross(right.get(r), left.get(l), left.get(l - 1)) < 0) {
                    l--;
                    changed = true;
                }

                // Advance r CW (increasing index) on right hull while improving
                while (cross(left.get(l), right.get(r), right.get(r + 1)) > 0) {
                    r++;
                    changed = true;
                }
            }
            //sanitize the data to fix OB indexing
            int n = left.size();
            l = (((l % n) + n) % n);
            int m = right.size();
            r = (((r % m) + m) % m);

            return new int[]{l, r};
        }

        /**
         * Finds the lower tangent between the left and right hulls.
         *
         * The lower tangent is the line segment (left.get(l), right.get(r))
         * such that both hulls lie entirely above (or on) the line.
         *
         * Walking rule (symmetric to upper tangent):
         *   - Advance l CW  on left  while right.get(r) is to the RIGHT of
         *     the current tangent line (i.e. cross < 0).
         *   - Advance r CCW on right while left.get(l)  is to the LEFT  of
         *     the current tangent line (i.e. cross > 0).
         *
         * @return int[]{l, r} — positions into left.points and right.points
         */
        static int[] findLowerTangent(Hull left, Hull right) {
            int l = left.size() - 1;  // start at rightmost point of left hull
            int r = 0;                // start at leftmost point of right hull

            boolean changed = true;
            while (changed) {
                changed = false;

                // Advance l CW (increasing index) on left hull while improving
                while (cross(right.get(r), left.get(l), left.get(l + 1)) > 0) {
                    l++;
                    changed = true;
                }

                // Advance r CCW (decreasing index) on right hull while improving
                while (cross(left.get(l), right.get(r), right.get(r - 1)) < 0) {
                    r--;
                    changed = true;
                }
            }
            //sanitize the data to fix OB indexing
            int n = left.size();
            l = (((l % n) + n) % n);
            int m = right.size();
            r = (((r % m) + m) % m);
            
            return new int[]{l, r};
        }
    }

    // -------------------------------------------------------------------------
    // Merger — stitches two hulls together using their upper and lower tangents
    // -------------------------------------------------------------------------
    static class Merger {

        /**
         * Merges two convex hulls into one using the precomputed tangents.
         *
         * The merged hull is assembled by walking CCW along the surviving
         * vertices of each sub-hull:
         *
         *   1. Walk CCW along left  from lL (lower-left)  to lU (upper-left),
         *      inclusive.
         *   2. Walk CCW along right from rU (upper-right) to rL (lower-right),
         *      inclusive.
         *
         * This traces the full outer boundary of the merged hull in CCW order.
         * Interior vertices — those between the tangent cut points — are
         * simply never visited and drop out naturally.
         *
         * @param left          the left sub-hull in CCW order
         * @param right         the right sub-hull in CCW order
         * @param upperTangent  int[]{lU, rU} from TangentFinder.findUpperTangent
         * @param lowerTangent  int[]{lL, rL} from TangentFinder.findLowerTangent
         * @return a new Hull in CCW order representing the merged convex hull
         */
        static Hull merge(Hull left, Hull right,
                          int[] upperTangent, int[] lowerTangent) {
            int lU = upperTangent[0];
            int rU = upperTangent[1];
            int lL = lowerTangent[0];
            int rL = lowerTangent[1];

            List<Point> merged = new ArrayList<>();

            // Walk CCW along left hull from lL up to and including lU.
            // CCW on the left hull means increasing index (with wrap-around).
            int i = lL;
            while (true) {
                merged.add(left.get(i));
                if (i == lU) break;
                i++;
            }

            // Walk CCW along right hull from rU down to and including rL.
            // CCW on the right hull means increasing index (with wrap-around).
            int j = rU;
            while (true) {
                merged.add(right.get(j));
                if (j == rL) break;
                j++;
            }

            return new Hull(merged);
        }
    }

    // -------------------------------------------------------------------------
    // divideAndConquer — recursive hull computation
    // -------------------------------------------------------------------------

    /**
     * Recursively computes the convex hull of points[lo..hi].
     *
     * Base case : 1 or 2 points → delegate to BaseCase.compute().
     * Recursive : split in half, recurse on each half, merge results.
     *
     * @param points the full input array, sorted ascending by x
     * @param lo     inclusive start index of the current subproblem
     * @param hi     inclusive end index of the current subproblem
     * @return the convex Hull of points[lo..hi]
     */
    static Hull divideAndConquer(Point[] points, int lo, int hi) {
        if (hi - lo + 1 <= 2) {
            return BaseCase.compute(points, lo, hi);
        }

        int mid = (lo + hi) / 2;
        Hull leftHull  = divideAndConquer(points, lo, mid);
        Hull rightHull = divideAndConquer(points, mid + 1, hi);

        int[] upper = TangentFinder.findUpperTangent(leftHull, rightHull);
        int[] lower = TangentFinder.findLowerTangent(leftHull, rightHull);

        return Merger.merge(leftHull, rightHull, upper, lower);
    }

    // -------------------------------------------------------------------------
    // main — entry point
    // -------------------------------------------------------------------------

    /**
     * Usage: java ConvexHull <input.csv> <output.txt>
     *
     * The input CSV must be sorted ascending by x-coordinate.
     * Each row: x,y (no header).
     * Output: one 0-based point index per line, in CCW order.
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("Usage: java ConvexHull <input.csv> <output.txt>");
            System.exit(1);
        }

        String inputFile  = args[0];
        String outputFile = args[1];

        Point[] points = InputParser.parse(inputFile);
        Hull result    = divideAndConquer(points, 0, points.length - 1);
        OutputWriter.write(result, outputFile);
    }
}