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
import org.json.JSONException;
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
    private long postId;
    
    public static android.content.Intent newIntent(android.content.Context context, long postId) {
        android.content.Intent intent = new android.content.Intent(context, PostDetailActivity.class);
        intent.putExtra("postId", postId);
        return intent;
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_detail);
        
        postId = getIntent().getLongExtra("postId", 0);
        if (postId <= 0) {
            Toast.makeText(this, "帖子不存在", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        apiClient = new ApiClient(this);
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
                if (json.optInt("code", -1) == 200) {
                    JSONObject data = json.optJSONObject("data");
                    JSONArray repliesArray = data != null ? data.optJSONArray("replies") : null;
                    List<JSONObject> newReplies = new ArrayList<>();
                    if (repliesArray != null) {
                        for (int i = 0; i < repliesArray.length(); i++) {
                            newReplies.add(repliesArray.optJSONObject(i));
                        }
                    }
                    runOnUiThread(() -> {
                        replies.clear();
                        replies.addAll(newReplies);
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
                int code = json.getInt("code");
                String message = json.optString("message", "Reply sent");
                runOnUiThread(() -> {
                    if (code == 200) {
                        etReply.setText("");
                        loadReplies();
                        Toast.makeText(this, "Reply sent", Toast.LENGTH_SHORT).show();
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