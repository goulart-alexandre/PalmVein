package com.hfims.android.lib.palm;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hfims.android.lib.palm.dao.UserDao;
import com.hfims.android.lib.palm.databinding.ActivityRegisterBinding;
import com.hfims.android.lib.palm.model.User;
import com.hfims.android.lib.palm.PalmSdk;
import com.hfims.android.lib.palm.PalmConfig;
import com.hfims.android.lib.palm.IPalmSdk;
import com.hfims.android.lib.palm.PalmConst;
import com.hfims.android.core.util.ThreadUtils;
import com.hfims.android.lib.palm.model.PalmCamera;
import com.hfims.android.lib.palm.model.PalmTrack;

import java.util.List;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity implements TextureView.OnCameraDataEnableListener {

    private ActivityRegisterBinding binding;
    private UserDao userDao;
    private TextureView mTextureView;
    private int mCameraId = android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK;
    private String collectedPalmFeature = null;
    private boolean isCollecting = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userDao = new UserDao(this);
        setupClickListeners();
        initCamera();
        initPalmSdk();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mTextureView != null) {
            View surfaceView = mTextureView.getFrame();
            if (surfaceView.getParent() != null) {
                ((ViewGroup) surfaceView.getParent()).removeView(surfaceView);
            }
            binding.rlContainerCameraRegister.addView(surfaceView);
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
        if (userDao != null) {
            userDao.close();
        }
        binding = null;
    }

    private void setupClickListeners() {
        binding.btnStartCollect.setOnClickListener(v -> startCollect());
        binding.btnStopCollect.setOnClickListener(v -> stopCollect());
        binding.btnSave.setOnClickListener(v -> saveUser());
        binding.btnClearAll.setOnClickListener(v -> clearAllUsers());
        binding.btnBackToMain.setOnClickListener(v -> finish());
        
        // Ocultar teclado ao clicar em qualquer lugar da tela
        binding.getRoot().setOnClickListener(v -> hideKeyboard());
    }

    private void initCamera() {
        if (mTextureView == null) {
            mTextureView = new TextureView(this);
            mTextureView.setListener(this);
            mTextureView.setCameraId(mCameraId);
        }
    }

    private void initPalmSdk() {
        // O SDK já foi inicializado na MainActivity, apenas adicionamos o callback
        PalmSdk.getInstance().addCollectCallback(collectCallback);
    }

    private void releasePalmSdk() {
        PalmSdk.getInstance().stopCollect();
        PalmSdk.getInstance().removeCollectCallback(collectCallback);
        // Não liberamos o SDK aqui pois é usado pela MainActivity
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
        String name = binding.etName.getText().toString().trim();
        String userId = binding.etUserId.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(userId)) {
            Toast.makeText(this, "Nome e ID do Usuário não podem ser vazios", Toast.LENGTH_SHORT).show();
            return;
        }

        if (userDao.getUserById(userId) != null) {
            Toast.makeText(this, "ID do Usuário já existe. Por favor, use um ID diferente.", Toast.LENGTH_LONG).show();
            return;
        }

        binding.btnStartCollect.setEnabled(false);
        binding.btnStopCollect.setEnabled(true);
        binding.btnSave.setEnabled(false);
        collectedPalmFeature = null;
        isCollecting = true;
        binding.tvCollectStatus.setText("Iniciando coleta da palma...");
        PalmSdk.getInstance().startCollect(null, null);
    }

    private void stopCollect() {
        PalmSdk.getInstance().stopCollect();
        isCollecting = false;
        binding.btnStartCollect.setEnabled(true);
        binding.btnStopCollect.setEnabled(false);
        binding.tvCollectStatus.setText("Coleta parada.");
    }

    private void saveUser() {
        String name = binding.etName.getText().toString().trim();
        String userId = binding.etUserId.getText().toString().trim();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(userId) || TextUtils.isEmpty(collectedPalmFeature)) {
            Toast.makeText(this, "Preencha todos os campos e colete a palma antes de salvar.", Toast.LENGTH_SHORT).show();
            return;
        }

        User newUser = new User(name, userId, collectedPalmFeature, System.currentTimeMillis());
        long newRowId = userDao.insertUser(newUser);

        if (newRowId != -1) {
            // Salvar também no banco do SDK para reconhecimento
            PalmSdk.getInstance().getPalmLib().addFeature(userId, collectedPalmFeature);
            
            android.util.Log.d("PalmRegister", "Usuário salvo no SQLite com ID: " + newRowId);
            android.util.Log.d("PalmRegister", "Usuário salvo no SDK com userId: " + userId);
            
            Toast.makeText(this, "Usuário " + name + " cadastrado com sucesso!", Toast.LENGTH_SHORT).show();
            finish(); // Volta para a MainActivity
        } else {
            Toast.makeText(this, "Erro ao cadastrar usuário. O ID do usuário pode já existir.", Toast.LENGTH_LONG).show();
        }
    }

    private void clearAllUsers() {
        // Mostrar diálogo de confirmação
        new android.app.AlertDialog.Builder(this)
                .setTitle("Confirmar Exclusão")
                .setMessage("Tem certeza que deseja apagar TODOS os usuários cadastrados? Esta ação não pode ser desfeita.")
                .setPositiveButton("Sim, Apagar", (dialog, which) -> {
                    // Apagar todos os usuários do banco SQLite
                    userDao.clearAllUsers();
                    
                    // Apagar todos os usuários do banco do SDK
                    PalmSdk.getInstance().getPalmLib().deleteAll();
                    
                    // Limpar formulário
                    clearForm();
                    
                    Toast.makeText(this, "Todos os usuários foram apagados!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void hideKeyboard() {
        android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(binding.getRoot().getWindowToken(), 0);
        }
    }

    private void clearForm() {
        binding.etName.setText("");
        binding.etUserId.setText("");
        collectedPalmFeature = null;
        binding.btnSave.setEnabled(false);
        binding.tvCollectStatus.setText("Aguardando coleta...");
        binding.btnStartCollect.setEnabled(true);
        binding.btnStopCollect.setEnabled(false);
        isCollecting = false;
    }

    @Override
    public void onCameraDataCallback(byte[] data, int cameraId) {
        if (data != null && data.length > 0 && isCollecting) {
            ThreadUtils.getCachedPool().execute(() -> {
                com.hfims.android.lib.palm.model.PalmCamera palmCamera = new com.hfims.android.lib.palm.model.PalmCamera(
                    data,
                    TextureView.CAMERA_WIDTH,
                    TextureView.CAMERA_HEIGHT,
                    com.hfims.android.lib.palm.SettingVar.previewRotation,
                    TextureView.REAL_WIDTH,
                    TextureView.REAL_HEIGHT,
                    com.hfims.android.lib.palm.SettingVar.palmMirrorH,
                    com.hfims.android.lib.palm.SettingVar.palmMirrorV
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
        public void onPalmIn(List<com.hfims.android.lib.palm.model.PalmTrack> palmTrackList, Map<String, Object> map) {
            // Detected palm vein
        }

        @Override
        public void onDistance(float distance) {
            // not support
        }

        @Override
        public void onImage(android.graphics.Bitmap bitmap, List<com.hfims.android.lib.palm.model.PalmTrack> palmTrackList, Map<String, Object> map) {
            // Image captured
        }

        @Override
        public void onSuccess(String onceFeature, String feature, String palmId, Map<String, Object> map) {
            // Collection successful
            runOnUiThread(() -> {
                collectedPalmFeature = feature;
                binding.tvCollectStatus.setText("Palma coletada com sucesso!");
                binding.btnSave.setEnabled(true);
                binding.btnStartCollect.setEnabled(true);
                binding.btnStopCollect.setEnabled(false);
                isCollecting = false;
                Toast.makeText(RegisterActivity.this, "Palma coletada com sucesso!", Toast.LENGTH_SHORT).show();
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

                binding.tvCollectStatus.setText("Falha: " + message);
                binding.btnStartCollect.setEnabled(true);
                binding.btnStopCollect.setEnabled(false);
                isCollecting = false;
                Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_SHORT).show();
            });
        }
    };
}
