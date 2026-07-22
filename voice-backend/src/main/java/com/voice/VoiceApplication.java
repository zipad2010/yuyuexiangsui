package com.voice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class VoiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(VoiceApplication.class, args);
        System.out.println("语音助手后端启动成功！");
    }
}