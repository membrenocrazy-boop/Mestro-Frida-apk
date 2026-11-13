package com.mestro.ac;
import android.content.Context;
import android.content.SharedPreferences;
public class AuthManager {
    private static final String PREFS = "mestro_auth";
    private static final long TRIAL_MS = 2 * 60 * 60 * 1000L;
    private final SharedPreferences prefs;
    public AuthManager(Context c) { prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    public boolean isFullyAuthed() { return prefs.getBoolean("full_auth", false); }
    public boolean isTrialExpired() {
        if (isFullyAuthed()) return false;
        long start = prefs.getLong("trial_start", 0);
        return start == 0 || System.currentTimeMillis() - start > TRIAL_MS;
    }
    public void startTrial() { prefs.edit().putLong("trial_start", System.currentTimeMillis()).apply(); }
    public void setFullAuth(String token, String discord, String device) {
        prefs.edit().putBoolean("full_auth", true).putString("token", token).putString("discord", discord).putString("device", device).apply();
    }
}