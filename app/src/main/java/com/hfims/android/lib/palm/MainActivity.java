package com.hfims.android.lib.palm;

import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.hfims.android.core.util.ThreadUtils;
import com.hfims.android.lib.palm.model.PalmCamera;
import com.hfims.android.lib.palm.model.PalmTrack;
import com.hfims.android.lib.palm.model.User;
import com.hfims.android.lib.palm.dao.UserDao;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.XXPermissions;
import com.hjq.permissions.Permission;
import com.hfims.android.lib.palm.databinding.ActivityMainBinding;

// Imports do SDK Palm - estas classes devem estar no AAR
import com.hfims.android.lib.palm.PalmSdk;
import com.hfims.android.lib.palm.PalmConfig;
import com.hfims.android.lib.palm.IPalmSdk;
import com.hfims.android.lib.palm.PalmConst;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Import do SDK de hardware
import com.q_zheng.QZhengGPIOManager;

// ButterKnife removido - usando ViewBinding

public class MainActivity extends AppCompatActivity implements TextureView.OnCameraDataEnableListener {

    private static final String CUSTOM_ID = "c91cfb4752244e1393ed784fad577811"; // Contact the business to obtain the customer id

    private TextureView mTextureView;
    private int mCameraId = Camera.CameraInfo.CAMERA_FACING_BACK;
    private ActivityMainBinding binding;
    private UserDao userDao;
    private QZhengGPIOManager gpioManager;
    private Handler ledHandler = new Handler(Looper.getMainLooper());
    private MediaPlayer successSound;
    
    // Variáveis para funcionalidades avançadas
    private boolean isMenuVisible = false;
    private int currentThreshold = 65;
    private float currentQuality = 0f;
    private float currentDistance = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userDao = new UserDao(this);
        gpioManager = QZhengGPIOManager.getInstance(this);
        setupClickListeners();
        checkPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        initCamera();
        // Recarregar usuários do banco SQLite para o SDK quando voltar de outras telas
        if (PalmSdk.getInstance() != null) {
            // Re-adicionar callbacks que foram removidos no onPause
            PalmSdk.getInstance().addCollectCallback(collectCallback);
            PalmSdk.getInstance().addRecognizeCallback(recognizeCallback);
            loadUsersToSdk();
            // Iniciar reconhecimento automaticamente após 300ms
            binding.rlContainerCamera.postDelayed(() -> startRecognize(), 300);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeCamera();
        // Remover callbacks para evitar conflito com CameraCollectActivity
        if (PalmSdk.getInstance() != null) {
            PalmSdk.getInstance().removeCollectCallback(collectCallback);
            PalmSdk.getInstance().removeRecognizeCallback(recognizeCallback);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePalmSdk();
        resetAllLEDs(); // Resetar todos os LEDs ao destruir a activity
        
        // Limpar MediaPlayer
        if (successSound != null) {
            successSound.release();
            successSound = null;
        }
        
        if (userDao != null) {
            userDao.close();
        }
        binding = null;
    }

    private void resetAllLEDs() {
        try {
            if (gpioManager != null) {
                gpioManager.setValue(QZhengGPIOManager.GPIO_ID_LED_R, QZhengGPIOManager.GPIO_VALUE_LOW);
                gpioManager.setValue(QZhengGPIOManager.GPIO_ID_LED_G, QZhengGPIOManager.GPIO_VALUE_LOW);
                gpioManager.setValue(QZhengGPIOManager.GPIO_ID_LED_B, QZhengGPIOManager.GPIO_VALUE_LOW);
                android.util.Log.d("PalmLED", "Todos os LEDs resetados");
            }
        } catch (Exception e) {
            android.util.Log.e("PalmLED", "Erro ao resetar LEDs: " + e.getMessage());
        }
    }

    private void initData() {
        runOnUiThread(() -> binding.palmTrackView.initPaint());
    }

    private void initPalmSdk() {
        PalmConfig palmConfig = new PalmConfig.Builder()
                .recognizeThreshold(65)
                .customId(CUSTOM_ID)
                .build();
        PalmSdk.getInstance().init(this, palmConfig, (success, errorCode, message) -> {
            getDeviceKey();
            if (!success) {
                if ("Palm unauthorized".equals(message)) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, getString(R.string.activity_main_palm_vein_unauth) + PalmSdk.getInstance().getDeviceKey(), Toast.LENGTH_LONG).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
                }
                finish();
            } else {
                PalmSdk.getInstance().addRecognizeCallback(recognizeCallback);
                PalmSdk.getInstance().addCollectCallback(collectCallback);
                // Carregar usuários existentes do banco SQLite para o banco do SDK
                loadUsersToSdk();
            }
        });
    }

    private void loadUsersToSdk() {
        // Limpar o banco do SDK primeiro para remover IDs conflitantes
        PalmSdk.getInstance().getPalmLib().deleteAll();
        
        // Carregar todos os usuários do banco SQLite e adicionar ao banco do SDK
        List<User> users = userDao.getAllUsers();
        android.util.Log.d("PalmRecognition", "Carregando " + users.size() + " usuários do SQLite para o SDK");
        
        for (User user : users) {
            if (user.getPalmFeature() != null && !user.getPalmFeature().isEmpty()) {
                PalmSdk.getInstance().getPalmLib().addFeature(user.getUserId(), user.getPalmFeature());
                android.util.Log.d("PalmRecognition", "Usuário carregado no SDK: " + user.getName() + " (ID: " + user.getUserId() + ")");
            } else {
                android.util.Log.d("PalmRecognition", "Usuário sem palma: " + user.getName() + " (ID: " + user.getUserId() + ")");
            }
        }
    }

    private void releasePalmSdk() {
        PalmSdk.getInstance().stopCollect();
        PalmSdk.getInstance().removeCollectCallback(collectCallback);
        PalmSdk.getInstance().stopRecognize();
        PalmSdk.getInstance().removeRecognizeCallback(recognizeCallback);
        PalmSdk.getInstance().release();
    }

    private void getDeviceKey() {
        runOnUiThread(() -> binding.txtDeviceKey.setText("SN: " + PalmSdk.getInstance().getDeviceKey()));
    }

    private void setupClickListeners() {
        binding.btnStartCollect.setOnClickListener(v -> {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "collect for 1111", Toast.LENGTH_SHORT).show());
        PalmSdk.getInstance().startCollect(null, null);
        });

        binding.btnStopCollect.setOnClickListener(v -> {
            PalmSdk.getInstance().stopCollect();
        });

        binding.btnStartRecognize.setOnClickListener(v -> {
            PalmSdk.getInstance().startRecognize(null);
        });

        binding.btnStopRecognize.setOnClickListener(v -> {
            PalmSdk.getInstance().stopRecognize();
        });

        binding.btnClearCollect.setOnClickListener(v -> {
            PalmSdk.getInstance().getPalmLib().deleteAll();
        });

        binding.btnRegister.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent(MainActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        // Botão de debug para verificar usuários no banco
        binding.btnClearCollect.setOnClickListener(v -> {
            // Mostrar usuários no banco de dados
            List<User> users = userDao.getAllUsers();
            android.util.Log.d("PalmDebug", "Usuários no banco SQLite: " + users.size());
            for (User user : users) {
                android.util.Log.d("PalmDebug", "Usuário: " + user.getName() + " (ID: " + user.getUserId() + ") - Palma: " + (user.getPalmFeature() != null ? "Sim" : "Não"));
            }
            Toast.makeText(this, "Usuários no banco: " + users.size(), Toast.LENGTH_SHORT).show();
        });

        // Controles avançados
        
        // Botão hambúrguer para alternar menu
        binding.btnHamburger.setOnClickListener(v -> toggleMenu());
        
        // SeekBar para threshold dinâmico
        binding.seekbarThreshold.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    currentThreshold = progress;
                    binding.txtThresholdValue.setText(String.valueOf(progress));
                    updateThreshold(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
    }

    private void toggleMenu() {
        isMenuVisible = !isMenuVisible;
        int vis = isMenuVisible ? View.VISIBLE : View.GONE;
        binding.llOperateCollect.setVisibility(vis);
        binding.llOperateRecognize.setVisibility(vis);
        binding.llOperateClear.setVisibility(vis);
        binding.llAdvancedControls.setVisibility(vis);
        // manter gabarito sempre visível sobre a câmera
        binding.imgGabaritoPalmMain.setVisibility(View.VISIBLE);
        binding.txtOperateResult.setVisibility(View.GONE); // manter oculto
    }

    public void startRecognize() {
        PalmSdk.getInstance().startRecognize(null);
    }

    public void stopRecognize() {
        PalmSdk.getInstance().stopRecognize();
    }

    private void initCamera() {
        if (null == mTextureView) {
            mTextureView = new TextureView(this);
            mTextureView.setListener(this);
            mTextureView.setCameraId(mCameraId);
            View surfaceView = mTextureView.getFrame();
            if (surfaceView.getParent() != null) {
                mTextureView.closeCamera();
                ((ViewGroup) surfaceView.getParent()).removeView(surfaceView);
            }
            binding.rlContainerCamera.addView(surfaceView);
        } else {
            mTextureView.initCamera(mCameraId, this);
        }
    }

    private void closeCamera() {
        if (null != mTextureView) {
            mTextureView.closeCamera();
            View surfaceView = mTextureView.getFrame();
            if (surfaceView.getParent() != null) {
                ((ViewGroup) surfaceView.getParent()).removeView(surfaceView);
            }
            mTextureView = null;
        }
    }

    @Override
    public void onCameraDataCallback(byte[] data, int cameraId) {
        if (data != null && data.length > 0) {
            ThreadUtils.getCachedPool().execute(() -> {
                PalmCamera palmCamera = new PalmCamera(data, TextureView.CAMERA_WIDTH, TextureView.CAMERA_HEIGHT, SettingVar.previewRotation, TextureView.REAL_WIDTH, TextureView.REAL_HEIGHT, SettingVar.palmMirrorH, SettingVar.palmMirrorV);
                PalmSdk.getInstance().setDataSource(palmCamera, null);
            });
        }
    }

    private void showPalmTrackView(List<TFaceTrack> trackFaceList) {
        if (null == trackFaceList || trackFaceList.isEmpty()) {
            hidePalmTrackView();
            return;
        }
        Map<String, Object> inputMap = new HashMap<>();
        inputMap.put("faceTrack", trackFaceList);
        binding.palmTrackView.setFaces(trackFaceList);
        binding.palmTrackView.setVisibility(View.VISIBLE);
    }

    private void hidePalmTrackView() {
        binding.palmTrackView.setVisibility(View.GONE);
    }

    private void showUserInfo(User user) {
        binding.txtUserName.setText("Nome: " + user.getName());
        binding.txtUserId.setText("ID: " + user.getUserId());
        
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        String dateStr = sdf.format(new Date(user.getCreatedAt()));
        binding.txtUserCreated.setText("Cadastrado em: " + dateStr);
        
        binding.llUserInfo.setVisibility(View.VISIBLE);
    }

    private void hideUserInfo() {
        binding.llUserInfo.setVisibility(View.GONE);
    }

    private void turnOnGreenLED() {
        try {
            if (gpioManager != null) {
                gpioManager.setValue(QZhengGPIOManager.GPIO_ID_LED_G, QZhengGPIOManager.GPIO_VALUE_HIGH);
                android.util.Log.d("PalmLED", "LED verde ligado via GPIO");
            }
        } catch (Exception e) {
            android.util.Log.e("PalmLED", "Erro ao ligar LED verde: " + e.getMessage());
        }
    }

    private void turnOffLED() {
        try {
            if (gpioManager != null) {
                gpioManager.setValue(QZhengGPIOManager.GPIO_ID_LED_G, QZhengGPIOManager.GPIO_VALUE_LOW);
                android.util.Log.d("PalmLED", "LED verde desligado via GPIO");
            }
        } catch (Exception e) {
            android.util.Log.e("PalmLED", "Erro ao desligar LED verde: " + e.getMessage());
        }
    }

    private void playSuccessSound() {
        try {
            if (successSound != null) {
                successSound.release();
            }
            successSound = MediaPlayer.create(this, R.raw.finger_lendo);
            if (successSound != null) {
                successSound.start();
                android.util.Log.d("PalmSound", "Som de sucesso tocado");
            }
        } catch (Exception e) {
            android.util.Log.e("PalmSound", "Erro ao tocar som de sucesso: " + e.getMessage());
        }
    }

    // Métodos para funcionalidades avançadas

    private void updateThreshold(int newThreshold) {
        // Atualizar threshold dinamicamente
        PalmConfig newConfig = new PalmConfig.Builder()
                .recognizeThreshold(newThreshold)
                .customId(CUSTOM_ID)
                .build();
        
        // Reinicializar SDK com novo threshold
        PalmSdk.getInstance().init(this, newConfig, (success, errorCode, message) -> {
            if (success) {
                android.util.Log.d("PalmConfig", "Threshold atualizado para: " + newThreshold);
                Toast.makeText(this, "Threshold atualizado: " + newThreshold, Toast.LENGTH_SHORT).show();
            } else {
                android.util.Log.e("PalmConfig", "Erro ao atualizar threshold: " + message);
            }
        });
    }

    private void updateQualityIndicator(float quality, float distance) {
        if (true) { // Sempre mostrar qualidade
            currentQuality = quality;
            currentDistance = distance;
            
            int qualityPercent = (int) (quality * 100);
            binding.progressQuality.setProgress(qualityPercent);
            binding.txtQualityPercentage.setText(qualityPercent + "%");
            binding.txtDistance.setText("Distância: " + String.format("%.1f", distance));
            
            // Mudar cor da barra baseada na qualidade
            if (qualityPercent >= 80) {
                binding.progressQuality.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF4CAF50)); // Verde
            } else if (qualityPercent >= 60) {
                binding.progressQuality.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFFF9800)); // Laranja
            } else {
                binding.progressQuality.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFF44336)); // Vermelho
            }
        }
    }

    private void showTrackingInfo(List<TFaceTrack> faceTrackList) {
        if (faceTrackList != null && !faceTrackList.isEmpty()) {
            TFaceTrack track = faceTrackList.get(0);
            android.util.Log.d("PalmTracking", "Tracking - Centro: " + track.getCenter() + 
                             ", Raio: " + track.getRadius() + 
                             ", Retângulo: " + track.getRect());
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
                    updateQualityIndicator(quality, currentDistance);
                }
            });
        }

        @Override
        public void onDistance(float distance) {
            // Atualizar distância
            runOnUiThread(() -> {
                currentDistance = distance;
                if (true) { // Sempre mostrar qualidade
                    binding.txtDistance.setText("Distância: " + String.format("%.1f", distance));
                }
            });
        }

        @Override
        public void onImage(Bitmap bitmap, List<PalmTrack> palmTrackList, Map<String, Object> map) {

        }

        @Override
        public void onSuccess(String onceFeature, String feature, String palmId, Map<String, Object> map) {
            // Collection successful - não salvar aqui, apenas mostrar mensagem
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, getString(R.string.activity_main_palm_collect_success), Toast.LENGTH_SHORT).show();
                // Não salvar no SDK aqui - isso é feito apenas na tela de cadastro
            });
        }

        @Override
        public void onFailure(int result, int times, String onceFeature, String palmId, Map<String, Object> map) {
            if (PalmConst.COLLECT_RESULT_PALM_EXIST == result) {
                // Palm vein already exists
                runOnUiThread(() -> Toast.makeText(MainActivity.this, getString(R.string.activity_main_palm_already_exists), Toast.LENGTH_SHORT).show());
                return;
            }
            if (PalmConst.COLLECT_RESULT_UNKNOWN_ERROR == result) {
                // Collection failed
                // Removido toast de erro - coleta falhou silenciosamente
            }
        }
    };

    private final IPalmSdk.RecognizeCallback recognizeCallback = new IPalmSdk.RecognizeCallback() {
        @Override
        public void onPalmNull(int times, Map<String, Object> map) {
            // Palm vein not recognized
            runOnUiThread(() -> hidePalmTrackView());
        }

        @Override
        public void onPalmIn(List<PalmTrack> list, Map<String, Object> map) {
            // Detected palm vein
            List<TFaceTrack> faceTrackList = new ArrayList<>();
            for (PalmTrack palmTrack : list) {
                TFaceTrack faceTrack = new TFaceTrack();
                faceTrack.setRect(palmTrack.getRect());
                faceTrack.setCenter(palmTrack.getCenter());
                faceTrack.setRadius(palmTrack.getRadius());
                faceTrackList.add(faceTrack);
            }
            runOnUiThread(() -> {
                showPalmTrackView(faceTrackList);
                showTrackingInfo(faceTrackList);
                
                // Atualizar indicador de qualidade durante reconhecimento
                if (list != null && !list.isEmpty()) {
                    float quality = Math.min(1.0f, list.size() * 0.2f + 0.6f);
                    updateQualityIndicator(quality, currentDistance);
                }
            });
        }

        @Override
        public void onDistance(float distance) {
            // Atualizar distância durante reconhecimento
            runOnUiThread(() -> {
                currentDistance = distance;
                if (true) { // Sempre mostrar qualidade
                    binding.txtDistance.setText("Distância: " + String.format("%.1f", distance));
                }
            });
        }

        @Override
        public void onImage(Bitmap bitmap, List<PalmTrack> list, Map<String, Object> map) {

        }

        @Override
        public void onSuccess(String palmId, int score, Map<String, Object> map) {
            // Palm vein recognition successful
            runOnUiThread(() -> {
                android.util.Log.d("PalmRecognition", "Reconhecimento bem-sucedido! palmId: " + palmId + ", score: " + score);
                
                // Atualizar indicador de qualidade com score
                float quality = Math.min(1.0f, score / 100.0f);
                updateQualityIndicator(quality, currentDistance);
                
                // Buscar usuário no banco de dados pelo userId (palmId é o userId que salvamos)
                User recognizedUser = userDao.getUserById(palmId);
                android.util.Log.d("PalmRecognition", "Usuário encontrado: " + (recognizedUser != null ? recognizedUser.getName() : "null"));
                
                  if (recognizedUser != null) {
                      showUserInfo(recognizedUser);
                      Toast.makeText(MainActivity.this, "Usuário reconhecido: " + recognizedUser.getName() + " (Score: " + score + ")", Toast.LENGTH_LONG).show();

                      // Ligar LED verde e tocar som quando reconhecer
                      turnOnGreenLED();
                      playSuccessSound();

                      // Esconder informações do usuário após 3 segundos e desligar LED
                      binding.llUserInfo.postDelayed(() -> {
                          hideUserInfo();
                          turnOffLED();
                      }, 3000);
                  } else {
                    hideUserInfo();
                    Toast.makeText(MainActivity.this, "Usuário não encontrado no banco de dados. palmId: " + palmId, Toast.LENGTH_LONG).show();
                }
            });
            binding.rlContainerCamera.postDelayed(() -> startRecognize(), 2000);
        }

        @Override
        public void onFailure(int result, int score, Map<String, Object> map) {
            // Palm vein recognition failed
            // Removido toast de erro - reconhecimento falhou silenciosamente
            runOnUiThread(() -> hidePalmTrackView());
            binding.rlContainerCamera.postDelayed(() -> startRecognize(), 2000);
        }
    };

    private void checkPermission() {
        XXPermissions.with(this)
                .permission(Permission.CAMERA)
                .permission(Permission.READ_EXTERNAL_STORAGE)
                .permission(Permission.WRITE_EXTERNAL_STORAGE)
                .request(new OnPermissionCallback() {
                    @Override
                    public void onGranted(@NonNull List<String> permissions, boolean allGranted) {
                        if (!allGranted) {
                            Toast.makeText(MainActivity.this, "Some permissions are not granted properly", Toast.LENGTH_LONG).show();
                            return;
                        }

                        initData();
                        ThreadUtils.getCachedPool().execute(() -> initPalmSdk());
                    }

                    @Override
                    public void onDenied(@NonNull List<String> permissions, boolean doNotAskAgain) {
                        if (doNotAskAgain) {
                            Toast.makeText(MainActivity.this, "Permanently denied authorization, pls grant permission manually", Toast.LENGTH_LONG).show();
                            // If permanently rejected, redirect to the application permission system settings page
                            XXPermissions.startPermissionActivity(MainActivity.this, permissions);
                        } else {
                            // Permissão negada - não mostrar toast
                        }
                    }
                });
    }
}