package ru.marthastudios.smmtgbot.property;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class LolzteamProperty {
    @Value("${lolzteam.token}")
    private String token;

    @Value("${lolzteam.nickname}")
    private String nickname;

    @Value("${lolzteam.userId}")
    private Long userId;
}
