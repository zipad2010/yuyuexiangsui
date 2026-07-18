package com.voice.assistant;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ForumActivity extends AppCompatActivity {
    
    private RecyclerView rvPosts;
    private Button btnNewPost;
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private List<JSONObject> posts;
    private ForumAdapter adapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forum);
        
        apiClient = new ApiClient();
        tokenManager = new TokenManager(this);
        
        initViews();
        loadPosts();
        
        btnNewPost.setOnClickListener(v -> showNewPostDialog());
    }
    
    private void initViews() {
        rvPosts = findViewById(R.id.rv_posts);
        btnNewPost = findViewById(R.id.btn_new_post);
        
        posts = new ArrayList<>();
        adapter = new ForumAdapter(posts, postId -> showPostDetail(postId));
        rvPosts.setLayoutManager(new LinearLayoutManager(this));
        rvPosts.setAdapter(adapter);
    }
    
    private void loadPosts() {
        new Thread(() -> {
            try {
                String response = apiClient.getForumPosts(tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                if (json.getInt("code") == 200) {
                    JSONArray data = json.getJSONArray("data");
                    runOnUiThread(() -> {
                        posts.clear();
                        for (int i = 0; i < data.length(); i++) {
                            posts.add(data.getJSONObject(i));
                        }
                        adapter.notifyDataSetChanged();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void showNewPostDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_new_post, null);
        EditText etTitle = view.findViewById(R.id.et_title);
        EditText etContent = view.findViewById(R.id.et_content);
        
        new AlertDialog.Builder(this)
            .setTitle("New Post")
            .setView(view)
            .setPositiveButton("Post", (dialog, which) -> {
                String title = etTitle.getText().toString().trim();
                String content = etContent.getText().toString().trim();
                if (!title.isEmpty() && !content.isEmpty()) {
                    createPost(title, content);
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }
    
    private void createPost(String title, String content) {
        new Thread(() -> {
            try {
                String response = apiClient.createForumPost(title, content, tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                runOnUiThread(() -> {
                    if (json.getInt("code") == 200) {
                        Toast.makeText(this, "Posted", Toast.LENGTH_SHORT).show();
                        loadPosts();
                    } else {
                        Toast.makeText(this, json.getString("message"), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    
    private void showPostDetail(Long postId) {
        // 打开帖子详情页
        startActivity(PostDetailActivity.newIntent(this, postId));
    }
}