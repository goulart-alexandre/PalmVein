package com.hfims.android.lib.palm;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.hfims.android.core.util.UnitUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class TextureView extends android.view.TextureView implements android.view.TextureView.SurfaceTextureListener {
    private static final String TAG = "CustomTextureView";

    public static int CAMERA_WIDTH = SettingVar.cameraWidth;
    public static int CAMERA_HEIGHT = SettingVar.cameraHeight;

    public static int REAL_WIDTH;
    public static int REAL_HEIGHT;

    private LinearLayout frame = null;
    private RelativeLayout innerFrame = null;
    private boolean cameraConfigured;

    private SurfaceTexture mSurfaceTexture;
    private Camera mCamera;
    private final Context mContext;

    private int previewWidth;
    private int previewHeight;

    private OnCameraDataEnableListener listener;

    private Timer timer = new Timer();
    private long count = 0;
    private long oldCount = 0;
    private int cameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private boolean isCheckCamera = true;

    private byte[] mPicBuffer;

    public LinearLayout getFrame() {
        return frame;
    }

    public void setListener(OnCameraDataEnableListener listener) {
        this.listener = listener;
    }

    /**
     * 初始化相机
     */
    public void initCamera(int cameraId, OnCameraDataEnableListener listener) {
        if (mCamera == null) {
            try {
                mCamera = Camera.open(cameraId);
            } catch (Exception e) {
                e.printStackTrace();
                //相机不可用
                cameraDisable();
            }
        }

        if (mCamera != null) {
            try {
                mCamera.setPreviewTexture(mSurfaceTexture);
                mCamera.setPreviewCallbackWithBuffer(frameCallback);

                mCamera.setDisplayOrientation(SettingVar.previewRotation);
                initPreviewSize(UnitUtils.getScreenWidth(getContext()), UnitUtils.getScreenHeight(getContext()));

                Camera.Parameters parameters = mCamera.getParameters();
                parameters.setAntibanding(Camera.Parameters.ANTIBANDING_AUTO);

//                if (LanguageUtils.getCurrentLocale().getLanguage().equals(Locale.KOREA.getLanguage())) {
//                    parameters.setAntibanding(Camera.Parameters.ANTIBANDING_60HZ);
//                }

//                String antibanding = parameters.getAntibanding();
//                LogUtils.tag(TAG).dToFile(cameraId + " Camera antibanding: " + antibanding);

                PixelFormat pixelinfo = new PixelFormat();
                int pixelformat = parameters.getPreviewFormat();
                PixelFormat.getPixelFormatInfo(pixelformat, pixelinfo);

                Camera.Size sz = parameters.getPreviewSize();
                int bufSize = sz.width * sz.height * pixelinfo.bitsPerPixel / 8;
                if (mPicBuffer == null || mPicBuffer.length != bufSize) {
                    mPicBuffer = new byte[bufSize];
                }
                mCamera.addCallbackBuffer(mPicBuffer);

                //摄像头异常监听
                mCamera.setErrorCallback((error, camera) -> {
                    String error_str;
                    switch (error) {
                        case Camera.CAMERA_ERROR_SERVER_DIED: // 摄像头已损坏
                            error_str = "CAMERA_ERROR_SERVER_DIED";
                            break;
                        case Camera.CAMERA_ERROR_UNKNOWN: // 摄像头异常
                            error_str = "CAMERA_ERROR_UNKNOWN";
                            break;
                        default:
                            error_str = String.valueOf(error);
                            break;
                    }
                });

                mCamera.startPreview();
                confirmCameraData();
            } catch (Exception e) {
                e.printStackTrace();
                //相机不可用
                cameraDisable();
            }
        } else {
            cameraDisable();
        }
    }

    public TextureView(Context context) {
        super(context);
        mContext = context.getApplicationContext();
        init();
    }

    public TextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context.getApplicationContext();
        init();
    }

    public TextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mContext = context.getApplicationContext();
        init();
    }

    public TextureView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mContext = context.getApplicationContext();
        init();
    }

    public int getPreviewWidth() {
        return previewWidth;
    }

    public void setPreviewWidth(int previewWidth) {
        this.previewWidth = previewWidth;
    }

    public int getPreviewHeight() {
        return previewHeight;
    }

    public void setPreviewHeight(int previewHeight) {
        this.previewHeight = previewHeight;
    }

    private void init() {
        innerFrame = new RelativeLayout(getContext());
        innerFrame.addView(this);
        frame = new LinearLayout(getContext());
        frame.addView(innerFrame);

        setSurfaceTextureListener(this);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
//        LogUtils.tag(TAG).d("onSurfaceTextureAvailable -> width:" + width + ", height:" + height);
        mSurfaceTexture = surface;
//        handler.sendEmptyMessageDelayed(0, 300);
        initCamera(cameraId, listener);
//        initCamera(cameraId);
//        initPreviewSize(width, height);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        initPreviewSize(width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        closeCamera();
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
//        LogUtils.tag(TAG).d("onSurfaceTextureUpdated()");
    }


    /**
     * 检查Camera是否有数据
     */
    private void confirmCameraData() {
        if (timer == null) {
            timer = new Timer();
        }
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
//                LogUtils.d("confirmCameraData-----------timer");
                if (count > oldCount) {
                    oldCount = count;
                } else {
                    cameraDisable();
                }
            }
        }, 20 * 1000, 10 * 1000);
    }

    /**
     * 相机不可用
     */
    private void cameraDisable() {
        if (isCheckCamera) {
//            CameraErrorRestart.reStartCamera(mContext);
            isCheckCamera = false;
            /* CameraErrorRestart.reStartCamera(mContext);*/
//            if (null != listener){
//                listener.onCameraNoData();
//                isCheckCamera = false;
//            }else {
//                isCheckCamera = true;
//            }
        }
    }

    /**
     * 关闭照相机
     */
    public void closeCamera() {
        handler.removeCallbacksAndMessages(null);
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        if (null != mCamera) {
            //一定要设置为空
            mCamera.setErrorCallback(null);
            mCamera.setPreviewCallback(null);
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }

    /**
     * 相机数据回调
     */
    Camera.PreviewCallback frameCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            //传递进来的data,默认是YUV420SP的
//            LogUtils.tag(TAG).d("frameCallback(), length=" + ((null == data) ? 0 : data.length));
            if (data != null && data.length > 0) {
                mCamera.addCallbackBuffer(data);
                listener.onCameraDataCallback(data, cameraId);
                count++;
            }

        }
    };

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 0:
                    initCamera(cameraId, listener);
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };

    public void takePicture(Camera.PictureCallback callback) {
        try {
            mCamera.takePicture(null, null, callback);
        } catch (Exception e) {
            e.printStackTrace();
            callback.onPictureTaken(null, mCamera);
        }
    }

    public void restartPreview() {
        try {
            mCamera.startPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stopPreview() {
        try {
            mCamera.stopPreview();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isSupportedPreviewSize(int width, int height, Camera mCamera) {
        Camera.Parameters camPara = mCamera.getParameters();
        List<Camera.Size> allSupportedSize = camPara.getSupportedPreviewSizes();
        for (Camera.Size tmpSize : allSupportedSize) {
            Log.i(TAG, "supported width is: " + tmpSize.width + ", height is: " + tmpSize.height);
            if (tmpSize.height == height && tmpSize.width == width) {
                return true;
            }
        }
        return false;
    }

    private Camera.Size getBigPreviewSize(Camera mCamera) {
        Camera.Parameters parameters = mCamera.getParameters();
        Camera.Size result = null;
        int maxWidth = 0;
        for (Camera.Size size : parameters.getSupportedPreviewSizes()) {
            Log.i(TAG, "supported width is: " + size.width + ", height is: " + size.height);
            if (0 == maxWidth) {
                maxWidth = size.width;
                result = size;
            } else {
                if (size.width > maxWidth && (Math.abs(maxWidth - UnitUtils.getScreenHeight(mContext)) > Math.abs(size.width - UnitUtils.getScreenHeight(mContext)))) {
                    maxWidth = size.width;
                    result = size;
                }
            }
        }
        return result;
    }

    private static Camera.Size getBestPreviewSize(Camera mCamera) {
        Camera.Parameters camPara = mCamera.getParameters();
        List<Camera.Size> allSupportedSize = camPara.getSupportedPreviewSizes();
        ArrayList<Camera.Size> widthLargerSize = new ArrayList<>();
        int max = Integer.MIN_VALUE;
        Camera.Size maxSize = null;
        for (Camera.Size tmpSize : allSupportedSize) {
            Log.i(TAG, "supported width is: " + tmpSize.width + ", height is: " + tmpSize.height);
            int multi = tmpSize.height * tmpSize.width;
            if (multi > max) {
                max = multi;
                maxSize = tmpSize;
            }
            //选分辨率比较高的
            if (tmpSize.width > tmpSize.height && (tmpSize.width > SettingVar.cameraHeight / 2 || tmpSize.height > SettingVar.cameraWidth / 2)) {
                widthLargerSize.add(tmpSize);
            }
        }
        if (widthLargerSize.isEmpty()) {
            widthLargerSize.add(maxSize);
        }

        final float propotion = SettingVar.cameraWidth >= SettingVar.cameraHeight ? (float) SettingVar.cameraWidth / (float) SettingVar.cameraHeight : (float) SettingVar.cameraHeight / (float) SettingVar.cameraWidth;

        Collections.sort(widthLargerSize, (lhs, rhs) -> {
            //                                int off_one = Math.abs(lhs.width * lhs.height - Screen.mWidth * Screen.mHeight);
            //                                int off_two = Math.abs(rhs.width * rhs.height - Screen.mWidth * Screen.mHeight);
            //                                return off_one - off_two;
            //选预览比例跟屏幕比例比较接近的
            float a = getPropotionDiff(lhs, propotion);
            float b = getPropotionDiff(rhs, propotion);
            return (int) ((a - b) * 10000);
        });

        float minPropotionDiff = getPropotionDiff(widthLargerSize.get(0), propotion);
        ArrayList<Camera.Size> validSizes = new ArrayList<>();
        for (int i = 0; i < widthLargerSize.size(); i++) {
            Camera.Size size = widthLargerSize.get(i);
            float propotionDiff = getPropotionDiff(size, propotion);
            if (propotionDiff > minPropotionDiff) {
                break;
            }
            validSizes.add(size);
        }

        Collections.sort(validSizes, (lhs, rhs) -> rhs.width * rhs.height - lhs.width * lhs.height);
        return widthLargerSize.get(0);
    }

    public static float getPropotionDiff(Camera.Size size, float standardPropotion) {
        return Math.abs((float) size.width / (float) size.height - standardPropotion);
    }

    private void initPreviewSize(int width, int height) {
        //利用布局，重设宽高来撑开界面，超出的部分直接撑出屏幕
        if (mCamera != null) {
            if (!cameraConfigured) {
                Camera.Parameters parameters = mCamera.getParameters();
                if (SettingVar.cameraWidth > 0 && SettingVar.cameraHeight > 0 && isSupportedPreviewSize(SettingVar.cameraWidth, SettingVar.cameraHeight, mCamera)) {
                    CAMERA_WIDTH = SettingVar.cameraWidth;
                    CAMERA_HEIGHT = SettingVar.cameraHeight;
                    parameters.setPreviewSize(CAMERA_WIDTH, CAMERA_HEIGHT);
                } else {
                    Camera.Size bestPreviewSize = getBigPreviewSize(mCamera);
                    Log.i(TAG, "best width is: " + bestPreviewSize.width + ", height is: " + bestPreviewSize.height);
                    CAMERA_WIDTH = bestPreviewSize.width;
                    CAMERA_HEIGHT = bestPreviewSize.height;
                    parameters.setPreviewSize(CAMERA_WIDTH, CAMERA_HEIGHT);
                }
                Log.i(TAG, "camera width: " + CAMERA_WIDTH + ", height: " + CAMERA_HEIGHT);
                parameters.setPreviewFormat(ImageFormat.NV21);
                parameters.setPictureSize(CAMERA_WIDTH, CAMERA_HEIGHT);

                mCamera.setParameters(parameters);
                cameraConfigured = true;

                // Setting up correctly the view
                double ratio = CAMERA_HEIGHT / (double) CAMERA_WIDTH;
                DisplayMetrics dm = new DisplayMetrics();
                WindowManager windowMgr = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
                windowMgr.getDefaultDisplay().getRealMetrics(dm);
                int y = dm.heightPixels; //getResources().getDisplayMetrics().heightPixels;
                int x = dm.widthPixels; //getResources().getDisplayMetrics().widthPixels;
                ViewGroup.LayoutParams params = innerFrame.getLayoutParams();

                if (90 == SettingVar.previewRotation || 270 == SettingVar.previewRotation) {
                    params.height = y;
                    params.width = (int) (y * ratio);

                    if (params.width < x) {
                        double ratioWidth = x / (double) params.width;
                        params.width = x;
                        params.height *= ratioWidth;
                    }
                } else {
                    params.height = y;
                    params.width = (int) (y / ratio);
                }
                innerFrame.setLayoutParams(params);
                int deslocationX = (int) (params.width / 2.0 - x / 2.0);
                innerFrame.animate().translationX(-deslocationX);

                REAL_WIDTH = params.width;
                REAL_HEIGHT = params.height;
            }
        }
    }

    public int getCameraId() {
        return cameraId;
    }

    public void setCameraId(int cameraId) {
        this.cameraId = cameraId;
    }

    public interface OnCameraDataEnableListener {

        void onCameraDataCallback(byte[] data, int cameraId);
    }

    public interface OnTakePictureCallback {
        void onResult(byte[] data, Camera camera);
    }
}
