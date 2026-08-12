package com.voice.assistant;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
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

    /**
     * 发起私信：用户列表选择 + 输入名字搜索 结合
     */
    private void showNewMessageDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_new_message, null);
        EditText etKeyword = view.findViewById(R.id.et_user_keyword);
        TextView tvEmpty = view.findViewById(R.id.tv_user_empty);
        RecyclerView rvUsers = view.findViewById(R.id.rv_user_list);

        List<JSONObject> users = new ArrayList<>();
        UserSelectAdapter userAdapter = new UserSelectAdapter(users, user -> {
            long userId = user.optLong("userId", 0);
            String name = user.optString("nickname", user.optString("username", ""));
            startChat(userId, name);
        });
        rvUsers.setLayoutManager(new LinearLayoutManager(this));
        rvUsers.setAdapter(userAdapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("发起私信")
                .setView(view)
                .setNegativeButton("取消", null)
                .create();
        dialog.show();

        // 默认加载全部用户
        searchUsers("", users, userAdapter, tvEmpty);

        // 输入变化即搜索
        etKeyword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                searchUsers(s.toString().trim(), users, userAdapter, tvEmpty);
            }
        });

        // 搜索按钮：同样触发列表刷新
        view.findViewById(R.id.btn_user_search).setOnClickListener(v ->
                searchUsers(etKeyword.getText().toString().trim(), users, userAdapter, tvEmpty));

        // 精确查找按钮：按用户名精确匹配并直接进入聊天
        view.findViewById(R.id.btn_user_exact).setOnClickListener(v -> {
            String keyword = etKeyword.getText().toString().trim();
            if (keyword.isEmpty()) {
                Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show();
                return;
            }
            findRecipientExact(keyword, dialog);
        });
    }

    private void searchUsers(String keyword, List<JSONObject> users,
                             UserSelectAdapter adapter, TextView tvEmpty) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(
                        apiClient.searchMessageRecipients(keyword, tokenManager.getToken()));
                runOnUiThread(() -> {
                    if (json.optInt("code") != 200) {
                        Toast.makeText(this, json.optString("message", "加载用户失败"),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    users.clear();
                    JSONArray data = json.optJSONArray("data");
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            users.add(data.optJSONObject(i));
                        }
                    }
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(users.isEmpty() ? View.VISIBLE : View.GONE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "网络错误: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void findRecipientExact(String username, AlertDialog dialog) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(
                        apiClient.findMessageRecipient(username, tokenManager.getToken()));
                runOnUiThread(() -> {
                    if (json.optInt("code") == 200) {
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            dialog.dismiss();
                            startChat(data.optLong("userId"),
                                    data.optString("nickname", data.optString("username", username)));
                        }
                    } else {
                        Toast.makeText(this, json.optString("message", "无法发起私信"),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "网络错误: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void startChat(long userId, String nickname) {
        startActivity(ChatActivity.newIntent(this, userId, nickname));
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