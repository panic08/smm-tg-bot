package ru.marthastudios.smmtgbot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.marthastudios.smmtgbot.api.payload.CrystalPayCallbackRequest;
import ru.marthastudios.smmtgbot.api.payload.CrystalPayCreateOrderRequest;
import ru.marthastudios.smmtgbot.bot.TelegramBot;
import ru.marthastudios.smmtgbot.enums.UserPrivilege;
import ru.marthastudios.smmtgbot.model.Replenishment;
import ru.marthastudios.smmtgbot.model.User;
import ru.marthastudios.smmtgbot.pojo.Pair;
import ru.marthastudios.smmtgbot.repository.ReplenishmentRepository;
import ru.marthastudios.smmtgbot.repository.UserRepository;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReplenishmentService {
    private final UserRepository userRepository;
    private final ReplenishmentRepository replenishmentRepository;
    private final TelegramBot telegramBot;

    public static final Map<String, Pair<Long, Double>> replenishmentSecretChatIdAmountMap = new HashMap<>();

    public void handleCrystalPayReplenishment(long chatId, int messageId, String replenishmentSecret, double amount) {
        replenishmentSecretChatIdAmountMap.put(replenishmentSecret, new Pair<>(chatId, amount));

        long startTime = System.currentTimeMillis();
        long endTime = startTime + 15 * 60 * 1000;

        while (System.currentTimeMillis() < endTime) {
            if (replenishmentSecretChatIdAmountMap.get(replenishmentSecret) == null) {
                telegramBot.handleSuccessReplenishment(chatId, messageId, amount);
                return;
            }

            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        replenishmentSecretChatIdAmountMap.remove(replenishmentSecret);
        telegramBot.handleCancelReplenishment(chatId, messageId);
    }

    @Transactional
    public void handleCallbackCrystalPayReplenishment(CrystalPayCallbackRequest crystalPayCallbackRequest) {
        if (replenishmentSecretChatIdAmountMap.get(crystalPayCallbackRequest.getExtra()) == null) {
            return;
        }

        Pair<Long, Double> userChatIdAmountPair = replenishmentSecretChatIdAmountMap.get(crystalPayCallbackRequest.getExtra());

        User user = userRepository.findByTelegramChatId(userChatIdAmountPair.getTupleOne());

        Replenishment replenishment = Replenishment.builder()
                .userId(user.getId())
                .amount(userChatIdAmountPair.getTupleTwo())
                .timestamp(System.currentTimeMillis())
                .build();

        replenishmentRepository.save(replenishment);

        double userBalance = userRepository.findBalanceById(user.getId());

        userRepository.updateBalanceById(userBalance + userChatIdAmountPair.getTupleTwo(), user.getId());

        if (!(user.getPrivilege().equals(UserPrivilege.CUSTOMER)) &&
                replenishmentRepository.amountSum() >= 100){
            userRepository.updatePrivilegeById(UserPrivilege.CUSTOMER, user.getId());
        }

        replenishmentSecretChatIdAmountMap.remove(crystalPayCallbackRequest.getExtra());
    }

}
