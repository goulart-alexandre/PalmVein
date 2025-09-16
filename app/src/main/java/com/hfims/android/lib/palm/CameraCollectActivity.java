package com.hfims.android.lib.palm;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
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
import android.graphics.Point;

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
    private CountDownTimer countdownTimer;
    // Limiares baseados no raio da palma (ajustados conforme dados reais)
    private static final float MIN_RADIUS_OK = 90f; // abaixo: muito perto (raio pequeno)
    private static final float MAX_RADIUS_OK = 160f; // acima: muito longe (raio grande)
    
    // Flag para controlar se a coleta foi bem-sucedida mas ainda está na contagem
    private boolean isCountdownActive = false;
    private String pendingPalmFeature = null;
    
    // Variáveis simples para feedback
    private int qualityFrames = 0;
    private static final int QUALITY_FRAMES_THRESHOLD = 5;
    
    private TextView txtCountdown;
    private TextView txtFeedback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_collect);

        txtCountdown = findViewById(R.id.txt_countdown);
        txtFeedback = txtCountdown; // Usar o mesmo TextView para feedback e contagem

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
        cancelCountdownIfRunning();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePalmSdk();
        closeCamera();
        cancelCountdownIfRunning();
    }

    private void initCamera() {
        if (mTextureView == null) {
            mTextureView = new TextureView(this);
            mTextureView.setListener(this);
            mTextureView.setCameraId(mCameraId);
        }
    }

    private void initPalmSdk() {
        // O SDK já foi inicializado no MainActivity, apenas adicionar o callback
        android.util.Log.d("CameraCollect", "Adicionando callback de coleta ao SDK já inicializado");
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
        findViewById(R.id.btn_cancel).setOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            stopCollect();
        });
    }

    private void startCollect() {
        isCollecting = true;
        isCountdownActive = false;
        pendingPalmFeature = null;
        
        // Resetar contadores para nova tentativa
        qualityFrames = 0;
        stableFrames = 0;
        
        PalmSdk.getInstance().startCollect(null, null);
        txtFeedback.setText("Posicione a mão");
        txtCountdown.setVisibility(View.GONE);
    }


    private void stopCollect() {
        PalmSdk.getInstance().stopCollect();
        isCollecting = false;
        isCountdownActive = false;
        pendingPalmFeature = null;
        finish();
    }

    private void startStabilityCountdownIfNeeded() {
        if (countdownTimer != null) return;
        txtCountdown.setVisibility(View.VISIBLE);
        txtFeedback.setVisibility(View.GONE);
        isCountdownActive = true;
        
        countdownTimer = new CountDownTimer(3000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long seconds = millisUntilFinished / 1000L + 1; // mostrar 3,2,1
                txtCountdown.setText(String.valueOf(seconds));
            }

            @Override
            public void onFinish() {
                txtCountdown.setText("Coletando...");
                isCountdownActive = false;
                
                // Se há uma palma pendente, processar agora
                if (pendingPalmFeature != null) {
                    processSuccess(pendingPalmFeature);
                }
            }
        }.start();
    }

    private void cancelCountdownIfRunning() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
        stableFrames = 0;
        isCountdownActive = false;
        pendingPalmFeature = null;
        txtCountdown.setVisibility(View.GONE);
        txtFeedback.setVisibility(View.VISIBLE);
    }
    
    

    private void processSuccess(String palmFeature) {
        collectedPalmFeature = palmFeature;
        isCollecting = false;
        cancelCountdownIfRunning();

        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_PALM_FEATURE, collectedPalmFeature);
        setResult(Activity.RESULT_OK, resultIntent);

        Toast.makeText(CameraCollectActivity.this, "Palma coletada com sucesso!", Toast.LENGTH_SHORT).show();

        // Fechar a tela após um pequeno delay
        findViewById(R.id.rl_container_camera_collect).postDelayed(() -> {
            finish();
        }, 1500);
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
            runOnUiThread(() -> {
                stableFrames = 0;
                qualityFrames = 0;
                cancelCountdownIfRunning();
                txtFeedback.setText("Posicione a mão");
            });
        }

        @Override
        public void onPalmIn(List<PalmTrack> palmTrackList, Map<String, Object> map) {
            runOnUiThread(() -> {
                // Estimar qualidade baseada no número de tracks
                float estimatedQuality = 0.0f;
                float palmRadius = 0f;
                
                if (palmTrackList != null && !palmTrackList.isEmpty()) {
                    estimatedQuality = Math.min(1.0f, palmTrackList.size() * 0.3f + 0.4f);
                    // Usar o raio da primeira palma detectada
                    PalmTrack track = palmTrackList.get(0);
                    palmRadius = track.getRadius();
                }

                android.util.Log.d("CameraCollect", "onPalmIn - tracks: " + (palmTrackList != null ? palmTrackList.size() : 0) + 
                                 ", raio: " + palmRadius + ", qualidade: " + estimatedQuality);

                // Feedback baseado no raio da palma e qualidade
                if (palmRadius > MAX_RADIUS_OK) {
                    txtFeedback.setText("Aproxime mais a mão");
                    cancelCountdownIfRunning();
                    qualityFrames = 0;
                } else if (palmRadius < MIN_RADIUS_OK) {
                    txtFeedback.setText("Afaste mais a mão");
                    cancelCountdownIfRunning();
                    qualityFrames = 0;
                } else {
                    // Raio OK - verificar qualidade
                    if (estimatedQuality >= 0.7f) {
                        qualityFrames++;
                        stableFrames++;
                        
                        if (stableFrames >= STABLE_FRAMES_THRESHOLD && qualityFrames >= QUALITY_FRAMES_THRESHOLD) {
                            if (countdownTimer == null) {
                                android.util.Log.d("CameraCollect", "Iniciando contagem - stableFrames: " + stableFrames + ", qualityFrames: " + qualityFrames);
                                startStabilityCountdownIfNeeded();
                            }
                            txtFeedback.setText("Mantenha a mão parada");
                        } else {
                            txtFeedback.setText("Posição boa, aguarde... (" + stableFrames + "/" + STABLE_FRAMES_THRESHOLD + ")");
                        }
                    } else {
                        txtFeedback.setText("Posicione a mão melhor");
                        cancelCountdownIfRunning();
                        qualityFrames = 0;
                    }
                }
            });
        }

        @Override
        public void onDistance(float distance) {
            android.util.Log.d("CameraCollect", "onDistance chamado: " + distance);
            runOnUiThread(() -> {
                currentDistance = distance;
                android.util.Log.d("CameraCollect", "Distância atualizada: " + currentDistance);
                // A lógica de feedback de distância é tratada em onPalmIn
            });
        }

        @Override
        public void onImage(android.graphics.Bitmap bitmap, List<PalmTrack> palmTrackList, Map<String, Object> map) {
            // Image captured
        }

        @Override
        public void onSuccess(String onceFeature, String feature, String palmId, Map<String, Object> map) {
            runOnUiThread(() -> {
                if (isCountdownActive) {
                    // Se ainda está na contagem, armazenar a palma e aguardar
                    pendingPalmFeature = feature;
                    // Não processar ainda, aguardar a contagem terminar
                } else {
                    // Se não está na contagem, processar imediatamente
                    processSuccess(feature);
                }
            });
        }

        @Override
        public void onFailure(int result, int times, String onceFeature, String palmId, Map<String, Object> map) {
            runOnUiThread(() -> {
                // Não mostrar mensagens de erro - apenas reiniciar silenciosamente
                cancelCountdownIfRunning();
                isCollecting = false; // Para garantir que o startCollect reinicie
                findViewById(R.id.rl_container_camera_collect).postDelayed(() -> {
                    startCollect();
                }, 300); // Pequeno delay antes de tentar novamente
            });
        }
    };
}
