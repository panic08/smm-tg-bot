package ru.marthastudios.smmtgbot.api.payload;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OpenaiCreateChatCompletionResponse {
    private Choice[] choices;

    @Getter
    @Setter
    public static class Choice{
        private Message message;
    }
    @Getter
    @Setter
    public static class Message{
        private String role;
        private String content;
    }
}
