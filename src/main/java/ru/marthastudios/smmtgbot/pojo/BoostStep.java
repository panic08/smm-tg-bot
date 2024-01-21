package ru.marthastudios.smmtgbot.pojo;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import ru.marthastudios.smmtgbot.enums.BoostSociety;

@Getter
@Setter
@Builder
public class BoostStep {
    private BoostSociety boostSociety;
    private Integer count;
}
