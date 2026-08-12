package com.voice.assistant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 违规记录列表适配器（管理中心，仅管理员使用）
 */
public class ViolationAdapter extends RecyclerView.Adapter<ViolationAdapter.ViewHolder> {

    public interface OnHandleListener {
        void onHandle(JSONObject record);
    }

    private final List<JSONObject> records;
    private final OnHandleListener listener;
    private final SimpleDateFormat dateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public ViolationAdapter(List<JSONObject> records, OnHandleListener listener) {
        this.records = records;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_violation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject record = records.get(position);
        holder.tvUser.setText(record.optString("username", "未知用户"));
        String source = record.optString("source", "");
        String sourceLabel;
        switch (source) {
            case "forum":
                sourceLabel = "论坛发帖";
                break;
            case "persona":
                sourceLabel = "人设投稿";
                break;
            case "chat":
                sourceLabel = "聊天";
                break;
            default:
                sourceLabel = source;
        }
        holder.tvSource.setText(sourceLabel);
        holder.tvReason.setText("违规原因：" + record.optString("violationReason", "违规"));
        holder.tvContent.setText(record.optString("content", ""));

        long createdAt = record.optLong("createdAt", 0);
        holder.tvTime.setText(createdAt > 0
                ? dateFormat.format(new Date(createdAt)) : "");

        boolean handled = record.optInt("status", 0) == 1;
        holder.btnHandle.setVisibility(handled ? View.GONE : View.VISIBLE);
        holder.btnHandle.setOnClickListener(v -> {
            if (listener != null) {
                listener.onHandle(record);
            }
        });
    }

    @Override
    public int getItemCount() {
        return records.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvUser, tvSource, tvReason, tvContent, tvTime;
        MaterialButton btnHandle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvUser = itemView.findViewById(R.id.tv_v_user);
            tvSource = itemView.findViewById(R.id.tv_v_source);
            tvReason = itemView.findViewById(R.id.tv_v_reason);
            tvContent = itemView.findViewById(R.id.tv_v_content);
            tvTime = itemView.findViewById(R.id.tv_v_time);
            btnHandle = itemView.findViewById(R.id.btn_v_handle);
        }
    }
}
