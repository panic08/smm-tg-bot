package ru.marthastudios.smmtgbot.api.payload;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SeoLibGetTaskResultResponse {
    private boolean success;
    private Content content;

    @Getter
    @Setter
    public static class Content {
        private boolean status;
        @JsonProperty("result")
        private Result[] results;
    }

    @Getter
    @Setter
    public static class Result {
        private String keyword;
        @JsonProperty("engines")
        private Engine engine;
    }

    @Getter
    @Setter
    public static class Engine {
        private int engine;
        @JsonProperty("regions")
        private Region region;
    }

    @Getter
    @Setter
    public static class Region {
        private int region;
        private Report report;
    }

    @Getter
    @Setter
    public static class Report {
        private String total;
        private String page;
        private Object position;
        private String snippet;
    }
}
