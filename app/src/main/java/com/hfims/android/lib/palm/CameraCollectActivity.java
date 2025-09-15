package com.hfims.android.lib.palm;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hfims.android.core.util.ThreadUtils;
import com.hfims.android.lib.palm.model.PalmCamera;
import com.hfims.android.lib.palm.model.PalmTrack;
import com.hfims.android.lib.palm.PalmSdk;
import com.hfims.android.lib.palm.IPalmSdk;
import com.hfims.android.lib.palm.PalmConst;

import java.util.List;
import java.util.Map;

public class CameraCollectActivity extends AppCompatActivity implements TextureView.OnCameraDataEnableListener {

    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_USER_ID = "user_id";
    public static final String EXTRA_PALM_FEATURE = "palm_feature";

    private TextureView mTextureView;
    private int mCameraId = android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK;
    private boolean isCollecting = false;
    private String collectedPalmFeature = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_collect);

        initCamera();
        initPalmSdk();
        startCollect();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mTextureView != null) {
            View surfaceView = mTextureView.getFrame();
            if (surfaceView.getParent() != null) {
                ((ViewGroup) surfaceView.getParent()).removeView(surfaceView);
            }
            ((ViewGroup) findViewById(R.id.rl_container_camera_collect)).addView(surfaceView);
            mTextureView.initCamera(mCameraId, this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCamera();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePalmSdk();
        closeCamera();
    }

    private void initCamera() {
        if (mTextureView == null) {
            mTextureView = new TextureView(this);
            mTextureView.setListener(this);
            mTextureView.setCameraId(mCameraId);
        }
    }

    private void initPalmSdk() {
        PalmSdk.getInstance().addCollectCallback(collectCallback);
    }

    private void releasePalmSdk() {
        PalmSdk.getInstance().stopCollect();
        PalmSdk.getInstance().removeCollectCallback(collectCallback);
    }

    private void closeCamera() {
        if (mTextureView != null) {
            mTextureView.closeCamera();
            View surfaceView = mTextureView.getFrame();
            if (surfaceView.getParent() != null) {
                ((ViewGroup) surfaceView.getParent()).removeView(surfaceView);
            }
            mTextureView = null;
        }
    }

    private void startCollect() {
        isCollecting = true;
        PalmSdk.getInstance().startCollect(null, null);
        Toast.makeText(this, "Posicione a palma da mão na câmera", Toast.LENGTH_LONG).show();
    }

    private void stopCollect() {
        PalmSdk.getInstance().stopCollect();
        isCollecting = false;
        finish();
    }

    @Override
    public void onCameraDataCallback(byte[] data, int cameraId) {
        if (data != null && data.length > 0 && isCollecting) {
            ThreadUtils.getCachedPool().execute(() -> {
                PalmCamera palmCamera = new PalmCamera(
                    data,
                    TextureView.CAMERA_WIDTH,
                    TextureView.CAMERA_HEIGHT,
                    SettingVar.previewRotation,
                    TextureView.REAL_WIDTH,
                    TextureView.REAL_HEIGHT,
                    SettingVar.palmMirrorH,
                    SettingVar.palmMirrorV
                );
                PalmSdk.getInstance().setDataSource(palmCamera, null);
            });
        }
    }

    private final IPalmSdk.CollectCallback collectCallback = new IPalmSdk.CollectCallback() {
        @Override
        public void onPalmNull(int times, Map<String, Object> map) {
            // Palm vein not recognized
        }

        @Override
        public void onPalmIn(List<PalmTrack> palmTrackList, Map<String, Object> map) {
            // Detected palm vein
        }

        @Override
        public void onDistance(float distance) {
            // not support
        }

        @Override
        public void onImage(android.graphics.Bitmap bitmap, List<PalmTrack> palmTrackList, Map<String, Object> map) {
            // Image captured
        }

        @Override
        public void onSuccess(String onceFeature, String feature, String palmId, Map<String, Object> map) {
            // Collection successful
            runOnUiThread(() -> {
                collectedPalmFeature = feature;
                isCollecting = false;
                
                // Retornar resultado para a tela de cadastro
                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_PALM_FEATURE, collectedPalmFeature);
                setResult(Activity.RESULT_OK, resultIntent);
                
                Toast.makeText(CameraCollectActivity.this, "Palma coletada com sucesso!", Toast.LENGTH_SHORT).show();
                
                // Fechar a tela após um pequeno delay
                findViewById(R.id.rl_container_camera_collect).postDelayed(() -> {
                    finish();
                }, 1000);
            });
        }

        @Override
        public void onFailure(int result, int times, String onceFeature, String palmId, Map<String, Object> map) {
            runOnUiThread(() -> {
                String message = "Falha na coleta";
                if (PalmConst.COLLECT_RESULT_PALM_EXIST == result) {
                    message = "Palma já existe";
                } else if (PalmConst.COLLECT_RESULT_UNKNOWN_ERROR == result) {
                    message = "Erro desconhecido na coleta";
                }

                Toast.makeText(CameraCollectActivity.this, message, Toast.LENGTH_SHORT).show();
                
                // Fechar a tela após mostrar o erro
                findViewById(R.id.rl_container_camera_collect).postDelayed(() -> {
                    finish();
                }, 2000);
            });
        }
    };
}
