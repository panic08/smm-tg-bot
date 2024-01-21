package ru.marthastudios.smmtgbot.api.payload;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SeoLibCreateTaskRequest {
    private String token;
    private String domain;
    private String[] keywords;
    private Engine[] engines;

    @Getter
    @Setter
    @Builder
    public static class Engine {
        private int engine;
        private int[] regions;
        private int depth;
    }
}
