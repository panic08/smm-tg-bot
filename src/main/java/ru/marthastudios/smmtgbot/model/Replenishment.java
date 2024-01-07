package ru.marthastudios.smmtgbot.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table(name = "replenishments_table")
@Data
@Builder
public class Replenishment {
    @Id
    private Long id;
    @Column("user_id")
    private Long userId;
    private Double amount;
    private Long timestamp;
}
