package com.voice.assistant;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class ClockRadialMenuView extends View {

    private static final int MAX_VISIBLE_ITEMS = 7;

    public interface Listener {
        void onMenuAction(int itemId);
        void onMenuStateChanged(boolean open);
    }

    private static class MenuItem {
        final int id;
        String label;
        final Drawable icon;
        final RectF bounds = new RectF();

        MenuItem(int id, String label, Drawable icon) {
            this.id = id;
            this.label = label;
            this.icon = icon;
        }
    }

    private final Paint arcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint panelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hubRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glassStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrollTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scrollThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<MenuItem> items = new ArrayList<>();

    private Listener listener;
    private ValueAnimator menuAnimator;
    private float animationProgress;
    private float axisY = -1f;
    private float downY;
    private float initialAxisY;
    private float initialScrollOffset;
    private float menuScrollOffset;
    private boolean dragging;
    private boolean scrollingItems;
    private boolean open;
    private boolean sponsorVisible;
    private boolean adminVisible;
    private boolean darkModeEnabled;
    private int forumUnreadCount;
    private int privateMessageUnreadCount;
    private String accountName = "账号";
    private String accountHandle = "";
    private String accountSignature = "";

    private final float density;
    private final float hubRadius;
    private final float preferredItemWidth;
    private final float preferredItemHeight;

    public ClockRadialMenuView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        hubRadius = dp(27);
        preferredItemWidth = dp(132);
        preferredItemHeight = dp(34);
        configurePaints();
        buildItems();
    }

    private void configurePaints() {
        boolean night = isNightMode();
        arcPaint.setColor(Color.argb(145, 200, 47, 72));
        arcPaint.setStyle(Paint.Style.STROKE);
        arcPaint.setStrokeWidth(dp(1.3f));

        tickPaint.setColor(Color.argb(105, 118, 31, 54));
        tickPaint.setStrokeWidth(dp(1));

        panelPaint.setColor(night ? Color.argb(178, 42, 37, 48) : Color.argb(168, 255, 255, 255));

        glassStrokePaint.setColor(night ? Color.argb(96, 255, 255, 255) : Color.argb(152, 255, 255, 255));
        glassStrokePaint.setStyle(Paint.Style.STROKE);
        glassStrokePaint.setStrokeWidth(dp(1));

        scrollTrackPaint.setColor(Color.argb(82, 255, 255, 255));
        scrollTrackPaint.setStyle(Paint.Style.STROKE);
        scrollTrackPaint.setStrokeWidth(dp(2));
        scrollTrackPaint.setStrokeCap(Paint.Cap.ROUND);

        scrollThumbPaint.setColor(night ? Color.argb(215, 230, 220, 235) : Color.argb(215, 255, 255, 255));
        scrollThumbPaint.setStyle(Paint.Style.STROKE);
        scrollThumbPaint.setStrokeWidth(dp(3));
        scrollThumbPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setColor(night ? Color.rgb(240, 236, 245) : Color.rgb(42, 34, 39));
        textPaint.setTextSize(dp(13));
        textPaint.setFakeBoldText(true);

        hubPaint.setColor(Color.rgb(118, 31, 54));

        hubRingPaint.setColor(Color.argb(180, 255, 255, 255));
        hubRingPaint.setStyle(Paint.Style.STROKE);
        hubRingPaint.setStrokeWidth(dp(2));

        scrimPaint.setColor(night ? Color.argb(120, 10, 8, 12) : Color.argb(82, 24, 17, 22));

        badgePaint.setColor(Color.rgb(197, 46, 70));
        badgeTextPaint.setColor(Color.WHITE);
        badgeTextPaint.setTextAlign(Paint.Align.CENTER);
        badgeTextPaint.setTextSize(dp(10));
        badgeTextPaint.setFakeBoldText(true);
    }

    private boolean isNightMode() {
        int uiMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private void buildItems() {
        items.clear();
        addItem(R.id.nav_home, "对话", R.drawable.ic_home);
        addItem(R.id.nav_conversations, "对话列表", R.drawable.ic_home);
        addItem(R.id.nav_call, "实时通话", R.drawable.ic_call);
        addItem(R.id.nav_profile, "个人中心", R.drawable.ic_edit);
        addItem(R.id.nav_forum, "社区论坛", R.drawable.ic_forum);
        addItem(R.id.nav_messages, "我的私信", R.drawable.ic_send);
        addItem(R.id.nav_center, "信息中心", R.drawable.ic_notifications);
        addItem(R.id.nav_persona, "投稿中心", R.drawable.ic_manage);
        if (adminVisible) {
            addItem(R.id.nav_admin, "管理中心", R.drawable.ic_admin);
        }
        if (sponsorVisible) {
            addItem(R.id.nav_model, "模型选择", R.drawable.ic_manage);
        }
        addItem(R.id.nav_balance, "我的积分", R.drawable.ic_info);
        addItem(R.id.nav_bubble, "悬浮窗聊天", R.drawable.ic_bubble);
        addItem(R.id.nav_dark_mode, darkModeEnabled ? "暗黑模式：开" : "暗黑模式", R.drawable.ic_dark_mode);
        addItem(R.id.nav_update, "检查更新", R.drawable.ic_restore);
        addItem(R.id.nav_background, "更换背景", R.drawable.ic_image);
        addItem(R.id.nav_reset_background, "恢复背景", R.drawable.ic_restore);
        addItem(R.id.nav_logout, "退出账号", R.drawable.ic_logout);
    }

    /**
     * 更新暗黑模式开关的菜单显示状态
     */
    public void setDarkModeEnabled(boolean enabled) {
        if (darkModeEnabled != enabled) {
            darkModeEnabled = enabled;
            buildItems();
            invalidate();
        }
    }

    /**
     * 更新悬浮窗聊天开关的菜单显示状态
     */
    public void setBubbleEnabled(boolean enabled) {
        for (MenuItem item : items) {
            if (item.id == R.id.nav_bubble) {
                item.label = enabled ? "悬浮窗：已开" : "悬浮窗聊天";
                invalidate();
                return;
            }
        }
    }

    private void addItem(int id, String label, int iconResource) {
        Drawable icon = ContextCompat.getDrawable(getContext(), iconResource);
        items.add(new MenuItem(id, label, icon));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setSponsorVisible(boolean visible) {
        if (sponsorVisible != visible) {
            sponsorVisible = visible;
            buildItems();
            invalidate();
        }
    }

    /**
     * 设置管理员可见性（仅数据库 username=zipad 的账户可见管理中心）
     */
    public void setAdminVisible(boolean visible) {
        if (adminVisible != visible) {
            adminVisible = visible;
            buildItems();
            invalidate();
        }
    }

    public void setAccount(String name, String username, String signature) {
        accountName = name == null || name.trim().isEmpty() ? "账号" : name.trim();
        accountHandle = username == null || username.trim().isEmpty() ? "" : "@" + username.trim();
        accountSignature = signature == null ? "" : signature.trim();
        invalidate();
    }

    public void setUnreadCounts(int forumCount, int privateMessageCount) {
        forumUnreadCount = Math.max(0, forumCount);
        privateMessageUnreadCount = Math.max(0, privateMessageCount);
        invalidate();
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        if (open) {
            animateMenu(false);
        }
    }

    private void toggle() {
        animateMenu(!open);
    }

    private void animateMenu(boolean opening) {
        open = opening;
        if (menuAnimator != null) {
            menuAnimator.cancel();
        }
        menuAnimator = ValueAnimator.ofFloat(animationProgress, opening ? 1f : 0f);
        menuAnimator.setDuration(opening ? 430L : 300L);
        menuAnimator.setInterpolator(new PathInterpolator(0.2f, 0.8f, 0.2f, 1f));
        menuAnimator.addUpdateListener(animation -> {
            animationProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        menuAnimator.start();
        if (listener != null) {
            listener.onMenuStateChanged(opening);
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        if (axisY < 0f) {
            axisY = height / 2f;
        }
        axisY = clampAxisY(axisY);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float axisX = dp(16);
        if (animationProgress > 0f) {
            scrimPaint.setAlpha(Math.round(32 * animationProgress));
            canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
            drawClockArc(canvas, axisX);
            drawAccountPlate(canvas, axisX);
            drawItems(canvas, axisX);
        }
        drawHub(canvas, axisX);
    }

    private void drawAccountPlate(Canvas canvas, float axisX) {
        float radius = menuRadius();
        float plateLeft = axisX + dp(44);
        float plateTop = Math.max(dp(18), axisY - radius - dp(54));
        RectF plate = new RectF(plateLeft, plateTop, plateLeft + dp(132), plateTop + dp(52));
        panelPaint.setAlpha(Math.round(150 * animationProgress));
        canvas.drawRoundRect(plate, dp(8), dp(8), panelPaint);
        glassStrokePaint.setAlpha(Math.round(152 * animationProgress));
        canvas.drawRoundRect(plate, dp(8), dp(8), glassStrokePaint);

        textPaint.setAlpha(Math.round(255 * animationProgress));
        textPaint.setTextSize(dp(13));
        canvas.drawText(ellipsize(accountName, 14), plate.left + dp(12), plate.top + dp(18), textPaint);
        textPaint.setFakeBoldText(false);
        textPaint.setTextSize(dp(10));
        textPaint.setColor(isNightMode() ? Color.rgb(190, 182, 196) : Color.rgb(112, 96, 103));
        canvas.drawText(ellipsize(accountHandle, 18), plate.left + dp(12), plate.top + dp(33), textPaint);
        if (!accountSignature.isEmpty()) {
            canvas.drawText(ellipsize(accountSignature, 18), plate.left + dp(12), plate.top + dp(47), textPaint);
        }
        textPaint.setFakeBoldText(true);
        textPaint.setTextSize(dp(13));
        textPaint.setColor(isNightMode() ? Color.rgb(240, 236, 245) : Color.rgb(42, 34, 39));
    }

    private String ellipsize(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength - 1) + "…";
    }

    private void drawClockArc(Canvas canvas, float axisX) {
        float radius = menuRadius();
        RectF arcBounds = new RectF(axisX - radius, axisY - radius,
                axisX + radius, axisY + radius);
        arcPaint.setAlpha(Math.round(145 * animationProgress));
        canvas.drawArc(arcBounds, -76, 152, false, arcPaint);

        tickPaint.setAlpha(Math.round(105 * animationProgress));
        for (int index = 0; index <= 20; index++) {
            double angle = Math.toRadians(-76 + index * 7.6);
            float outerX = axisX + (float) Math.cos(angle) * radius;
            float outerY = axisY + (float) Math.sin(angle) * radius;
            float tickLength = index % 5 == 0 ? dp(10) : dp(5);
            float innerX = axisX + (float) Math.cos(angle) * (radius - tickLength);
            float innerY = axisY + (float) Math.sin(angle) * (radius - tickLength);
            canvas.drawLine(innerX, innerY, outerX, outerY, tickPaint);
        }
        drawScrollIndicator(canvas, axisX, radius);
    }

    private void drawScrollIndicator(Canvas canvas, float axisX, float radius) {
        if (items.size() <= MAX_VISIBLE_ITEMS) {
            return;
        }
        RectF indicatorBounds = new RectF(axisX - radius + dp(16), axisY - radius + dp(16),
                axisX + radius - dp(16), axisY + radius - dp(16));
        scrollTrackPaint.setAlpha(Math.round(110 * animationProgress));
        canvas.drawArc(indicatorBounds, -68, 136, false, scrollTrackPaint);

        float maxOffset = items.size() - MAX_VISIBLE_ITEMS;
        float thumbSweep = 136f * MAX_VISIBLE_ITEMS / items.size();
        float availableSweep = 136f - thumbSweep;
        float thumbStart = -68f + availableSweep * (menuScrollOffset / maxOffset);
        scrollThumbPaint.setAlpha(Math.round(235 * animationProgress));
        canvas.drawArc(indicatorBounds, thumbStart, thumbSweep, false, scrollThumbPaint);
    }

    private void drawItems(Canvas canvas, float axisX) {
        int count = items.size();
        int visibleCount = Math.min(count, MAX_VISIBLE_ITEMS);
        float radius = menuRadius();
        float itemHeight = Math.min(preferredItemHeight,
            Math.max(dp(32), availableMenuHeight() / visibleCount - dp(7)));
        float itemWidth = Math.min(preferredItemWidth, getWidth() - dp(190));
        for (int index = 0; index < count; index++) {
            float displayIndex = index - menuScrollOffset;
            if (displayIndex < -1f || displayIndex > visibleCount) {
                items.get(index).bounds.setEmpty();
                continue;
            }
            float stagger = Math.max(0f, displayIndex) * 0.022f;
            float localProgress = clamp((animationProgress - stagger) / (1f - stagger));
                float normalizedY = visibleCount == 1 ? 0f
                        : -0.86f + (1.72f * displayIndex / (visibleCount - 1));
                float targetYOffset = normalizedY * radius;
                float targetXOffset = (float) Math.sqrt(
                    Math.max(0f, radius * radius - targetYOffset * targetYOffset));
                float targetX = axisX + dp(30) + targetXOffset;
                float targetY = axisY + targetYOffset;
            float centerX = axisX + (targetX - axisX) * localProgress;
            float centerY = axisY + (targetY - axisY) * localProgress;
            float scale = 0.88f + 0.12f * localProgress;
            float halfWidth = itemWidth * scale / 2f;
            float halfHeight = itemHeight * scale / 2f;
            MenuItem item = items.get(index);
            item.bounds.set(centerX - halfWidth, centerY - halfHeight,
                    centerX + halfWidth, centerY + halfHeight);

            panelPaint.setAlpha(Math.round(168 * localProgress));
            canvas.drawRoundRect(item.bounds, dp(8), dp(8), panelPaint);
            glassStrokePaint.setAlpha(Math.round(152 * localProgress));
            canvas.drawRoundRect(item.bounds, dp(8), dp(8), glassStrokePaint);
            textPaint.setAlpha(Math.round(255 * localProgress));
            float textY = centerY - (textPaint.ascent() + textPaint.descent()) / 2f;
            canvas.drawText(item.label, item.bounds.left + dp(14), textY, textPaint);

            if (item.icon != null) {
                int iconSize = Math.round(dp(20) * scale);
                int right = Math.round(item.bounds.right - dp(12));
                int top = Math.round(centerY - iconSize / 2f);
                item.icon.setBounds(right - iconSize, top, right, top + iconSize);
                item.icon.setAlpha(Math.round(255 * localProgress));
                item.icon.draw(canvas);
            }
            drawUnreadBadge(canvas, item, localProgress);
        }
    }

    private void drawUnreadBadge(Canvas canvas, MenuItem item, float progress) {
        int unreadCount = item.id == R.id.nav_forum ? forumUnreadCount
                : item.id == R.id.nav_messages ? privateMessageUnreadCount
                : item.id == R.id.nav_center ? forumUnreadCount + privateMessageUnreadCount : 0;
        if (unreadCount <= 0) {
            return;
        }
        float radius = dp(10);
        float centerX = item.bounds.right - dp(6);
        float centerY = item.bounds.top + dp(6);
        badgePaint.setAlpha(Math.round(255 * progress));
        canvas.drawCircle(centerX, centerY, radius, badgePaint);
        badgeTextPaint.setAlpha(Math.round(255 * progress));
        String label = unreadCount > 99 ? "99+" : String.valueOf(unreadCount);
        float textY = centerY - (badgeTextPaint.ascent() + badgeTextPaint.descent()) / 2f;
        canvas.drawText(label, centerX, textY, badgeTextPaint);
    }

    private void drawHub(Canvas canvas, float axisX) {
        canvas.drawCircle(axisX, axisY, hubRadius, hubPaint);
        canvas.drawCircle(axisX, axisY, hubRadius - dp(3), hubRingPaint);
        Paint handPaint = tickPaint;
        handPaint.setColor(Color.WHITE);
        handPaint.setStrokeWidth(dp(2));
        handPaint.setStrokeCap(Paint.Cap.ROUND);
        float rotation = animationProgress * 90f;
        double angle = Math.toRadians(-45 + rotation);
        canvas.drawLine(axisX, axisY,
                axisX + (float) Math.cos(angle) * dp(13),
                axisY + (float) Math.sin(angle) * dp(13), handPaint);
        canvas.drawCircle(axisX, axisY, dp(2.5f), handPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float axisX = dp(16);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (distance(event.getX(), event.getY(), axisX, axisY) <= hubRadius + dp(12)) {
                    downY = event.getY();
                    initialAxisY = axisY;
                    dragging = false;
                    scrollingItems = false;
                    return true;
                }
                if (open) {
                    for (MenuItem item : items) {
                        if (item.bounds.contains(event.getX(), event.getY())) {
                            setTag(item.id);
                            downY = event.getY();
                            initialScrollOffset = menuScrollOffset;
                            scrollingItems = false;
                            return true;
                        }
                    }
                    if (event.getX() > axisX + dp(24)) {
                        downY = event.getY();
                        initialScrollOffset = menuScrollOffset;
                        scrollingItems = false;
                        return true;
                    }
                    close();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (open && initialAxisY < 0f) {
                    float deltaY = event.getY() - downY;
                    if (Math.abs(deltaY) > dp(5)) {
                        scrollingItems = true;
                        setTag(null);
                        float itemHeight = Math.max(dp(32), availableMenuHeight()
                                / Math.min(items.size(), MAX_VISIBLE_ITEMS) - dp(7));
                        menuScrollOffset = clampScrollOffset(initialScrollOffset - deltaY / itemHeight);
                        invalidate();
                    }
                    return true;
                }
                if (initialAxisY > 0f) {
                    float deltaY = event.getY() - downY;
                    if (Math.abs(deltaY) > dp(5)) {
                        dragging = true;
                        if (open) {
                            close();
                        }
                    }
                    axisY = clampAxisY(initialAxisY + deltaY);
                    invalidate();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_UP:
                Object selectedId = getTag();
                setTag(null);
                initialAxisY = -1f;
                if (selectedId instanceof Integer && !dragging && !scrollingItems) {
                    close();
                    if (listener != null) {
                        listener.onMenuAction((Integer) selectedId);
                    }
                    return true;
                }
                if (!dragging) {
                    if (!scrollingItems) {
                        toggle();
                    }
                }
                scrollingItems = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                initialAxisY = -1f;
                setTag(null);
                scrollingItems = false;
                return true;
            default:
                return false;
        }
    }

    private float menuRadius() {
        float widthLimit = getWidth() - dp(16 + 30 + 69 + 12);
        float heightLimit = availableMenuHeight() / 2f;
        float spacingRadius = (preferredItemHeight + dp(5)) * Math.max(1, MAX_VISIBLE_ITEMS - 1) / 1.96f;
        return Math.max(dp(118), Math.min(dp(170), Math.min(widthLimit,
                Math.max(spacingRadius, heightLimit * 0.92f))));
    }

    private float availableMenuHeight() {
        return Math.max(dp(310), Math.min(axisY - dp(68), getHeight() - axisY - dp(68)) * 2f);
    }

    private float clampAxisY(float value) {
        return Math.max(dp(86), Math.min(getHeight() - dp(86), value));
    }

    private float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private float clampScrollOffset(float value) {
        return Math.max(0f, Math.min(Math.max(0, items.size() - MAX_VISIBLE_ITEMS), value));
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x1 - x2;
        float dy = y1 - y2;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private float dp(float value) {
        return value * density;
    }
}