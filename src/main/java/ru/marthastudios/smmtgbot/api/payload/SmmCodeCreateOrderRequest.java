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
public class SmmCodeCreateOrderRequest {
    private String apiToken;
    private Integer serviceId;
    private Integer count;
    private String link;
}
