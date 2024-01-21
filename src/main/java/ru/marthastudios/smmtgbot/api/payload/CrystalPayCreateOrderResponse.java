package ru.marthastudios.smmtgbot.api.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CrystalPayCreateOrderResponse {
    @JsonProperty("error")
    private boolean isError;
    private String url;
    private double amount;
    private String type;
}
