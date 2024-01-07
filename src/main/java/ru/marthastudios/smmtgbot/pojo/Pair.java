package ru.marthastudios.smmtgbot.pojo;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Pair <T, L>{
    private T tupleOne;
    private L tupleTwo;
}
