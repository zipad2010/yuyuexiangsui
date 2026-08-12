package com.voice.assistant;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class PostDetailActivity extends WallpaperActivity {
    
    private RecyclerView rvReplies;
    private EditText etReply;
    private ImageButton btnSendReply;
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private List<JSONObject> replies;
    private ReplyAdapter adapter;
    private long postId;
    private TextView tvPostTitle;
    private TextView tvPostContent;
    private android.widget.LinearLayout llPostMedia;
    
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
        tvPostTitle = findViewById(R.id.tv_post_title);
        llPostMedia = findViewById(R.id.ll_post_media);
        tvPostContent = findViewById(R.id.tv_post_content);
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
                    if (data != null) {
                        // 后端返回结构：data.post { title, content, ... } + data.replies
                        JSONObject post = data.optJSONObject("post");
                        final String title = post != null ? post.optString("title", "") : "";
                        final String content = post != null ? post.optString("content", "") : "";
                        final JSONArray mediaUrls = post != null ? post.optJSONArray("mediaUrls") : null;
                        final JSONArray repliesArray = data.optJSONArray("replies");
                        List<JSONObject> newReplies = new ArrayList<>();
                        if (repliesArray != null) {
                            for (int i = 0; i < repliesArray.length(); i++) {
                                newReplies.add(repliesArray.optJSONObject(i));
                            }
                        }
                        runOnUiThread(() -> {
                            tvPostTitle.setText(title);
                            tvPostContent.setText(content);
                            renderMedia(mediaUrls);
                            replies.clear();
                            replies.addAll(newReplies);
                            adapter.notifyDataSetChanged();
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    /** 动态渲染帖子媒体：图片用 ImageView + Glide，视频用 VideoView（点击播放） */
    private void renderMedia(JSONArray mediaUrls) {
        llPostMedia.removeAllViews();
        if (mediaUrls == null || mediaUrls.length() == 0) {
            return;
        }
        for (int i = 0; i < mediaUrls.length(); i++) {
            String url = mediaUrls.optString(i, "");
            if (url.isEmpty()) {
                continue;
            }
            String resolved = ApiClient.resolveResourceUrl(url);
            boolean isVideo = url.toLowerCase().matches(".*\\.(mp4|webm|mkv|mov|3gp|avi)$")
                    || url.toLowerCase().contains("/video/");
            if (isVideo) {
                android.widget.VideoView videoView = new android.widget.VideoView(this);
                videoView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 220));
                videoView.setVideoPath(resolved);
                videoView.setOnClickListener(v -> {
                    if (videoView.isPlaying()) {
                        videoView.pause();
                    } else {
                        videoView.start();
                    }
                });
                android.widget.MediaController controller = new android.widget.MediaController(this);
                videoView.setMediaController(controller);
                llPostMedia.addView(videoView);
            } else {
                android.widget.ImageView imageView = new android.widget.ImageView(this);
                imageView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));
                imageView.setAdjustViewBounds(true);
                imageView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                com.bumptech.glide.Glide.with(this).load(resolved).into(imageView);
                llPostMedia.addView(imageView);
            }
        }
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