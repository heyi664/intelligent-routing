package com.xinchan.voiceqa.qa;

import java.util.Optional;

public interface FastQaService {
    Optional<String> findAnswer(String message);
}
