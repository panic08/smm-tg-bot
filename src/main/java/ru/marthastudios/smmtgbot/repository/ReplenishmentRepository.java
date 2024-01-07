package ru.marthastudios.smmtgbot.repository;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import ru.marthastudios.smmtgbot.model.Replenishment;

@Repository
public interface ReplenishmentRepository extends CrudRepository<Replenishment, Long> {
    @Query("SELECT sum(r.amount) FROM replenishments_table r")
    double amountSum();
}
