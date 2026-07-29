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
        void onPostClick(long postId);
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
            holder.tvLikes.setText("赞 " + post.optInt("likeCount", 0));
            holder.tvReplies.setText("回复 " + post.optInt("replyCount", 0));
                holder.itemView.setAlpha(0f);
                holder.itemView.setTranslationY(18f);
                holder.itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(Math.min(position, 5) * 45L)
                    .setDuration(260L)
                    .start();
            
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    long postId = post.optLong("id", 0);
                    if (postId > 0) {
                        listener.onPostClick(postId);
                    }
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