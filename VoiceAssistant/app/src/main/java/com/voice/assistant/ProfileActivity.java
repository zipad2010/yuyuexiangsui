package com.voice.assistant;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import org.json.JSONException;
import org.json.JSONObject;
import okhttp3.*;

public class ProfileActivity extends AppCompatActivity {
    
    private EditText etNickname, etSignature;
    private Button btnSave;
    private ImageView ivAvatar;
    private ApiClient apiClient;
    private TokenManager tokenManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        
        apiClient = new ApiClient(this);
        tokenManager = new TokenManager(this);
        
        initViews();
        loadProfile();
        
        btnSave.setOnClickListener(v -> saveProfile());
    }
    
    private void initViews() {
        etNickname = findViewById(R.id.et_nickname);
        etSignature = findViewById(R.id.et_signature);
        btnSave = findViewById(R.id.btn_save);
        ivAvatar = findViewById(R.id.iv_avatar);
    }
    
    private void loadProfile() {
        new Thread(() -> {
            try {
                String response = apiClient.getProfile(tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                if (json.getInt("code") == 200) {
                    JSONObject data = json.getJSONObject("data");
                    String nickname = data.optString("nickname", tokenManager.getUsername());
                    String signature = data.optString("signature", "");
                    String avatarUrl = data.optString("avatarUrl", null);
                    
                    runOnUiThread(() -> {
                        etNickname.setText(nickname);
                        etSignature.setText(signature);
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            Glide.with(this).load(avatarUrl).into(ivAvatar);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void saveProfile() {
        String nickname = etNickname.getText().toString().trim();
        String signature = etSignature.getText().toString().trim();
        
        new Thread(() -> {
            try {
                String response = apiClient.updateProfile(nickname, signature, tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                int code = json.getInt("code");
                String message = json.optString("message", "Saved");
                runOnUiThread(() -> {
                    if (code == 200) {
                        Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}