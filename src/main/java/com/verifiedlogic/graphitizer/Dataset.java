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
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Dataset {
    private String name;
    private Color color;
    private List<Point2D.Double> points;
    private Set<Point2D.Double> manualPoints;

    // Curve-specific Calibration
    private Double pixX1, pixX2, pixY1, pixY2;
    private Double realX1, realX2, realY1, realY2;
    private boolean isLogX = false;
    private boolean isLogY = false;

    public Dataset(String name, Color color) {
        this.name = name;
        this.color = color;
        this.points = new ArrayList<>();
        this.manualPoints = new HashSet<>();
    }

    public void markManual(Point2D.Double p) {
        manualPoints.add(p);
    }

    public boolean isManual(Point2D.Double p) {
        return manualPoints.contains(p);
    }

    public String getName() {
        return name;
    }

    public Color getColor() {
        return color;
    }

    public List<Point2D.Double> getPoints() {
        return points;
    }

    // --- Calibration Getters and Setters ---

    public Double getPixX1() {
        return pixX1;
    }

    public void setPixX1(Double pixX1) {
        this.pixX1 = pixX1;
    }

    public Double getPixX2() {
        return pixX2;
    }

    public void setPixX2(Double pixX2) {
        this.pixX2 = pixX2;
    }

    public Double getPixY1() {
        return pixY1;
    }

    public void setPixY1(Double pixY1) {
        this.pixY1 = pixY1;
    }

    public Double getPixY2() {
        return pixY2;
    }

    public void setPixY2(Double pixY2) {
        this.pixY2 = pixY2;
    }

    public Double getRealX1() {
        return realX1;
    }

    public void setRealX1(Double realX1) {
        this.realX1 = realX1;
    }

    public Double getRealX2() {
        return realX2;
    }

    public void setRealX2(Double realX2) {
        this.realX2 = realX2;
    }

    public Double getRealY1() {
        return realY1;
    }

    public void setRealY1(Double realY1) {
        this.realY1 = realY1;
    }

    public Double getRealY2() {
        return realY2;
    }

    public void setRealY2(Double realY2) {
        this.realY2 = realY2;
    }

    public boolean isLogX() {
        return isLogX;
    }

    public void setLogX(boolean logX) {
        isLogX = logX;
    }

    public boolean isLogY() {
        return isLogY;
    }

    public void setLogY(boolean logY) {
        isLogY = logY;
    }

    @Override
    public String toString() {
        return name;
    }
}
