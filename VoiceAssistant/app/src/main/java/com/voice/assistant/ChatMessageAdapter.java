package com.voice.assistant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ViewHolder> {
    
    private List<JSONObject> messages;
    private long currentUserId;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    
    public ChatMessageAdapter(List<JSONObject> messages, long currentUserId) {
        this.messages = messages;
        this.currentUserId = currentUserId;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        try {
            JSONObject msg = messages.get(position);
            long fromUserId = msg.optLong("fromUserId");
            String content = msg.optString("content", "");
            String createdAt = msg.optString("createdAt");
            
            boolean isSent = (fromUserId == currentUserId);
            
            if (isSent) {
                holder.llSent.setVisibility(View.VISIBLE);
                holder.llReceived.setVisibility(View.GONE);
                holder.tvSent.setText(content);
            } else {
                holder.llSent.setVisibility(View.GONE);
                holder.llReceived.setVisibility(View.VISIBLE);
                holder.tvReceived.setText(content);
            }
            
            if (createdAt != null && !createdAt.isEmpty()) {
                // 可选：显示时间
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public int getItemCount() {
        return messages.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout llSent, llReceived;
        TextView tvSent, tvReceived;
        
        ViewHolder(View itemView) {
            super(itemView);
            llSent = itemView.findViewById(R.id.ll_sent);
            llReceived = itemView.findViewById(R.id.ll_received);
            tvSent = itemView.findViewById(R.id.tv_sent);
            tvReceived = itemView.findViewById(R.id.tv_received);
        }
    }
}