package ru.marthastudios.smmtgbot.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.marthastudios.smmtgbot.api.payload.SeoLibCreateTaskRequest;
import ru.marthastudios.smmtgbot.api.payload.SeoLibCreateTaskResponse;
import ru.marthastudios.smmtgbot.api.payload.SeoLibGetTaskResultRequest;
import ru.marthastudios.smmtgbot.api.payload.SeoLibGetTaskResultResponse;
import ru.marthastudios.smmtgbot.property.SeoLibProperty;

@Component
@RequiredArgsConstructor
public class SeoLibApi {
    private final RestTemplate restTemplate;
    private static final String SEO_LIB_API_URL = "https://api.seolib.ru";
    private final SeoLibProperty seoLibProperty;
    
    public SeoLibCreateTaskResponse createTask(SeoLibCreateTaskRequest seoLibCreateTaskRequest) {
        seoLibCreateTaskRequest.setToken(seoLibProperty.getApiKey());

        ResponseEntity<SeoLibCreateTaskResponse> seoLibGetTaskResultRequestResponseEntity =
                restTemplate.postForEntity(SEO_LIB_API_URL + "/metrics/positions/task/create", seoLibCreateTaskRequest, SeoLibCreateTaskResponse.class);

        return seoLibGetTaskResultRequestResponseEntity.getBody();
    }

    public SeoLibGetTaskResultResponse getTaskResult(SeoLibGetTaskResultRequest seoLibGetTaskResultRequest) {
        seoLibGetTaskResultRequest.setToken(seoLibProperty.getApiKey());

        ResponseEntity<SeoLibGetTaskResultResponse> seoLibGetTaskResultResponseResponseEntity =
                restTemplate.postForEntity(SEO_LIB_API_URL + "/metrics/positions/task/result", seoLibGetTaskResultRequest, SeoLibGetTaskResultResponse.class);

        return seoLibGetTaskResultResponseResponseEntity.getBody();
    }
}
