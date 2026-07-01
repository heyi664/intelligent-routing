package com.xinchan.voiceqa.qa;


import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class InMemoryFastQaService implements FastQaService {
    private final Map<String, String> answers = new LinkedHashMap<>();

    public InMemoryFastQaService() {
        // TODO: replace with Redis + managed QA table for <=200ms hit path.
        answers.put("缴费", "您可以通过营业厅、线上缴费渠道或客服指引完成天然气缴费。");
        answers.put("充值", "您可以使用线上充值或到营业厅办理燃气充值。");
    }

    @Override
    public Optional<String> findAnswer(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }

        for (Map.Entry<String, String> entry : answers.entrySet()) {
            if (message.contains(entry.getKey())) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }
}
