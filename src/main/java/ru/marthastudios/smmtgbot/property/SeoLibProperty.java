package ru.marthastudios.smmtgbot.property;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class SeoLibProperty {
    @Value("${seolib.apiKey}")
    private String apiKey;
}
