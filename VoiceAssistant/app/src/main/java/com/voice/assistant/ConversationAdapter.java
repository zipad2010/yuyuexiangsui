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

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {
    
    private List<JSONObject> conversations;
    private OnConversationClickListener listener;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd", Locale.getDefault());
    
    public interface OnConversationClickListener {
        void onConversationClick(Long userId);
    }
    
    public ConversationAdapter(List<JSONObject> conversations, OnConversationClickListener listener) {
        this.conversations = conversations;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        try {
            JSONObject conv = conversations.get(position);
            String username = conv.optString("nickname", conv.optString("username", "Unknown"));
            String lastMessage = conv.optString("lastMessage", "");
            String avatarUrl = conv.optString("avatarUrl");
            Long userId = conv.optLong("userId");
            
            holder.tvName.setText(username);
            holder.tvLastMessage.setText(lastMessage);
            
            String createdAt = conv.optString("lastMessageTime");
            if (createdAt != null && !createdAt.isEmpty()) {
                holder.tvTime.setText(dateFormat.format(new Date(Long.parseLong(createdAt))));
            }
            
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(avatarUrl)
                        .into(holder.ivAvatar);
            }
            
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onConversationClick(userId);
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public int getItemCount() {
        return conversations.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivAvatar;
        TextView tvName, tvLastMessage, tvTime;
        
        ViewHolder(View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvName = itemView.findViewById(R.id.tv_name);
            tvLastMessage = itemView.findViewById(R.id.tv_last_message);
            tvTime = itemView.findViewById(R.id.tv_time);
        }
    }
}