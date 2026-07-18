package com.voice.assistant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.util.List;

public class ForumAdapter extends RecyclerView.Adapter<ForumAdapter.ViewHolder> {
    
    private List<JSONObject> posts;
    private OnPostClickListener listener;
    
    public interface OnPostClickListener {
        void onPostClick(Long postId);
    }
    
    public ForumAdapter(List<JSONObject> posts, OnPostClickListener listener) {
        this.posts = posts;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_post, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        try {
            JSONObject post = posts.get(position);
            holder.tvTitle.setText(post.optString("title", "No Title"));
            holder.tvContent.setText(post.optString("content", ""));
            holder.tvAuthor.setText(post.optString("nickname", post.optString("username", "Unknown")));
            holder.tvLikes.setText(String.valueOf(post.optInt("likeCount", 0)));
            holder.tvReplies.setText(String.valueOf(post.optInt("replyCount", 0)));
            
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPostClick(post.optLong("id"));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public int getItemCount() {
        return posts.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvContent, tvAuthor, tvLikes, tvReplies;
        
        ViewHolder(View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvContent = itemView.findViewById(R.id.tv_content);
            tvAuthor = itemView.findViewById(R.id.tv_author);
            tvLikes = itemView.findViewById(R.id.tv_likes);
            tvReplies = itemView.findViewById(R.id.tv_replies);
        }
    }
}