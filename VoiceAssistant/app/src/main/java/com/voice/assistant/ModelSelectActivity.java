package com.voice.assistant;

import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class ModelSelectActivity extends AppCompatActivity {
    
    private RecyclerView rvModels;
    private Switch swThinking;
    private TextView tvCurrentModel;
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private List<ModelItem> models;
    private ModelAdapter adapter;
    private String selectedModel;
    private boolean enableThinking;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_select);
        
        apiClient = new ApiClient(this);
        tokenManager = new TokenManager(this);
        
        initViews();
        loadModels();
        
        // 加载已保存的设置
        selectedModel = apiClient.getSelectedModel();
        enableThinking = apiClient.isEnableThinking();
        swThinking.setChecked(enableThinking);
        updateCurrentModelDisplay();
        
        swThinking.setOnCheckedChangeListener((buttonView, isChecked) -> {
            enableThinking = isChecked;
            apiClient.saveSelectedModel(selectedModel, enableThinking);
            Toast.makeText(this, enableThinking ? "深度思考已开启" : "深度思考已关闭", Toast.LENGTH_SHORT).show();
        });
    }
    
    private void initViews() {
        rvModels = findViewById(R.id.rv_models);
        swThinking = findViewById(R.id.sw_thinking);
        tvCurrentModel = findViewById(R.id.tv_current_model);
        
        models = new ArrayList<>();
        adapter = new ModelAdapter(models, model -> {
            selectedModel = model.id;
            apiClient.saveSelectedModel(selectedModel, enableThinking);
            updateCurrentModelDisplay();
            Toast.makeText(this, "已切换至: " + model.name, Toast.LENGTH_SHORT).show();
        });
        
        rvModels.setLayoutManager(new LinearLayoutManager(this));
        rvModels.setAdapter(adapter);
    }
    
    private void loadModels() {
        new Thread(() -> {
            try {
                String response = apiClient.getAvailableModels(tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                if (json.getInt("code") == 200) {
                    JSONObject data = json.getJSONObject("data");
                    JSONArray modelsArray = data.getJSONArray("models");
                    List<ModelItem> loadedModels = new ArrayList<>();
                    for (int i = 0; i < modelsArray.length(); i++) {
                        JSONObject m = modelsArray.getJSONObject(i);
                        ModelItem item = new ModelItem();
                        item.id = m.getString("id");
                        item.name = m.getString("name");
                        item.supportsThinking = m.optBoolean("supportsThinking", false);
                        loadedModels.add(item);
                    }
                    runOnUiThread(() -> {
                        models.clear();
                        models.addAll(loadedModels);
                        adapter.notifyDataSetChanged();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "加载模型失败", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
    
    private void updateCurrentModelDisplay() {
        for (ModelItem item : models) {
            if (item.id.equals(selectedModel)) {
                tvCurrentModel.setText("当前模型: " + item.name);
                return;
            }
        }
        tvCurrentModel.setText("当前模型: " + selectedModel);
    }
    
    static class ModelItem {
        String id;
        String name;
        boolean supportsThinking;
    }
}