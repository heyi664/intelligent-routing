package com.xinchan.voiceqa.qa;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "app.qa")
public class QaProperties {
    private Map<String, String> fastAnswers = new LinkedHashMap<>();

    public Map<String, String> getFastAnswers() {
        return fastAnswers;
    }

    public void setFastAnswers(Map<String, String> fastAnswers) {
        this.fastAnswers = fastAnswers == null ? new LinkedHashMap<>() : fastAnswers;
    }
}
