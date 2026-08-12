package com.voice.assistant;

import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class ForumActivity extends WallpaperActivity {
    
    private RecyclerView rvPosts;
    private FloatingActionButton btnNewPost;
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private List<JSONObject> posts;
    private ForumAdapter adapter;

    private final List<String> pendingMediaUrls = new ArrayList<>();
    private ActivityResultLauncher<String[]> imagePicker;
    private ActivityResultLauncher<String[]> videoPicker;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forum);
        
        apiClient = new ApiClient(this);
        tokenManager = new TokenManager(this);

        imagePicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri != null) {
                        uploadSelectedMedia(uri, true);
                    }
                });
        videoPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri != null) {
                        uploadSelectedMedia(uri, false);
                    }
                });
        
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
        pendingMediaUrls.clear();
        View view = getLayoutInflater().inflate(R.layout.dialog_new_post, null);
        EditText etTitle = view.findViewById(R.id.et_title);
        EditText etContent = view.findViewById(R.id.et_content);
        TextView tvMediaStatus = view.findViewById(R.id.tv_media_status);

        view.findViewById(R.id.btn_pick_image).setOnClickListener(v ->
                imagePicker.launch(new String[]{"image/*"}));
        view.findViewById(R.id.btn_pick_video).setOnClickListener(v ->
                videoPicker.launch(new String[]{"video/*"}));

        new AlertDialog.Builder(this)
            .setTitle("发布帖子")
            .setView(view)
            .setPositiveButton("发布", (dialog, which) -> {
                String title = etTitle.getText().toString().trim();
                String content = etContent.getText().toString().trim();
                if (!title.isEmpty() && !content.isEmpty()) {
                    createPost(title, content, new ArrayList<>(pendingMediaUrls));
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void uploadSelectedMedia(Uri uri, boolean isImage) {
        new Thread(() -> {
            try {
                byte[] data = readBytes(uri);
                if (data.length > 50 * 1024 * 1024) {
                    throw new IllegalArgumentException("媒体文件不能超过 50MB");
                }
                String mimeType = getContentResolver().getType(uri);
                if (mimeType == null) {
                    mimeType = isImage ? "image/jpeg" : "video/mp4";
                }
                String fileName = getDisplayName(uri);
                String response = apiClient.uploadForumMedia(data, fileName, mimeType, tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                runOnUiThread(() -> {
                    if (json.optInt("code") == 200) {
                        JSONObject dataObj = json.optJSONObject("data");
                        String url = dataObj == null ? null : dataObj.optString("url", null);
                        if (url != null && !url.isEmpty()) {
                            pendingMediaUrls.add(url);
                            Toast.makeText(this, "媒体已添加：" + fileName, Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(this, json.optString("message", "媒体上传失败"), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "上传失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private byte[] readBytes(Uri uri) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) {
                throw new IllegalArgumentException("无法读取所选文件");
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
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        }
        return "media.jpg";
    }
    
    private void createPost(String title, String content, List<String> mediaUrls) {
        new Thread(() -> {
            try {
                String response = apiClient.createForumPostWithMedia(title, content, mediaUrls, tokenManager.getToken());
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
        startActivity(PostDetailActivity.newIntent(this, postId));
    }
}
