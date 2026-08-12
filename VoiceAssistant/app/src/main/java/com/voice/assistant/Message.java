package com.voice.assistant;

public class Message {
    private String content;
    private boolean isUser;
    private String audioBase64;
    /** 后端历史记录 id（撤回用）；0 表示无（如正在输入/加载中的消息） */
    private long historyId;

    public Message(String content, boolean isUser) {
        this.content = content;
        this.isUser = isUser;
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isUser() { return isUser; }
    public String getAudioBase64() { return audioBase64; }
    public void setAudioBase64(String audioBase64) { this.audioBase64 = audioBase64; }
    public long getHistoryId() { return historyId; }
    public void setHistoryId(long historyId) { this.historyId = historyId; }
}