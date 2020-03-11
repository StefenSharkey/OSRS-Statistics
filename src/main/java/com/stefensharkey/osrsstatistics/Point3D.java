package com.stefensharkey.osrsstatistics;

import java.awt.Point;
import java.io.Serializable;

public class Point3D implements Serializable {

    public int x;
    public int y;
    public int plane;

    public Point3D() {
        this(0, 0, 0);
    }

    public Point3D(Point3D point3D) {
        this(point3D.x, point3D.y, point3D.plane);
    }

    public Point3D(Point point, int plane) {
        this(point.x, point.y, plane);
    }

    public Point3D(net.runelite.api.Point point, int plane) {
        this(point.getX(), point.getY(), plane);
    }

    public Point3D(int x, int y, int plane) {
        this.x = x;
        this.y = y;
        this.plane = plane;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setPlane(int plane) {
        this.plane = plane;
    }

    public Point3D getLocation() {
        return new Point3D(x, y, plane);
    }

    public void setLocation(Point3D point3D) {
        setLocation(point3D.x, point3D.y, point3D.plane);
    }

    public void setLocation(Point point, int plane) {
        setLocation(point.x, point.y, plane);
    }

    public void setLocation(net.runelite.api.Point point, int plane) {
        setLocation(point.getX(), point.getY(), plane);
    }

    public void setLocation(int x, int y, int plane) {
        this.x = x;
        this.y = y;
        this.plane = plane;
    }

    public void setLocation(double x, double y, int plane) {
        setLocation((int) Math.round(x), (int) Math.round(y), plane);
    }

    public void translate(int dx, int dy, int dPlane) {
        x += dx;
        y += dy;
        plane += dPlane;
    }

    public Point toAwtPoint() {
        return new Point(x, y);
    }

    public net.runelite.api.Point toRuneLitePoint() {
        return new net.runelite.api.Point(x, y);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Point3D) {
            Point3D point3D = (Point3D) obj;
            return x == point3D.x && y == point3D.y && plane == point3D.plane;
        }

        return super.equals(obj);
    }

    @Override
    public String toString() {
        return getClass().getName() + "[x=" + x + ",y=" + y + ",plane=" + plane + "]";
    }
}
