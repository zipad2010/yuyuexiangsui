package com.voice.assistant;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class PostDetailActivity extends AppCompatActivity {
    
    private RecyclerView rvReplies;
    private EditText etReply;
    private ImageButton btnSendReply;
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private List<JSONObject> replies;
    private ReplyAdapter adapter;
    private Long postId;
    
    public static android.content.Intent newIntent(android.content.Context context, Long postId) {
        android.content.Intent intent = new android.content.Intent(context, PostDetailActivity.class);
        intent.putExtra("postId", postId);
        return intent;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);
        
        postId = getIntent().getLongExtra("postId", 0);
        apiClient = new ApiClient();
        tokenManager = new TokenManager(this);
        
        initViews();
        loadReplies();
        
        btnSendReply.setOnClickListener(v -> sendReply());
    }
    
    private void initViews() {
        rvReplies = findViewById(R.id.rv_replies);
        etReply = findViewById(R.id.et_reply);
        btnSendReply = findViewById(R.id.btn_send_reply);
        
        replies = new ArrayList<>();
        adapter = new ReplyAdapter(replies);
        rvReplies.setLayoutManager(new LinearLayoutManager(this));
        rvReplies.setAdapter(adapter);
    }
    
    private void loadReplies() {
        new Thread(() -> {
            try {
                String response = apiClient.getForumPost(postId, tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                if (json.getInt("code") == 200) {
                    JSONObject data = json.getJSONObject("data");
                    JSONArray repliesArray = data.optJSONArray("replies");
                    runOnUiThread(() -> {
                        replies.clear();
                        if (repliesArray != null) {
                            for (int i = 0; i < repliesArray.length(); i++) {
                                replies.add(repliesArray.getJSONObject(i));
                            }
                        }
                        adapter.notifyDataSetChanged();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void sendReply() {
        String content = etReply.getText().toString().trim();
        if (content.isEmpty()) {
            Toast.makeText(this, "Please enter content", Toast.LENGTH_SHORT).show();
            return;
        }
        
        new Thread(() -> {
            try {
                String response = apiClient.createForumReply(postId, content, tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                runOnUiThread(() -> {
                    if (json.getInt("code") == 200) {
                        etReply.setText("");
                        loadReplies();
                        Toast.makeText(this, "Reply sent", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, json.getString("message"), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}