/*
 * Graphitizer
 * Copyright (C) 2026 Willem Langeveld
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.verifiedlogic.graphitizer;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.awt.geom.Point2D;

public class ImageAnalyzer {

    // RGB Euclidean distance tolerance
    private static final double COLOR_TOLERANCE = 40.0;

    public static List<Point2D.Double> findSimilarPoints(BufferedImage image, Point referencePixel,
            Rectangle searchBounds,
            double matchThreshold) {
        List<Point2D.Double> foundPoints = new ArrayList<>();

        // 1. Extract the template (mask) at the reference point
        Rectangle templateBounds = extractTemplateBounds(image, referencePixel);
        if (templateBounds == null) {
            return foundPoints; // Could not find a distinct blob
        }

        BufferedImage template = image.getSubimage(templateBounds.x, templateBounds.y, templateBounds.width,
                templateBounds.height);

        // 2. Scan the search bounds for matches
        List<Point2D.Double> rawMatches = new ArrayList<>();
        int searchMaxX = searchBounds.x + searchBounds.width - templateBounds.width;
        int searchMaxY = searchBounds.y + searchBounds.height - templateBounds.height;

        for (int y = searchBounds.y; y <= searchMaxY; y++) {
            for (int x = searchBounds.x; x <= searchMaxX; x++) {
                double score = calculateRMSE(image, template, x, y);
                if (score < matchThreshold) {
                    // Center of the match with sub-pixel 0.5 offset
                    rawMatches.add(new Point2D.Double(x + templateBounds.width / 2.0 + 0.5,
                            y + templateBounds.height / 2.0 + 0.5));
                }
            }
        }

        // 3. Apply Non-Maximum Suppression (NMS) to remove overlapping duplicates
        return applyNMS(rawMatches, Math.max(templateBounds.width, templateBounds.height));
    }

    private static Rectangle extractTemplateBounds(BufferedImage image, Point start) {
        int width = image.getWidth();
        int height = image.getHeight();

        if (start.x < 0 || start.x >= width || start.y < 0 || start.y >= height) {
            return null;
        }

        int targetRGB = image.getRGB(start.x, start.y);
        Color targetColor = new Color(targetRGB, true);

        // Flood fill to find connected pixels of similar color
        boolean[][] visited = new boolean[width][height];
        Queue<Point> queue = new LinkedList<>();
        List<Point> blobPixels = new ArrayList<>();

        queue.add(start);
        visited[start.x][start.y] = true;

        int minX = start.x;
        int maxX = start.x;
        int minY = start.y;
        int maxY = start.y;

        // Limit maximum template size to prevent analyzing half the graph as one
        // "point"
        int MAX_BLOB_SIZE = 1000;

        while (!queue.isEmpty() && blobPixels.size() < MAX_BLOB_SIZE) {
            Point p = queue.poll();
            blobPixels.add(p);

            minX = Math.min(minX, p.x);
            maxX = Math.max(maxX, p.x);
            minY = Math.min(minY, p.y);
            maxY = Math.max(maxY, p.y);

            // Check 4-way neighbors
            int[][] dirs = { { 0, 1 }, { 0, -1 }, { 1, 0 }, { -1, 0 } };
            for (int[] dir : dirs) {
                int nx = p.x + dir[0];
                int ny = p.y + dir[1];

                if (nx >= 0 && nx < width && ny >= 0 && ny < height && !visited[nx][ny]) {
                    Color neighborColor = new Color(image.getRGB(nx, ny), true);
                    if (colorDistance(targetColor, neighborColor) <= COLOR_TOLERANCE) {
                        visited[nx][ny] = true;
                        queue.add(new Point(nx, ny));
                    }
                }
            }
        }

        if (blobPixels.isEmpty() || blobPixels.size() == MAX_BLOB_SIZE) {
            return null; // Invalid or too large
        }

        // Add a 1-pixel margin to capture slightly fuzzy edges of the shape
        int margin = 1;
        minX = Math.max(0, minX - margin);
        minY = Math.max(0, minY - margin);
        maxX = Math.min(width - 1, maxX + margin);
        maxY = Math.min(height - 1, maxY + margin);

        int boundWidth = maxX - minX + 1;
        int boundHeight = maxY - minY + 1;

        return new Rectangle(minX, minY, boundWidth, boundHeight);
    }

    private static double calculateRMSE(BufferedImage image, BufferedImage template, int startX, int startY) {
        long sumSqDiff = 0;
        int tw = template.getWidth();
        int th = template.getHeight();

        for (int ty = 0; ty < th; ty++) {
            for (int tx = 0; tx < tw; tx++) {
                Color c1 = new Color(template.getRGB(tx, ty), true);
                Color c2 = new Color(image.getRGB(startX + tx, startY + ty), true);

                int dr = c1.getRed() - c2.getRed();
                int dg = c1.getGreen() - c2.getGreen();
                int db = c1.getBlue() - c2.getBlue();

                sumSqDiff += (dr * dr) + (dg * dg) + (db * db);
            }
        }

        double meanSqDiff = (double) sumSqDiff / (tw * th);
        return Math.sqrt(meanSqDiff);
    }

    private static double colorDistance(Color c1, Color c2) {
        int rDiff = c1.getRed() - c2.getRed();
        int gDiff = c1.getGreen() - c2.getGreen();
        int bDiff = c1.getBlue() - c2.getBlue();
        return Math.sqrt((rDiff * rDiff) + (gDiff * gDiff) + (bDiff * bDiff));
    }

    private static List<Point2D.Double> applyNMS(List<Point2D.Double> rawMatches, int suppressionRadius) {
        List<Point2D.Double> filtered = new ArrayList<>();
        double radiusSq = suppressionRadius * suppressionRadius;

        for (Point2D.Double p : rawMatches) {
            boolean isDuplicate = false;
            for (Point2D.Double f : filtered) {
                if (p.distanceSq(f) <= radiusSq) {
                    isDuplicate = true;
                    // For a basic NMS, we just keep the first one we find in an area.
                    // A better approach would be keeping the one with the lowest RMSE score,
                    // but since they are all within threshold, the first is usually sufficient for
                    // graph points.
                    break;
                }
            }
            if (!isDuplicate) {
                filtered.add(p);
            }
        }
        return filtered;
    }

    public static List<Point2D.Double> traceLine(BufferedImage image, Point seedPixel, Rectangle bounds,
            double colorTolerance) {
        List<Point2D.Double> linePoints = new ArrayList<>();

        if (seedPixel.x < bounds.x || seedPixel.x >= bounds.x + bounds.width ||
                seedPixel.y < bounds.y || seedPixel.y >= bounds.y + bounds.height) {
            return linePoints;
        }

        Color targetColor = new Color(image.getRGB(seedPixel.x, seedPixel.y), true);
        int stepSize = 1;

        // Trace Left
        List<Point2D.Double> leftPoints = traceDirection(image, targetColor, seedPixel, bounds, colorTolerance, -1,
                stepSize);
        // Trace Right
        List<Point2D.Double> rightPoints = traceDirection(image, targetColor, seedPixel, bounds, colorTolerance, 1,
                stepSize);

        // Combine and sort
        linePoints.addAll(leftPoints);
        linePoints.add(new Point2D.Double(seedPixel.x + 0.5, seedPixel.y + 0.5));
        linePoints.addAll(rightPoints);

        linePoints.sort((p1, p2) -> Double.compare(p1.x, p2.x));
        return linePoints;
    }

    private static List<Point2D.Double> traceDirection(BufferedImage image, Color targetColor, Point startPoint,
            Rectangle bounds, double colorTolerance, int dirX, int stepSize) {
        List<Point2D.Double> points = new ArrayList<>();
        double currentX = startPoint.x + (dirX * stepSize);
        double currentY = startPoint.y;

        int lookaheadAttempts = 0;
        int maxLookahead = 4; // Number of failed steps to tolerate before giving up

        while (currentX >= bounds.x && currentX < bounds.x + bounds.width) {
            // Scan vertical slice to find the single best matching pixel
            int sliceRadius = 30; // Increased radius to catch steep slopes

            int intCurrentX = (int) Math.round(currentX);
            int intCurrentY = (int) Math.round(currentY);
            int minY = Math.max(bounds.y, intCurrentY - sliceRadius);
            int maxY = Math.min(bounds.y + bounds.height - 1, intCurrentY + sliceRadius);

            double minDistance = Double.MAX_VALUE;

            // Pass 1: Find the absolute minimum color distance in the slice
            for (int y = minY; y <= maxY; y++) {
                Color c = new Color(image.getRGB(intCurrentX, y), true);
                double dist = colorDistance(targetColor, c);
                if (dist < minDistance) {
                    minDistance = dist;
                }
            }

            double sumYWeight = 0;
            double totalWeight = 0;

            // Pass 2: Calculate weighted center of mass sub-pixel interpolation
            if (minDistance <= colorTolerance) {
                for (int y = minY; y <= maxY; y++) {
                    Color c = new Color(image.getRGB(intCurrentX, y), true);
                    double dist = colorDistance(targetColor, c);

                    double localTolerance = 15.0; // Window of contextual influence
                    if (dist <= minDistance + localTolerance) {
                        // Calculate weight (0.0 to 1.0) using distance and apply a cubic curve to
                        // tightly group influence
                        double weight = 1.0 - ((dist - minDistance) / localTolerance);
                        weight = weight * weight * weight;

                        sumYWeight += (y + 0.5) * weight;
                        totalWeight += weight;
                    }
                }
            }

            double bestY = Double.NaN;
            if (totalWeight > 0) {
                bestY = sumYWeight / totalWeight;
            }

            if (!Double.isNaN(bestY)) {
                currentY = bestY; // Snap firmly to the sub-pixel calculated core
                points.add(new Point2D.Double(intCurrentX + 0.5, currentY));
                lookaheadAttempts = 0; // Reset lookahead
            } else {
                // Lookahead: line might be crossed by a gridline
                lookaheadAttempts++;
                if (lookaheadAttempts > maxLookahead) {
                    break; // Lost the line completely
                }
            }

            currentX += (dirX * stepSize);
        }

        return points;
    }

    public static List<Point2D.Double> resampleTraceByXDistance(List<Point2D.Double> rawTrace, int xDistance) {
        if (rawTrace == null || rawTrace.isEmpty()) {
            return new ArrayList<>();
        }
        if (xDistance <= 0) {
            return new ArrayList<>(rawTrace);
        }

        List<Point2D.Double> resampled = new ArrayList<>();
        // rawTrace is already sorted by X, so get(0) is the left-most point
        Point2D.Double lastAdded = rawTrace.get(0);
        resampled.add(lastAdded);

        for (int i = 1; i < rawTrace.size(); i++) {
            Point2D.Double current = rawTrace.get(i);
            if (Math.abs(current.x - lastAdded.x) >= xDistance) {
                resampled.add(current);
                lastAdded = current;
            }
        }

        return resampled;
    }

    /**
     * Applies a 4-point Keystone (Perspective) warp to the given image.
     * pts must be ordered: Top-Left, Top-Right, Bottom-Right, Bottom-Left
     */
    public static BufferedImage applyKeystoneWarp(BufferedImage src, Point2D.Double[] pts) {
        if (pts == null || pts.length != 4)
            return src;

        // Estimate target dimensions based on max width/height of the quad
        double topW = pts[1].distance(pts[0]);
        double botW = pts[2].distance(pts[3]);
        double leftH = pts[3].distance(pts[0]);
        double rightH = pts[2].distance(pts[1]);

        int dstWidth = (int) Math.round(Math.max(topW, botW));
        int dstHeight = (int) Math.round(Math.max(leftH, rightH));

        if (dstWidth <= 0 || dstHeight <= 0)
            return src;

        Point2D.Double[] dstPts = new Point2D.Double[] {
                new Point2D.Double(0, 0),
                new Point2D.Double(dstWidth, 0),
                new Point2D.Double(dstWidth, dstHeight),
                new Point2D.Double(0, dstHeight)
        };

        double[] h = computeHomography(dstPts, pts); // Map Dst -> Src for inverse sampling
        if (h == null)
            return src;

        BufferedImage dst = new BufferedImage(dstWidth, dstHeight, BufferedImage.TYPE_INT_ARGB);
        int srcW = src.getWidth();
        int srcH = src.getHeight();

        for (int y = 0; y < dstHeight; y++) {
            for (int x = 0; x < dstWidth; x++) {
                double denominator = h[6] * x + h[7] * y + h[8];
                double srcX = (h[0] * x + h[1] * y + h[2]) / denominator;
                double srcY = (h[3] * x + h[4] * y + h[5]) / denominator;

                int sx = (int) Math.round(srcX);
                int sy = (int) Math.round(srcY);

                if (sx >= 0 && sx < srcW && sy >= 0 && sy < srcH) {
                    dst.setRGB(x, y, src.getRGB(sx, sy));
                }
            }
        }
        return dst;
    }

    /**
     * Computes the 3x3 homography matrix (flattened to 9 elements) mapping src to
     * dst.
     * Uses Direct Linear Transformation (DLT).
     */
    private static double[] computeHomography(Point2D.Double[] src, Point2D.Double[] dst) {
        double[][] a = new double[8][8];
        double[] b = new double[8];

        for (int i = 0; i < 4; i++) {
            double sx = src[i].x;
            double sy = src[i].y;
            double dx = dst[i].x;
            double dy = dst[i].y;

            a[i * 2][0] = sx;
            a[i * 2][1] = sy;
            a[i * 2][2] = 1;
            a[i * 2][3] = 0;
            a[i * 2][4] = 0;
            a[i * 2][5] = 0;
            a[i * 2][6] = -sx * dx;
            a[i * 2][7] = -sy * dx;
            b[i * 2] = dx;

            a[i * 2 + 1][0] = 0;
            a[i * 2 + 1][1] = 0;
            a[i * 2 + 1][2] = 0;
            a[i * 2 + 1][3] = sx;
            a[i * 2 + 1][4] = sy;
            a[i * 2 + 1][5] = 1;
            a[i * 2 + 1][6] = -sx * dy;
            a[i * 2 + 1][7] = -sy * dy;
            b[i * 2 + 1] = dy;
        }

        double[] x = solveLinearSystem(a, b);
        if (x == null)
            return null;

        return new double[] { x[0], x[1], x[2], x[3], x[4], x[5], x[6], x[7], 1.0 };
    }

    /**
     * Solves Ax = b using Gaussian elimination with partial pivoting.
     */
    private static double[] solveLinearSystem(double[][] a, double[] b) {
        int n = b.length;
        for (int p = 0; p < n; p++) {
            int max = p;
            for (int i = p + 1; i < n; i++) {
                if (Math.abs(a[i][p]) > Math.abs(a[max][p])) {
                    max = i;
                }
            }
            double[] temp = a[p];
            a[p] = a[max];
            a[max] = temp;
            double t = b[p];
            b[p] = b[max];
            b[max] = t;

            if (Math.abs(a[p][p]) <= 1e-10)
                return null; // Singular

            for (int i = p + 1; i < n; i++) {
                double alpha = a[i][p] / a[p][p];
                b[i] -= alpha * b[p];
                for (int j = p; j < n; j++) {
                    a[i][j] -= alpha * a[p][j];
                }
            }
        }

        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double sum = 0.0;
            for (int j = i + 1; j < n; j++) {
                sum += a[i][j] * x[j];
            }
            x[i] = (b[i] - sum) / a[i][i];
        }
        return x;
    }
}
