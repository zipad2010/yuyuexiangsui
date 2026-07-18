package com.voice.assistant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
    
    private List<Message> messages;
    private OnPlayClickListener playClickListener;
    
    public interface OnPlayClickListener {
        void onPlayClick(int position);
    }
    
    public ChatAdapter(List<Message> messages, OnPlayClickListener listener) {
        this.messages = messages;
        this.playClickListener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message msg = messages.get(position);
        
        if (msg.isUser()) {
            holder.llUser.setVisibility(View.VISIBLE);
            holder.llAi.setVisibility(View.GONE);
            holder.tvUserMsg.setText(msg.getContent());
        } else {
            holder.llUser.setVisibility(View.GONE);
            holder.llAi.setVisibility(View.VISIBLE);
            holder.tvAiMsg.setText(msg.getContent());
            
            if (msg.getAudioBase64() != null) {
                holder.btnPlay.setVisibility(View.VISIBLE);
                holder.btnPlay.setOnClickListener(v -> {
                    if (playClickListener != null) {
                        playClickListener.onPlayClick(position);
                    }
                });
            }
        }
    }
    
    @Override
    public int getItemCount() {
        return messages.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout llUser, llAi;
        TextView tvUserMsg, tvAiMsg;
        ImageButton btnPlay;
        
        ViewHolder(View itemView) {
            super(itemView);
            llUser = itemView.findViewById(R.id.ll_user);
            llAi = itemView.findViewById(R.id.ll_ai);
            tvUserMsg = itemView.findViewById(R.id.tv_user_msg);
            tvAiMsg = itemView.findViewById(R.id.tv_ai_msg);
            btnPlay = itemView.findViewById(R.id.btn_play);
        }
    }
}