package com.voice.assistant;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 投稿中心：浏览/发布/订阅人设
 */
public class PersonaCenterActivity extends WallpaperActivity {

    private RecyclerView rvPersonas;
    private TextView tvEmpty, tvTabSquare, tvTabMine;
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private List<JSONObject> personas;
    private PersonaAdapter adapter;
    private boolean mineMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_persona_center);

        apiClient = new ApiClient(this);
        tokenManager = new TokenManager(this);

        initViews();
        loadSquare();
    }

    private void initViews() {
        rvPersonas = findViewById(R.id.rv_personas);
        tvEmpty = findViewById(R.id.tv_empty);
        tvTabSquare = findViewById(R.id.tv_tab_square);
        tvTabMine = findViewById(R.id.tv_tab_mine);
        findViewById(R.id.btn_publish).setOnClickListener(v -> showPublishDialog());

        personas = new ArrayList<>();
        adapter = new PersonaAdapter(personas, false, persona -> {
            boolean subscribed = persona.optBoolean("subscribed", false);
            long id = persona.optLong("id", 0);
            if (mineMode) {
                showPromptDialog(persona);
            } else if (subscribed) {
                unsubscribe(id);
            } else {
                subscribe(id);
            }
        });
        rvPersonas.setLayoutManager(new LinearLayoutManager(this));
        rvPersonas.setAdapter(adapter);

        tvTabSquare.setOnClickListener(v -> {
            mineMode = false;
            updateTabs();
            loadSquare();
        });
        tvTabMine.setOnClickListener(v -> {
            mineMode = true;
            updateTabs();
            loadMine();
        });
    }

    private void updateTabs() {
        tvTabSquare.setTextColor(getColor(mineMode ? R.color.text_muted : R.color.primary));
        tvTabMine.setTextColor(getColor(mineMode ? R.color.primary : R.color.text_muted));
    }

    private void loadSquare() {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(apiClient.getPersonaList(tokenManager.getToken()));
                runOnUiThread(() -> applyData(json));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("网络错误，请稍后重试");
                });
            }
        }).start();
    }

    private void loadMine() {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(apiClient.getMyPersonas(tokenManager.getToken()));
                runOnUiThread(() -> applyData(json));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText("网络错误，请稍后重试");
                });
            }
        }).start();
    }

    private void applyData(JSONObject json) {
        personas.clear();
        JSONArray data = json.optJSONArray("data");
        if (json.optInt("code") == 200 && data != null) {
            for (int i = 0; i < data.length(); i++) {
                personas.add(data.optJSONObject(i));
            }
        }
        adapter.notifyDataSetChanged();
        tvEmpty.setVisibility(personas.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void subscribe(long personaId) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(apiClient.subscribePersona(personaId, tokenManager.getToken()));
                runOnUiThread(() -> {
                    Toast.makeText(this, json.optString("message", "订阅成功"),
                            Toast.LENGTH_SHORT).show();
                    loadSquare();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "网络错误: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void unsubscribe(long personaId) {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(apiClient.unsubscribePersona(personaId, tokenManager.getToken()));
                runOnUiThread(() -> {
                    Toast.makeText(this, json.optString("message", "已取消订阅"),
                            Toast.LENGTH_SHORT).show();
                    loadSquare();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "网络错误: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void showPromptDialog(JSONObject persona) {
        String prompt = persona.optString("prompt", "");
        new AlertDialog.Builder(this)
                .setTitle(persona.optString("name", "人设"))
                .setMessage(prompt.isEmpty() ? "无内容" : prompt)
                .setPositiveButton("知道了", null)
                .show();
    }

    /** 发布人设：输入名称/简介/人设内容 */
    private void showPublishDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_publish_persona, null);
        EditText etName = view.findViewById(R.id.et_persona_name);
        EditText etDesc = view.findViewById(R.id.et_persona_desc);
        EditText etPrompt = view.findViewById(R.id.et_persona_prompt);

        new AlertDialog.Builder(this)
                .setTitle("发布人设")
                .setView(view)
                .setPositiveButton("发布", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String desc = etDesc.getText().toString().trim();
                    String prompt = etPrompt.getText().toString().trim();
                    publish(name, desc, prompt);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void publish(String name, String desc, String prompt) {
        if (name.isEmpty() || prompt.isEmpty()) {
            Toast.makeText(this, "名称和人设内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(apiClient.publishPersona(
                        name, desc, prompt, tokenManager.getToken()));
                runOnUiThread(() -> {
                    if (json.optInt("code") == 200) {
                        Toast.makeText(this, "发布成功", Toast.LENGTH_SHORT).show();
                        mineMode = true;
                        updateTabs();
                        loadMine();
                    } else {
                        Toast.makeText(this, json.optString("message", "发布失败"),
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "网络错误: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
