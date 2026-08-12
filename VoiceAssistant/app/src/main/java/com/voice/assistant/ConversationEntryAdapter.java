package com.voice.assistant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 对话列表适配器：显示会话标题/人设/时间，支持打开、长按重命名/删除
 */
public class ConversationEntryAdapter extends RecyclerView.Adapter<ConversationEntryAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onOpen(JSONObject conversation);
        void onRename(JSONObject conversation);
        void onDelete(JSONObject conversation);
        void onSetPersona(JSONObject conversation);
        void onLongClick(JSONObject conversation);
    }

    private final List<JSONObject> conversations;
    private final OnItemClickListener listener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());

    public ConversationEntryAdapter(List<JSONObject> conversations, OnItemClickListener listener) {
        this.conversations = conversations;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject conversation = conversations.get(position);
        String title = conversation.optString("title", "新对话");
        long updatedAt = conversation.optLong("updatedAt", 0);
        long personaId = conversation.optLong("activePersonaId", 0);

        holder.tvTitle.setText(title);
        if (personaId > 0) {
            holder.tvPersona.setVisibility(View.VISIBLE);
            holder.tvPersona.setText("人设 #" + personaId);
        } else {
            holder.tvPersona.setVisibility(View.GONE);
        }
        holder.tvTime.setText(updatedAt > 0 ? dateFormat.format(new Date(updatedAt)) : "");

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOpen(conversation);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) {
                listener.onLongClick(conversation);
            }
            return true;
        });
        holder.tvOpen.setOnClickListener(v -> {
            if (listener != null) {
                listener.onOpen(conversation);
            }
        });
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvPersona, tvTime, tvOpen;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_conv_title);
            tvPersona = itemView.findViewById(R.id.tv_conv_persona);
            tvTime = itemView.findViewById(R.id.tv_conv_time);
            tvOpen = itemView.findViewById(R.id.tv_btn_open);
        }
    }
}
