package com.voice.assistant;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ModelAdapter extends RecyclerView.Adapter<ModelAdapter.ViewHolder> {
    
    private List<ModelSelectActivity.ModelItem> models;
    private OnModelSelectedListener listener;
    
    public interface OnModelSelectedListener {
        void onModelSelected(ModelSelectActivity.ModelItem model);
    }
    
    public ModelAdapter(List<ModelSelectActivity.ModelItem> models, OnModelSelectedListener listener) {
        this.models = models;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ModelSelectActivity.ModelItem model = models.get(position);
        holder.text1.setText(model.name);
        holder.text2.setText(model.id);
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onModelSelected(model);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return models.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text1, text2;
        ViewHolder(View itemView) {
            super(itemView);
            text1 = itemView.findViewById(android.R.id.text1);
            text2 = itemView.findViewById(android.R.id.text2);
        }
    }
}