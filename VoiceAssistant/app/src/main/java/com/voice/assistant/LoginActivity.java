package com.voice.assistant;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;
import com.guolindev.permissionx.PermissionX;

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
        apiClient = new ApiClient();
        tokenManager = new TokenManager(this);
        
        if (tokenManager.isLoggedIn()) {
            startMainActivity();
            return;
        }
        
        initViews();
        
        PermissionX.init(this)
            .permissions(android.Manifest.permission.RECORD_AUDIO)
            .request((allGranted, grantedList, deniedList) -> {});
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
                
                mainHandler.post(() -> {
                    setLoading(false);
                    try {
                        if (json.getInt("code") == 200) {
                            JSONObject data = json.getJSONObject("data");
                            tokenManager.saveToken(
                                data.getString("token"),
                                data.getString("username"),
                                data.getString("balance")
                            );
                            Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
                            startMainActivity();
                        } else {
                            Toast.makeText(this, json.getString("message"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "解析失败", Toast.LENGTH_SHORT).show();
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
                
                mainHandler.post(() -> {
                    setLoading(false);
                    if (json.getInt("code") == 200) {
                        Toast.makeText(this, "注册成功，请登录", Toast.LENGTH_SHORT).show();
                        isRegisterMode = false;
                        btnLogin.setText("登录");
                        tvRegister.setText("还没有账号？立即注册");
                    } else {
                        Toast.makeText(this, json.getString("message"), Toast.LENGTH_SHORT).show();
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
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}