package com.voice.assistant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.util.List;

/**
 * 人设列表适配器（广场/我的投稿通用）
 */
public class PersonaAdapter extends RecyclerView.Adapter<PersonaAdapter.ViewHolder> {

    public interface OnActionClickListener {
        void onAction(JSONObject persona);
    }

    private final List<JSONObject> personas;
    private final boolean mineMode;
    private final OnActionClickListener listener;

    public PersonaAdapter(List<JSONObject> personas, boolean mineMode, OnActionClickListener listener) {
        this.personas = personas;
        this.mineMode = mineMode;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_persona, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject persona = personas.get(position);
        String name = persona.optString("name", "未命名");
        String author = persona.optString("authorName", "");
        String description = persona.optString("description", "");
        int subscribeCount = persona.optInt("subscribeCount", 0);
        boolean subscribed = persona.optBoolean("subscribed", false);

        holder.tvName.setText(name);
        holder.tvAuthor.setText("作者：" + author);
        holder.tvDesc.setText(description.isEmpty() ? "这个人设还没有简介" : description);
        holder.tvSubCount.setText(subscribeCount + " 人订阅");

        if (mineMode) {
            holder.tvSubscribed.setVisibility(View.GONE);
            holder.tvAction.setText("查看");
        } else if (subscribed) {
            holder.tvSubscribed.setVisibility(View.VISIBLE);
            holder.tvAction.setText("取消订阅");
        } else {
            holder.tvSubscribed.setVisibility(View.GONE);
            holder.tvAction.setText("订阅");
        }

        holder.tvAction.setOnClickListener(v -> {
            if (listener != null) {
                listener.onAction(persona);
            }
        });
        holder.itemView.setOnClickListener(v -> showPrompt(persona, holder.itemView));
    }

    private void showPrompt(JSONObject persona, View view) {
        String prompt = persona.optString("prompt", "");
        if (prompt == null || prompt.isEmpty()) {
            android.widget.Toast.makeText(view.getContext(),
                    "订阅后可查看完整人设内容", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(view.getContext())
                .setTitle(persona.optString("name", "人设"))
                .setMessage(prompt)
                .setPositiveButton("知道了", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return personas.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvAuthor, tvDesc, tvSubCount, tvAction, tvSubscribed;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_persona_name);
            tvAuthor = itemView.findViewById(R.id.tv_persona_author);
            tvDesc = itemView.findViewById(R.id.tv_persona_desc);
            tvSubCount = itemView.findViewById(R.id.tv_persona_sub_count);
            tvAction = itemView.findViewById(R.id.tv_persona_action);
            tvSubscribed = itemView.findViewById(R.id.tv_persona_subscribed);
        }
    }
}
