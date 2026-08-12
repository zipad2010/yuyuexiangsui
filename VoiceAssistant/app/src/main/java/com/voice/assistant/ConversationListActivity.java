package com.voice.assistant;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话列表：查看全部对话，新建对话（可命名/选人设），切换对话，重命名/删除
 */
public class ConversationListActivity extends WallpaperActivity {

    private static final String PREFS_NAME = "voice_prefs";
    private static final String KEY_CURRENT_CONVERSATION = "current_conversation_id";

    private RecyclerView rvConversations;
    private TextView tvEmpty;
    private FloatingActionButton btnNew;
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private List<JSONObject> conversations;
    private ConversationEntryAdapter adapter;
    private List<JSONObject> subscribedPersonas;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversations);

        apiClient = new ApiClient(this);
        tokenManager = new TokenManager(this);

        initViews();
        loadConversations();
    }

    private void initViews() {
        rvConversations = findViewById(R.id.rv_conversations);
        tvEmpty = findViewById(R.id.tv_empty);
        btnNew = findViewById(R.id.btn_new_conversation);

        conversations = new ArrayList<>();
        subscribedPersonas = new ArrayList<>();
        adapter = new ConversationEntryAdapter(conversations, new ConversationEntryAdapter.OnItemClickListener() {
            @Override
            public void onOpen(JSONObject conversation) {
                long id = conversation.optLong("id", 0);
                if (id >= 0) {
                    setCurrentConversation(id);
                    Toast.makeText(ConversationListActivity.this,
                            "已切换到：" + conversation.optString("title", "对话"),
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onRename(JSONObject conversation) {
                if (conversation.optBoolean("defaultConversation", false)) {
                    Toast.makeText(ConversationListActivity.this, "默认对话不可重命名",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                showRenameDialog(conversation);
            }

            @Override
            public void onDelete(JSONObject conversation) {
                if (conversation.optBoolean("defaultConversation", false)) {
                    Toast.makeText(ConversationListActivity.this, "默认对话不可删除",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                showDeleteDialog(conversation);
            }

            @Override
            public void onSetPersona(JSONObject conversation) {
                showPersonaPicker(conversation);
            }

            @Override
            public void onLongClick(JSONObject conversation) {
                showItemMenu(conversation);
            }
        });
        rvConversations.setLayoutManager(new LinearLayoutManager(this));
        rvConversations.setAdapter(adapter);

        btnNew.setOnClickListener(v -> showCreateDialog());
    }

    /** 长按会话弹窗：打开 / 重命名 / 切换人设 / 删除 */
    private void showItemMenu(JSONObject conversation) {
        final boolean isDefault = conversation.optBoolean("defaultConversation", false);
        final String[] actions = isDefault
                ? new String[]{"打开对话"}
                : new String[]{"打开对话", "重命名", "切换人设", "删除对话"};
        new AlertDialog.Builder(this)
                .setTitle(conversation.optString("title", "对话"))
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            onOpen(conversation);
                            break;
                        case 1:
                            showRenameDialog(conversation);
                            break;
                        case 2:
                            showPersonaPicker(conversation);
                            break;
                        case 3:
                            showDeleteDialog(conversation);
                            break;
                        default:
                            break;
                    }
                })
                .show();
    }

    private void onOpen(JSONObject conversation) {
        long id = conversation.optLong("id", 0);
        if (id >= 0) {
            setCurrentConversation(id);
            Toast.makeText(this, "已切换到：" + conversation.optString("title", "对话"),
                    Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadConversations() {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(apiClient.getChatConversations(tokenManager.getToken()));
                runOnUiThread(() -> {
                    if (json.optInt("code") != 200) {
                        tvEmpty.setVisibility(View.VISIBLE);
                        tvEmpty.setText(json.optString("message", "加载失败"));
                        return;
                    }
                    conversations.clear();
                    JSONArray data = json.optJSONArray("data");
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            conversations.add(data.optJSONObject(i));
                        }
                    }
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(conversations.isEmpty() ? View.VISIBLE : View.GONE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("网络错误，请稍后重试");
                });
            }
        }).start();
    }

    private void loadSubscribedPersonas(Runnable after) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(apiClient.getSubscribedPersonas(tokenManager.getToken()));
                runOnUiThread(() -> {
                    subscribedPersonas.clear();
                    JSONArray data = json.optJSONArray("data");
                    if (json.optInt("code") == 200 && data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            subscribedPersonas.add(data.optJSONObject(i));
                        }
                    }
                    if (after != null) {
                        after.run();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (after != null) {
                        after.run();
                    }
                });
            }
        }).start();
    }

    /** 新建对话：输入名称 + 可选人设 */
    private void showCreateDialog() {
        loadSubscribedPersonas(() -> {
            View view = getLayoutInflater().inflate(R.layout.dialog_new_conversation, null);
            EditText etName = view.findViewById(R.id.et_conv_name);
            TextView tvPersonaPick = view.findViewById(R.id.tv_conv_persona_pick);

            final JSONObject[] selectedPersona = {null};
            tvPersonaPick.setOnClickListener(v -> {
                if (subscribedPersonas.isEmpty()) {
                    Toast.makeText(this, "还没有订阅的人设，请先到投稿中心订阅", Toast.LENGTH_SHORT).show();
                    return;
                }
                String[] names = new String[subscribedPersonas.size()];
                for (int i = 0; i < subscribedPersonas.size(); i++) {
                    names[i] = subscribedPersonas.get(i).optString("name", "人设");
                }
                new AlertDialog.Builder(this)
                        .setTitle("选择人设")
                        .setItems(names, (dialog, which) -> {
                            selectedPersona[0] = subscribedPersonas.get(which);
                            tvPersonaPick.setText("人设：" + selectedPersona[0].optString("name", ""));
                        })
                        .show();
            });

            new AlertDialog.Builder(this)
                    .setTitle("新建对话")
                    .setView(view)
                    .setPositiveButton("创建", (dialog, which) -> {
                        String name = etName.getText().toString().trim();
                        Long personaId = selectedPersona[0] == null ? null
                                : selectedPersona[0].optLong("id", 0);
                        createConversation(name, personaId);
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });
    }

    private void createConversation(String name, Long personaId) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(apiClient.createConversation(
                        name.isEmpty() ? "新对话" : name, personaId, tokenManager.getToken()));
                runOnUiThread(() -> {
                    if (json.optInt("code") == 200) {
                        JSONObject data = json.optJSONObject("data");
                        if (data != null) {
                            setCurrentConversation(data.optLong("id", 0));
                        }
                        Toast.makeText(this, "对话已创建", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, json.optString("message", "创建失败"),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "网络错误: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showRenameDialog(JSONObject conversation) {
        EditText input = new EditText(this);
        input.setHint("输入新名称");
        input.setText(conversation.optString("title", ""));
        input.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle("重命名对话")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        renameConversation(conversation.optLong("id", 0), name);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void renameConversation(long id, String name) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(apiClient.renameConversation(id, name, tokenManager.getToken()));
                runOnUiThread(() -> {
                    Toast.makeText(this, json.optInt("code") == 200 ? "已重命名"
                            : json.optString("message", "重命名失败"), Toast.LENGTH_SHORT).show();
                    loadConversations();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "网络错误: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showDeleteDialog(JSONObject conversation) {
        new AlertDialog.Builder(this)
                .setTitle("删除对话")
                .setMessage("确定删除《" + conversation.optString("title", "对话") + "》吗？历史记录将清空。")
                .setPositiveButton("删除", (dialog, which) -> deleteConversation(conversation.optLong("id", 0)))
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteConversation(long id) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(apiClient.deleteConversation(id, tokenManager.getToken()));
                runOnUiThread(() -> {
                    Toast.makeText(this, json.optInt("code") == 200 ? "已删除"
                            : json.optString("message", "删除失败"), Toast.LENGTH_SHORT).show();
                    loadConversations();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "网络错误: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    /** 为会话切换人设（从已订阅人设中选择） */
    private void showPersonaPicker(JSONObject conversation) {
        long conversationId = conversation.optLong("id", 0);
        loadSubscribedPersonas(() -> {
            if (subscribedPersonas.isEmpty()) {
                Toast.makeText(this, "还没有订阅的人设", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] names = new String[subscribedPersonas.size() + 1];
            names[0] = "不使用人设";
            for (int i = 0; i < subscribedPersonas.size(); i++) {
                names[i + 1] = subscribedPersonas.get(i).optString("name", "人设");
            }
            new AlertDialog.Builder(this)
                    .setTitle("切换人设")
                    .setItems(names, (dialog, which) -> {
                        Long personaId = which == 0 ? null
                                : subscribedPersonas.get(which - 1).optLong("id", 0);
                        setPersona(conversationId, personaId);
                    })
                    .show();
        });
    }

    private void setPersona(long conversationId, Long personaId) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(apiClient.setConversationPersona(
                        conversationId, personaId, tokenManager.getToken()));
                runOnUiThread(() -> {
                    Toast.makeText(this, json.optInt("code") == 200 ? "人设已更新"
                            : json.optString("message", "设置失败"), Toast.LENGTH_SHORT).show();
                    loadConversations();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "网络错误: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void setCurrentConversation(long id) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putLong(KEY_CURRENT_CONVERSATION, id)
                .apply();
    }
}
