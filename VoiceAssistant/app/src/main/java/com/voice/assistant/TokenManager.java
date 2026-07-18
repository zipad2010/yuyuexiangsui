package com.voice.assistant;

import android.content.Context;
import android.content.SharedPreferences;

public class TokenManager {
    private static final String PREFS_NAME = "voice_prefs";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_BALANCE = "balance";
    
    private SharedPreferences prefs;
    
    public TokenManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public void saveToken(String token, String username, String balance) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USERNAME, username)
            .putString(KEY_BALANCE, balance)
            .apply();
    }
    
    public String getToken() { return prefs.getString(KEY_TOKEN, null); }
    public String getUsername() { return prefs.getString(KEY_USERNAME, null); }
    public String getBalance() { return prefs.getString(KEY_BALANCE, "0.00"); }
    public void updateBalance(String balance) { prefs.edit().putString(KEY_BALANCE, balance).apply(); }
    public void clear() { prefs.edit().clear().apply(); }
    public boolean isLoggedIn() { return getToken() != null; }
}