package ru.marthastudios.smmtgbot.api;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import ru.marthastudios.smmtgbot.api.payload.SmmCodeCreateOrderRequest;
import ru.marthastudios.smmtgbot.property.SmmCodeProperty;

@Component
@RequiredArgsConstructor
public class SmmCodeApi {
    private static final String SMM_CODE_API_URL = "https://smmcode.shop";
    private final RestTemplate restTemplate;
    private final SmmCodeProperty smmCodeProperty;

    public boolean createOrder(int serviceId, int count, String link){
        SmmCodeCreateOrderRequest smmCodeCreateOrderRequest = SmmCodeCreateOrderRequest.builder()
                .apiToken(smmCodeProperty.getApiKey())
                .count(count)
                .serviceId(serviceId)
                .link(link)
                .build();

        try {
            String responseString = restTemplate.postForEntity(SMM_CODE_API_URL + "/api/reseller/create_order",
                    smmCodeCreateOrderRequest, String.class).getBody();

            return true;
        } catch (HttpClientErrorException.BadRequest e){
            return false;
        }
    }
}
