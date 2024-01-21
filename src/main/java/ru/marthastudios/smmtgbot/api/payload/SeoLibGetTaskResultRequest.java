package ru.marthastudios.smmtgbot.api.payload;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class SeoLibGetTaskResultRequest {
    private String token;
    private String task;
}
