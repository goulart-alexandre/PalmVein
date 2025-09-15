package com.hfims.android.lib.palm;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class TrackView extends View {
    private static final int TINY_DISTANCE = 0; //30;
    private static final int RECTANGLE_PADDING_TOP = 45;

    public enum DisplayType {
        /**
         * 不显示
         */
        NONE,
        /**
         * 矩形跟踪
         */
        RECTANGLE
    }

    /**
     * 画笔工具
     */
    private Paint mLinePaint;
    /**
     * 人脸跟踪列表
     */
    private List<TFaceTrack> faceTrackList;

    /**
     * 矩形人脸框Bitmap
     */
    private Bitmap rectangleBmp;

    private HashMap<String, TFaceTrack> lastTrackMap;

    private int rectangleWidth;
    private int rectangleHeight;

    private final DisplayType displayType = DisplayType.RECTANGLE;

    public TrackView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
    }

    /**
     * 调用此方法 用于绘制人脸框
     */
    public synchronized void setFaces(List<TFaceTrack> trackList) {
        if (null != trackList && null != faceTrackList && !faceTrackList.isEmpty()) {
            boolean faceExist;
            Iterator<TFaceTrack> it = faceTrackList.iterator();
            while (it.hasNext()) {
                TFaceTrack faceTrack = it.next();
                faceExist = false;
                for (TFaceTrack newFaceTrack : trackList) {
                    if (faceTrack.getTrackId() == newFaceTrack.getTrackId()) {
                        faceExist = true;
                        faceTrack.setCenter(newFaceTrack.getCenter());
                        faceTrack.setRadius(newFaceTrack.getRadius());
                        faceTrack.setRect(newFaceTrack.getRect());
                        break;
                    }
                }
                if (!faceExist) {
                    it.remove();
                }
            }
        } else {
            this.faceTrackList = trackList;
        }

        try {
            invalidate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void initPaint() {
        mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mLinePaint.setStyle(Paint.Style.STROKE);
        mLinePaint.setStrokeWidth(3f);
        mLinePaint.setAntiAlias(true);

        lastTrackMap = new HashMap<>();

        if (displayType == DisplayType.RECTANGLE) {
            rectangleBmp = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_track_blue);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
        super.onSizeChanged(w, h, oldWidth, oldHeight);

        if (null != rectangleBmp) {
            rectangleWidth = rectangleBmp.getWidth() / 2;
            rectangleHeight = rectangleBmp.getHeight() / 2;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            if (DisplayType.NONE == displayType) {
                return;
            }
            if (faceTrackList != null) {
                TFaceTrack lastTrack;
                for (TFaceTrack face : faceTrackList) {
                    if (lastTrackMap.containsKey(String.valueOf(face.getTrackId()))) {
                        lastTrack = lastTrackMap.get(String.valueOf(face.getTrackId()));
                        if (!isTinyDistance(lastTrack, face)) {
                            lastTrackMap.put(String.valueOf(face.getTrackId()), face);
                        }
                    } else {
                        lastTrackMap.put(String.valueOf(face.getTrackId()), face);
                    }
                }

                for (TFaceTrack face : faceTrackList) {
                    if (lastTrackMap.containsKey(String.valueOf(face.getTrackId()))) {
                        lastTrack = lastTrackMap.get(String.valueOf(face.getTrackId()));
                    } else {
                        lastTrack = face;
                        lastTrackMap.put(String.valueOf(face.getTrackId()), face);
                    }

                    drawRectangle(canvas, rectangleBmp, rectangleWidth, rectangleHeight, lastTrack);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDraw(canvas);
    }

    private void drawRectangle(Canvas canvas, Bitmap bitmap, int width, int height, TFaceTrack face) {
        canvas.save();
        Matrix matrix = new Matrix();
        matrix.postTranslate(-width, -height);
        matrix.postTranslate(face.getCenter().x, face.getCenter().y - RECTANGLE_PADDING_TOP);
        float scaleWidth = face.getRadius() * 1.0f / width;
        float scaleHeight = face.getRadius() * 1.0f / height;
        matrix.postScale(scaleWidth, scaleHeight, face.getCenter().x, face.getCenter().y);
        canvas.drawBitmap(bitmap, matrix, mLinePaint);
        canvas.restore();
    }

    private boolean isTinyDistance(TFaceTrack originTrack, TFaceTrack newTrack) {
        if (null == originTrack) {
            return false;
        }
        boolean tiny = true;
        if (Math.abs(originTrack.getCenter().x - newTrack.getCenter().x) > TINY_DISTANCE
                || Math.abs(originTrack.getCenter().y - newTrack.getCenter().y) > TINY_DISTANCE) {
            tiny = false;
        }
        return tiny;
    }
}
