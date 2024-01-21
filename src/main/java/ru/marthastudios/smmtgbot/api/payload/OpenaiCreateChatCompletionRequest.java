package ru.marthastudios.smmtgbot.api.payload;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OpenaiCreateChatCompletionRequest {
    private String model;
    private Message[] messages;
    @Getter
    @Setter
    @Builder
    public static class Message{
        private String role;
        private String content;
    }
}
