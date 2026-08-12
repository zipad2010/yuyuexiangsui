package com.voice.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.bumptech.glide.Glide;

public class WallpaperActivity extends AppCompatActivity {

    static final String UI_PREFS_NAME = "ui_preferences";
    static final String KEY_BACKGROUND_URI = "custom_background_uri";
    static final String KEY_DARK_MODE = "dark_mode_enabled";

    /**
     * 在 Activity 内容创建前应用暗黑模式（应在 super.onCreate 之前调用）
     */
    static void applyNightMode(Context context) {
        boolean dark = context.getSharedPreferences(UI_PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_DARK_MODE, false);
        AppCompatDelegate.setDefaultNightMode(dark
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    static boolean isDarkModeEnabled(Context context) {
        return context.getSharedPreferences(UI_PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_DARK_MODE, false);
    }

    private ImageView wallpaperView;

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        installWallpaper();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadWallpaper();
    }

    static void saveWallpaperUri(AppCompatActivity activity, Uri uri) {
        activity.getSharedPreferences(UI_PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_BACKGROUND_URI, uri.toString())
                .apply();
    }

    private void installWallpaper() {
        ViewGroup content = findViewById(android.R.id.content);
        if (content == null || wallpaperView != null) {
            return;
        }
        wallpaperView = new ImageView(this);
        wallpaperView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wallpaperView.setBackgroundColor(Color.WHITE);
        wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallpaperView.setContentDescription("自定义背景");
        content.addView(wallpaperView, 0);
        loadWallpaper();
    }

    private void loadWallpaper() {
        if (wallpaperView == null) {
            return;
        }
        SharedPreferences preferences = getSharedPreferences(UI_PREFS_NAME, MODE_PRIVATE);
        String uriValue = preferences.getString(KEY_BACKGROUND_URI, null);
        if (uriValue == null || uriValue.trim().isEmpty()) {
            Glide.with(this).clear(wallpaperView);
            wallpaperView.setBackgroundColor(Color.WHITE);
            return;
        }
        Glide.with(this).load(Uri.parse(uriValue)).centerCrop().into(wallpaperView);
    }
}