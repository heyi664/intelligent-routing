package com.xinchan.voiceqa.ai;

import org.springframework.web.client.RestClient;

@FunctionalInterface
interface QwenRestClientFactory {
    RestClient create(int connectTimeoutMs, int readTimeoutMs);
}
