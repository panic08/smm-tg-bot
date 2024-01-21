package ru.marthastudios.smmtgbot.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.marthastudios.smmtgbot.api.payload.CrystalPayCreateOrderRequest;
import ru.marthastudios.smmtgbot.api.payload.CrystalPayCreateOrderResponse;
import ru.marthastudios.smmtgbot.property.CrystalPayProperty;

@Component
@RequiredArgsConstructor
public class CrystalPayApi {
    private final RestTemplate restTemplate;
    private final CrystalPayProperty crystalPayProperty;
    private static final String CRYSTAL_PAY_API_URL = "https://api.crystalpay.io/v2";

    public CrystalPayCreateOrderResponse createOrder(CrystalPayCreateOrderRequest crystalPayCreateOrderRequest) {
        crystalPayCreateOrderRequest.setAuthLogin(crystalPayProperty.getAuthLogin());
        crystalPayCreateOrderRequest.setAuthSecret(crystalPayProperty.getAuthSecret());
        crystalPayCreateOrderRequest.setCallbackUrl(crystalPayProperty.getCallbackUrl());

        ResponseEntity<CrystalPayCreateOrderResponse> crystalPayCreateOrderResponseResponseEntity =
                restTemplate.postForEntity(CRYSTAL_PAY_API_URL + "/invoice/create/", crystalPayCreateOrderRequest, CrystalPayCreateOrderResponse.class);


        return crystalPayCreateOrderResponseResponseEntity.getBody();
    }
}
