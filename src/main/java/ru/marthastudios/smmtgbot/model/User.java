package ru.marthastudios.smmtgbot.model;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import ru.marthastudios.smmtgbot.enums.UserPrivilege;
import ru.marthastudios.smmtgbot.enums.UserRole;

@Table(name = "users_table")
@Data
@Builder
public class User {
    @Id
    private Long id;
    @Column("telegram_chat_id")
    private Long telegramChatId;
    private Double balance;
    private UserRole role;
    private UserPrivilege privilege;
    @Column("account_non_locked")
    private Boolean isAccountNonLocked;
    @Column("registered_at")
    private Long registeredAt;
}
