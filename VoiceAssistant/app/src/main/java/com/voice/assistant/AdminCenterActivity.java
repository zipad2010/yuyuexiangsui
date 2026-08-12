package com.voice.assistant;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 管理中心：查看用户违规记录（仅管理员可用）。
 * 非管理员访问时直接提示无权限并退出。
 */
public class AdminCenterActivity extends WallpaperActivity {

    private RecyclerView rvViolations;
    private TextView tvEmpty;
    private MaterialButton btnPending, btnAll;
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private List<JSONObject> records;
    private ViolationAdapter adapter;
    private int currentStatus = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_center);

        apiClient = new ApiClient(this);
        tokenManager = new TokenManager(this);

        initViews();
        checkAdminAndLoad();
    }

    private void initViews() {
        rvViolations = findViewById(R.id.rv_violations);
        tvEmpty = findViewById(R.id.tv_empty);
        btnPending = findViewById(R.id.btn_pending);
        btnAll = findViewById(R.id.btn_all);

        records = new ArrayList<>();
        adapter = new ViolationAdapter(records, record -> {
            long id = record.optLong("id", 0);
            if (id > 0) {
                handleViolation(id);
            }
        });
        rvViolations.setLayoutManager(new LinearLayoutManager(this));
        rvViolations.setAdapter(adapter);

        btnPending.setOnClickListener(v -> {
            currentStatus = 0;
            btnPending.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFC82F48));
            btnAll.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF8B8791));
            loadViolations();
        });
        btnAll.setOnClickListener(v -> {
            currentStatus = 1;
            btnAll.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFC82F48));
            btnPending.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF8B8791));
            loadViolations();
        });
    }

    private void checkAdminAndLoad() {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(apiClient.isAdmin(tokenManager.getToken()));
                // 后端返回 ApiResponse<Boolean>，data 为 JSON 布尔值
                boolean isAdmin = json.optInt("code") == 200 && json.optBoolean("data", false);
                runOnUiThread(() -> {
                    if (!isAdmin) {
                        Toast.makeText(this, "无管理员权限", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    loadViolations();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "网络错误，无法验证权限", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        }).start();
    }

    private void loadViolations() {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(
                        apiClient.getViolations(currentStatus, tokenManager.getToken()));
                runOnUiThread(() -> {
                    if (json.optInt("code") != 200) {
                        Toast.makeText(this, json.optString("message", "加载失败"),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    records.clear();
                    JSONArray data = json.optJSONArray("data");
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            records.add(data.optJSONObject(i));
                        }
                    }
                    adapter.notifyDataSetChanged();
                    tvEmpty.setVisibility(records.isEmpty() ? View.VISIBLE : View.GONE);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "网络错误: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void handleViolation(long recordId) {
        new AlertDialog.Builder(this)
                .setTitle("确认处理")
                .setMessage("标记该违规记录为已处理？")
                .setPositiveButton("确定", (dialog, which) -> {
                    new Thread(() -> {
                        try {
                            JSONObject json = new JSONObject(apiClient.resolveViolation(
                                    recordId, tokenManager.getToken()));
                            runOnUiThread(() -> {
                                Toast.makeText(this,
                                        json.optInt("code") == 200
                                                ? "已处理" : json.optString("message", "处理失败"),
                                        Toast.LENGTH_SHORT).show();
                                loadViolations();
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(this, "网络错误",
                                    Toast.LENGTH_SHORT).show());
                        }
                    }).start();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
