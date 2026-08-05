package com.voice.assistant;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class MessagesActivity extends WallpaperActivity {
    
    private RecyclerView rvConversations;
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private List<JSONObject> conversations;
    private ConversationAdapter adapter;
    private FloatingActionButton btnNewMessage;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);
        
        apiClient = new ApiClient(this);
        tokenManager = new TokenManager(this);
        
        initViews();
        loadConversations();
    }
    
    private void initViews() {
        rvConversations = findViewById(R.id.rv_conversations);
        conversations = new ArrayList<>();
        btnNewMessage = findViewById(R.id.btn_new_message);
        adapter = new ConversationAdapter(conversations, (userId, username) -> {
            startActivity(ChatActivity.newIntent(this, userId, username));
        });
        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        rvConversations.setAdapter(adapter);
        btnNewMessage.setOnClickListener(v -> showNewMessageDialog());
        playEntranceAnimation();
    }

    private void playEntranceAnimation() {
        rvConversations.setAlpha(0f);
        rvConversations.animate().alpha(1f).setDuration(380L).start();
        btnNewMessage.setScaleX(0f);
        btnNewMessage.setScaleY(0f);
        btnNewMessage.animate().scaleX(1f).scaleY(1f).setStartDelay(160L).setDuration(300L).start();
    }

    private void showNewMessageDialog() {
        EditText input = new EditText(this);
        input.setHint("对方用户名");
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, 0, padding, 0);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("发起私信")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("开始聊天", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> findRecipient(input.getText().toString().trim(), dialog)));
        dialog.show();
    }

    private void findRecipient(String username, AlertDialog dialog) {
        if (username.isEmpty()) {
            Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(apiClient.findMessageRecipient(username, tokenManager.getToken()));
                runOnUiThread(() -> {
                    if (json.optInt("code") == 200) {
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            dialog.dismiss();
                            startActivity(ChatActivity.newIntent(this,
                                    data.optLong("userId"), data.optString("username", username)));
                        }
                    } else {
                        Toast.makeText(this, json.optString("message", "无法发起私信"), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "网络错误: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    
    private void loadConversations() {
        new Thread(() -> {
            try {
                String response = apiClient.getConversations(tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                if (json.getInt("code") == 200) {
                    JSONArray data = json.getJSONArray("data");
                    List<JSONObject> newConversations = new ArrayList<>();
                    for (int i = 0; i < data.length(); i++) {
                        newConversations.add(data.getJSONObject(i));
                    }
                    runOnUiThread(() -> {
                        conversations.clear();
                        conversations.addAll(newConversations);
                        adapter.notifyDataSetChanged();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (apiClient == null || tokenManager == null) {
            return;
        }
        new Thread(() -> {
            try {
                apiClient.markMessagesRead(tokenManager.getToken());
            } catch (Exception ignored) {
            }
        }).start();
    }
}