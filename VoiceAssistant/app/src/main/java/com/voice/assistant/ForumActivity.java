package com.voice.assistant;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ForumActivity extends WallpaperActivity {
    
    private RecyclerView rvPosts;
    private FloatingActionButton btnNewPost;
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private List<JSONObject> posts;
    private ForumAdapter adapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forum);
        
        apiClient = new ApiClient(this);
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
        rvPosts.setAlpha(0f);
        rvPosts.animate().alpha(1f).setDuration(380L).start();
        btnNewPost.setScaleX(0f);
        btnNewPost.setScaleY(0f);
        btnNewPost.animate().scaleX(1f).scaleY(1f).setStartDelay(160L).setDuration(300L).start();
    }
    
    private void loadPosts() {
        new Thread(() -> {
            try {
                String response = apiClient.getForumPosts(tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                if (json.getInt("code") == 200) {
                    JSONArray data = json.getJSONArray("data");
                    List<JSONObject> loadedPosts = new ArrayList<>();
                    for (int i = 0; i < data.length(); i++) {
                        loadedPosts.add(data.getJSONObject(i));
                    }
                    runOnUiThread(() -> {
                        posts.clear();
                        posts.addAll(loadedPosts);
                        adapter.notifyDataSetChanged();
                    });
                } else {
                    String message = json.optString("message", "论坛加载失败");
                    runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "无法连接论坛，请检查网络或服务状态", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tokenManager != null) {
            tokenManager.updateForumCheckedAt(System.currentTimeMillis());
        }
    }
    
    private void showNewPostDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_new_post, null);
        EditText etTitle = view.findViewById(R.id.et_title);
        EditText etContent = view.findViewById(R.id.et_content);
        
        new AlertDialog.Builder(this)
            .setTitle("发布帖子")
            .setView(view)
            .setPositiveButton("发布", (dialog, which) -> {
                String title = etTitle.getText().toString().trim();
                String content = etContent.getText().toString().trim();
                if (!title.isEmpty() && !content.isEmpty()) {
                    createPost(title, content);
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void createPost(String title, String content) {
        new Thread(() -> {
            try {
                String response = apiClient.createForumPost(title, content, tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                int code = json.getInt("code");
                String message = json.optString("message", "Posted");
                runOnUiThread(() -> {
                    if (code == 200) {
                        Toast.makeText(this, "发布成功", Toast.LENGTH_SHORT).show();
                        loadPosts();
                    } else {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    
    private void showPostDetail(long postId) {
        // 打开帖子详情页
        startActivity(PostDetailActivity.newIntent(this, postId));
    }
}