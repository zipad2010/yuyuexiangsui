package com.voice.service;

import com.baidu.aip.speech.AipSpeech;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
public class BaiduAsrService {

    private static final Logger log = LoggerFactory.getLogger(BaiduAsrService.class);

    @Value("${api.baidu.app-id}")
    private String appId;

    @Value("${api.baidu.api-key}")
    private String apiKey;

    @Value("${api.baidu.secret-key}")
    private String secretKey;

    private AipSpeech client;

    private AipSpeech getClient() {
        if (client == null) {
            client = new AipSpeech(appId, apiKey, secretKey);
            client.setConnectionTimeoutInMillis(20000);
            client.setSocketTimeoutInMillis(60000);
        }
        return client;
    }

    public String recognize(byte[] audioData) {
        try {
            HashMap<String, Object> options = new HashMap<>();
            options.put("dev_pid", 1537);
            options.put("lan", "zh");

            JSONObject result = getClient().asr(audioData, "pcm", 16000, options);
            log.info("百度ASR结果: {}", result);

            if (result.getInt("err_no") == 0) {
                return result.getJSONArray("result").getString(0);
            } else {
                log.error("ASR错误: {}", result.getString("err_msg"));
                return null;
            }
        } catch (Exception e) {
            log.error("ASR识别失败", e);
            return null;
        }
    }
}