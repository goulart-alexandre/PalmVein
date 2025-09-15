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
    private int stableFrames = 0;
    private static final int STABLE_FRAMES_THRESHOLD = 10; // ~10 ciclos antes de iniciar contagem
    private android.os.CountDownTimer countdownTimer;
    // Limiares de distância (ajustáveis conforme hardware)
    private static final float MIN_DISTANCE_OK = 0.25f; // abaixo: muito perto
    private static final float MAX_DISTANCE_OK = 0.55f; // acima: muito longe

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
        // UI de qualidade removida a pedido. Mantemos assinatura para não quebrar chamadas.
    }

    private void stopCollect() {
        PalmSdk.getInstance().stopCollect();
        isCollecting = false;
        finish();
    }

    private void startStabilityCountdownIfNeeded() {
        if (countdownTimer != null) return;
        android.widget.TextView countdownView = findViewById(R.id.txt_countdown);
        if (countdownView == null) return;
        countdownView.setVisibility(View.VISIBLE);
        countdownTimer = new android.os.CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000L + 1; // mostrar 3,2,1
                countdownView.setText(String.valueOf(seconds));
            }

            @Override
            public void onFinish() {
                countdownView.setText("");
                countdownView.setVisibility(View.GONE);
                countdownTimer = null;
                // Coleta contínua vai concluir e chamar onSuccess
            }
        }.start();
    }

    private void cancelCountdownIfRunning() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
            android.widget.TextView countdownView = findViewById(R.id.txt_countdown);
            if (countdownView != null) {
                countdownView.setText("");
                countdownView.setVisibility(View.GONE);
            }
        }
    }

    private void updateStatusMessage() {
        android.widget.TextView countdownView = findViewById(R.id.txt_countdown);
        if (countdownView == null) return;
        // Se contagem ativa, não sobrescrever números
        if (countdownTimer != null) return;

        String message;
        if (currentDistance > 0f) {
            if (currentDistance < MIN_DISTANCE_OK) {
                message = "Afaste mais a mão";
            } else if (currentDistance > MAX_DISTANCE_OK) {
                message = "Aproxime mais a mão";
            } else {
                // Dentro da zona: foco em estabilidade
                message = (stableFrames >= STABLE_FRAMES_THRESHOLD / 2) ? "Mantenha a mão parada" : "Posicione a mão";
            }
        } else {
            message = "Posicione a mão";
        }
        countdownView.setText(message);
        countdownView.setVisibility(View.VISIBLE);
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
            // Palma não detectada: cancelar contagem e resetar estabilidade
            runOnUiThread(() -> {
                stableFrames = 0;
                cancelCountdownIfRunning();
                updateStatusMessage();
            });
        }

        @Override
        public void onPalmIn(List<PalmTrack> palmTrackList, Map<String, Object> map) {
            // Palma dentro do frame: considerar estável por recorrência
            runOnUiThread(() -> {
                if (palmTrackList != null && !palmTrackList.isEmpty()) {
                    stableFrames++;
                    if (stableFrames >= STABLE_FRAMES_THRESHOLD) {
                        startStabilityCountdownIfNeeded();
                    }
                    updateStatusMessage();
                }
            });
        }

        @Override
        public void onDistance(float distance) {
            // Atualizar distância (opcional)
            runOnUiThread(() -> {
                currentDistance = distance;
                updateStatusMessage();
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
            // Modo inteligente: não sair em falha; reiniciar coleta automaticamente
            runOnUiThread(() -> {
                stableFrames = 0;
                cancelCountdownIfRunning();
                isCollecting = true;
                // Alguns devices param a coleta após erro: reiniciar após pequeno delay
                findViewById(R.id.rl_container_camera_collect).postDelayed(() -> {
                    PalmSdk.getInstance().startCollect(null, null);
                    updateStatusMessage();
                }, 300);
            });
        }
    };
}
