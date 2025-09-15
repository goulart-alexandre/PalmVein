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
    private float currentDistance = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_collect);

        initCamera();
        initPalmSdk();
        setupClickListeners();
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

    private void setupClickListeners() {
        findViewById(R.id.btn_cancel).setOnClickListener(v -> stopCollect());
    }

    private void startCollect() {
        isCollecting = true;
        PalmSdk.getInstance().startCollect(null, null);
        Toast.makeText(this, "Posicione a palma da mão na câmera", Toast.LENGTH_LONG).show();
    }

    private void updateCollectQualityIndicator(float quality, float distance) {
        int qualityPercent = (int) (quality * 100);
        ((android.widget.ProgressBar) findViewById(R.id.progress_collect_quality)).setProgress(qualityPercent);
        ((android.widget.TextView) findViewById(R.id.txt_collect_quality_percentage)).setText(qualityPercent + "%");
        ((android.widget.TextView) findViewById(R.id.txt_collect_distance)).setText("Distância: " + String.format("%.1f", distance));
        
        // Mudar cor da barra baseada na qualidade
        android.widget.ProgressBar progressBar = findViewById(R.id.progress_collect_quality);
        if (qualityPercent >= 80) {
            progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50)); // Verde
        } else if (qualityPercent >= 60) {
            progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFFF9800)); // Laranja
        } else {
            progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFF44336)); // Vermelho
        }
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
            // Detected palm vein - atualizar indicador de qualidade
            runOnUiThread(() -> {
                if (palmTrackList != null && !palmTrackList.isEmpty()) {
                    // Calcular qualidade baseada no número de tracks e outros fatores
                    float quality = Math.min(1.0f, palmTrackList.size() * 0.3f + 0.4f);
                    updateCollectQualityIndicator(quality, currentDistance);
                }
            });
        }

        @Override
        public void onDistance(float distance) {
            // Atualizar distância
            runOnUiThread(() -> {
                currentDistance = distance;
                updateCollectQualityIndicator(0.5f, distance); // Qualidade padrão durante detecção
            });
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
                
                // Atualizar indicador de qualidade para 100%
                updateCollectQualityIndicator(1.0f, currentDistance);
                
                // Retornar resultado para a tela de cadastro
                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_PALM_FEATURE, collectedPalmFeature);
                setResult(Activity.RESULT_OK, resultIntent);
                
                Toast.makeText(CameraCollectActivity.this, "Palma coletada com sucesso! Qualidade: 100%", Toast.LENGTH_SHORT).show();
                
                // Fechar a tela após um pequeno delay
                findViewById(R.id.rl_container_camera_collect).postDelayed(() -> {
                    finish();
                }, 1500);
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
