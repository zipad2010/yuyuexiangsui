package com.voice.assistant;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class MessagesActivity extends AppCompatActivity {
    
    private RecyclerView rvConversations;
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private List<JSONObject> conversations;
    private ConversationAdapter adapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_messages);
        
        apiClient = new ApiClient();
        tokenManager = new TokenManager(this);
        
        initViews();
        loadConversations();
    }
    
    private void initViews() {
        rvConversations = findViewById(R.id.rv_conversations);
        conversations = new ArrayList<>();
        adapter = new ConversationAdapter(conversations, userId -> {
            // 打开聊天页面
            startActivity(ChatActivity.newIntent(this, userId));
        });
        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        rvConversations.setAdapter(adapter);
    }
    
    private void loadConversations() {
        new Thread(() -> {
            try {
                String response = apiClient.getConversations(tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                if (json.getInt("code") == 200) {
                    JSONArray data = json.getJSONArray("data");
                    runOnUiThread(() -> {
                        conversations.clear();
                        for (int i = 0; i < data.length(); i++) {
                            conversations.add(data.getJSONObject(i));
                        }
                        adapter.notifyDataSetChanged();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}