package ru.marthastudios.smmtgbot.api.payload;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SeoLibCreateTaskResponse {
    private boolean success;
    private Content content;

    @Getter
    @Setter
    public static class Content {
        private String task;
        private int price;
    }
}
