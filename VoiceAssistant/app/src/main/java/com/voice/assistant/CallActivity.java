package com.voice.assistant;

import android.annotation.SuppressLint;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 实时通话页：语音 → 百度 ASR → AI 回复 → 小米 MiMo 语音复刻 TTS → 播放。
 * 采用"聆听-应答"循环：能量阈值 VAD 检测说话，静音结束或超时后整段发送，
 * AI 回复播放期间暂停采集麦克风，避免回声。
 */
public class CallActivity extends AppCompatActivity {

    private static final String TAG = "CallActivity";

    private static final int SAMPLE_RATE = 16000;
    private static final int FRAME_BYTES = 3200;      // 100ms @16kHz mono 16bit
    private static final double SPEECH_THRESHOLD = 900.0;
    private static final long MAX_SEGMENT_MS = 12000; // 单段最长 12 秒
    private static final long END_SILENCE_MS = 900;   // 静音 900ms 判定一句话结束
    private static final int PREROLL_FRAMES = 3;      // 开始说话前保留 300ms 音频

    private TextView tvCallTime;
    private TextView tvCallStatus;
    private TextView tvCallButtonHint;
    private ImageButton btnCall;
    private LinearLayout llTranscript;
    private ScrollView scrollTranscript;

    private ApiClient apiClient;
    private TokenManager tokenManager;
    private AudioManager audioManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final AtomicBoolean calling = new AtomicBoolean(false);
    private final AtomicBoolean micPaused = new AtomicBoolean(false);
    private AudioRecord audioRecord;
    private Thread captureThread;
    private MediaPlayer mediaPlayer;
    private long callStartTime;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!calling.get()) {
                return;
            }
            long elapsed = System.currentTimeMillis() - callStartTime;
            long minutes = elapsed / 60000;
            long seconds = (elapsed % 60000) / 1000;
            tvCallTime.setText(String.format(Locale.CHINA, "%02d:%02d", minutes, seconds));
            mainHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        apiClient = new ApiClient(this);
        tokenManager = new TokenManager(this);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        initViews();
    }

    private void initViews() {
        tvCallTime = findViewById(R.id.tv_call_time);
        tvCallStatus = findViewById(R.id.tv_call_status);
        tvCallButtonHint = findViewById(R.id.tv_call_button_hint);
        btnCall = findViewById(R.id.btn_call);
        llTranscript = findViewById(R.id.ll_transcript);
        scrollTranscript = findViewById(R.id.scroll_transcript);

        if (!tokenManager.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnCall.setOnClickListener(v -> {
            if (calling.get()) {
                endCall();
            } else {
                startCall();
            }
        });
    }

    private void startCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            Toast.makeText(this, "请先允许麦克风权限", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!tokenManager.isLoggedIn()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        calling.set(true);
        micPaused.set(false);
        enterCallMode();
        startTimer();
        startCapture();
    }

    private void enterCallMode() {
        try {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true);
        } catch (Exception ignored) {
        }
        btnCall.setImageResource(R.drawable.ic_call_end);
        btnCall.setContentDescription("结束通话");
        tvCallButtonHint.setText("结束通话");
        tvCallStatus.setText("正在聆听...");
        llTranscript.removeAllViews();
    }

    private void endCall() {
        calling.set(false);
        micPaused.set(false);
        mainHandler.removeCallbacks(timerRunnable);

        // 停止录音线程
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (Exception ignored) {
            }
            audioRecord.release();
            audioRecord = null;
        }
        if (captureThread != null && captureThread.isAlive()) {
            captureThread.interrupt();
        }
        captureThread = null;

        // 停止播放
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }

        try {
            audioManager.setMode(AudioManager.MODE_NORMAL);
            audioManager.setSpeakerphoneOn(false);
        } catch (Exception ignored) {
        }

        btnCall.setImageResource(R.drawable.ic_call);
        btnCall.setContentDescription("开始通话");
        tvCallButtonHint.setText("开始通话");
        tvCallStatus.setText("通话已结束，可再次开始");
    }

    private void startTimer() {
        callStartTime = System.currentTimeMillis();
        mainHandler.removeCallbacks(timerRunnable);
        mainHandler.post(timerRunnable);
    }

    // ========== 采集与 VAD ==========

    // 权限已在 startCall() 中通过 checkSelfPermission 检查并申请
    @SuppressLint("MissingPermission")
    private void startCapture() {
        captureThread = new Thread(() -> {
            int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(bufferSize, FRAME_BYTES * 4));
            if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                runOnUiThread(() -> Toast.makeText(this, "麦克风初始化失败", Toast.LENGTH_SHORT).show());
                calling.set(false);
                return;
            }
            audioRecord.startRecording();

            ArrayDeque<byte[]> recentFrames = new ArrayDeque<>();
            ByteArrayOutputStream segment = new ByteArrayOutputStream();
            boolean inSpeech = false;
            long lastSpeechTime = 0;
            long segmentStartTime = 0;
            byte[] frame = new byte[FRAME_BYTES];

            while (calling.get() && !Thread.currentThread().isInterrupted()) {
                if (micPaused.get()) {
                    // 暂停期间持续读取并丢弃音频，避免 AI 播放声残留缓冲区造成回声误触发
                    int read = audioRecord.read(frame, 0, FRAME_BYTES);
                    if (read <= 0) {
                        try {
                            Thread.sleep(60);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                    continue;
                }
                int read = audioRecord.read(frame, 0, FRAME_BYTES);
                if (read <= 0) {
                    continue;
                }
                byte[] data = new byte[read];
                System.arraycopy(frame, 0, data, 0, read);

                boolean speech = rms(data) > SPEECH_THRESHOLD;
                long now = System.currentTimeMillis();

                if (speech) {
                    if (!inSpeech) {
                        inSpeech = true;
                        segment.reset();
                        segmentStartTime = now;
                        for (byte[] recent : recentFrames) {
                            segment.write(recent, 0, recent.length);
                        }
                    }
                    segment.write(data, 0, data.length);
                    lastSpeechTime = now;
                } else if (inSpeech) {
                    segment.write(data, 0, data.length);
                    boolean tooLong = now - segmentStartTime > MAX_SEGMENT_MS;
                    boolean tooQuiet = now - lastSpeechTime > END_SILENCE_MS;
                    if (tooLong || tooQuiet) {
                        byte[] utterance = segment.toByteArray();
                        inSpeech = false;
                        if (utterance.length > FRAME_BYTES) {
                            sendUtterance(utterance);
                        }
                    }
                }

                recentFrames.addLast(data);
                while (recentFrames.size() > PREROLL_FRAMES) {
                    recentFrames.removeFirst();
                }
            }

            if (audioRecord != null) {
                try {
                    audioRecord.stop();
                } catch (Exception ignored) {
                }
                audioRecord.release();
                audioRecord = null;
            }
        }, "call-capture");
        captureThread.start();
    }

    private double rms(byte[] data) {
        long sum = 0;
        int samples = data.length / 2;
        if (samples == 0) {
            return 0;
        }
        for (int i = 0; i + 1 < data.length; i += 2) {
            short s = (short) ((data[i + 1] << 8) | (data[i] & 0xFF));
            sum += (long) s * s;
        }
        return Math.sqrt((double) sum / samples);
    }

    // ========== 网络与播放 ==========

    private void sendUtterance(byte[] pcmData) {
        micPaused.set(true);
        runOnUiThread(() -> tvCallStatus.setText("识别中..."));
        new Thread(() -> {
            try {
                String response = apiClient.voiceCall(pcmData, tokenManager.getToken());
                JSONObject json = new JSONObject(response);
                runOnUiThread(() -> {
                    if (!calling.get()) {
                        return;
                    }
                    if (json.optInt("code") == 200) {
                        JSONObject data = json.optJSONObject("data");
                        if (data == null) {
                            resumeMic();
                            return;
                        }
                        String recognized = data.optString("recognizedText", "");
                        String aiReply = data.optString("aiReply", "");
                        appendTranscript("我", recognized);
                        appendTranscript("月下", aiReply);
                        String audio = data.isNull("audioBase64")
                                ? "" : data.optString("audioBase64", "");
                        if (!audio.isEmpty()) {
                            playAudio(audio);
                        } else {
                            resumeMic();
                        }
                    } else {
                        Toast.makeText(CallActivity.this,
                                json.optString("message", "处理失败"), Toast.LENGTH_SHORT).show();
                        resumeMic();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "实时通话请求失败", e);
                runOnUiThread(() -> {
                    if (!calling.get()) {
                        return;
                    }
                    Toast.makeText(CallActivity.this,
                            "网络错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    resumeMic();
                });
            }
        }, "call-network").start();
    }

    private void playAudio(String base64Audio) {
        runOnUiThread(() -> tvCallStatus.setText("月下正在说话..."));
        try {
            byte[] audioData = Base64.decode(base64Audio, Base64.DEFAULT);
            File tempFile = File.createTempFile("call_tts", ".wav", getCacheDir());
            FileOutputStream fos = new FileOutputStream(tempFile);
            fos.write(audioData);
            fos.close();

            MediaPlayer player = new MediaPlayer();
            mediaPlayer = player;
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setDataSource(tempFile.getAbsolutePath());
            player.setOnCompletionListener(mp -> {
                mp.release();
                if (mediaPlayer == mp) {
                    mediaPlayer = null;
                }
                tempFile.delete();
                resumeMic();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                mp.release();
                if (mediaPlayer == mp) {
                    mediaPlayer = null;
                }
                tempFile.delete();
                resumeMic();
                return true;
            });
            player.prepare();
            player.start();
        } catch (Exception e) {
            Log.e(TAG, "TTS 播放失败", e);
            resumeMic();
        }
    }

    private void resumeMic() {
        micPaused.set(false);
        if (calling.get()) {
            runOnUiThread(() -> tvCallStatus.setText("正在聆听..."));
        }
    }

    private void appendTranscript(String who, String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        TextView line = new TextView(this);
        line.setText(who + "：" + text.trim());
        line.setTextSize(14);
        line.setTextColor(Color.WHITE);
        line.setLineSpacing(0f, 1.2f);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = (int) (10 * getResources().getDisplayMetrics().density);
        line.setLayoutParams(params);
        llTranscript.addView(line);
        scrollTranscript.post(() -> scrollTranscript.fullScroll(View.FOCUS_DOWN));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCall();
        }
    }

    @Override
    protected void onDestroy() {
        endCall();
        super.onDestroy();
    }
}
