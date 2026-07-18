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

public class ReplyAdapter extends RecyclerView.Adapter<ReplyAdapter.ViewHolder> {
    
    private List<JSONObject> replies;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
    
    public ReplyAdapter(List<JSONObject> replies) {
        this.replies = replies;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_reply, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        try {
            JSONObject reply = replies.get(position);
            holder.tvAuthor.setText(reply.optString("nickname", reply.optString("username", "Unknown")));
            holder.tvContent.setText(reply.optString("content", ""));
            
            String createdAt = reply.optString("createdAt");
            if (createdAt != null && !createdAt.isEmpty()) {
                holder.tvTime.setText(dateFormat.format(new Date(Long.parseLong(createdAt))));
            } else {
                holder.tvTime.setText("");
            }
            
            String avatarUrl = reply.optString("avatarUrl");
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(avatarUrl)
                        .into(holder.ivAvatar);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public int getItemCount() {
        return replies.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivAvatar;
        TextView tvAuthor, tvContent, tvTime;
        
        ViewHolder(View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvAuthor = itemView.findViewById(R.id.tv_author);
            tvContent = itemView.findViewById(R.id.tv_content);
            tvTime = itemView.findViewById(R.id.tv_time);
        }
    }
}