package ru.marthastudios.smmtgbot.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.marthastudios.smmtgbot.api.payload.OpenaiCreateChatCompletionRequest;
import ru.marthastudios.smmtgbot.api.payload.OpenaiCreateChatCompletionResponse;
import ru.marthastudios.smmtgbot.property.OpenaiProperty;

@Component
@RequiredArgsConstructor
public class OpenaiApi {
    private final RestTemplate restTemplate;
    private final OpenaiProperty openaiProperty;
    private static final String OPENAI_API_URL = "https://api.openai.com";

    public OpenaiCreateChatCompletionResponse createChatCompletion(OpenaiCreateChatCompletionRequest openaiCreateChatCompletionRequest) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + openaiProperty.getApiKey());

        HttpEntity<OpenaiCreateChatCompletionRequest> entity = new HttpEntity<>(openaiCreateChatCompletionRequest, headers);
        String url = OPENAI_API_URL + "/v1/chat/completions";

        return restTemplate.exchange(
                url,
                HttpMethod.POST,
                entity,
                OpenaiCreateChatCompletionResponse.class
        ).getBody();
    }
}
