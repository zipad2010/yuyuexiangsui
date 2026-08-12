package com.voice.assistant;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 应用内检查更新：每天自动检查一次 + 手动检查。
 * 版本号读取 https://api.q4y.cn/uploads/newapps/banben.txt（纯数字，如 2），
 * 更新公告读取 .../gonggao.txt，安装包下载 .../1.apk。
 */
public class UpdateManager {

    private static final String PREFS_NAME = "update_prefs";
    private static final String KEY_LAST_CHECK_DAY = "last_check_day";

    private static final String BASE = "https://api.q4y.cn/uploads/newapps/";
    private static final String VERSION_URL = BASE + "banben.txt";
    private static final String ANNOUNCE_URL = BASE + "gonggao.txt";
    private static final String APK_URL = BASE + "1.apk";

    private final Context context;
    private final OkHttpClient client;

    public UpdateManager(Context context) {
        this.context = context.getApplicationContext();
        client = new OkHttpClient();
    }

    /**
     * 检查更新。manual=true 时用户主动触发（有提示反馈），false 时每日自动检查（静默）。
     */
    public void checkForUpdate(boolean manual) {
        new Thread(() -> {
            try {
                Response response = client.newCall(
                        new Request.Builder().url(VERSION_URL).build()).execute();
                if (!response.isSuccessful() || response.body() == null) {
                    notifyResult(manual, false, "无法获取版本信息", null);
                    return;
                }
                String versionText = response.body().string().trim();
                int serverVersion;
                try {
                    serverVersion = Integer.parseInt(versionText);
                } catch (NumberFormatException e) {
                    notifyResult(manual, false, "版本号格式错误", null);
                    return;
                }

                int localVersion = getLocalVersionCode();
                if (serverVersion <= localVersion) {
                    // 已是最新
                    if (manual) {
                        showToast("已是最新版本");
                    }
                    markCheckedToday();
                    return;
                }

                // 有更新：读取公告
                String announce = fetchAnnounce();
                runOnUi(() -> showUpdateDialog(serverVersion, announce));
            } catch (Exception e) {
                notifyResult(manual, false, "网络错误，检查更新失败", null);
            }
        }).start();
    }

    /** 每天首次进入时调用：今天没查过才查 */
    public void checkForUpdateDaily() {
        if (!isCheckedToday()) {
            checkForUpdate(false);
        }
    }

    /** 是否今天已检查过更新 */
    private boolean isCheckedToday() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        return today.equals(prefs.getString(KEY_LAST_CHECK_DAY, ""));
    }

    private void markCheckedToday() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_LAST_CHECK_DAY, today).apply();
    }

    private String fetchAnnounce() throws Exception {
        try {
            Response response = client.newCall(
                    new Request.Builder().url(ANNOUNCE_URL).build()).execute();
            if (response.isSuccessful() && response.body() != null) {
                String text = response.body().string().trim();
                return text.isEmpty() ? "发现新版本，请更新到最新版本体验新功能。" : text;
            }
        } catch (Exception ignored) {
        }
        return "发现新版本，请更新到最新版本体验新功能。";
    }

    private void notifyResult(boolean manual, boolean success, String message, Object unused) {
        if (manual) {
            showToast(message);
        }
    }

    private void showUpdateDialog(int serverVersion, String announce) {
        new AlertDialog.Builder(context)
                .setTitle("发现新版本 v" + serverVersion)
                .setMessage(announce)
                .setPositiveButton("立即更新", (dialog, which) -> downloadAndInstall())
                .setNegativeButton("稍后再说", (dialog, which) -> markCheckedToday())
                .setCancelable(false)
                .show();
    }

    /** 下载 APK 并通过 FileProvider 安装 */
    private void downloadAndInstall() {
        // Android 8+ 需要"安装未知应用"授权
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !context.getPackageManager().canRequestPackageInstalls()) {
            runOnUi(() -> new AlertDialog.Builder(context)
                    .setTitle("需要安装权限")
                    .setMessage("安装更新需要允许安装未知来源应用，请在设置中开启")
                    .setPositiveButton("去开启", (d, w) -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:" + context.getPackageName()));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(intent);
                    })
                    .setNegativeButton("取消", null)
                    .show());
            return;
        }
        new Thread(() -> {
            try {
                Response response = client.newCall(
                        new Request.Builder().url(APK_URL).build()).execute();
                if (!response.isSuccessful() || response.body() == null) {
                    showToast("下载失败，请重试");
                    return;
                }
                File dir = new File(context.getCacheDir(), "update");
                if (!dir.exists() && !dir.mkdirs()) {
                    showToast("下载失败，无法创建目录");
                    return;
                }
                File apkFile = new File(dir, "app-update.apk");
                try (InputStream input = response.body().byteStream();
                     FileOutputStream output = new FileOutputStream(apkFile)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                }
                installApk(apkFile);
            } catch (Exception e) {
                showToast("下载失败: " + e.getMessage());
            }
        }).start();
    }

    private void installApk(File apkFile) {
        runOnUi(() -> {
            try {
                Uri apkUri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".fileprovider", apkFile);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                showToast("安装失败: " + e.getMessage());
            }
        });
    }

    private int getLocalVersionCode() {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    private void showToast(String message) {
        runOnUi(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    private void runOnUi(Runnable runnable) {
        android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
        handler.post(runnable);
    }
}
