package ru.marthastudios.smmtgbot.service;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.marthastudios.smmtgbot.api.LolzteamApi;
import ru.marthastudios.smmtgbot.api.payload.LolzteamPaymentResponse;
import ru.marthastudios.smmtgbot.bot.TelegramBot;
import ru.marthastudios.smmtgbot.enums.UserPrivilege;
import ru.marthastudios.smmtgbot.model.Replenishment;
import ru.marthastudios.smmtgbot.model.User;
import ru.marthastudios.smmtgbot.repository.ReplenishmentRepository;
import ru.marthastudios.smmtgbot.repository.UserRepository;

import java.util.Map;

import static ru.marthastudios.smmtgbot.bot.TelegramBot.replenishmentCache;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReplenishmentService {
    private final LolzteamApi lolzteamApi;
    private final UserRepository userRepository;
    private final ReplenishmentRepository replenishmentRepository;
    private final TelegramBot telegramBot;

    @Transactional
    public void handleLolzteamReplenishment(long chatId, String replenishmentSecret, User user){
        log.info("Starting method handleLolzteamReplenishment");

        long startTime = System.currentTimeMillis();
        long endTime = startTime + 15 * 60 * 1000;

        while (System.currentTimeMillis() < endTime) {
            if (replenishmentCache.get(chatId) == null){
                return;
            }

            LolzteamPaymentResponse lolzteamPaymentResponse = lolzteamApi.getAllPaymentByStartDayDateAndEndYearAheadDate(-3, 1);

            if (lolzteamPaymentResponse.getPayments() != null){
                for (Map.Entry<String, LolzteamPaymentResponse.Payment> paymentEntry : lolzteamPaymentResponse.getPayments().entrySet()){
                    if (paymentEntry.getValue().getData().getComment().equals(replenishmentSecret)){
                        double incomingSum = Math.round(paymentEntry.getValue().getIncomingSum() * 100.0) / 100.0;

                        Replenishment replenishment = Replenishment.builder()
                                .userId(user.getId())
                                .amount(incomingSum)
                                .timestamp(System.currentTimeMillis())
                                .build();

                        replenishmentRepository.save(replenishment);

                        double userBalance = userRepository.findBalanceById(user.getId());

                        userRepository.updateBalanceById(userBalance + incomingSum, user.getId());

                        if (!(user.getPrivilege().equals(UserPrivilege.CUSTOMER)) &&
                                replenishmentRepository.amountSum() >= 100){
                            userRepository.updatePrivilegeById(UserPrivilege.CUSTOMER, user.getId());
                        }

                        telegramBot.handleSuccessReplenishment(chatId, replenishmentCache.get(chatId), incomingSum);

                        return;
                    }
                }
            }

            try {
                Thread.sleep(5 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        telegramBot.handleCancelReplenishment(chatId, replenishmentCache.get(chatId));
    }
}
