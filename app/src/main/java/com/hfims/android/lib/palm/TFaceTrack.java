package com.hfims.android.lib.palm;

import android.graphics.Point;
import android.graphics.Rect;

public class TFaceTrack {

    private long trackId;
    private Rect rect;
    private Point center;
    private int radius;

    public TFaceTrack() {
    }

    public long getTrackId() {
        return this.trackId;
    }

    public void setTrackId(long trackId) {
        this.trackId = trackId;
    }

    public Rect getRect() {
        return this.rect;
    }

    public void setRect(Rect rect) {
        this.rect = rect;
    }

    public Point getCenter() {
        return this.center;
    }

    public void setCenter(Point center) {
        this.center = center;
    }

    public int getRadius() {
        return this.radius;
    }

    public void setRadius(int radius) {
        this.radius = radius;
    }

    public String toString() {
        return "TFaceTrack{trackId=" + this.trackId + ", rect=" + this.rect + ", center=" + this.center + ", radius=" + this.radius + '}';
    }
}
