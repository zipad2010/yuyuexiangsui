package com.voice.assistant;

public class Message {
    private String content;
    private boolean isUser;
    private String audioBase64;

    public Message(String content, boolean isUser) {
        this.content = content;
        this.isUser = isUser;
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public boolean isUser() { return isUser; }
    public String getAudioBase64() { return audioBase64; }
    public void setAudioBase64(String audioBase64) { this.audioBase64 = audioBase64; }
}