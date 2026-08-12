package com.voice.assistant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import de.hdodenhof.circleimageview.CircleImageView;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 信息中心列表适配器：混合展示「帖子回复」与「私信」两类通知
 */
public class InfoCenterAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_SECTION = 0;
    public static final int TYPE_ITEM = 1;

    public interface OnItemClickListener {
        /** type: "reply" 或 "message"；targetId: 帖子 id 或用户 id */
        void onItemClick(String type, long targetId, String name);
    }

    private final List<Object> rows;
    private final OnItemClickListener listener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public InfoCenterAdapter(List<Object> rows, OnItemClickListener listener) {
        this.rows = rows;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof RowItem ? TYPE_ITEM : TYPE_SECTION;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_SECTION) {
            return new SectionHolder(inflater.inflate(R.layout.item_center_section, parent, false));
        }
        return new ItemHolder(inflater.inflate(R.layout.item_center_notification, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (holder instanceof SectionHolder) {
            ((SectionHolder) holder).tvSection.setText((String) row);
            return;
        }
        ItemHolder itemHolder = (ItemHolder) holder;
        RowItem item = (RowItem) row;
        itemHolder.tvName.setText(item.name);
        itemHolder.tvContent.setText(item.content);
        itemHolder.tvTime.setText(item.createdAt > 0
                ? dateFormat.format(new Date(item.createdAt)) : "");
        // 动作提示：帖子回复显示帖子标题，私信显示提示语
        if ("reply".equals(item.type)) {
            itemHolder.tvAction.setText("回复了你的帖子《" + item.extra + "》");
        } else {
            itemHolder.tvAction.setText("给你发来私信");
        }
        if (item.avatarUrl != null && !item.avatarUrl.isEmpty()) {
            Glide.with(itemHolder.itemView.getContext())
                    .load(ApiClient.resolveResourceUrl(item.avatarUrl))
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .into(itemHolder.ivAvatar);
        } else {
            itemHolder.ivAvatar.setImageResource(R.drawable.ic_default_avatar);
        }
        itemHolder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item.type, item.targetId, item.name);
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    /** 列表行数据：type = reply / message */
    public static class RowItem {
        final String type;
        final long targetId;
        final String name;
        final String content;
        final String extra;
        final String avatarUrl;
        final long createdAt;

        public RowItem(String type, long targetId, String name, String content,
                       String extra, String avatarUrl, long createdAt) {
            this.type = type;
            this.targetId = targetId;
            this.name = name;
            this.content = content;
            this.extra = extra;
            this.avatarUrl = avatarUrl;
            this.createdAt = createdAt;
        }
    }

    static class SectionHolder extends RecyclerView.ViewHolder {
        TextView tvSection;

        SectionHolder(@NonNull View itemView) {
            super(itemView);
            tvSection = itemView.findViewById(R.id.tv_section);
        }
    }

    static class ItemHolder extends RecyclerView.ViewHolder {
        CircleImageView ivAvatar;
        TextView tvName, tvAction, tvContent, tvTime;

        ItemHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvName = itemView.findViewById(R.id.tv_name);
            tvAction = itemView.findViewById(R.id.tv_action);
            tvContent = itemView.findViewById(R.id.tv_content);
            tvTime = itemView.findViewById(R.id.tv_time);
        }
    }
}
