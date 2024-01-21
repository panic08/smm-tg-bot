package ru.marthastudios.smmtgbot.pojo;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import ru.marthastudios.smmtgbot.enums.GenerateMethod;

@Getter
@Setter
@Builder
public class GenerateStep {
    private GenerateMethod generateMethod;
    private String word;
}
