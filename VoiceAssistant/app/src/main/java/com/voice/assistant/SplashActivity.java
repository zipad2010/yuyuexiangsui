package com.voice.assistant;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

public class SplashActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "app_consent_prefs";
    private static final String KEY_DISCLAIMER_ACCEPTED = "disclaimer_accepted_v1";
    private static final long SPLASH_DURATION_MS = 1400L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 必须在 super.onCreate 之前应用暗黑模式，否则主题不会切换
        WallpaperActivity.applyNightMode(this);
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        setContentView(R.layout.activity_splash);

        playSplashAnimation();
        handler.postDelayed(this::continueAfterSplash, SPLASH_DURATION_MS);
    }

    private void playSplashAnimation() {
        View mark = findViewById(R.id.splash_mark);
        View title = findViewById(R.id.splash_title);
        View subtitle = findViewById(R.id.splash_subtitle);

        mark.setAlpha(0f);
        mark.setScaleX(0.78f);
        mark.setScaleY(0.78f);
        mark.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650L).start();

        title.setAlpha(0f);
        title.setTranslationY(18f);
        title.animate().alpha(1f).translationY(0f).setStartDelay(240L).setDuration(520L).start();

        subtitle.setAlpha(0f);
        subtitle.setTranslationY(12f);
        subtitle.animate().alpha(1f).translationY(0f).setStartDelay(420L).setDuration(520L).start();
    }

    private void continueAfterSplash() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (preferences.getBoolean(KEY_DISCLAIMER_ACCEPTED, false)) {
            openNextScreen();
        } else {
            showDisclaimer(preferences);
        }
    }

    private void showDisclaimer(SharedPreferences preferences) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.disclaimer_title)
                .setMessage(R.string.disclaimer_message)
                .setCancelable(false)
                .setPositiveButton(R.string.disclaimer_accept, (dialog, which) -> {
                    preferences.edit().putBoolean(KEY_DISCLAIMER_ACCEPTED, true).apply();
                    openNextScreen();
                })
                .setNegativeButton(R.string.disclaimer_decline, (dialog, which) -> finishAffinity())
                .show();
    }

    private void openNextScreen() {
        TokenManager tokenManager = new TokenManager(this);
        if (tokenManager.isLoggedIn()) {
            // 有本地登录态：后台验证 token 是否仍有效（防止退登后看不出）
            verifyLoginAndContinue(tokenManager);
        } else {
            goTo(LoginActivity.class);
        }
    }

    /** 后台校验 token，无效则清除本地登录态并跳登录页 */
    private void verifyLoginAndContinue(TokenManager tokenManager) {
        new Thread(() -> {
            boolean valid = false;
            try {
                JSONObject json = new JSONObject(
                        new ApiClient(this).getUserInfo(tokenManager.getToken()));
                valid = json.optInt("code") == 200;
            } catch (Exception ignored) {
                // 网络异常时不强制登出，保留本地登录态
                valid = true;
            }
            final boolean finalValid = valid;
            runOnUiThread(() -> {
                if (finalValid) {
                    goTo(HomeActivity.class);
                } else {
                    tokenManager.clear();
                    Toast.makeText(this, "登录已失效，请重新登录", Toast.LENGTH_SHORT).show();
                    goTo(LoginActivity.class);
                }
            });
        }).start();
    }

    private void goTo(Class<?> destination) {
        startActivity(new Intent(this, destination));
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}