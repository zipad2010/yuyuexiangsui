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
import java.util.List;

/**
 * 发起私信时的用户列表适配器（用户列表 + 输入名字搜索结合）
 */
public class UserSelectAdapter extends RecyclerView.Adapter<UserSelectAdapter.ViewHolder> {

    public interface OnUserClickListener {
        void onUserClick(JSONObject user);
    }

    private final List<JSONObject> users;
    private final OnUserClickListener listener;

    public UserSelectAdapter(List<JSONObject> users, OnUserClickListener listener) {
        this.users = users;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user_select, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject user = users.get(position);
        String nickname = user.optString("nickname", user.optString("username", "未知"));
        String username = user.optString("username", "");
        String avatarUrl = user.optString("avatarUrl", "");

        holder.tvName.setText(nickname);
        holder.tvUsername.setText("@" + username);

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(ApiClient.resolveResourceUrl(avatarUrl))
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_default_avatar);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onUserClick(user);
            }
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivAvatar;
        TextView tvName, tvUsername;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_user_avatar);
            tvName = itemView.findViewById(R.id.tv_user_name);
            tvUsername = itemView.findViewById(R.id.tv_user_username);
        }
    }
}
