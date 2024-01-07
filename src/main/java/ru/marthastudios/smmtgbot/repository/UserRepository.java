package ru.marthastudios.smmtgbot.repository;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.marthastudios.smmtgbot.enums.UserPrivilege;
import ru.marthastudios.smmtgbot.model.User;

@Repository
public interface UserRepository extends CrudRepository<User, Long> {
    @Query("SELECT u.* FROM users_table u WHERE u.telegram_chat_id = :telegramChatId")
    User findByTelegramChatId(@Param("telegramChatId") long telegramChatId);

    @Query("SELECT u.balance FROM users_table u WHERE u.id = :id")
    double findBalanceById(@Param("id") long id);

    @Query("UPDATE users_table SET balance = :balance WHERE id = :id")
    @Modifying
    void updateBalanceById(@Param("balance") double balance, @Param("id") long id);

    @Query("UPDATE users_table SET privilege = :privilege WHERE id = :id")
    @Modifying
    void updatePrivilegeById(@Param("privilege") UserPrivilege privilege, @Param("id") long id);
}
