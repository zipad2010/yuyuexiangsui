package com.voice.assistant;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

public class MainActivity extends AppCompatActivity {
    
    private RecyclerView rvMessages;
    private FloatingActionButton fabVoice;
    private EditText etTextInput;
    private ImageButton btnSend;
    private CardView cardRecording;
    private TextView tvRecordingHint, tvBalance, tvTitle;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageButton btnMenu;
    
    private List<Message> messages;
    private ChatAdapter adapter;
    
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private AudioPlayer audioPlayer;
    private Handler mainHandler;
    
    private boolean isRecording = false;
    private AudioRecord audioRecord;
    
    // 赞助者相关
    private boolean isSponsor = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        mainHandler = new Handler(Looper.getMainLooper());
        // 修改：传入 this（Context）
        apiClient = new ApiClient(this);
        tokenManager = new TokenManager(this);
        audioPlayer = new AudioPlayer(this);
        
        if (!tokenManager.isLoggedIn()) {
            startLoginActivity();
            return;
        }
        
        initViews();
        setupListeners();
        loadBalance();
        loadUserInfo();      // 加载用户信息（赞助者状态）
        loadUserProfile();   // 加载用户头像和昵称
        addWelcomeMessage();
        checkPermission();
    }
    
    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
    }
    
    private void initViews() {
        rvMessages = findViewById(R.id.rv_messages);
        fabVoice = findViewById(R.id.fab_voice);
        etTextInput = findViewById(R.id.et_text_input);
        btnSend = findViewById(R.id.btn_send);
        cardRecording = findViewById(R.id.card_recording);
        tvRecordingHint = findViewById(R.id.tv_recording_hint);
        tvBalance = findViewById(R.id.tv_balance);
        tvTitle = findViewById(R.id.tv_title);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        btnMenu = findViewById(R.id.btn_menu);
        
        messages = new ArrayList<>();
        adapter = new ChatAdapter(messages, position -> {
            Message msg = messages.get(position);
            if (!msg.isUser() && msg.getAudioBase64() != null) {
                audioPlayer.playBase64Audio(msg.getAudioBase64(), null);
            }
        });
        
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);
        
        tvBalance.setText("Balance: ¥" + tokenManager.getBalance());
        if (tvTitle != null) {
            tvTitle.setText("AI Voice Assistant");
        }
        
        // 设置侧边栏菜单点击事件
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            } else if (id == R.id.nav_forum) {
                startActivity(new Intent(this, ForumActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            } else if (id == R.id.nav_messages) {
                startActivity(new Intent(this, MessagesActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            } else if (id == R.id.nav_model) {
                startActivity(new Intent(this, ModelSelectActivity.class));
                drawerLayout.closeDrawer(GravityCompat.START);
            } else if (id == R.id.nav_balance) {
                Toast.makeText(this, "Balance: ¥" + tokenManager.getBalance(), Toast.LENGTH_SHORT).show();
                drawerLayout.closeDrawer(GravityCompat.START);
            } else if (id == R.id.nav_logout) {
                tokenManager.clear();
                startLoginActivity();
                finish();
                drawerLayout.closeDrawer(GravityCompat.START);
            }
            return true;
        });
    }
    
    /**
     * 加载用户信息（赞助者状态）
     */
    private void loadUserInfo() {
        new Thread(() -> {
            try {
                String response = apiClient.getUserInfo(tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                if (json.getInt("code") == 200) {
                    JSONObject data = json.getJSONObject("data");
                    isSponsor = data.optBoolean("isSponsor", false);
                    runOnUiThread(() -> {
                        // 根据赞助者状态显示/隐藏模型切换菜单
                        MenuItem modelItem = navigationView.getMenu().findItem(R.id.nav_model);
                        if (modelItem != null) {
                            modelItem.setVisible(isSponsor);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void loadUserProfile() {
        new Thread(() -> {
            try {
                String response = apiClient.getProfile(tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                if (json.getInt("code") == 200) {
                    JSONObject data = json.getJSONObject("data");
                    String nickname = data.optString("nickname", tokenManager.getUsername());
                    String avatarUrl = data.optString("avatarUrl", null);
                    runOnUiThread(() -> {
                        View headerView = navigationView.getHeaderView(0);
                        TextView tvNickname = headerView.findViewById(R.id.tv_nickname);
                        TextView tvUsername = headerView.findViewById(R.id.tv_username);
                        CircleImageView ivAvatar = headerView.findViewById(R.id.iv_avatar);
                        
                        tvNickname.setText(nickname);
                        tvUsername.setText("@" + tokenManager.getUsername());
                        
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            Glide.with(this).load(avatarUrl).into(ivAvatar);
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void setupListeners() {
        fabVoice.setOnClickListener(v -> {
            if (isRecording) {
                stopRecording();
            } else {
                startRecording();
            }
        });
        
        btnSend.setOnClickListener(v -> {
            String text = etTextInput.getText().toString().trim();
            if (!TextUtils.isEmpty(text)) {
                etTextInput.setText("");
                addUserMessage(text);
                sendTextToAI(text);
            }
        });
        
        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
    }
    
    private void loadBalance() {
        new Thread(() -> {
            try {
                String response = apiClient.getBalance(tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                if (json.getInt("code") == 200) {
                    String balance = json.getString("data");
                    runOnUiThread(() -> {
                        tvBalance.setText("Balance: ¥" + balance);
                        tokenManager.updateBalance(balance);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
    
    private void startRecording() {
        if (isRecording) return;
        
        isRecording = true;
        cardRecording.setVisibility(View.VISIBLE);
        tvRecordingHint.setText("Recording...");
        
        new Thread(() -> {
            int bufferSize = AudioRecord.getMinBufferSize(16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2);
            
            audioRecord.startRecording();
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[bufferSize];
            
            long startTime = System.currentTimeMillis();
            while (isRecording && (System.currentTimeMillis() - startTime) < 5000) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    baos.write(buffer, 0, read);
                }
            }
            
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
            
            byte[] audioData = baos.toByteArray();
            
            runOnUiThread(() -> {
                cardRecording.setVisibility(View.GONE);
                sendVoiceToServer(audioData);
            });
        }).start();
    }
    
    private void stopRecording() {
        isRecording = false;
    }
    
    /**
     * 发送语音到服务器（支持模型切换）
     */
    private void sendVoiceToServer(byte[] audioData) {
        addLoadingMessage();
        
        String selectedModel = apiClient.getSelectedModel();
        boolean enableThinking = apiClient.isEnableThinking();
        
        new Thread(() -> {
            try {
                String response;
                if (isSponsor && !selectedModel.equals("deepseek-chat")) {
                    // 赞助者使用选择的模型
                    response = apiClient.voiceChat(audioData, tokenManager.getToken(), 
                                                    selectedModel, enableThinking, null);
                } else {
                    // 普通用户使用默认模型
                    response = apiClient.voiceChat(audioData, tokenManager.getToken());
                }
                JSONObject json = new JSONObject(response);
                
                runOnUiThread(() -> {
                    removeLoadingMessage();
                    
                    try {
                        if (json.getInt("code") == 200) {
                            JSONObject data = json.getJSONObject("data");
                            String recognizedText = data.getString("recognizedText");
                            String aiReply = data.getString("aiReply");
                            String audioBase64 = data.getString("audioBase64");
                            String balance = data.getString("balance");
                            
                            tvBalance.setText("Balance: ¥" + balance);
                            tokenManager.updateBalance(balance);
                            
                            addUserMessage(recognizedText);
                            
                            Message aiMsg = new Message(aiReply, false);
                            aiMsg.setAudioBase64(audioBase64);
                            messages.add(aiMsg);
                            adapter.notifyItemInserted(messages.size() - 1);
                            rvMessages.scrollToPosition(messages.size() - 1);
                            
                            audioPlayer.playBase64Audio(audioBase64, null);
                            
                        } else {
                            Toast.makeText(MainActivity.this, json.getString("message"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Parse error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    removeLoadingMessage();
                    Toast.makeText(MainActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    /**
     * 发送文字到服务器（支持模型切换）
     */
    private void sendTextToAI(String text) {
        addLoadingMessage();
        
        String selectedModel = apiClient.getSelectedModel();
        boolean enableThinking = apiClient.isEnableThinking();
        
        new Thread(() -> {
            try {
                String response;
                if (isSponsor && !selectedModel.equals("deepseek-chat")) {
                    // 赞助者使用选择的模型
                    response = apiClient.textChat(text, tokenManager.getToken(),
                                                   selectedModel, enableThinking, null);
                } else {
                    // 普通用户使用默认模型
                    response = apiClient.textChat(text, tokenManager.getToken());
                }
                JSONObject json = new JSONObject(response);
                
                runOnUiThread(() -> {
                    removeLoadingMessage();
                    
                    try {
                        if (json.getInt("code") == 200) {
                            JSONObject data = json.getJSONObject("data");
                            String aiReply = data.getString("aiReply");
                            String audioBase64 = data.getString("audioBase64");
                            String balance = data.getString("balance");
                            
                            tvBalance.setText("Balance: ¥" + balance);
                            tokenManager.updateBalance(balance);
                            
                            Message aiMsg = new Message(aiReply, false);
                            aiMsg.setAudioBase64(audioBase64);
                            messages.add(aiMsg);
                            adapter.notifyItemInserted(messages.size() - 1);
                            rvMessages.scrollToPosition(messages.size() - 1);
                            
                            audioPlayer.playBase64Audio(audioBase64, null);
                            
                        } else {
                            Toast.makeText(MainActivity.this, json.getString("message"), Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Parse error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    removeLoadingMessage();
                    Toast.makeText(MainActivity.this, "Network error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void addUserMessage(String content) {
        Message msg = new Message(content, true);
        messages.add(msg);
        adapter.notifyItemInserted(messages.size() - 1);
        rvMessages.scrollToPosition(messages.size() - 1);
    }
    
    private void addWelcomeMessage() {
        String welcome = "Hello! I am AI Voice Assistant 🤗\n\nClick microphone to speak, or type a message below.";
        Message msg = new Message(welcome, false);
        messages.add(msg);
        adapter.notifyItemInserted(0);
    }
    
    private void addLoadingMessage() {
        Message loading = new Message("Thinking...", false);
        messages.add(loading);
        adapter.notifyItemInserted(messages.size() - 1);
    }
    
    private void removeLoadingMessage() {
        if (messages.size() > 0 && messages.get(messages.size() - 1).getContent().equals("Thinking...")) {
            messages.remove(messages.size() - 1);
            adapter.notifyItemRemoved(messages.size());
        }
    }
    
    private void startLoginActivity() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (audioPlayer != null) audioPlayer.shutdown();
    }
}