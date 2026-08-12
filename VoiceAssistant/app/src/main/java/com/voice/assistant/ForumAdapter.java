package com.voice.assistant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import de.hdodenhof.circleimageview.CircleImageView;
import org.json.JSONArray;
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

            String avatarUrl = post.optString("avatarUrl", "");
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                Glide.with(holder.itemView.getContext())
                        .load(ApiClient.resolveResourceUrl(avatarUrl))
                        .placeholder(R.drawable.ic_default_avatar)
                        .error(R.drawable.ic_default_avatar)
                        .into(holder.ivAvatar);
            } else {
                holder.ivAvatar.setImageResource(R.drawable.ic_default_avatar);
            }

            // 媒体预览：显示第一个媒体（图片/视频缩略图）
            JSONArray mediaUrls = post.optJSONArray("mediaUrls");
            boolean hasMedia = mediaUrls != null && mediaUrls.length() > 0;
            if (hasMedia) {
                String first = mediaUrls.optString(0, "");
                boolean isVideo = first.toLowerCase().matches(".*\\.(mp4|webm|mkv|mov|3gp|avi)$")
                        || first.toLowerCase().contains("/video/");
                holder.flMedia.setVisibility(View.VISIBLE);
                holder.tvVideoBadge.setVisibility(isVideo ? View.VISIBLE : View.GONE);
                if (isVideo) {
                    holder.ivThumb.setImageResource(R.drawable.ic_image);
                } else {
                    Glide.with(holder.itemView.getContext())
                            .load(ApiClient.resolveResourceUrl(first))
                            .centerCrop()
                            .into(holder.ivThumb);
                }
            } else {
                holder.flMedia.setVisibility(View.GONE);
            }

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
        TextView tvTitle, tvContent, tvAuthor, tvLikes, tvReplies, tvVideoBadge;
        CircleImageView ivAvatar;
        FrameLayout flMedia;
        ImageView ivThumb;
        
        ViewHolder(View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvContent = itemView.findViewById(R.id.tv_content);
            tvAuthor = itemView.findViewById(R.id.tv_author);
            tvLikes = itemView.findViewById(R.id.tv_likes);
            tvReplies = itemView.findViewById(R.id.tv_replies);
            flMedia = itemView.findViewById(R.id.fl_media_preview);
            ivThumb = itemView.findViewById(R.id.iv_media_thumb);
            tvVideoBadge = itemView.findViewById(R.id.tv_video_badge);
        }
    }
}