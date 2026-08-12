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
    private OnItemLongClickListener longClickListener;
    
    public interface OnPlayClickListener {
        void onPlayClick(int position);
    }
    
    public interface OnItemLongClickListener {
        void onItemLongClick(int position);
    }
    
    public ChatAdapter(List<Message> messages, OnPlayClickListener listener) {
        this.messages = messages;
        this.playClickListener = listener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
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
            holder.llSent.setVisibility(View.VISIBLE);
            holder.llReceived.setVisibility(View.GONE);
            holder.tvSent.setText(msg.getContent());
        } else {
            holder.llSent.setVisibility(View.GONE);
            holder.llReceived.setVisibility(View.VISIBLE);
            holder.tvReceived.setText(msg.getContent());
        }

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onItemLongClick(holder.getBindingAdapterPosition());
                return true;
            }
            return false;
        });
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