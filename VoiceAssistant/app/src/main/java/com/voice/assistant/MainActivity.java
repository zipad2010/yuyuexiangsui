package com.voice.assistant;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.MediaPlayer;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends WallpaperActivity {

    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 2;
    
    private RecyclerView rvMessages;
    private FloatingActionButton fabVoice;
    private EditText etTextInput;
    private ImageButton btnSend;
    private CardView cardRecording;
    private TextView tvRecordingHint, tvBalance, tvTitle;
    private ImageView ivCustomBackground;
    private View toolbar, composer, chatContent;
    private ClockRadialMenuView clockMenu;
    private AnimatorSet recordingPulse;
    private ActivityResultLauncher<String[]> backgroundPicker;
    
    private List<Message> messages;
    private ChatAdapter adapter;
    
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private AudioPlayer audioPlayer;
    private MediaPlayer startSoundPlayer;
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
        backgroundPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::setCustomBackground);
        
        if (!tokenManager.isLoggedIn()) {
            startLoginActivity();
            return;
        }

        playStartSound();
        
        initViews();
        setupListeners();
        loadBalance();
        loadUserInfo();      // 加载用户信息（赞助者状态）
        loadUserProfile();   // 加载用户头像和昵称
        loadChatHistory();
        checkPermission();
        NotificationHelper.createChannel(this);
        requestNotificationPermission();
        refreshNotifications();
    }

    private void playStartSound() {
        startSoundPlayer = MediaPlayer.create(this, R.raw.start);
        if (startSoundPlayer == null) {
            return;
        }
        startSoundPlayer.setOnCompletionListener(player -> {
            player.release();
            if (startSoundPlayer == player) {
                startSoundPlayer = null;
            }
        });
        startSoundPlayer.start();
    }
    
    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST_CODE);
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
        toolbar = findViewById(R.id.toolbar);
        composer = findViewById(R.id.composer);
        chatContent = findViewById(R.id.chat_content);
        clockMenu = findViewById(R.id.clock_menu);
        ivCustomBackground = findViewById(R.id.iv_custom_background);
        
        messages = new ArrayList<>();
        adapter = new ChatAdapter(messages, position -> {
            Message msg = messages.get(position);
            if (!msg.isUser() && msg.getAudioBase64() != null) {
                audioPlayer.playBase64Audio(msg.getAudioBase64(), null);
            }
        });
        
        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);
        
        tvBalance.setText("积分 " + tokenManager.getBalance());
        if (tvTitle != null) {
            tvTitle.setText(R.string.app_name);
        }

        showLocalAccountInfo();
        restoreCustomBackground();

        playEntranceAnimation();
        
        clockMenu.setListener(new ClockRadialMenuView.Listener() {
            @Override
            public void onMenuAction(int itemId) {
                handleMenuAction(itemId);
            }

            @Override
            public void onMenuStateChanged(boolean open) {
                setChatBlurred(open);
            }
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
                        clockMenu.setSponsorVisible(isSponsor);
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
                    String signature = data.optString("signature", "让每次对话都有温度");
                    String avatarUrl = data.optString("avatarUrl", null);
                    String wallpaperUrl = data.optString("wallpaperUrl", "");
                    runOnUiThread(() -> {
                        clockMenu.setAccount(nickname, tokenManager.getUsername(),
                                signature.isEmpty() ? "让每次对话都有温度" : signature);
                        if (!wallpaperUrl.isEmpty()) {
                            Uri wallpaperUri = Uri.parse(ApiClient.resolveResourceUrl(wallpaperUrl));
                            saveWallpaperUri(this, wallpaperUri);
                            displayCustomBackground(wallpaperUri);
                        }
                    });
                }
            } catch (Exception e) {
                runOnUiThread(this::showLocalAccountInfo);
            }
        }).start();
    }

    private void showLocalAccountInfo() {
        if (clockMenu == null) {
            return;
        }
        clockMenu.setAccount(tokenManager.getUsername(), tokenManager.getUsername(), "让每次对话都有温度");
    }

    private void setCustomBackground(Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                saveWallpaperUri(this, uri);
            displayCustomBackground(uri);
            uploadCustomBackground(uri);
        } catch (SecurityException e) {
            Toast.makeText(this, "无法长期读取所选图片，请重新选择", Toast.LENGTH_SHORT).show();
        }
    }

    private void restoreCustomBackground() {
        SharedPreferences preferences = getSharedPreferences(UI_PREFS_NAME, MODE_PRIVATE);
        String uriValue = preferences.getString(KEY_BACKGROUND_URI, null);
        if (uriValue != null) {
            displayCustomBackground(Uri.parse(uriValue));
        }
    }

    private void displayCustomBackground(Uri uri) {
        ivCustomBackground.setVisibility(View.VISIBLE);
        Glide.with(this).load(uri).centerCrop().into(ivCustomBackground);
        saveWallpaperUri(this, uri);
    }

    private void uploadCustomBackground(Uri uri) {
        new Thread(() -> {
            try {
                byte[] imageData = readBytes(uri);
                if (imageData.length > 10 * 1024 * 1024) {
                    throw new IllegalArgumentException("壁纸不能超过 10MB");
                }
                String mimeType = getContentResolver().getType(uri);
                String response = apiClient.uploadWallpaper(imageData, getDisplayName(uri),
                        mimeType == null ? "image/jpeg" : mimeType, tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                String message = json.optString("message", "壁纸上传失败");
                runOnUiThread(() -> Toast.makeText(this,
                        json.optInt("code", -1) == 200 ? "壁纸已同步到云端" : message,
                        Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "壁纸仅保存在本机", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private byte[] readBytes(Uri uri) throws Exception {
        try (java.io.InputStream input = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) {
                throw new IllegalArgumentException("无法读取所选图片");
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
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        }
        return "wallpaper.jpg";
    }

    private void clearCustomBackground() {
        getSharedPreferences(UI_PREFS_NAME, MODE_PRIVATE)
                .edit()
                .remove(KEY_BACKGROUND_URI)
                .apply();
        Glide.with(this).clear(ivCustomBackground);
        ivCustomBackground.setImageDrawable(null);
        ivCustomBackground.setVisibility(View.GONE);
        Toast.makeText(this, "已恢复默认背景", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (clockMenu != null && tokenManager != null && tokenManager.isLoggedIn()) {
            showLocalAccountInfo();
            loadUserProfile();
            refreshNotifications();
        }
    }

    private void refreshNotifications() {
        new Thread(() -> {
            try {
                String response = apiClient.getNotificationSummary(tokenManager.getForumCheckedAt(),
                        tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                JSONObject data = json.optJSONObject("data");
                if (json.optInt("code") != 200 || data == null) {
                    return;
                }
                int forumUnread = data.optInt("forumReplyUnread", 0);
                int privateMessageUnread = data.optInt("privateMessageUnread", 0);
                runOnUiThread(() -> {
                    if (clockMenu != null) {
                        clockMenu.setUnreadCounts(forumUnread, privateMessageUnread);
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                            || ContextCompat.checkSelfPermission(this,
                            Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        NotificationHelper.updateSummary(this, forumUnread, privateMessageUnread);
                    }
                });
            } catch (Exception ignored) {
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
        
    }

    private void handleMenuAction(int id) {
        if (id == R.id.nav_home) {
            return;
        } else if (id == R.id.nav_profile) {
            startActivity(new Intent(this, ProfileActivity.class));
        } else if (id == R.id.nav_forum) {
            startActivity(new Intent(this, ForumActivity.class));
        } else if (id == R.id.nav_messages) {
            startActivity(new Intent(this, MessagesActivity.class));
        } else if (id == R.id.nav_model) {
            startActivity(new Intent(this, ModelSelectActivity.class));
        } else if (id == R.id.nav_balance) {
            Toast.makeText(this, "当前积分 " + tokenManager.getBalance(), Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_background) {
            backgroundPicker.launch(new String[]{"image/*"});
        } else if (id == R.id.nav_reset_background) {
            clearCustomBackground();
        } else if (id == R.id.nav_logout) {
            tokenManager.clear();
            startLoginActivity();
            finish();
        }
    }

    private void setChatBlurred(boolean blurred) {
        chatContent.animate()
                .alpha(blurred ? 0.72f : 1f)
                .scaleX(blurred ? 0.985f : 1f)
                .scaleY(blurred ? 0.985f : 1f)
                .setDuration(blurred ? 420L : 300L)
                .start();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            chatContent.setRenderEffect(blurred
                    ? RenderEffect.createBlurEffect(18f, 18f, Shader.TileMode.CLAMP)
                    : null);
        }
    }

    @Override
    public void onBackPressed() {
        if (clockMenu != null && clockMenu.isOpen()) {
            clockMenu.close();
            return;
        }
        super.onBackPressed();
    }

    private void playEntranceAnimation() {
        toolbar.setAlpha(0f);
        toolbar.setTranslationY(-20f);
        toolbar.animate().alpha(1f).translationY(0f).setDuration(320L).start();

        rvMessages.setAlpha(0f);
        rvMessages.animate().alpha(1f).setStartDelay(100L).setDuration(420L).start();

        composer.setAlpha(0f);
        composer.setTranslationY(32f);
        composer.animate().alpha(1f).translationY(0f).setStartDelay(180L).setDuration(360L).start();
    }

    private void startRecordingPulse() {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(fabVoice, View.SCALE_X, 1f, 1.12f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(fabVoice, View.SCALE_Y, 1f, 1.12f, 1f);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        recordingPulse = new AnimatorSet();
        recordingPulse.setDuration(900L);
        recordingPulse.playTogether(scaleX, scaleY);
        recordingPulse.start();
    }

    private void stopRecordingPulse() {
        if (recordingPulse != null) {
            recordingPulse.cancel();
        }
        fabVoice.animate().scaleX(1f).scaleY(1f).setDuration(160L).start();
    }
    
    private void loadBalance() {
        new Thread(() -> {
            try {
                String response = apiClient.getBalance(tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                if (json.getInt("code") == 200) {
                    String balance = json.getString("data");
                    runOnUiThread(() -> {
                        tvBalance.setText("积分 " + balance);
                        tokenManager.updateBalance(balance);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void loadChatHistory() {
        setChatInputEnabled(false);
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(apiClient.getChatHistory(tokenManager.getToken()));
                JSONArray data = json.optJSONArray("data");
                List<Message> historyMessages = new ArrayList<>();
                if (json.optInt("code") == 200 && data != null) {
                    for (int index = 0; index < data.length(); index++) {
                        JSONObject item = data.optJSONObject(index);
                        if (item == null) {
                            continue;
                        }
                        String content = item.optString("content", "").trim();
                        if (!content.isEmpty()) {
                            historyMessages.add(new Message(content, "user".equals(item.optString("role"))));
                        }
                    }
                }
                runOnUiThread(() -> {
                    messages.clear();
                    messages.addAll(historyMessages);
                    if (messages.isEmpty()) {
                        addWelcomeMessage();
                    } else {
                        adapter.notifyDataSetChanged();
                        rvMessages.scrollToPosition(messages.size() - 1);
                    }
                    setChatInputEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (messages.isEmpty()) {
                        addWelcomeMessage();
                    }
                    setChatInputEnabled(true);
                    Toast.makeText(this, "历史对话加载失败", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void setChatInputEnabled(boolean enabled) {
        etTextInput.setEnabled(enabled);
        btnSend.setEnabled(enabled);
        fabVoice.setEnabled(enabled);
        composer.animate().alpha(enabled ? 1f : 0.55f).setDuration(180L).start();
    }
    
    private void startRecording() {
        if (isRecording) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            checkPermission();
            Toast.makeText(this, "请先允许麦克风权限", Toast.LENGTH_SHORT).show();
            return;
        }
        
        isRecording = true;
        cardRecording.setVisibility(View.VISIBLE);
        cardRecording.setAlpha(0f);
        cardRecording.setScaleX(0.92f);
        cardRecording.setScaleY(0.92f);
        cardRecording.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220L).start();
        tvRecordingHint.setText("正在聆听...");
        startRecordingPulse();
        
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
                stopRecordingPulse();
                sendVoiceToServer(audioData);
            });
        }).start();
    }
    
    private void stopRecording() {
        isRecording = false;
        stopRecordingPulse();
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
                response = apiClient.voiceChat(audioData, tokenManager.getToken(),
                                                selectedModel, enableThinking, null);
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
                            
                            tvBalance.setText("积分 " + balance);
                            tokenManager.updateBalance(balance);
                            
                            addUserMessage(recognizedText);
                            
                            showStreamingAiMessage(aiReply, audioBase64);
                            
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
                response = apiClient.textChat(text, tokenManager.getToken(),
                                               selectedModel, enableThinking, null);
                JSONObject json = new JSONObject(response);
                
                runOnUiThread(() -> {
                    removeLoadingMessage();
                    
                    try {
                        if (json.getInt("code") == 200) {
                            JSONObject data = json.getJSONObject("data");
                            String aiReply = data.getString("aiReply");
                            String audioBase64 = data.getString("audioBase64");
                            String balance = data.getString("balance");
                            
                            tvBalance.setText("积分 " + balance);
                            tokenManager.updateBalance(balance);
                            
                            showStreamingAiMessage(aiReply, audioBase64);
                            
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

    private void showStreamingAiMessage(String content, String audioBase64) {
        Message message = new Message("", false);
        message.setAudioBase64(audioBase64);
        messages.add(message);
        adapter.notifyItemInserted(messages.size() - 1);
        rvMessages.scrollToPosition(messages.size() - 1);

        final int chunkSize = content.length() > 180 ? 3 : 2;
        Runnable streamTask = new Runnable() {
            private int displayedLength = 0;

            @Override
            public void run() {
                if (isFinishing() || isDestroyed() || !messages.contains(message)) {
                    return;
                }
                displayedLength = Math.min(displayedLength + chunkSize, content.length());
                message.setContent(content.substring(0, displayedLength));
                int position = messages.indexOf(message);
                adapter.notifyItemChanged(position);
                rvMessages.scrollToPosition(position);

                if (displayedLength < content.length()) {
                    mainHandler.postDelayed(this, 24L);
                } else if (audioBase64 != null && !audioBase64.isEmpty()) {
                    audioPlayer.playBase64Audio(audioBase64, null);
                }
            }
        };
        mainHandler.post(streamTask);
    }
    
    private void addWelcomeMessage() {
        String welcome = getString(R.string.welcome_message);
        Message msg = new Message(welcome, false);
        messages.add(msg);
        adapter.notifyItemInserted(0);
    }
    
    private void addLoadingMessage() {
        Message loading = new Message(getString(R.string.thinking), false);
        messages.add(loading);
        adapter.notifyItemInserted(messages.size() - 1);
    }
    
    private void removeLoadingMessage() {
        if (messages.size() > 0 && messages.get(messages.size() - 1).getContent().equals(getString(R.string.thinking))) {
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
        if (mainHandler != null) mainHandler.removeCallbacksAndMessages(null);
        if (audioPlayer != null) audioPlayer.shutdown();
        if (startSoundPlayer != null) {
            startSoundPlayer.release();
            startSoundPlayer = null;
        }
    }
}