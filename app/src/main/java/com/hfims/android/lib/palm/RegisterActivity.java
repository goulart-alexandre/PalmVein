package com.hfims.android.lib.palm;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.hfims.android.lib.palm.dao.UserDao;
import com.hfims.android.lib.palm.databinding.ActivityRegisterBinding;
import com.hfims.android.lib.palm.model.User;

public class RegisterActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_COLLECT = 1001;
    
    private ActivityRegisterBinding binding;
    private UserDao userDao;
    private String collectedPalmFeature = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userDao = new UserDao(this);
        setupClickListeners();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (userDao != null) {
            userDao.close();
        }
        binding = null;
    }

    private void setupClickListeners() {
        binding.btnStartCollect.setOnClickListener(v -> startCollect());
        binding.btnSave.setOnClickListener(v -> saveUser());
        binding.btnClearAll.setOnClickListener(v -> clearAllUsers());
        binding.btnBackToMain.setOnClickListener(v -> finish());
        
        // Ocultar teclado ao clicar em qualquer lugar da tela
        binding.getRoot().setOnClickListener(v -> hideKeyboard());
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

        // Abrir tela de câmera completa para coleta
        Intent intent = new Intent(this, CameraCollectActivity.class);
        intent.putExtra(CameraCollectActivity.EXTRA_NAME, name);
        intent.putExtra(CameraCollectActivity.EXTRA_USER_ID, userId);
        startActivityForResult(intent, REQUEST_CAMERA_COLLECT);
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CAMERA_COLLECT) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                collectedPalmFeature = data.getStringExtra(CameraCollectActivity.EXTRA_PALM_FEATURE);
                if (collectedPalmFeature != null) {
                    binding.tvCollectStatus.setText("Palma coletada com sucesso!");
                    binding.btnSave.setEnabled(true);
                    Toast.makeText(this, "Palma coletada com sucesso!", Toast.LENGTH_SHORT).show();
                } else {
                    binding.tvCollectStatus.setText("Falha na coleta da palma");
                    Toast.makeText(this, "Falha na coleta da palma", Toast.LENGTH_SHORT).show();
                }
            } else {
                binding.tvCollectStatus.setText("Coleta cancelada");
                Toast.makeText(this, "Coleta cancelada", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void clearForm() {
        binding.etName.setText("");
        binding.etUserId.setText("");
        collectedPalmFeature = null;
        binding.btnSave.setEnabled(false);
        binding.tvCollectStatus.setText("Aguardando coleta...");
        binding.btnStartCollect.setEnabled(true);
    }

}
