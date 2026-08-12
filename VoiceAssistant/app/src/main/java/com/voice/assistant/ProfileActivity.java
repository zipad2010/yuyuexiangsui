package com.voice.assistant;

import android.net.Uri;
import android.content.Intent;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import com.bumptech.glide.Glide;
import com.google.gson.Gson;
import org.json.JSONException;
import org.json.JSONObject;
import okhttp3.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ProfileActivity extends WallpaperActivity {
    
    private EditText etNickname, etSignature;
    private Button btnSave;
    private ImageView ivAvatar;
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private ActivityResultLauncher<String> avatarPicker;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        
        apiClient = new ApiClient(this);
        tokenManager = new TokenManager(this);
        if (!tokenManager.isLoggedIn()) {
            Intent loginIntent = new Intent(this, LoginActivity.class);
            loginIntent.putExtra("return_to_profile", true);
            startActivity(loginIntent);
            finish();
            return;
        }
        avatarPicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                uploadAvatar(uri);
            }
        });
        
        initViews();
        loadProfile();
        
        btnSave.setOnClickListener(v -> saveProfile());
        ivAvatar.setOnClickListener(v -> avatarPicker.launch("image/*"));
    }
    
    private void initViews() {
        etNickname = findViewById(R.id.et_nickname);
        etSignature = findViewById(R.id.et_signature);
        btnSave = findViewById(R.id.btn_save);
        ivAvatar = findViewById(R.id.iv_avatar);
    }
    
    private void loadProfile() {
        String token = tokenManager.getToken();
        if (token == null || token.trim().isEmpty()) {
            handleProfileError(401, "登录已失效，请重新登录");
            return;
        }
        new Thread(() -> {
            try {
                String response = apiClient.getProfile(token);
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                if (code == 200) {
                    JSONObject data = json.optJSONObject("data");
                    if (data == null) {
                        throw new JSONException("资料响应缺少 data 字段");
                    }
                    String nickname = data.optString("nickname", tokenManager.getUsername());
                    String signature = data.optString("signature", "");
                    String avatarUrl = data.optString("avatarUrl", "");
                    
                    runOnUiThread(() -> {
                        etNickname.setText(nickname);
                        etSignature.setText(signature);
                        if (!avatarUrl.isEmpty()) {
                            Glide.with(this)
                                .load(ApiClient.resolveResourceUrl(avatarUrl))
                                .placeholder(R.drawable.ic_default_avatar)
                                .error(R.drawable.ic_default_avatar)
                                .into(ivAvatar);
                        }
                    });
                } else {
                    String message = json.optString("message", "资料加载失败");
                    runOnUiThread(() -> handleProfileError(code, message));
                }
            } catch (Exception e) {
                String message = e.getMessage();
                runOnUiThread(() -> Toast.makeText(this,
                        message == null || message.trim().isEmpty()
                                ? "无法加载个人资料，请检查网络或服务状态"
                                : "无法加载个人资料: " + message,
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }
    
    private void saveProfile() {
        String nickname = etNickname.getText().toString().trim();
        String signature = etSignature.getText().toString().trim();

        if (nickname.isEmpty()) {
            etNickname.setError("请输入昵称");
            etNickname.requestFocus();
            return;
        }
        
        new Thread(() -> {
            try {
                String response = apiClient.updateProfile(nickname, signature, tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                int code = json.getInt("code");
                String message = json.optString("message", "保存失败");
                runOnUiThread(() -> {
                    if (code == 200) {
                        Toast.makeText(this, "资料已保存", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        handleProfileError(code, message);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "保存失败，请检查网络", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void handleProfileError(int code, String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        if (code == 401) {
            tokenManager.clear();
            Intent loginIntent = new Intent(this, LoginActivity.class);
            loginIntent.putExtra("return_to_profile", true);
            startActivity(loginIntent);
            finishAffinity();
        }
    }

    private void uploadAvatar(Uri uri) {
        ivAvatar.setEnabled(false);
        Glide.with(this).load(uri).into(ivAvatar);
        new Thread(() -> {
            try {
                byte[] imageData = readBytes(uri);
                if (imageData.length > 5 * 1024 * 1024) {
                    throw new IllegalArgumentException("头像不能超过 5MB");
                }
                String mimeType = getContentResolver().getType(uri);
                if (mimeType == null) {
                    mimeType = "image/jpeg";
                }
                String response = apiClient.uploadAvatar(
                        imageData, getDisplayName(uri), mimeType, tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                String message = json.optString("message", "头像上传失败");
                runOnUiThread(() -> {
                    ivAvatar.setEnabled(true);
                    Toast.makeText(this, code == 200 ? "头像已更新" : message, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    ivAvatar.setEnabled(true);
                    Toast.makeText(this, e.getMessage() == null ? "头像上传失败" : e.getMessage(), Toast.LENGTH_SHORT).show();
                    loadProfile();
                });
            }
        }).start();
    }

    private byte[] readBytes(Uri uri) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) {
                throw new IllegalArgumentException("无法读取所选图片");
            }
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private String getDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        }
        return "avatar.jpg";
    }
}