package com.voice.assistant;

public class UserInfo {
    private long userId;
    private String username;
    private String balance;
    private boolean isSponsor;
    
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getBalance() { return balance; }
    public void setBalance(String balance) { this.balance = balance; }
    public boolean isSponsor() { return isSponsor; }
    public void setSponsor(boolean sponsor) { isSponsor = sponsor; }
}