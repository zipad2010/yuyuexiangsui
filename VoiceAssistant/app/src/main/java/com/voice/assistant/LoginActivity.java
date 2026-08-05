package com.voice.assistant;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {
    
    private EditText etUsername, etPassword;
    private Button btnLogin;
    private TextView tvRegister;
    private ProgressBar progressBar;
    
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private Handler mainHandler;
    private boolean isRegisterMode = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        mainHandler = new Handler(Looper.getMainLooper());
        apiClient = new ApiClient(this);
        tokenManager = new TokenManager(this);
        
        if (tokenManager.isLoggedIn()) {
            startMainActivity();
            return;
        }
        
        initViews();
        playEntranceAnimation();
        
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.RECORD_AUDIO},
                    1001);
        }
    }
    
    private void initViews() {
        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        tvRegister = findViewById(R.id.tv_register);
        progressBar = findViewById(R.id.progress_bar);
        
        btnLogin.setOnClickListener(v -> {
            if (isRegisterMode) {
                doRegister();
            } else {
                doLogin();
            }
        });
        
        tvRegister.setOnClickListener(v -> {
            isRegisterMode = !isRegisterMode;
            if (isRegisterMode) {
                btnLogin.setText("注册");
                tvRegister.setText("已有账号？去登录");
            } else {
                btnLogin.setText("登录");
                tvRegister.setText("还没有账号？立即注册");
            }
        });
    }

    private void playEntranceAnimation() {
        ViewGroup content = findViewById(R.id.login_content);
        for (int index = 0; index < content.getChildCount(); index++) {
            View child = content.getChildAt(index);
            child.setAlpha(0f);
            child.setTranslationY(24f);
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(index * 55L)
                    .setDuration(360L)
                    .start();
        }
    }
    
    private void doLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show();
            return;
        }
        
        setLoading(true);
        
        new Thread(() -> {
            try {
                String response = apiClient.login(username, password);
                JSONObject json = new JSONObject(response);
                int code = json.getInt("code");
                String message = json.optString("message", "登录失败");
                String token = null;
                String returnedUsername = null;
                String balance = null;
                long userId = 0;
                if (code == 200) {
                    JSONObject data = json.getJSONObject("data");
                    token = data.optString("token", null);
                    returnedUsername = data.optString("username", null);
                    balance = data.optString("balance", null);
                    userId = data.optLong("userId", 0);
                }
                final int finalCode = code;
                final String finalMessage = message;
                final String finalToken = token;
                final String finalReturnedUsername = returnedUsername;
                final String finalBalance = balance;
                final long finalUserId = userId;

                mainHandler.post(() -> {
                    setLoading(false);
                    if (finalCode == 200 && finalToken != null && finalReturnedUsername != null && finalUserId > 0) {
                        tokenManager.saveToken(finalToken, finalReturnedUsername, finalBalance, finalUserId);
                        Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
                        startMainActivity();
                    } else {
                        Toast.makeText(this, finalMessage, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    Toast.makeText(this, "网络错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void doRegister() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        
        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (password.length() < 4) {
            Toast.makeText(this, "密码至少4位", Toast.LENGTH_SHORT).show();
            return;
        }
        
        setLoading(true);
        
        new Thread(() -> {
            try {
                String response = apiClient.register(username, password);
                JSONObject json = new JSONObject(response);
                int code = json.getInt("code");
                String message = json.optString("message", "注册成功，请登录");
                
                mainHandler.post(() -> {
                    setLoading(false);
                    if (code == 200) {
                        Toast.makeText(this, "注册成功，请登录", Toast.LENGTH_SHORT).show();
                        isRegisterMode = false;
                        btnLogin.setText("登录");
                        tvRegister.setText("还没有账号？立即注册");
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setLoading(false);
                    Toast.makeText(this, "网络错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!loading);
        tvRegister.setEnabled(!loading);
    }
    
    private void startMainActivity() {
        Intent intent = new Intent(this, HomeActivity.class);
        startActivity(intent);
        finish();
    }
}