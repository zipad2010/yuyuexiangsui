package com.voice.assistant;

import android.content.Context;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;
import android.util.Base64;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Locale;

public class AudioPlayer {
    private TextToSpeech tts;
    private Context context;
    
    public AudioPlayer(Context context) {
        this.context = context;
        initTTS();
    }
    
    private void initTTS() {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.CHINESE);
                tts.setSpeechRate(1.0f);
            }
        });
    }
    
    public void speak(String text, Runnable onComplete) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            if (onComplete != null) {
                new android.os.Handler().postDelayed(onComplete, text.length() * 100);
            }
        }
    }
    
    public void playBase64Audio(String base64Audio, Runnable onComplete) {
        try {
            byte[] audioData = Base64.decode(base64Audio, Base64.DEFAULT);
            File tempFile = File.createTempFile("audio", ".pcm", context.getCacheDir());
            FileOutputStream fos = new FileOutputStream(tempFile);
            fos.write(audioData);
            fos.close();
            Log.d("AudioPlayer", " ’µΩ“Ù∆µ: " + audioData.length + " bytes");
            if (onComplete != null) onComplete.run();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}