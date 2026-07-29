package com.voice.assistant;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ChatActivity extends AppCompatActivity {
    
    private TextView tvTitle;
    private RecyclerView rvMessages;
    private EditText etMessage;
    private ImageButton btnSend;
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private List<JSONObject> messages;
    private ChatMessageAdapter adapter;
    private long targetUserId;
    private String targetUsername;
    private Handler handler;
    private Runnable refreshRunnable;
    
    public static android.content.Intent newIntent(android.content.Context context, long userId, String username) {
        android.content.Intent intent = new android.content.Intent(context, ChatActivity.class);
        intent.putExtra("targetUserId", userId);
        intent.putExtra("targetUsername", username);
        return intent;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        
        targetUserId = getIntent().getLongExtra("targetUserId", 0);
        targetUsername = getIntent().getStringExtra("targetUsername");
        if (targetUserId <= 0) {
            Toast.makeText(this, "无效的聊天对象", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        apiClient = new ApiClient(this);
        tokenManager = new TokenManager(this);
        handler = new Handler(Looper.getMainLooper());
        
        initViews();
        loadConversation();
        
        btnSend.setOnClickListener(v -> sendMessage());
        
        // 每3秒刷新一次消息
        refreshRunnable = () -> {
            loadConversation();
            handler.postDelayed(refreshRunnable, 3000);
        };
        handler.post(refreshRunnable);
    }
    
    private void initViews() {
        tvTitle = findViewById(R.id.tv_chat_title);
        rvMessages = findViewById(R.id.rv_messages);
        etMessage = findViewById(R.id.et_message);
        btnSend = findViewById(R.id.btn_send_message);
        tvTitle.setText(targetUsername == null || targetUsername.isEmpty() ? "私信" : targetUsername);
        
        messages = new ArrayList<>();
        adapter = new ChatMessageAdapter(messages, tokenManager.getUserId());
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);
    }
    
    private void loadConversation() {
        new Thread(() -> {
            try {
                String response = apiClient.getConversation(targetUserId, tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                int code = json.optInt("code", -1);
                JSONArray data = json.optJSONArray("data");
                List<JSONObject> newMessages = new ArrayList<>();
                if (code == 200 && data != null) {
                    for (int i = 0; i < data.length(); i++) {
                        JSONObject message = data.optJSONObject(i);
                        if (message != null) {
                            newMessages.add(message);
                        }
                    }
                }
                runOnUiThread(() -> {
                    messages.clear();
                    messages.addAll(newMessages);
                    adapter.notifyDataSetChanged();
                    if (messages.size() > 0) {
                        JSONObject first = messages.get(0);
                        targetUsername = first.optString("fromUsername", "User");
                        if (targetUsername.equals(tokenManager.getUsername())) {
                            targetUsername = first.optString("toUsername", "User");
                        }
                        tvTitle.setText(targetUsername);
                    }
                    if (messages.size() > 0) {
                        rvMessages.scrollToPosition(messages.size() - 1);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void sendMessage() {
        String content = etMessage.getText().toString().trim();
        if (content.isEmpty()) return;
        
        etMessage.setText("");
        
        new Thread(() -> {
            try {
                String response = apiClient.sendMessage(targetUserId, content, tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                runOnUiThread(() -> {
                    try {
                        if (json.getInt("code") == 200) {
                            loadConversation();
                        } else {
                            Toast.makeText(this, json.getString("message"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (JSONException ex) {
                        Toast.makeText(this, "JSON解析失败", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && refreshRunnable != null) {
            handler.removeCallbacks(refreshRunnable);
        }
    }
}