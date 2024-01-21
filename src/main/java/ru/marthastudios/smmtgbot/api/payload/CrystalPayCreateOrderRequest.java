package ru.marthastudios.smmtgbot.api.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CrystalPayCreateOrderRequest {
    private String authLogin;
    private String authSecret;
    private double amount;
    private String amountCurrency;
    private String type;
    private String description;
    private String extra;
    private String callbackUrl;
    private int lifetime;
}
