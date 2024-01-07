package ru.marthastudios.smmtgbot.property;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
public class TelegramBotProperty {
    @Value("${telegram.bots.token}")
    private String token;
    @Value("${telegram.bots.name}")
    private String name;
}
