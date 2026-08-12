package com.voice.assistant;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 信息中心：查看谁回复了自己的帖子、谁给自己发了私信
 */
public class InfoCenterActivity extends WallpaperActivity {

    private RecyclerView rvCenter;
    private TextView tvEmpty, tvReplyCount, tvMessageCount;
    private View btnReplySection, btnMessageSection;
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private List<Object> rows;
    private InfoCenterAdapter adapter;

    private int forumReplyUnread;
    private int privateMessageUnread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info_center);

        apiClient = new ApiClient(this);
        tokenManager = new TokenManager(this);

        initViews();
        loadNotifications();
    }

    private void initViews() {
        rvCenter = findViewById(R.id.rv_center);
        tvEmpty = findViewById(R.id.tv_empty);
        tvReplyCount = findViewById(R.id.tv_reply_count);
        tvMessageCount = findViewById(R.id.tv_message_count);
        btnReplySection = findViewById(R.id.btn_reply_section);
        btnMessageSection = findViewById(R.id.btn_message_section);

        rows = new ArrayList<>();
        adapter = new InfoCenterAdapter(rows, (type, targetId, name) -> {
            if ("reply".equals(type)) {
                startActivity(PostDetailActivity.newIntent(this, targetId));
            } else if (targetId > 0) {
                startActivity(ChatActivity.newIntent(this, targetId, name));
            }
        });
        rvCenter.setLayoutManager(new LinearLayoutManager(this));
        rvCenter.setAdapter(adapter);

        btnReplySection.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, ForumActivity.class)));
        btnMessageSection.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, MessagesActivity.class)));
    }

    private void loadNotifications() {
        new Thread(() -> {
            try {
                String response = apiClient.getNotificationSummary(
                        tokenManager.getForumCheckedAt(), tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                runOnUiThread(() -> {
                    if (json.optInt("code") != 200) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText(json.optString("message", "加载失败"));
                        return;
                    }
                    JSONObject data = json.optJSONObject("data");
                    if (data == null) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        return;
                    }
                    forumReplyUnread = data.optInt("forumReplyUnread", 0);
                    privateMessageUnread = data.optInt("privateMessageUnread", 0);
                    tvReplyCount.setText(String.valueOf(forumReplyUnread));
                    tvMessageCount.setText(String.valueOf(privateMessageUnread));

                    buildRows(data);
                    // 已查看：标记私信已读，并更新论坛已读时间戳
                    if (privateMessageUnread > 0) {
                        markMessagesRead();
                    }
                    tokenManager.updateForumCheckedAt(System.currentTimeMillis());
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("网络错误，请稍后重试");
                });
            }
        }).start();
    }

    private void buildRows(JSONObject data) {
        rows.clear();
        JSONArray replies = data.optJSONArray("recentReplies");
        JSONArray messages = data.optJSONArray("recentMessages");

        boolean hasReply = replies != null && replies.length() > 0;
        boolean hasMessage = messages != null && messages.length() > 0;

        if (hasReply) {
            rows.add("帖子回复");
            for (int i = 0; i < replies.length(); i++) {
                JSONObject reply = replies.optJSONObject(i);
                if (reply == null) {
                    continue;
                }
                rows.add(new InfoCenterAdapter.RowItem(
                        "reply",
                        reply.optLong("postId", 0),
                        reply.optString("nickname", reply.optString("username", "匿名")),
                        reply.optString("content", ""),
                        reply.optString("postTitle", "帖子"),
                        reply.optString("avatarUrl", ""),
                        reply.optLong("createdAt", 0)));
            }
        }
        if (hasMessage) {
            rows.add("未读私信");
            for (int i = 0; i < messages.length(); i++) {
                JSONObject msg = messages.optJSONObject(i);
                if (msg == null) {
                    continue;
                }
                rows.add(new InfoCenterAdapter.RowItem(
                        "message",
                        msg.optLong("fromUserId", 0),
                        msg.optString("nickname", msg.optString("username", "匿名")),
                        msg.optString("content", ""),
                        "",
                        msg.optString("avatarUrl", ""),
                        msg.optLong("createdAt", 0)));
            }
        }
        if (!hasReply && !hasMessage) {
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
        adapter.notifyDataSetChanged();
    }

    private void markMessagesRead() {
        new Thread(() -> {
            try {
                apiClient.markMessagesRead(tokenManager.getToken());
            } catch (Exception ignored) {
            }
        }).start();
    }
}
