package ru.marthastudios.smmtgbot.property;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class CrystalPayProperty {
    @Value("${crystalpay.authLogin}")
    private String authLogin;

    @Value("${crystalpay.authSecret}")
    private String authSecret;

    @Value("${crystalpay.callbackUrl}")
    private String callbackUrl;
}
