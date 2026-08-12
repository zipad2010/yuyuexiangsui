package com.voice.assistant;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 悬浮球聊天服务：在任意界面上显示可拖动的悬浮圆点，
 * 点击后展开缩小版对话界面，方便随时聊天。
 */
public class FloatingBubbleService extends Service {

    public static boolean isRunning = false;

    private static final String CHANNEL_ID = "floating_bubble";
    private static final int NOTIFICATION_ID = 1001;

    private WindowManager windowManager;
    private Handler mainHandler;
    // Service 没有应用主题，inflate 含 Material 主题属性(?attr/...)的布局
    // 会抛 Resources.NotFoundException 导致闪退，这里用应用主题包装上下文
    private Context themedContext;

    // 悬浮球
    private View bubbleView;
    private WindowManager.LayoutParams bubbleParams;
    private float bubbleStartX, bubbleStartY, bubbleInitX, bubbleInitY;
    private boolean bubbleDragging;

    // 聊天窗
    private View chatView;
    private WindowManager.LayoutParams chatParams;
    private boolean chatVisible;
    private float chatStartX, chatStartY, chatInitX, chatInitY;
    private boolean chatDragging;
    private float chatDefaultY;

    private RecyclerView rvMessages;
    private EditText etInput;
    private ImageButton btnSend;
    private TextView tvChatTitle;

    private List<Message> messages;
    private ChatAdapter adapter;
    private ApiClient apiClient;
    private TokenManager tokenManager;
    private boolean sending;

    private final ViewTreeObserver.OnGlobalLayoutListener keyboardListener = this::onKeyboardLayoutChanged;

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        mainHandler = new Handler(Looper.getMainLooper());
        themedContext = new ContextThemeWrapper(this, R.style.Theme_VoiceAssistant);
        apiClient = new ApiClient(this);
        tokenManager = new TokenManager(this);
        messages = new ArrayList<>();
        adapter = new ChatAdapter(messages, position -> {
            // 悬浮窗内不支持播放语音，保持静默
        });

        startForegroundCompat();
        createBubble();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "stop".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (bubbleView != null && bubbleView.isAttachedToWindow()) {
            try {
                windowManager.removeView(bubbleView);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (chatView != null && chatView.isAttachedToWindow()) {
            try {
                windowManager.removeView(chatView);
            } catch (IllegalArgumentException ignored) {
            }
        }
        bubbleView = null;
        chatView = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForegroundCompat() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, 0);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification() {
        createChannel();
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, FloatingBubbleService.class);
        stopIntent.setAction("stop");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 1, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bubble)
                .setContentTitle("悬浮聊天已开启")
                .setContentText("点击悬浮球即可展开对话")
                .setContentIntent(contentIntent)
                .addAction(R.drawable.ic_close, "关闭悬浮窗", stopPendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "悬浮聊天", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("悬浮球聊天服务");
            manager.createNotificationChannel(channel);
        }
    }

    // ========== 悬浮球 ==========

    private void createBubble() {
        int size = Math.round(56 * getResources().getDisplayMetrics().density);

        FrameLayout bubble = new FrameLayout(themedContext);
        bubble.setBackgroundResource(R.drawable.bg_bubble);

        ImageView icon = new ImageView(themedContext);
        icon.setImageResource(R.drawable.ic_bubble);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(
                Math.round(26 * getResources().getDisplayMetrics().density),
                Math.round(26 * getResources().getDisplayMetrics().density),
                Gravity.CENTER);
        bubble.addView(icon, iconLp);

        int type = getOverlayType();
        bubbleParams = new WindowManager.LayoutParams(
                size, size, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = getScreenWidth() - size - dp(12);
        bubbleParams.y = getScreenHeight() / 3;

        bubble.setOnTouchListener(this::onBubbleTouch);
        bubbleView = bubble;
        windowManager.addView(bubbleView, bubbleParams);
    }

    private boolean onBubbleTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                bubbleStartX = event.getRawX();
                bubbleStartY = event.getRawY();
                bubbleInitX = bubbleParams.x;
                bubbleInitY = bubbleParams.y;
                bubbleDragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - bubbleStartX;
                float dy = event.getRawY() - bubbleStartY;
                if (!bubbleDragging && (Math.abs(dx) > getTouchSlop() || Math.abs(dy) > getTouchSlop())) {
                    bubbleDragging = true;
                }
                if (bubbleDragging) {
                    bubbleParams.x = Math.round(bubbleInitX + dx);
                    bubbleParams.y = Math.round(bubbleInitY + dy);
                    windowManager.updateViewLayout(bubbleView, bubbleParams);
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!bubbleDragging) {
                    showChatWindow();
                }
                bubbleDragging = false;
                return true;
        }
        return false;
    }

    // ========== 聊天窗 ==========

    private void showChatWindow() {
        if (chatView != null && chatView.isAttachedToWindow()) {
            return;
        }
        if (!tokenManager.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            return;
        }

        chatView = LayoutInflater.from(themedContext).inflate(R.layout.layout_floating_chat, null);
        bindChatViews(chatView);

        int width = Math.round(getScreenWidth() * 0.86f);
        int height = Math.round(getScreenHeight() * 0.62f);

        int type = getOverlayType();
        chatParams = new WindowManager.LayoutParams(
                width, height, type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        chatParams.gravity = Gravity.TOP | Gravity.START;
        chatParams.x = Math.round((getScreenWidth() - width) / 2f);
        chatParams.y = Math.round((getScreenHeight() - height) / 2.2f);
        chatDefaultY = chatParams.y;

        // 标题栏支持拖动
        View header = chatView.findViewById(R.id.floating_chat_header);
        header.setOnTouchListener(this::onChatHeaderTouch);
        chatView.findViewById(R.id.floating_chat_close).setOnClickListener(v -> hideChatWindow());

        rvMessages.setLayoutManager(new LinearLayoutManager(this));
        rvMessages.setAdapter(adapter);

        windowManager.addView(chatView, chatParams);
        chatVisible = true;

        chatView.getViewTreeObserver().addOnGlobalLayoutListener(keyboardListener);

        if (messages.isEmpty()) {
            messages.add(new Message("你好呀，我是如月相随 ❤ 有什么想聊的吗？", false));
            adapter.notifyItemInserted(messages.size() - 1);
            loadHistory();
        }
    }

    private void bindChatViews(View view) {
        rvMessages = view.findViewById(R.id.floating_rv_messages);
        etInput = view.findViewById(R.id.floating_et_input);
        btnSend = view.findViewById(R.id.floating_btn_send);
        tvChatTitle = view.findViewById(R.id.floating_chat_title);
        btnSend.setOnClickListener(v -> sendMessage());
    }

    private boolean onChatHeaderTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                chatStartX = event.getRawX();
                chatStartY = event.getRawY();
                chatInitX = chatParams.x;
                chatInitY = chatParams.y;
                chatDragging = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = event.getRawX() - chatStartX;
                float dy = event.getRawY() - chatStartY;
                if (!chatDragging && (Math.abs(dx) > getTouchSlop() || Math.abs(dy) > getTouchSlop())) {
                    chatDragging = true;
                }
                if (chatDragging) {
                    chatParams.x = Math.round(chatInitX + dx);
                    chatParams.y = Math.round(chatInitY + dy);
                    windowManager.updateViewLayout(chatView, chatParams);
                }
                return true;
            case MotionEvent.ACTION_UP:
                chatDragging = false;
                return true;
        }
        return false;
    }

    private void hideChatWindow() {
        if (chatView == null || !chatView.isAttachedToWindow()) {
            return;
        }
        chatView.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardListener);
        windowManager.removeView(chatView);
        chatView = null;
        chatVisible = false;
    }

    private void onKeyboardLayoutChanged() {
        if (chatView == null || !chatView.isAttachedToWindow()) {
            return;
        }
        Rect visible = new Rect();
        chatView.getWindowVisibleDisplayFrame(visible);
        int screenHeight = getScreenHeight();
        int keyboardHeight = screenHeight - visible.bottom;
        if (keyboardHeight > screenHeight * 0.15f) {
            // 键盘弹出：将聊天窗移到键盘上方，避免输入框被遮挡
            int targetY = visible.bottom - chatParams.height - dp(8);
            if (chatParams.y > targetY) {
                chatParams.y = Math.max(0, targetY);
                windowManager.updateViewLayout(chatView, chatParams);
            }
        } else {
            // 键盘收起：恢复默认位置（用户手动拖动过则保持）
            if (chatParams.y + chatParams.height > visible.bottom - dp(8)) {
                chatParams.y = Math.round(chatDefaultY);
                windowManager.updateViewLayout(chatView, chatParams);
            }
        }
    }

    // ========== 聊天逻辑 ==========

    private void loadHistory() {
        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(apiClient.getChatHistory(tokenManager.getToken()));
                JSONArray data = json.optJSONArray("data");
                if (json.optInt("code") != 200 || data == null) {
                    return;
                }
                List<Message> history = new ArrayList<>();
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    String content = item.optString("content", "").trim();
                    if (!content.isEmpty()) {
                        history.add(new Message(content, "user".equals(item.optString("role"))));
                    }
                }
                mainHandler.post(() -> {
                    if (chatView == null || !chatView.isAttachedToWindow()) {
                        return;
                    }
                    messages.clear();
                    messages.addAll(history);
                    if (messages.isEmpty()) {
                        messages.add(new Message("你好呀，我是如月相随 ❤ 有什么想聊的吗？", false));
                    }
                    adapter.notifyDataSetChanged();
                    rvMessages.scrollToPosition(messages.size() - 1);
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void sendMessage() {
        String text = etInput.getText().toString().trim();
        if (text.isEmpty() || sending) {
            return;
        }
        etInput.setText("");
        messages.add(new Message(text, true));
        adapter.notifyItemInserted(messages.size() - 1);
        rvMessages.scrollToPosition(messages.size() - 1);
        sending = true;
        btnSend.setEnabled(false);

        new Thread(() -> {
            try {
                JSONObject json = new JSONObject(apiClient.textChat(text, tokenManager.getToken()));
                int code = json.optInt("code", -1);
                String reply = code == 200
                        ? json.optJSONObject("data").optString("aiReply", "")
                        : json.optString("message", "发送失败");
                mainHandler.post(() -> {
                    sending = false;
                    btnSend.setEnabled(true);
                    if (chatView == null || !chatView.isAttachedToWindow()) {
                        return;
                    }
                    messages.add(new Message(reply.isEmpty() ? "（无回复）" : reply, false));
                    adapter.notifyItemInserted(messages.size() - 1);
                    rvMessages.scrollToPosition(messages.size() - 1);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    sending = false;
                    btnSend.setEnabled(true);
                    if (chatView == null || !chatView.isAttachedToWindow()) {
                        return;
                    }
                    messages.add(new Message("网络异常，请稍后再试", false));
                    adapter.notifyItemInserted(messages.size() - 1);
                    rvMessages.scrollToPosition(messages.size() - 1);
                });
            }
        }).start();
    }

    // ========== 工具方法 ==========

    private int getOverlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private int getScreenWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int getScreenHeight() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    private int getTouchSlop() {
        return ViewConfiguration.get(this).getScaledTouchSlop();
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
