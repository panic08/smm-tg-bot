package ru.marthastudios.smmtgbot.api.payload;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class LolzteamPaymentResponse {
    private Map<String, Payment> payments;
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Payment {
        @JsonProperty("incoming_sum")
        private double incomingSum;
        private Data data;
    }
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Data {
        private String comment;
    }
}
