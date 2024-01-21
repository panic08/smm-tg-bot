package ru.marthastudios.smmtgbot.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.marthastudios.smmtgbot.api.CrystalPayApi;
import ru.marthastudios.smmtgbot.api.OpenaiApi;
import ru.marthastudios.smmtgbot.api.SeoLibApi;
import ru.marthastudios.smmtgbot.api.SmmCodeApi;
import ru.marthastudios.smmtgbot.api.payload.*;
import ru.marthastudios.smmtgbot.callback.AdminCallback;
import ru.marthastudios.smmtgbot.callback.BackCallback;
import ru.marthastudios.smmtgbot.callback.ReplenishmentCallback;
import ru.marthastudios.smmtgbot.callback.UserCallback;
import ru.marthastudios.smmtgbot.enums.*;
import ru.marthastudios.smmtgbot.model.User;
import ru.marthastudios.smmtgbot.pojo.BoostStep;
import ru.marthastudios.smmtgbot.pojo.Pair;
import ru.marthastudios.smmtgbot.pojo.SocialsData;
import ru.marthastudios.smmtgbot.property.TelegramBotProperty;
import ru.marthastudios.smmtgbot.repository.UserRepository;
import ru.marthastudios.smmtgbot.service.ReplenishmentService;
import ru.marthastudios.smmtgbot.util.UrlFileDownloader;
import ru.marthastudios.smmtgbot.util.UrlValidator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
@Slf4j
public class TelegramBot extends TelegramLongPollingBot {
    @Autowired
    private TelegramBotProperty telegramBotProperty;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SmmCodeApi smmCodeApi;
    @Autowired
    private OpenaiApi openaiApi;
    @Autowired
    private SeoLibApi seoLibApi;
    @Autowired
    private CrystalPayApi crystalPayApi;
    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    @Lazy
    private ReplenishmentService replenishmentService;
    private static final Map<Long, ReplenishmentMethod> userReplenishmentSteps = new HashMap<>();
    private static final Map<Long, Pair<Integer, BoostStep>> userBoostSteps = new HashMap<>();
    private static final Map<Long, GenerateMethod> userGenerateSteps = new HashMap<>();
    private static final Map<Long, Pair<Integer, String>> userCheckSteps = new HashMap<>();
    private static final Set<Long> userPumpSteps = new HashSet();

    public static final Map<Long, Pair<Integer, Long>> adminAddBalanceSteps = new HashMap<>();
    public static final Map<Long, Pair<Integer, Long>> adminRemoveBalanceSteps = new HashMap<>();
    public static final Set<Long> adminAddAdminSteps = new HashSet<>();
    public static final Set<Long> adminRemoveAdminSteps = new HashSet<>();
    public static final Set<Long> adminAddBanSteps = new HashSet<>();
    public static final Set<Long> adminRemoveBanSteps = new HashSet<>();
    public static final Set<Long> adminDispatchSteps = new HashSet<>();


    public TelegramBot(TelegramBotProperty telegramBotProperty) {
        this.telegramBotProperty = telegramBotProperty;
        List<BotCommand> listOfCommands = new ArrayList<>();

        listOfCommands.add(new BotCommand("/start", "\uD83D\uDCD6 Главное меню"));
        listOfCommands.add(new BotCommand("/myprofile", "\uD83D\uDC64 Мой профиль"));
        listOfCommands.add(new BotCommand("/rules", "\uD83D\uDCCC Правила"));
        listOfCommands.add(new BotCommand("/boost", "\uD83D\uDCC8 Поднять активность"));
        listOfCommands.add(new BotCommand("/generate", "\uD83C\uDFF7 Генерация тегов"));
        listOfCommands.add(new BotCommand("/check", "\uD83D\uDD0D Проверка позиции"));
        listOfCommands.add(new BotCommand("/pump", "\uD83D\uDCE5 Выкачать и уникализировать"));

        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return telegramBotProperty.getName();
    }

    @Override
    public String getBotToken() {
        return telegramBotProperty.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        new Thread(() -> {
            if (update.hasMessage() && update.getMessage().hasText()){
                String text = update.getMessage().getText();
                long chatId = update.getMessage().getChatId();

                User user = userRepository.findByTelegramChatId(chatId);

                if (user == null){
                    user = userRepository.save(User.builder()
                            .telegramChatId(chatId)
                            .balance(0D)
                            .role(UserRole.DEFAULT)
                            .isAccountNonLocked(true)
                            .privilege(UserPrivilege.DEFAULT)
                            .registeredAt(System.currentTimeMillis())
                            .build());
                }

                if (!user.getIsAccountNonLocked()) {
                    SendMessage message = SendMessage.builder()
                            .text("\uD83D\uDEAB <b>Вы были заблокированы в этом боте навсегда</b>")
                            .chatId(chatId)
                            .parseMode("html")
                            .build();

                    try {
                        execute(message);
                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }

                    return;
                }

                switch (text){
                    case "/start" -> {
                        sendStartMessage(chatId, user);
                        return;
                    }

                    case "/boost", "\uD83D\uDCC8 Поднять активность" -> {
                        sendBoostMessage(chatId);
                        return;
                    }

                    case "/myprofile", "\uD83D\uDC64 Мой профиль" -> {
                        sendMyProfileMessage(chatId, user);
                        return;
                    }
                    case "/generate", "\uD83C\uDFF7 Генерация тегов" -> {
                        sendGenerateMessage(chatId, user);
                        return;
                    }
                    case "/check", "\uD83D\uDD0D Проверка позиции" -> {
                        sendCheckMessage(chatId, user);
                        return;
                    }
                    case "/pump", "\uD83D\uDCE5 Выкачать и уникализировать" -> {
                        sendPumpMessage(chatId, user);
                        return;
                    }
                    case "/rules", "\uD83D\uDCCC Правила" -> {
                        sendRulesMessage(chatId);
                        return;
                    }
                    case "♨\uFE0F Админ панель" -> {
                        sendAdminMessage(chatId, user);
                        return;
                    }
                }

                if (userReplenishmentSteps.get(chatId) != null){
                    ReplenishmentMethod replenishmentMethod = userReplenishmentSteps.get(chatId);

                    double amount = Double.parseDouble(text);

                    if (amount < 25){
                        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

                        InlineKeyboardButton backToReplenishmentButton = InlineKeyboardButton.builder()
                                .callbackData(BackCallback.BACK_TO_REPLENISHMENT_CALLBACK_DATA)
                                .text("◀\uFE0F Назад")
                                .build();

                        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                        keyboardButtonsRow1.add(backToReplenishmentButton);

                        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                        rowList.add(keyboardButtonsRow1);

                        inlineKeyboardMarkup.setKeyboard(rowList);

                        SendMessage message = SendMessage.builder()
                                .text("❌ <b>Минимальная сумма для пополнения:</b> 25.0$")
                                .chatId(chatId)
                                .replyMarkup(inlineKeyboardMarkup)
                                .parseMode("html")
                                .build();

                        try {
                            execute(message);
                        } catch (TelegramApiException e){
                            log.warn(e.getMessage());
                        }
                        return;
                    }

                    String replenishmentLink = null;
                    String replenishmentSecret = UUID.randomUUID().toString();

                    if (replenishmentMethod.equals(ReplenishmentMethod.CRYSTALPAY)){
                        CrystalPayCreateOrderResponse crystalPayCreateOrderResponse =
                                crystalPayApi.createOrder(CrystalPayCreateOrderRequest.builder()
                                        .type("purchase")
                                        .amount(amount)
                                        .amountCurrency("USD")
                                        .lifetime(4320)
                                        .extra(replenishmentSecret)
                                        .build());


                        replenishmentLink = crystalPayCreateOrderResponse.getUrl();

                        log.info("replenishment secret: {} replenishment link: {}", replenishmentSecret, replenishmentLink);
                    }

                    InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton replenishmentButton = InlineKeyboardButton.builder()
                            .url(replenishmentLink)
                            .text("Перейти к платежку")
                            .build();

                    InlineKeyboardButton checkReplenishmentButton = InlineKeyboardButton.builder()
                            .callbackData(UserCallback.CHECK_REPLENISHMENT_CALLBACK_DATA)
                            .text("Проверить платеж")
                            .build();

                    InlineKeyboardButton cancelReplenishmentButton = InlineKeyboardButton.builder()
                            .callbackData(UserCallback.CANCEL_REPLENISHMENT_CALLBACK_DATA)
                            .text("❌ Отменить платеж")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
                    List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();
                    List<InlineKeyboardButton> keyboardButtonsRow3 = new ArrayList<>();

                    keyboardButtonsRow1.add(replenishmentButton);
                    keyboardButtonsRow2.add(checkReplenishmentButton);
                    keyboardButtonsRow3.add(cancelReplenishmentButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);
                    rowList.add(keyboardButtonsRow2);
                    rowList.add(keyboardButtonsRow3);

                    inlineKeyboardMarkup.setKeyboard(rowList);

                    SendMessage message = SendMessage.builder()
                            .text("\uD83C\uDF10 <b>Подтверди Платеж</b>\n\n"
                                    + "Отличный выбор! Ты выбрал способ оплаты " +  (replenishmentMethod.equals(ReplenishmentMethod.CRYSTALPAY) ? "\uD83D\uDC8E <b>CrystalPay</b>" : "") +", а сумма в <b>" + amount + "</b>$ - просто идеальная!\n\n" +
                                    "<b>Теперь тебе осталось всего 15 минут, чтобы подтвердить свой платеж и погрузиться в мир новых возможностей.</b>\n\n" +
                                    "\uD83D\uDE80\uD83D\uDD52 Нажми <b>\"Перейти к платежу\"</b> или <b>\"Проверить платеж\"</b>, чтобы начать оптимизировать свой опыт! \uD83D\uDC4D\uD83D\uDCBC\n"
                            )
                            .chatId(chatId)
                            .replyMarkup(inlineKeyboardMarkup)
                            .parseMode("html")
                            .build();

                    userReplenishmentSteps.remove(chatId);

                    Integer sendedMessageId = null;

                    try {
                        sendedMessageId = execute(message).getMessageId();
                    } catch (TelegramApiException e){
                        log.warn(e.getMessage());
                    }

                    Integer finalSendedMessageId = sendedMessageId;

                    new Thread(() -> {
                        replenishmentService.handleCrystalPayReplenishment(chatId, finalSendedMessageId, replenishmentSecret, amount);
                    }).start();
                    return;
                }

                if (userBoostSteps.get(chatId) != null){
                    Pair<Integer, BoostStep> stepCount = userBoostSteps.get(chatId);

                    switch (stepCount.getTupleOne()){
                        case 1 -> {
                            if (Long.parseLong(text) > 80000000){
                                InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

                                InlineKeyboardButton backToBoostButton = InlineKeyboardButton.builder()
                                        .callbackData(BackCallback.BACK_TO_BOOST_CALLBACK_DATA)
                                        .text("◀\uFE0F Назад")
                                        .build();

                                List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                                keyboardButtonsRow1.add(backToBoostButton);

                                List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                                rowList.add(keyboardButtonsRow1);

                                inlineKeyboardMarkup.setKeyboard(rowList);

                                SendMessage message = SendMessage.builder()
                                        .text("❌ <b>Вы не можете привлечь больше чем 80000000 пользователей!</b>")
                                        .chatId(chatId)
                                        .replyMarkup(inlineKeyboardMarkup)
                                        .parseMode("html")
                                        .build();

                                try {
                                    execute(message);
                                } catch (TelegramApiException e){
                                    log.warn(e.getMessage());
                                }
                                return;
                            }

                            int count = Integer.parseInt(text);

                            if (count < 20000){
                                InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

                                InlineKeyboardButton backToBoostButton = InlineKeyboardButton.builder()
                                        .callbackData(BackCallback.BACK_TO_BOOST_CALLBACK_DATA)
                                        .text("◀\uFE0F Назад")
                                        .build();

                                List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                                keyboardButtonsRow1.add(backToBoostButton);

                                List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                                rowList.add(keyboardButtonsRow1);

                                inlineKeyboardMarkup.setKeyboard(rowList);

                                SendMessage message = SendMessage.builder()
                                        .text("❌ <b>Вы не можете привлечь меньше чем 20000 пользователей!</b>")
                                        .chatId(chatId)
                                        .replyMarkup(inlineKeyboardMarkup)
                                        .parseMode("html")
                                        .build();

                                try {
                                    execute(message);
                                } catch (TelegramApiException e){
                                    log.warn(e.getMessage());
                                }
                                return;
                            }

                            if (user.getBalance() < (double) count / 1000){
                                InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

                                InlineKeyboardButton replenishmentButton = InlineKeyboardButton.builder()
                                        .callbackData(BackCallback.BACK_TO_REPLENISHMENT_CALLBACK_DATA)
                                        .text("\uD83D\uDCB0 Пополнить баланс")
                                        .build();

                                InlineKeyboardButton backToBoostButton = InlineKeyboardButton.builder()
                                        .callbackData(BackCallback.BACK_TO_BOOST_CALLBACK_DATA)
                                        .text("◀\uFE0F Назад")
                                        .build();


                                List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
                                List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();

                                keyboardButtonsRow1.add(replenishmentButton);
                                keyboardButtonsRow2.add(backToBoostButton);

                                List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                                rowList.add(keyboardButtonsRow1);
                                rowList.add(keyboardButtonsRow2);

                                inlineKeyboardMarkup.setKeyboard(rowList);

                                SendMessage message = SendMessage.builder()
                                        .text("❌ <b>У вас не хватает</b> " + ((double) count / 1000 - user.getBalance()) +  "$ <b>для поднятие активности</b>")
                                        .chatId(chatId)
                                        .replyMarkup(inlineKeyboardMarkup)
                                        .parseMode("html")
                                        .build();

                                try {
                                    execute(message);
                                } catch (TelegramApiException e){
                                    log.warn(e.getMessage());
                                }

                                return;
                            }

                            stepCount.setTupleOne(2);
                            stepCount.setTupleTwo(BoostStep.builder().count(count).boostSociety(stepCount.getTupleTwo().getBoostSociety()).build());

                            userBoostSteps.put(chatId, stepCount);

                            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

                            InlineKeyboardButton backToBoostButton = InlineKeyboardButton.builder()
                                    .callbackData(BackCallback.BACK_TO_BOOST_CALLBACK_DATA)
                                    .text("◀\uFE0F Назад")
                                    .build();

                            List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                            keyboardButtonsRow1.add(backToBoostButton);

                            List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                            rowList.add(keyboardButtonsRow1);

                            inlineKeyboardMarkup.setKeyboard(rowList);

                            SendMessage message = SendMessage.builder()
                                    .text("\uD83C\uDF10 <b>Шаг к Успеху!</b>\n\n"
                                            + "Нам готовиться к запуску вашей <b>мощной</b> кампании! Введите URL вашего сайта, и давайте начнем магию! ✨\uD83D\uDE80")
                                    .chatId(chatId)
                                    .replyMarkup(inlineKeyboardMarkup)
                                    .parseMode("html")
                                    .build();

                            try {
                                execute(message);
                            } catch (TelegramApiException e){
                                log.warn(e.getMessage());
                            }
                        }
                        case 2 -> {
                            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

                            InlineKeyboardButton backToBoostButton = InlineKeyboardButton.builder()
                                    .callbackData(BackCallback.BACK_TO_BOOST_CALLBACK_DATA)
                                    .text("◀\uFE0F Назад")
                                    .build();

                            List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                            keyboardButtonsRow1.add(backToBoostButton);

                            List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                            rowList.add(keyboardButtonsRow1);

                            inlineKeyboardMarkup.setKeyboard(rowList);

                            if (UrlValidator.isValid(text)){
                                SendMessage message = SendMessage.builder()
                                        .text("⏳ <b>Загрузка...</b>")
                                        .chatId(chatId)
                                        .parseMode("html")
                                        .build();

                                Integer awaitMessageId = null;

                                try {
                                    awaitMessageId = execute(message).getMessageId();
                                } catch (TelegramApiException e){
                                    log.warn(e.getMessage());
                                }

                                Integer serviceId = null;

                                if (stepCount.getTupleTwo().getBoostSociety().equals(BoostSociety.GOOGLE)){
                                    serviceId = 1049;
                                } else if (stepCount.getTupleTwo().getBoostSociety().equals(BoostSociety.YANDEX)){
                                    serviceId = 1040;
                                }

                                boolean requestIsSuccess = smmCodeApi.createOrder(serviceId, stepCount.getTupleTwo().getCount(), text);

                                if (!requestIsSuccess){
                                    EditMessageText editMessage = EditMessageText.builder()
                                            .text("❌ <b>Вы уже запустили процесс поднятия активности для данного URL. Введите другой или отмените процесс поднятия активности</b>")
                                            .chatId(chatId)
                                            .messageId(awaitMessageId)
                                            .replyMarkup(inlineKeyboardMarkup)
                                            .parseMode("html")
                                            .build();

                                    try {
                                        execute(editMessage);
                                    } catch (TelegramApiException e){
                                        log.warn(e.getMessage());
                                    }
                                    return;
                                }
                                userRepository.updateBalanceById(Math.round((user.getBalance() - (double) stepCount.getTupleTwo().getCount() / 1000) * 100.0) / 100.0, user.getId());
                                userBoostSteps.remove(chatId);

                                EditMessageText editMessage = EditMessageText.builder()
                                        .text("\uD83D\uDE80 <b>Запускайтесь в Цифровое Пространство!</b>\n\n"
                                                        + "Ваша кампания стартовала! \uD83C\uDF10✨\n\n"
                                                        + "\uD83C\uDF10 <b>URL вашего сайта:</b> " + text + "\n\n"
                                                        + "\uD83D\uDE80 <b>Ожидаемое количество посетителей:</b> " + stepCount.getTupleTwo().getCount() + " человек\n\n"
                                                        + "Наблюдайте, как ваша видимость растет! Держите курс на успех! \uD83D\uDCBC\uD83D\uDCA1")
                                        .chatId(chatId)
                                        .messageId(awaitMessageId)
                                        .replyMarkup(inlineKeyboardMarkup)
                                        .parseMode("html")
                                        .disableWebPagePreview(true)
                                        .build();


                                try {
                                    execute(editMessage);
                                } catch (TelegramApiException e){
                                    log.warn(e.getMessage());
                                }
                            } else {
                                SendMessage message = SendMessage.builder()
                                        .text("❌ <b>Вы ввели некорректный URL, повторите попытку</b>")
                                        .chatId(chatId)
                                        .replyMarkup(inlineKeyboardMarkup)
                                        .parseMode("html")
                                        .build();

                                try {
                                    execute(message);
                                } catch (TelegramApiException e){
                                    log.warn(e.getMessage());
                                }
                            }
                        }
                    }
                    return;
                }

                if (userGenerateSteps.get(chatId) != null){
                    GenerateMethod generateMethod = userGenerateSteps.get(chatId);

                    userGenerateSteps.remove(chatId);

                    SendMessage awaitMessage = SendMessage.builder()
                            .text("⏳ <b>Загрузка...</b>")
                            .chatId(chatId)
                            .parseMode("html")
                            .build();

                    Integer awaitMessageId = null;

                    try {
                        awaitMessageId = execute(awaitMessage).getMessageId();
                    } catch (TelegramApiException e){
                        log.warn(e.getMessage());
                    }

                    String keyWords = null;

                    if (generateMethod.equals(GenerateMethod.AI)){
                        OpenaiCreateChatCompletionRequest openaiCreateChatCompletionRequest =
                                OpenaiCreateChatCompletionRequest.builder()
                                        .model("gpt-3.5-turbo")
                                        .messages(new OpenaiCreateChatCompletionRequest.Message[] {
                                                OpenaiCreateChatCompletionRequest.Message.builder()
                                                        .role("system")
                                                        .content("Ты генератор СЕО тегов по ключевым словам")
                                                        .build(),
                                                OpenaiCreateChatCompletionRequest.Message.builder()
                                                        .role("user")
                                                        .content("Сгенерируй только 30 тегов по ключевому слову \"" + text + "\" через запятую")
                                                        .build()
                                        })
                                        .build();


                        keyWords = openaiApi.createChatCompletion(openaiCreateChatCompletionRequest).getChoices()[0].getMessage().getContent();

                        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

                        InlineKeyboardButton backToGenerateButton = InlineKeyboardButton.builder()
                                .callbackData(BackCallback.BACK_TO_GENERATE_CALLBACK_DATA)
                                .text("◀\uFE0F Назад")
                                .build();

                        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                        keyboardButtonsRow1.add(backToGenerateButton);

                        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                        rowList.add(keyboardButtonsRow1);

                        inlineKeyboardMarkup.setKeyboard(rowList);

                        EditMessageText editMessageText = EditMessageText.builder()
                                .text("\uD83C\uDF1F <b>Отличный выбор!</b>\n\n"
                                        + "Вы выбрали ключевое слово, которое наилучшим образом описывает ваш контент. Теперь давайте создадим вокруг него магию тегов, чтобы максимально увеличить вашу видимость! \uD83D\uDE80\uD83D\uDCA1\n\n"
                                        + "\uD83D\uDD11 <b>Выбранное ключевое слово:</b> " + text + "\n\n"
                                        + "\uD83D\uDCCC <b>Ваш шаблон для тегов:</b>\n\n"
                                        + "<code>" + keyWords + "</code>")
                                .chatId(chatId)
                                .replyMarkup(inlineKeyboardMarkup)
                                .parseMode("html")
                                .messageId(awaitMessageId)
                                .build();

                        try {
                            execute(editMessageText);
                        } catch (TelegramApiException e){
                            log.warn(e.getMessage());
                        }
                    }

                    return;
                }

                if (userPumpSteps.contains(chatId)) {
                    InlineKeyboardMarkup backKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToBoostButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_PUMP_CALLBACK_DATA)
                            .text("◀\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToBoostButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    backKeyboardMarkup.setKeyboard(rowList);

                    if (!UrlValidator.isValid(text)) {
                        SendMessage message = SendMessage.builder()
                                .text("❌ <b>Вы ввели некорректный URL, повторите попытку</b>")
                                .chatId(chatId)
                                .parseMode("html")
                                .replyMarkup(backKeyboardMarkup)
                                .build();

                        try {
                            execute(message);
                        } catch (TelegramApiException e) {
                            log.warn(e.getMessage());
                        }

                        return;
                    }

                    userPumpSteps.remove(chatId);

                    SendMessage message = SendMessage.builder()
                            .text("⏳ <b>Загрузка...</b>")
                            .chatId(chatId)
                            .parseMode("html")
                            .build();

                    Integer awaitMessageId = null;

                    try {
                        awaitMessageId = execute(message).getMessageId();
                    } catch (TelegramApiException e){
                        log.warn(e.getMessage());
                    }

                    ResponseEntity<String> responseEntity = restTemplate.getForEntity(text, String.class);

                    File htmlLandingTempFile = null;

                    try {
                        htmlLandingTempFile = File.createTempFile("tempHtmlLanding", ".html");
                    } catch (IOException e) {
                        log.warn(e.getMessage());
                    }

                    try (FileWriter writer = new FileWriter(htmlLandingTempFile)) {
                        writer.write(responseEntity.getBody());
                    } catch (IOException e) {
                        log.warn(e.getMessage());
                    }

                    InputFile htmlLandingInputFile = new InputFile(htmlLandingTempFile);

                    InlineKeyboardMarkup backToPumpFromBuyerPumpKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToPumpFromBuyerPumpButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_PUMP_FROM_BUYER_PUMP_CALLBACK_DATA)
                            .text("◀\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow11 = new ArrayList<>();

                    keyboardButtonsRow11.add(backToPumpFromBuyerPumpButton);

                    List<List<InlineKeyboardButton>> rowList1 = new ArrayList<>();

                    rowList1.add(keyboardButtonsRow11);
                    backToPumpFromBuyerPumpKeyboardMarkup.setKeyboard(rowList1);

                    DeleteMessage deleteMessage = DeleteMessage.builder()
                            .messageId(awaitMessageId)
                            .chatId(chatId)
                            .build();

                    SendDocument document = SendDocument.builder()
                            .document(htmlLandingInputFile)
                            .caption("\uD83D\uDCE5 <b>Выкачать и Уникализировать!</b>\n\n"
                                    + "Прекрасно! Мы успешно выкачали HTML вашего лендинга. Теперь у вас есть уникальный контент в вашем распоряжении! \uD83D\uDE80\uD83D\uDCA1\n\n"
                                    + "<b>Что бы вы хотели сделать дальше?</b> ✨\uD83C\uDF10")
                            .replyMarkup(backToPumpFromBuyerPumpKeyboardMarkup)
                            .parseMode("html")
                            .chatId(chatId)
                            .build();

                    try {
                        execute(deleteMessage);
                        execute(document);
                        htmlLandingTempFile.delete();
                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }
                    return;
                }

                if (userCheckSteps.get(chatId) != null){
                    Pair<Integer, String> stepCount = userCheckSteps.get(chatId);

                    InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToBoostButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_CHECK_CALLBACK_DATA)
                            .text("◀\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToBoostButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    inlineKeyboardMarkup.setKeyboard(rowList);

                    switch (stepCount.getTupleOne()){
                        case 1 -> {
                            if (!UrlValidator.isValid(text)){

                                SendMessage message = SendMessage.builder()
                                        .text("❌ <b>Вы ввели некорректный URL, повторите попытку</b>")
                                        .chatId(chatId)
                                        .parseMode("html")
                                        .replyMarkup(inlineKeyboardMarkup)
                                        .build();

                                try {
                                    execute(message);
                                } catch (TelegramApiException e) {
                                    log.warn(e.getMessage());
                                }

                                return;
                            }

                            stepCount.setTupleOne(stepCount.getTupleOne() + 1);
                            stepCount.setTupleTwo(text);

                            userCheckSteps.put(chatId, stepCount);

                            SendMessage message = SendMessage.builder()
                                    .text("\uD83C\uDF1F <b>Подготовка к Проверке Позиции!</b>\n\n"
                                            + "Отлично! Теперь давайте определить ключевые слова для вашего сайта. \uD83D\uDE80\n\n"
                                            + "\uD83D\uDD11 <b>Введите ключевые слова через запятую, которые лучше всего описывают ваш контент.</b> \uD83D\uDCBC\n\n"
                                            + "Например, введите: <i>\"цифровой маркетинг, SEO оптимизация, веб-разработка\"</i>.\n\n"
                                            + "<b>С этими словами мы проведем точную и эффективную проверку позиции вашего сайта в поисковых результатах.</b> \uD83C\uDF10✨"
                                    )
                                    .chatId(chatId)
                                    .replyMarkup(inlineKeyboardMarkup)
                                    .parseMode("html")
                                    .build();

                            try {
                                execute(message);
                            } catch (TelegramApiException e) {
                                log.warn(e.getMessage());
                            }
                        }
                        case 2 -> {
                            userCheckSteps.remove(chatId);

                            SendMessage message = SendMessage.builder()
                                    .text("⏳ <b>Загрузка...</b>")
                                    .chatId(chatId)
                                    .parseMode("html")
                                    .build();

                            Integer awaitMessageId = null;

                            try {
                                awaitMessageId = execute(message).getMessageId();
                            } catch (TelegramApiException e){
                                log.warn(e.getMessage());
                            }

                            String[] keyWords = text.split(", ");

                            StringBuilder editMessageTextBuilder = new StringBuilder("\uD83D\uDCCA <b>Результаты Проверки Позиции в Поисковиках!</b>\n\n"
                                    + "Отлично! Мы завершили проверку для ваших ключевых фраз. \uD83D\uDE80\n\n"
                                    + "Вот полученные данные:\n\n");

                            SeoLibCreateTaskResponse seoLibCreateTaskResponse =
                                    seoLibApi.createTask(SeoLibCreateTaskRequest.builder()
                                            .domain(stepCount.getTupleTwo())
                                            .engines(new SeoLibCreateTaskRequest.Engine[]{
                                                    SeoLibCreateTaskRequest.Engine.builder()
                                                            .engine(1)
                                                            .regions(new int[]{531})
                                                            .depth(150)
                                                            .build(),
                                                    SeoLibCreateTaskRequest.Engine.builder()
                                                            .engine(2)
                                                            .regions(new int[]{937})
                                                            .depth(200)
                                                            .build(),
                                            })
                                            .keywords(keyWords)
                                            .build());

                            String task = seoLibCreateTaskResponse.getContent().getTask();

                            log.info("Task id: {}", task);

                            while (true) {
                                SeoLibGetTaskResultResponse seoLibGetTaskResultResponse =
                                        seoLibApi.getTaskResult(SeoLibGetTaskResultRequest.builder()
                                                .task(task)
                                                .build());

                                if (seoLibGetTaskResultResponse.getContent().isStatus()) {
                                    Map<String, SocialsData> keywordSocialDataMap = new HashMap<>();

                                    for (SeoLibGetTaskResultResponse.Result result : seoLibGetTaskResultResponse.getContent().getResults()) {
                                        if (result.getEngine().getEngine() == 1) {
                                            if (keywordSocialDataMap.get(result.getKeyword()) != null) {
                                                SocialsData socialsData = keywordSocialDataMap.get(result.getKeyword());

                                                socialsData.setYandexPosition(String.valueOf(result.getEngine().getRegion().getReport().getPosition()));

                                                keywordSocialDataMap.put(result.getKeyword(), socialsData);
                                            } else {
                                                SocialsData socialsData = new SocialsData();

                                                socialsData.setYandexPosition(String.valueOf(result.getEngine().getRegion().getReport().getPosition()));

                                                keywordSocialDataMap.put(result.getKeyword(), socialsData);
                                            }
                                        } else if (result.getEngine().getEngine() == 2) {
                                            if (keywordSocialDataMap.get(result.getKeyword()) != null) {
                                                SocialsData socialsData = keywordSocialDataMap.get(result.getKeyword());

                                                socialsData.setGooglePosition(String.valueOf(result.getEngine().getRegion().getReport().getPosition()));

                                                keywordSocialDataMap.put(result.getKeyword(), socialsData);
                                            } else {
                                                SocialsData socialsData = new SocialsData();

                                                socialsData.setGooglePosition(String.valueOf(result.getEngine().getRegion().getReport().getPosition()));

                                                keywordSocialDataMap.put(result.getKeyword(), socialsData);
                                            }
                                        }
                                    }

                                    for (Map.Entry<String, SocialsData> entry : keywordSocialDataMap.entrySet()) {
                                        String keywordName = entry.getKey();
                                        SocialsData socialsData = entry.getValue();

                                        editMessageTextBuilder
                                                .append("<b>\"").append(keywordName).append("\":</b>\n")
                                                .append("    <b>Yandex Search:</b> ").append(socialsData.getYandexPosition()).append(" позиция").append("\n")
                                                .append("    <b>Google Search:</b> ").append(socialsData.getGooglePosition()).append(" позиция").append("\n")
                                                .append("    <b>Регион:</b> \uD83C\uDDF7\uD83C\uDDFA Россия")
                                                .append("\n\n");
                                    }


                                    EditMessageText editMessageText = EditMessageText.builder()
                                            .text(editMessageTextBuilder.toString())
                                            .chatId(chatId)
                                            .messageId(awaitMessageId)
                                            .parseMode("html")
                                            .replyMarkup(inlineKeyboardMarkup)
                                            .build();

                                    try {
                                        execute(editMessageText);
                                    } catch (TelegramApiException e) {
                                        log.warn(e.getMessage());
                                    }
                                    break;
                                }

                                try {
                                    Thread.sleep(4000);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }

                    }
                    return;
                }

                if (adminAddBalanceSteps.get(chatId) != null) {
                    Pair<Integer, Long> stepCount = adminAddBalanceSteps.get(chatId);

                    InlineKeyboardMarkup backInlineKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                            .text("◀\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToAdminButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    backInlineKeyboardMarkup.setKeyboard(rowList);

                    switch (stepCount.getTupleOne()) {
                        case 1 -> {
                            long userChatId = Long.parseLong(text);

                            if (userRepository.findByTelegramChatId(userChatId) == null) {
                                SendMessage message = SendMessage.builder()
                                        .text("❌ <b>Пользователя с таким айди не существует, введите другой айди</b>")
                                        .chatId(chatId)
                                        .replyMarkup(backInlineKeyboardMarkup)
                                        .parseMode("html")
                                        .build();

                                try {
                                    execute(message);
                                } catch (TelegramApiException e) {
                                    log.warn(e.getMessage());
                                }
                                return;
                            }

                            stepCount.setTupleOne(2);
                            stepCount.setTupleTwo(userChatId);

                            adminAddBalanceSteps.put(chatId, stepCount);

                            SendMessage message = SendMessage.builder()
                                    .text("➕ <b>Введите сумму в долларах, которую вы хотите добавить к балансу пользователя</b>")
                                    .chatId(chatId)
                                    .replyMarkup(backInlineKeyboardMarkup)
                                    .parseMode("html")
                                    .build();

                            try {
                                execute(message);
                            } catch (TelegramApiException e) {
                                log.warn(e.getMessage());
                            }
                        }
                        case 2 -> {
                            double balanceToAdd = Double.parseDouble(text);

                            userRepository.updateBalanceByTelegramChatId(userRepository.findBalanceByTelegramChatId(stepCount.getTupleTwo())
                            + balanceToAdd, stepCount.getTupleTwo());

                            if (balanceToAdd >= 100) {
                                userRepository.updatePrivilegeByTelegramChatId(UserPrivilege.CUSTOMER, stepCount.getTupleTwo());
                            }

                            SendMessage message = SendMessage.builder()
                                    .text("➕ <b>Вы успешно добавили пользователю с айди</b> <code>" + stepCount.getTupleTwo()
                                            + "</code> <b>к балансу сумму на</b> " + balanceToAdd + "$")
                                    .chatId(chatId)
                                    .replyMarkup(backInlineKeyboardMarkup)
                                    .parseMode("html")
                                    .build();

                            adminAddBalanceSteps.remove(chatId);

                            try {
                                execute(message);
                            } catch (TelegramApiException e) {
                                log.warn(e.getMessage());
                            }
                        }
                    }
                    return;
                }

                if (adminRemoveBalanceSteps.get(chatId) != null) {
                    Pair<Integer, Long> stepCount = adminRemoveBalanceSteps.get(chatId);

                    InlineKeyboardMarkup backInlineKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                            .text("◀\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToAdminButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    backInlineKeyboardMarkup.setKeyboard(rowList);

                    switch (stepCount.getTupleOne()) {
                        case 1 -> {
                            long userChatId = Long.parseLong(text);

                            if (userRepository.findByTelegramChatId(userChatId) == null) {
                                SendMessage message = SendMessage.builder()
                                        .text("❌ <b>Пользователя с таким айди не существует, введите другой айди</b>")
                                        .chatId(chatId)
                                        .replyMarkup(backInlineKeyboardMarkup)
                                        .parseMode("html")
                                        .build();

                                try {
                                    execute(message);
                                } catch (TelegramApiException e) {
                                    log.warn(e.getMessage());
                                }
                                return;
                            }

                            stepCount.setTupleOne(2);
                            stepCount.setTupleTwo(userChatId);

                            adminRemoveBalanceSteps.put(chatId, stepCount);

                            SendMessage message = SendMessage.builder()
                                    .text("➖ <b>Введите сумму в долларах, которую вы хотите снять с баланса пользователя</b>")
                                    .chatId(chatId)
                                    .replyMarkup(backInlineKeyboardMarkup)
                                    .parseMode("html")
                                    .build();

                            try {
                                execute(message);
                            } catch (TelegramApiException e) {
                                log.warn(e.getMessage());
                            }
                        }
                        case 2 -> {
                            double balanceToAdd = Double.parseDouble(text);

                            userRepository.updateBalanceByTelegramChatId(userRepository.findBalanceByTelegramChatId(stepCount.getTupleTwo())
                                    - balanceToAdd, stepCount.getTupleTwo());

                            SendMessage message = SendMessage.builder()
                                    .text("➖ <b>Вы успешно сняли пользователю с айди</b> <code>" + stepCount.getTupleTwo()
                                            + "</code> <b>с баланса сумму на</b> " + balanceToAdd + "$")
                                    .chatId(chatId)
                                    .replyMarkup(backInlineKeyboardMarkup)
                                    .parseMode("html")
                                    .build();

                            adminRemoveBalanceSteps.remove(chatId);

                            try {
                                execute(message);
                            } catch (TelegramApiException e) {
                                log.warn(e.getMessage());
                            }
                        }
                    }
                    return;
                }

                if (adminAddAdminSteps.contains(chatId)) {
                    InlineKeyboardMarkup backInlineKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                            .text("◀\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToAdminButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    backInlineKeyboardMarkup.setKeyboard(rowList);

                    long userChatId = Long.parseLong(text);

                    if (userRepository.findByTelegramChatId(userChatId) == null) {
                        SendMessage message = SendMessage.builder()
                                .text("❌ <b>Пользователя с таким айди не существует, введите другой айди</b>")
                                .chatId(chatId)
                                .replyMarkup(backInlineKeyboardMarkup)
                                .parseMode("html")
                                .build();

                        try {
                            execute(message);
                        } catch (TelegramApiException e) {
                            log.warn(e.getMessage());
                        }
                        return;
                    }

                    userRepository.updateRoleByTelegramChatId(UserRole.ADMIN, userChatId);

                    adminAddAdminSteps.remove(chatId);

                    SendMessage message = SendMessage.builder()
                            .text("✖\uFE0F <b>Вы успешно добавили пользователю с айди</b> <code>" + userChatId
                                    + "</code> <b>админа</b>")
                            .chatId(chatId)
                            .replyMarkup(backInlineKeyboardMarkup)
                            .parseMode("html")
                            .build();

                    try {
                        execute(message);
                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }

                    return;
                }

                if (adminRemoveAdminSteps.contains(chatId)) {
                    InlineKeyboardMarkup backInlineKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                            .text("◀\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToAdminButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    backInlineKeyboardMarkup.setKeyboard(rowList);

                    long userChatId = Long.parseLong(text);

                    if (userRepository.findByTelegramChatId(userChatId) == null) {
                        SendMessage message = SendMessage.builder()
                                .text("❌ <b>Пользователя с таким айди не существует, введите другой айди</b>")
                                .chatId(chatId)
                                .replyMarkup(backInlineKeyboardMarkup)
                                .parseMode("html")
                                .build();

                        try {
                            execute(message);
                        } catch (TelegramApiException e) {
                            log.warn(e.getMessage());
                        }
                        return;
                    }

                    userRepository.updateRoleByTelegramChatId(UserRole.DEFAULT, userChatId);

                    adminRemoveAdminSteps.remove(chatId);

                    SendMessage message = SendMessage.builder()
                            .text("➗ <b>Вы успешно сняли с пользователя с айди</b> <code>" + userChatId
                                    + "</code> <b>админа</b>")
                            .chatId(chatId)
                            .replyMarkup(backInlineKeyboardMarkup)
                            .parseMode("html")
                            .build();

                    try {
                        execute(message);
                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }

                    return;
                }

                if (adminDispatchSteps.contains(chatId)) {
                    adminDispatchSteps.remove(chatId);

                    Iterable<User> allUsers = userRepository.findAll();

                    for (User key : allUsers) {
                        SendMessage message = SendMessage.builder()
                                .chatId(key.getTelegramChatId())
                                .text(text)
                                .parseMode("html")
                                .build();

                        try {
                            execute(message);
                        } catch (TelegramApiException e) {
                            log.warn(e.getMessage());
                        }
                    }

                    InlineKeyboardMarkup backInlineKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                            .text("◀\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToAdminButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    backInlineKeyboardMarkup.setKeyboard(rowList);

                    SendMessage message = SendMessage.builder()
                            .text("✅ <b>Вы успешно разослали сообщение всем пользователям</b>")
                            .chatId(chatId)
                            .replyMarkup(backInlineKeyboardMarkup)
                            .parseMode("html")
                            .build();

                    try {
                        execute(message);
                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }
                    return;
                }

                if (adminAddBanSteps.contains(chatId)) {
                    InlineKeyboardMarkup backInlineKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                            .text("◀\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToAdminButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    backInlineKeyboardMarkup.setKeyboard(rowList);

                    long userChatId = Long.parseLong(text);

                    if (userRepository.findByTelegramChatId(userChatId) == null) {
                        SendMessage message = SendMessage.builder()
                                .text("❌ <b>Пользователя с таким айди не существует, введите другой айди</b>")
                                .chatId(chatId)
                                .replyMarkup(backInlineKeyboardMarkup)
                                .parseMode("html")
                                .build();

                        try {
                            execute(message);
                        } catch (TelegramApiException e) {
                            log.warn(e.getMessage());
                        }
                        return;
                    }

                    userRepository.updateIsAccountNonLockedByTelegramChatId(false, userChatId);

                    adminAddBanSteps.remove(chatId);

                    SendMessage message = SendMessage.builder()
                            .text("◼\uFE0F <b>Вы успешно забанили пользователя с айди</b> <code>" + userChatId
                                    + "</code>")
                            .chatId(chatId)
                            .replyMarkup(backInlineKeyboardMarkup)
                            .parseMode("html")
                            .build();

                    try {
                        execute(message);
                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }

                    return;
                }
                if (adminRemoveBanSteps.contains(chatId)) {
                    InlineKeyboardMarkup backInlineKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                            .text("◀\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToAdminButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    backInlineKeyboardMarkup.setKeyboard(rowList);

                    long userChatId = Long.parseLong(text);

                    if (userRepository.findByTelegramChatId(userChatId) == null) {
                        SendMessage message = SendMessage.builder()
                                .text("❌ <b>Пользователя с таким айди не существует, введите другой айди</b>")
                                .chatId(chatId)
                                .replyMarkup(backInlineKeyboardMarkup)
                                .parseMode("html")
                                .build();

                        try {
                            execute(message);
                        } catch (TelegramApiException e) {
                            log.warn(e.getMessage());
                        }
                        return;
                    }

                    userRepository.updateIsAccountNonLockedByTelegramChatId(true, userChatId);

                    adminRemoveBanSteps.remove(chatId);

                    SendMessage message = SendMessage.builder()
                            .text("◽\uFE0F <b>Вы успешно разбанили пользователя с айди</b> <code>" + userChatId
                                    + "</code>")
                            .chatId(chatId)
                            .replyMarkup(backInlineKeyboardMarkup)
                            .parseMode("html")
                            .build();

                    try {
                        execute(message);
                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }

                    return;
                }


            } else if (update.hasCallbackQuery()){
                long chatId = update.getCallbackQuery().getMessage().getChatId();
                int messageId = update.getCallbackQuery().getMessage().getMessageId();
                String callbackData = update.getCallbackQuery().getData();

                User user = userRepository.findByTelegramChatId(chatId);

                if (user == null){
                    user = userRepository.save(User.builder()
                            .telegramChatId(chatId)
                            .balance(0D)
                            .role(UserRole.DEFAULT)
                            .privilege(UserPrivilege.DEFAULT)
                            .registeredAt(System.currentTimeMillis())
                            .build());
                }

                if (!user.getIsAccountNonLocked()) {
                    SendMessage message = SendMessage.builder()
                            .text("\uD83D\uDEAB <b>Вы были заблокированы в этом боте навсегда</b>")
                            .chatId(chatId)
                            .parseMode("html")
                            .build();

                    try {
                        execute(message);
                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }

                    return;
                }

                switch (callbackData){
                    case UserCallback.FAQ_CALLBACK_DATA -> {
                        editFaqMessage(chatId, messageId);
                        return;
                    }

                    case UserCallback.REPLENISHMENT_CALLBACK_DATA -> {
                        editReplenishmentMessage(chatId, messageId);
                        return;
                    }

                    case UserCallback.CANCEL_REPLENISHMENT_CALLBACK_DATA -> {
                        handleCancelReplenishment(chatId, messageId);
                        return;
                    }

                    case UserCallback.CHECK_REPLENISHMENT_CALLBACK_DATA -> {
                        AnswerCallbackQuery answerCallbackQuery = AnswerCallbackQuery.builder()
                                .text("Оплата по платежу не найдена")
                                .callbackQueryId(update.getCallbackQuery().getId())
                                .build();

                        try {
                            execute(answerCallbackQuery);
                        } catch (TelegramApiException e){
                            log.warn(e.getMessage());
                        }
                        return;
                    }

                    case UserCallback.START_CHECK_CALLBACK_DATA -> {
                        editBuyerCheckMessage(chatId, messageId);
                        return;
                    }

                    case UserCallback.START_PUMP_CALLBACK_DATA -> {
                        editBuyerPumpMessage(chatId, messageId);
                        return;
                    }

                    case BackCallback.BACK_TO_MY_PROFILE_CALLBACK_DATA -> {
                        editMyProfileMessage(chatId, messageId, user);
                        return;
                    }
                    case BackCallback.BACK_TO_PUMP_CALLBACK_DATA -> {
                        userPumpSteps.remove(chatId);

                        editPumpMessage(chatId, messageId, user);
                    }
                    case BackCallback.BACK_TO_REPLENISHMENT_CALLBACK_DATA -> {
                        userReplenishmentSteps.remove(chatId);
                        userBoostSteps.remove(chatId);

                        editReplenishmentMessage(chatId, messageId);
                        return;
                    }
                    case BackCallback.BACK_TO_BOOST_CALLBACK_DATA -> {
                        userBoostSteps.remove(chatId);

                        editBoostMessage(chatId, messageId);
                    }
                    case BackCallback.BACK_TO_GENERATE_CALLBACK_DATA -> {
                        userGenerateSteps.remove(chatId);

                        editGenerateMessage(chatId, messageId);
                    }
                    case BackCallback.BACK_TO_CHECK_CALLBACK_DATA -> {
                        userCheckSteps.remove(chatId);

                        editCheckMessage(chatId, messageId, user);
                    }
                    case BackCallback.BACK_TO_ADMIN_CALLBACK_DATA -> {
                        adminAddBalanceSteps.remove(chatId);
                        adminRemoveBalanceSteps.remove(chatId);
                        adminAddAdminSteps.remove(chatId);
                        adminRemoveAdminSteps.remove(chatId);
                        adminDispatchSteps.remove(chatId);
                        adminAddBanSteps.remove(chatId);
                        adminRemoveBanSteps.remove(chatId);

                        editAdminMessage(chatId, messageId, user);
                    }
                    case BackCallback.BACK_TO_ADMIN_FROM_GET_USERS_CALLBACK_DATA -> {
                        DeleteMessage deleteMessage = DeleteMessage.builder()
                                .chatId(chatId)
                                .messageId(messageId)
                                .build();

                        try {
                            execute(deleteMessage);
                        } catch (TelegramApiException e) {
                            log.warn(e.getMessage());
                        }

                        sendAdminMessage(chatId, user);
                    }
                    case BackCallback.BACK_TO_PUMP_FROM_BUYER_PUMP_CALLBACK_DATA -> {
                        DeleteMessage deleteMessage = DeleteMessage.builder()
                                .chatId(chatId)
                                .messageId(messageId)
                                .build();

                        try {
                            execute(deleteMessage);
                        } catch (TelegramApiException e) {
                            log.warn(e.getMessage());
                        }

                        sendPumpMessage(chatId, user);
                    }
                    case AdminCallback.ADMIN_ADD_BALANCE_CALLBACK_DATA -> {
                        adminAddBalanceSteps.put(chatId, new Pair<>(1, null));

                        editAdminAddBalanceMessage(chatId, messageId, user);
                    }
                    case AdminCallback.ADMIN_REMOVE_BALANCE_CALLBACK_DATA -> {
                        adminRemoveBalanceSteps.put(chatId, new Pair<>(1, null));

                        editAdminRemoveBalanceMessage(chatId, messageId, user);
                    }
                    case AdminCallback.ADMIN_ADD_ADMIN_CALLBACK_DATA -> {
                        adminAddAdminSteps.add(chatId);

                        editAdminAddAdminMessage(chatId, messageId, user);
                    }

                    case AdminCallback.ADMIN_REMOVE_ADMIN_CALLBACK_DATA -> {
                        adminRemoveAdminSteps.add(chatId);

                        editAdminRemoveAdminMessage(chatId, messageId, user);
                    }
                    case AdminCallback.ADMIN_ADD_BAN_CALLBACK_DATA -> {
                        adminAddBanSteps.add(chatId);

                        editAdminAddBanMessage(chatId, messageId, user);
                    }
                    case AdminCallback.ADMIN_REMOVE_BAN_CALLBACK_DATA -> {
                        adminRemoveBanSteps.add(chatId);

                        editAdminRemoveBanMessage(chatId, messageId, user);
                    }
                    case AdminCallback.ADMIN_GET_USERS_DATA_CALLBACK_DATA -> editAdminGetUsersDataMessage(chatId, messageId, user);
                    case AdminCallback.ADMIN_DISPATCH_CALLBACK_DATA -> {
                        adminDispatchSteps.add(chatId);

                        editAdminDispatchMessage(chatId, messageId, user);
                    }
                }

                if (callbackData.contains(ReplenishmentCallback.REPLENISHMENT_METHOD_CALLBACK_DATA)){
                    ReplenishmentMethod replenishmentMethod = ReplenishmentMethod.valueOf(callbackData.split(" ")[4]);

                    userReplenishmentSteps.put(chatId, replenishmentMethod);

                    InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToReplenishmentButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_REPLENISHMENT_CALLBACK_DATA)
                            .text("◀\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToReplenishmentButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    inlineKeyboardMarkup.setKeyboard(rowList);

                    EditMessageText editMessageText = EditMessageText.builder()
                            .text("\uD83C\uDF10 <b>Выберите сумму для Пополнения</b>\n\n"
                                    + "Вы в движении к новым возможностям!\n\n"
                                    + "\uD83D\uDE80 <b>Теперь, укажите сумму в долларах, которую вы хотите пополнить на свой баланс.</b>\n\n" +
                                    "Открывайте двери к успеху, выбирая сумму, которая подходит именно для вас! \uD83D\uDCB0\uD83D\uDD13"
                            )
                            .chatId(chatId)
                            .messageId(messageId)
                            .replyMarkup(inlineKeyboardMarkup)
                            .parseMode("html")
                            .build();

                    try {
                        execute(editMessageText);
                    } catch (TelegramApiException e){
                        log.warn(e.getMessage());
                    }

                    return;
                }

                if (callbackData.contains(UserCallback.START_BOOST_CALLBACK_DATA)){
                    String boostSocial = callbackData.split(" ")[4];

                    editBuyerBoostMessage(chatId, messageId, BoostSociety.valueOf(boostSocial));

                    return;
                }

                if (callbackData.contains(UserCallback.START_GENERATING_TAG_CALLBACK_DATA)){
                    GenerateMethod generateMethod = GenerateMethod.valueOf(callbackData.split(" ")[5]);

                    editBuyerGenerateMessage(chatId, messageId, generateMethod);

                    return;
                }
            } else if (update.hasMessage() && update.getMessage().hasPhoto()) {
                long chatId = update.getMessage().getChatId();
                String caption = update.getMessage().getCaption();

                if (caption == null) {
                    caption = "";
                }

                if (adminDispatchSteps.contains(chatId)) {
                    adminDispatchSteps.remove(chatId);

                    Iterable<User> allUsers = userRepository.findAll();

                    GetFile getFile = GetFile.builder()
                            .fileId(update.getMessage().getPhoto().get(3).getFileId())
                            .build();

                    String URL = null;

                    try {
                        URL = execute(getFile).getFileUrl(telegramBotProperty.getToken());
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }

                    File file = null;

                    try {
                        file = UrlFileDownloader.downloadFile(URL, "imageDispatch", ".img");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    InputFile dispatchImage = new InputFile(file);

                    for (User key : allUsers) {
                        SendPhoto sendPhoto = SendPhoto.builder()
                                .photo(dispatchImage)
                                .caption(caption)
                                .chatId(key.getTelegramChatId())
                                .parseMode("html")
                                .build();

                        try {
                            execute(sendPhoto);
                        } catch (TelegramApiException e) {
                            log.warn(e.getMessage());
                        }
                    }

                    file.delete();

                    InlineKeyboardMarkup backInlineKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                            .text("◀\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToAdminButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    backInlineKeyboardMarkup.setKeyboard(rowList);

                    SendMessage message = SendMessage.builder()
                            .text("✅ <b>Вы успешно разослали сообщение всем пользователям</b>")
                            .chatId(chatId)
                            .replyMarkup(backInlineKeyboardMarkup)
                            .parseMode("html")
                            .build();

                    try {
                        execute(message);
                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }
                    return;
                }
            } else if (update.hasMessage() && update.getMessage().hasVideo()) {
                long chatId = update.getMessage().getChatId();
                String caption = update.getMessage().getCaption();

                if (adminDispatchSteps.contains(chatId)) {
                    adminDispatchSteps.remove(chatId);

                    Iterable<User> allUsers = userRepository.findAll();

                    GetFile getFile = GetFile.builder()
                            .fileId(update.getMessage().getVideo().getFileId())
                            .build();

                    String URL = null;

                    try {
                        URL = execute(getFile).getFileUrl(telegramBotProperty.getToken());
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }

                    File file = null;

                    try {
                        file = UrlFileDownloader.downloadFile(URL, "videoDispatch", ".mp4");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    InputFile dispatchVideo = new InputFile(file);

                    for (User key : allUsers) {
                        SendVideo sendVideo = SendVideo.builder()
                                .video(dispatchVideo)
                                .caption(caption)
                                .chatId(key.getTelegramChatId())
                                .parseMode("html")
                                .build();

                        try {
                            execute(sendVideo);
                        } catch (TelegramApiException e) {
                            log.warn(e.getMessage());
                        }
                    }

                    file.delete();

                    InlineKeyboardMarkup backInlineKeyboardMarkup = new InlineKeyboardMarkup();

                    InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                            .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                            .text("◀\uFE0F Назад")
                            .build();

                    List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

                    keyboardButtonsRow1.add(backToAdminButton);

                    List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

                    rowList.add(keyboardButtonsRow1);

                    backInlineKeyboardMarkup.setKeyboard(rowList);

                    SendMessage message = SendMessage.builder()
                            .text("✅ <b>Вы успешно разослали сообщение всем пользователям</b>")
                            .chatId(chatId)
                            .replyMarkup(backInlineKeyboardMarkup)
                            .parseMode("html")
                            .build();

                    try {
                        execute(message);
                    } catch (TelegramApiException e) {
                        log.warn(e.getMessage());
                    }
                }
            }
        }).start();
    }

    private void sendStartMessage(long chatId, User user){
        SendMessage message = SendMessage.builder()
                .text("\uD83C\uDF1F <b>Я - бот SeoSearch, ваш верный гид в мире эффективного SEO.</b>\n" +
                        "\n" +
                        "\uD83D\uDE80 <b>Погружайтесь в уникальные возможности моих инструментов:</b> <i>генератор ключевых слов, мощные методы поднятия сайта, точный мониторинг позиции в поисковых результатах, выкачка лендингов с последующей уникализацией, и, конечно же, глубокий анализ SEO для достижения выдающихся результатов.</i> \n" +
                        "\n" +
                        "\uD83D\uDEE0 <b>Используйте мои передовые инструменты и давайте вместе построим стратегию, которая приведет ваш проект к вершинам успеха!</b> \n" +
                        "\n" +
                        "\uD83D\uDCA1✨ <b>Не теряйте времени, начинайте свой путь к успешному SEO прямо сейчас!</b> \uD83D\uDE80\uD83C\uDF10")
                .chatId(chatId)
                .replyMarkup(getDefaultReplyKeyboardMarkup(user))
                .parseMode("html")
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    private void sendBoostMessage(long chatId){
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton googleSearchButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.START_BOOST_CALLBACK_DATA + " " + BoostSociety.GOOGLE)
                .text("\uD83D\uDD0E Google Search")
                .build();

        InlineKeyboardButton yandexSearchButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.START_BOOST_CALLBACK_DATA + " " + BoostSociety.YANDEX)
                .text("\uD83D\uDD0D Yandex Search")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(googleSearchButton);
        keyboardButtonsRow1.add(yandexSearchButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        SendMessage message = SendMessage.builder()
                .text("\uD83D\uDCC8 <b>Поднимите активность сейчас!</b>\n" +
                        "\n" +
                        "Теперь вы ближе к максимальной видимости. Наши передовые технологии искусственного интеллекта гарантируют точность и эффективность.\n" +
                        "\n" +
                        "✨ <b>Технологии ИИ:</b> Инструменты, основанные на передовых технологиях искусственного интеллекта, обеспечивают выдающиеся результаты.\n" +
                        "\n" +
                        "\uD83D\uDE80 <b>Выбор пути:</b> <i>Google Search</i> и <i>Yandex Search</i> – два мощных метода набора трафика. Максимизируйте видимость и достигайте успеха в цифровом маркетинге! \uD83D\uDCA1\uD83C\uDF10")
                .chatId(chatId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }
    private void sendMyProfileMessage(long chatId, User user){
        Date registeredAtDate = new Date(user.getRegisteredAt());
        SimpleDateFormat registeredDate = new SimpleDateFormat("dd.MM.yyyy");

        String formattedRegisteredAtDate = registeredDate.format(registeredAtDate);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton replenishmentButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.REPLENISHMENT_CALLBACK_DATA)
                .text("\uD83D\uDCB0 Пополнить баланс")
                .build();

        InlineKeyboardButton faqButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.FAQ_CALLBACK_DATA)
                .text("\uD83D\uDCDA Как получить привилегию?")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();

        keyboardButtonsRow1.add(replenishmentButton);
        keyboardButtonsRow2.add(faqButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);

        inlineKeyboardMarkup.setKeyboard(rowList);

        SendMessage message = SendMessage.builder()
                .text("\uD83D\uDC64 <b>Мой профиль</b>\n\n"
                + "\uD83C\uDD94 <b>Ваш идентификатор:</b> <code>" + user.getTelegramChatId() + "</code>\n"
                        + "\uD83D\uDC51 <b>Ваша привилегия:</b> " + (user.getPrivilege().equals(UserPrivilege.DEFAULT) ? "Клиент" : "Заказчик") + " \n\n"
                + "\uD83D\uDCB0 <b>Ваш баланс:</b> " + user.getBalance() + "$\n\n"
                + "\uD83D\uDC9E <b>Вы с нами с</b> " + formattedRegisteredAtDate)
                .chatId(chatId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    private void sendGenerateMessage(long chatId, User user){
        if (user.getPrivilege().equals(UserPrivilege.DEFAULT)){
            handleCheckPrivilege(chatId);
            return;
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToMyProfileButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.START_GENERATING_TAG_CALLBACK_DATA + " " + GenerateMethod.AI)
                .text("\uD83E\uDD16 Генерация через ИИ")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToMyProfileButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        SendMessage message = SendMessage.builder()
                .text("\uD83C\uDFF7 <b>Генерация тегов: Ключ к Эффективному Продвижению!</b>\n\n"
                        +                                                                                                                                                                                                              "Генерация правильных тегов - это ключевой элемент успешного продвижения. \uD83D\uDE80\n\n"
                        + "\uD83D\uDCBC <b>Почему это важно?</b>\n\n"
                        + "<i>Увеличивает видимость вашего контента</i>\n"
                        + "<i>Повышает релевантность поисковых запросов</i>\n"
                        + "<i>Привлекает целевую аудиторию</i>\n\n"
                        + "\uD83C\uDF10 <b>Как мы это делаем:</b>\n\n"
                        + "<i>Генерация через передовые методы искусственного интеллекта</i>\n"
                        + "<i>Анализ популярных запросов и трендов</i>\n"
                        + "<i>Уникальные теги для вашего уникального контента</i>\n\n"
                        + "<b>Запускайте генерацию тегов и поднимайтесь на новый уровень в мире цифрового маркетинга!</b> \uD83D\uDCA1✨")
                .chatId(chatId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    private void sendCheckMessage(long chatId, User user){
        if (user.getPrivilege().equals(UserPrivilege.DEFAULT)){
            handleCheckPrivilege(chatId);
            return;
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToMyProfileButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.START_CHECK_CALLBACK_DATA)
                .text("\uD83D\uDD0D Проверить позицию")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToMyProfileButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        SendMessage message = SendMessage.builder()
                .text("\uD83D\uDD0D <b>Проверка Позиции в Поисковиках!</b>\n\n"
                        + "Понимаем, насколько важно знать, где ваш сайт располагается в поисковых результатах. \uD83C\uDF10\n\n"
                        + "\uD83D\uDCBC <b>Почему это важно?</b>\n\n"
                        + "<i>Оценка эффективности SEO</i>\n"
                        + "<i>Выявление потенциала для улучшений</i>\n"
                        + "<i>Слежение за конкурентами</i>\n\n"
                        + "\uD83D\uDD0E <b>Мы проведим проверку позиции сразу в двух поисковиках: Google Search и Yandex Search.</b> \uD83D\uDCBB")
                .chatId(chatId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }

    }

    private void sendAdminMessage(long chatId, User user) {
        if (!user.getRole().equals(UserRole.ADMIN)) {
            SendMessage message = SendMessage.builder()
                    .text("♨\uFE0F <b>У вас не хватает прав</b>")
                    .chatId(chatId)
                    .parseMode("html")
                    .build();

            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.warn(e.getMessage());
            }
            return;
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton dispatchButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_DISPATCH_CALLBACK_DATA)
                .text("\uD83D\uDCE2 Рассылка")
                .build();

        InlineKeyboardButton addBalanceButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_ADD_BALANCE_CALLBACK_DATA)
                .text("➕ Добавить баланс")
                .build();
        InlineKeyboardButton removeBalanceButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_REMOVE_BALANCE_CALLBACK_DATA)
                .text("➖ Снять баланс")
                .build();

        InlineKeyboardButton addAdminButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_ADD_ADMIN_CALLBACK_DATA)
                .text("✖\uFE0F Добавить админа")
                .build();
        InlineKeyboardButton removeAdminButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_REMOVE_ADMIN_CALLBACK_DATA)
                .text("➗ Снять админа")
                .build();

        InlineKeyboardButton addBanButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_ADD_BAN_CALLBACK_DATA)
                .text("◼\uFE0F Забанить пользователя")
                .build();
        InlineKeyboardButton removeBanButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_REMOVE_BAN_CALLBACK_DATA)
                .text("◽\uFE0F Разбанить пользователя")
                .build();

        InlineKeyboardButton usersInfoButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_GET_USERS_DATA_CALLBACK_DATA)
                .text("\uD83D\uDCC1 Данные пользователей")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow3 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow4 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow5 = new ArrayList<>();

        keyboardButtonsRow1.add(dispatchButton);

        keyboardButtonsRow2.add(addBalanceButton);
        keyboardButtonsRow2.add(removeBalanceButton);

        keyboardButtonsRow3.add(addAdminButton);
        keyboardButtonsRow3.add(removeAdminButton);

        keyboardButtonsRow4.add(addBanButton);
        keyboardButtonsRow4.add(removeBanButton);

        keyboardButtonsRow5.add(usersInfoButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);
        rowList.add(keyboardButtonsRow3);
        rowList.add(keyboardButtonsRow4);
        rowList.add(keyboardButtonsRow5);

        inlineKeyboardMarkup.setKeyboard(rowList);

        SendMessage message = SendMessage.builder()
                .text("♨\uFE0F <b>Админ панель</b>\n\n"
                        + "«Здесь есть все»")
                .chatId(chatId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }

    }

    private void editAdminMessage(long chatId, int messageId, User user) {
        if (!user.getRole().equals(UserRole.ADMIN)) {
            EditMessageText editMessageText = EditMessageText.builder()
                    .text("♨\uFE0F <b>У вас не хватает прав</b>")
                    .chatId(chatId)
                    .messageId(messageId)
                    .parseMode("html")
                    .build();

            try {
                execute(editMessageText);
            } catch (TelegramApiException e) {
                log.warn(e.getMessage());
            }
            return;
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton dispatchButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_DISPATCH_CALLBACK_DATA)
                .text("\uD83D\uDCE2 Рассылка")
                .build();

        InlineKeyboardButton addBalanceButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_ADD_BALANCE_CALLBACK_DATA)
                .text("➕ Добавить баланс")
                .build();
        InlineKeyboardButton removeBalanceButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_REMOVE_BALANCE_CALLBACK_DATA)
                .text("➖ Снять баланс")
                .build();

        InlineKeyboardButton addAdminButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_ADD_ADMIN_CALLBACK_DATA)
                .text("✖\uFE0F Добавить админа")
                .build();
        InlineKeyboardButton removeAdminButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_REMOVE_ADMIN_CALLBACK_DATA)
                .text("➗ Снять админа")
                .build();

        InlineKeyboardButton addBanButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_ADD_BAN_CALLBACK_DATA)
                .text("◼\uFE0F Забанить пользователя")
                .build();
        InlineKeyboardButton removeBanButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_REMOVE_BAN_CALLBACK_DATA)
                .text("◽\uFE0F Разбанить пользователя")
                .build();

        InlineKeyboardButton usersInfoButton = InlineKeyboardButton.builder()
                .callbackData(AdminCallback.ADMIN_GET_USERS_DATA_CALLBACK_DATA)
                .text("\uD83D\uDCC1 Данные пользователей")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow3 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow4 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow5 = new ArrayList<>();

        keyboardButtonsRow1.add(dispatchButton);

        keyboardButtonsRow2.add(addBalanceButton);
        keyboardButtonsRow2.add(removeBalanceButton);

        keyboardButtonsRow3.add(addAdminButton);
        keyboardButtonsRow3.add(removeAdminButton);

        keyboardButtonsRow4.add(addBanButton);
        keyboardButtonsRow4.add(removeBanButton);

        keyboardButtonsRow5.add(usersInfoButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);
        rowList.add(keyboardButtonsRow3);
        rowList.add(keyboardButtonsRow4);
        rowList.add(keyboardButtonsRow5);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("♨\uFE0F <b>Админ панель</b>\n\n"
                        + "«Здесь есть все»")
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }

    private void editPumpMessage(long chatId, int messageId, User user) {
        if (user.getPrivilege().equals(UserPrivilege.DEFAULT)){
            handleCheckPrivilege(chatId);
            return;
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton pumpButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.START_PUMP_CALLBACK_DATA)
                .text("\uD83D\uDCE5 Выкачать и уникализировать")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(pumpButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("\uD83D\uDCE5 <b>Выкачать и Уникализировать!</b>\n\n"
                        + "Отличный выбор! Этот инструмент обеспечит вам целый ряд преимуществ:\n\n"
                        + "\uD83D\uDE80 <b>Почему это важно?</b>\n\n"
                        + "<i>Быстрое скачивание вашего лендинга</i>\n"
                        + "<i>Уникальная уникализация контента</i>\n"
                        + "<i>Повышение SEO-параметров</i>\n\n"
                        + "<b>Давайте вместе создадим контент, который привлечет внимание и поднимет ваш сайт на новый уровень!</b> \uD83D\uDCA1\uD83D\uDCBB")
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }

    private void sendPumpMessage(long chatId, User user) {
        if (user.getPrivilege().equals(UserPrivilege.DEFAULT)){
            handleCheckPrivilege(chatId);
            return;
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton pumpButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.START_PUMP_CALLBACK_DATA)
                .text("\uD83D\uDCE5 Выкачать и уникализировать")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(pumpButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        SendMessage message = SendMessage.builder()
                .text("\uD83D\uDCE5 <b>Выкачать и Уникализировать!</b>\n\n"
                        + "Отличный выбор! Этот инструмент обеспечит вам целый ряд преимуществ:\n\n"
                        + "\uD83D\uDE80 <b>Почему это важно?</b>\n\n"
                        + "<i>Быстрое скачивание вашего лендинга</i>\n"
                        + "<i>Уникальная уникализация контента</i>\n"
                        + "<i>Повышение SEO-параметров</i>\n\n"
                        + "<b>Давайте вместе создадим контент, который привлечет внимание и поднимет ваш сайт на новый уровень!</b> \uD83D\uDCA1\uD83D\uDCBB")
                .chatId(chatId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }

    private void sendRulesMessage(long chatId) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton zelenkaButton = InlineKeyboardButton.builder()
                .url("https://t.me/supseo")
                .text("\uD83C\uDF10 Наша тема на Zelenka")
                .build();

        InlineKeyboardButton supportButton = InlineKeyboardButton.builder()
                .url("https://t.me/supseo")
                .text("\uD83D\uDEE0\uFE0F Связь с администрацией")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();

        keyboardButtonsRow1.add(zelenkaButton);
        keyboardButtonsRow2.add(supportButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);

        inlineKeyboardMarkup.setKeyboard(rowList);

        SendMessage message = SendMessage.builder()
                .text("\uD83D\uDCCC <b>Правила Использования</b>\n\n"
                        + "<b>Возврат средств:</b> Мы возвращаем средства только в случае наших ошибок. Бекапов нет из-за возможных проблем с поднятием сайта.\n\n"
                        + "<b>Проблемы с сайтом:</b> Если сайт не встал, мы содействуем поднятию SEO. В случае технических затруднений, решение наших специалистов всегда на вашей стороне.\n\n"
                        + "<b>Блокировка пользователей:</b> Мы оставляем за собой право блокировать пользователей без разбирательств и объяснений в случае нарушения правил.\n\n"
                        + "<b>Уважение к администрации:</b> Неуважительное общение с администрацией может привести к блокировке аккаунта.\n\n"
                        + "<b>Использование багов:</b> Запрещено использование любых багов в корыстных целях под угрозой блокировки.\n\n"
                        + "<b>Перевод средств:</b> В случае ошибки при переводе средств, администрация не несет ответственности.\n\n"
                        + "<b>Отказ администрации:</b> Администрация вправе отказать в обслуживании без объяснения причин.\n\n"
                        + "\uD83C\uDF10 <b>Дополнительная информация:</b>\n\n"
                        + "<a href=\"https://t.me/supseo\">Наша тема на Zelenka</a>\n"
                        + "<a href=\"https://t.me/supseo\">Связь с администрацией</a>\n\n"
                        + "\uD83D\uDE80 <b>Мы предоставляем инструменты для развития вашего сайта через улучшение позиций в поисковых результатах. Однако, мы не гарантируем топовые результаты, так как много факторов может повлиять на успех. Вместе мы создаем возможности для роста!</b> \uD83D\uDCBB✨"
                )
                .chatId(chatId)
                .parseMode("html")
                .replyMarkup(inlineKeyboardMarkup)
                .disableWebPagePreview(true)
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }

    private void editCheckMessage(long chatId, int messageId, User user) {
        if (user.getPrivilege().equals(UserPrivilege.DEFAULT)){
            handleCheckPrivilege(chatId);
            return;
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToMyProfileButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.START_CHECK_CALLBACK_DATA)
                .text("\uD83D\uDD0D Проверить позицию")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToMyProfileButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("\uD83D\uDD0D <b>Проверка Позиции в Поисковиках!</b>\n\n"
                        + "Понимаем, насколько важно знать, где ваш сайт располагается в поисковых результатах. \uD83C\uDF10\n\n"
                        + "\uD83D\uDCBC <b>Почему это важно?</b>\n\n"
                        + "<i>Оценка эффективности SEO</i>\n"
                        + "<i>Выявление потенциала для улучшений</i>\n"
                        + "<i>Слежение за конкурентами</i>\n\n"
                        + "\uD83D\uDD0E <b>Мы проведим проверку позиции сразу в двух поисковиках: Google Search и Yandex Search.</b> \uD83D\uDCBB")
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }

    private void editGenerateMessage(long chatId, int messageId){
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToMyProfileButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.START_GENERATING_TAG_CALLBACK_DATA + " " + GenerateMethod.AI)
                .text("\uD83E\uDD16 Генерация через ИИ")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToMyProfileButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessage = EditMessageText.builder()
                .text("\uD83C\uDFF7 <b>Генерация тегов: Ключ к Эффективному Продвижению!</b>\n\n"
                        +                                                                                                                                                                                                              "Генерация правильных тегов - это ключевой элемент успешного продвижения. \uD83D\uDE80\n\n"
                        + "\uD83D\uDCBC <b>Почему это важно?</b>\n\n"
                        + "<i>Увеличивает видимость вашего контента</i>\n"
                        + "<i>Повышает релевантность поисковых запросов</i>\n"
                        + "<i>Привлекает целевую аудиторию</i>\n\n"
                        + "\uD83C\uDF10 <b>Как мы это делаем:</b>\n\n"
                        + "<i>Генерация через передовые методы искусственного интеллекта</i>\n"
                        + "<i>Анализ популярных запросов и трендов</i>\n"
                        + "<i>Уникальные теги для вашего уникального контента</i>\n\n"
                        + "<b>Запускайте генерацию тегов и поднимайтесь на новый уровень в мире цифрового маркетинга!</b> \uD83D\uDCA1✨")
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(editMessage);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    private void editMyProfileMessage(long chatId, int messageId, User user){
        Date registeredAtDate = new Date(user.getRegisteredAt());
        SimpleDateFormat registeredDate = new SimpleDateFormat("dd.MM.yyyy");

        String formattedRegisteredAtDate = registeredDate.format(registeredAtDate);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton replenishmentButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.REPLENISHMENT_CALLBACK_DATA)
                .text("\uD83D\uDCB0 Пополнить баланс")
                .build();

        InlineKeyboardButton faqButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.FAQ_CALLBACK_DATA)
                .text("\uD83D\uDCDA Как получить привилегию?")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();

        keyboardButtonsRow1.add(replenishmentButton);
        keyboardButtonsRow2.add(faqButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("\uD83D\uDC64 <b>Мой профиль</b>\n\n"
                        + "\uD83C\uDD94 <b>Ваш идентификатор:</b> <code>" + user.getTelegramChatId() + "</code>\n"
                        + "\uD83D\uDC51 <b>Ваша привилегия:</b> " + (user.getPrivilege().equals(UserPrivilege.DEFAULT) ? "Клиент" : "Заказчик") + " \n\n"
                        + "\uD83D\uDCB0 <b>Ваш баланс:</b> " + user.getBalance() + " $\n\n"
                        + "\uD83D\uDC9E <b>Вы с нами с</b> " + formattedRegisteredAtDate)
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    private ReplyKeyboardMarkup getDefaultReplyKeyboardMarkup(User user){
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();

        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        KeyboardButton boostButton = new KeyboardButton("\uD83D\uDCC8 Поднять активность");
        KeyboardButton tagButton = new KeyboardButton("\uD83C\uDFF7 Генерация тегов");
        KeyboardButton checkButton = new KeyboardButton("\uD83D\uDD0D Проверка позиции");
        KeyboardButton downloadButton = new KeyboardButton("\uD83D\uDCE5 Выкачать и уникализировать");
        KeyboardButton profileButton = new KeyboardButton("\uD83D\uDC64 Мой профиль");
        KeyboardButton infoButton = new KeyboardButton("\uD83D\uDCCC Правила");

        KeyboardRow keyboardRow1 = new KeyboardRow();
        KeyboardRow keyboardRow2 = new KeyboardRow();
        KeyboardRow keyboardRow3 = new KeyboardRow();
        KeyboardRow keyboardRow4 = new KeyboardRow();

        keyboardRow1.add(boostButton);

        keyboardRow2.add(tagButton);
        keyboardRow2.add(checkButton);

        keyboardRow3.add(downloadButton);

        keyboardRow4.add(profileButton);
        keyboardRow4.add(infoButton);

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        keyboardRows.add(keyboardRow1);
        keyboardRows.add(keyboardRow2);
        keyboardRows.add(keyboardRow3);
        keyboardRows.add(keyboardRow4);

        if (user.getRole().equals(UserRole.ADMIN)) {
            KeyboardButton adminButton = new KeyboardButton("♨\uFE0F Админ панель");

            KeyboardRow keyboardRow5 = new KeyboardRow();

            keyboardRow5.add(adminButton);

            keyboardRows.add(keyboardRow5);
        }

        keyboardMarkup.setKeyboard(keyboardRows);

        return keyboardMarkup;
    }

    private void editFaqMessage(long chatId, int messageId){
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToMyProfileButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_MY_PROFILE_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToMyProfileButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("\uD83D\uDCDA <b>FAQ: Как получить привилегию?</b>\n" +
                        "\n" +
                        "Чтобы получить привилегию \"Заказчик\" и открыть доступ к дополнительным инструментам, выполните следующее:\n" +
                        "\n" +
                        "\uD83D\uDCB0 <b>Пополните Баланс:</b> Пополните свой баланс на сумму не менее 100$. Это количество будет аккумулироваться за все время использования.\n" +
                        "\n" +
                        "\uD83C\uDD99 <b>Привилегия \"Заказчик\":</b> При достижении суммы в 100$ вы автоматически получите привилегию \"Заказчик\".\n" +
                        "\n" +
                        "\uD83C\uDF81 <b>Доступные Инструменты для \"Заказчика\":</b>\n" +
                        "\n" +
                        "\uD83C\uDFF7 <b>Генерация тегов</b>\n" +
                        "\uD83D\uDD0D <b>Проверка позиции</b>\n" +
                        "\uD83D\uDCE5 <b>Выкачать и уникализировать</b>\n\n" +
                        "Теперь, как <b>\"Заказчик\"</b>, вам открыты все возможности для более эффективного управления и анализа вашего сайта. Наслаждайтесь привилегиями и достигайте новых высот в SEO! \uD83D\uDE80\uD83D\uDCBC"
                )
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    private void editReplenishmentMessage(long chatId, int messageId){
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton crystalPayButton = InlineKeyboardButton.builder()
                .callbackData(ReplenishmentCallback.REPLENISHMENT_METHOD_CALLBACK_DATA + " " + ReplenishmentMethod.CRYSTALPAY)
                .text("\uD83D\uDC8E CrystalPay")
                .build();

        InlineKeyboardButton backToMyProfileButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_MY_PROFILE_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow3 = new ArrayList<>();

        keyboardButtonsRow1.add(crystalPayButton);
        keyboardButtonsRow3.add(backToMyProfileButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);
        rowList.add(keyboardButtonsRow3);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("\uD83C\uDF10 <b>Шаг к успеху!</b>\n\n" +
                        "Мы почти готовы!\n\n" +
                        "\uD83D\uDE80 <b>Теперь выберите ваш любимый метод оплаты и начнем:</b>")
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    private void editBuyerBoostMessage(long chatId, int messageId, BoostSociety boostSociety){
        userBoostSteps.put(chatId, new Pair<>(1, BoostStep.builder().boostSociety(boostSociety).build()));

        String editMessageTextText = null;

        if (boostSociety.equals(BoostSociety.GOOGLE)){
            editMessageTextText = "\uD83D\uDE80 <b>Поднимите активность сейчас!</b>\n\n"
                    + "Теперь, когда вы выбрали <b>Google Search</b>, начнем мощную кампанию! \uD83C\uDF10\uD83D\uDD0D\n\n"
                    + "\uD83D\uDD0D <b>Google Search:</b> С этим выбором вы получаете:\n\n"
                    + "<i>Больший охват аудитории</i>\n"
                    + "<i>Мгновенные результаты</i>\n"
                    + "<i>Высокую точность таргетинга</i>\n\n"
                    + "\uD83D\uDCBC <b>Сколько людей вы хотите привлечь?</b> Укажите количество посетителей, которое вы хотите привлечь на свой сайт. Минимум: 20.000\n\n"
                    + "\uD83D\uDCB0 <b>Цена за посетителя:</b> Теперь давайте раскроем карты – всего 1$ за 1000 посетителей! Поднимите свою активность и добейтесь цифрового успеха с минимальными затратами! \uD83D\uDE80\uD83D\uDCA1";
        } else if (boostSociety.equals(BoostSociety.YANDEX)){
            editMessageTextText = "\uD83D\uDE80 <b>Поднимите активность сейчас!</b>\n\n"
                    + "Теперь, когда вы выбрали <b>Yandex Search</b>, начнем мощную кампанию! \uD83C\uDF10\uD83D\uDD0D\n\n"
                    + "\uD83D\uDD0D <b>Yandex Search:</b> С этим выбором вы получаете:\n\n"
                    + "<i>Локальной адаптацией</i>\n"
                    + "<i>Привлекательными рекламными решениями</i>\n"
                    + "<i>Повышенной видимостью в региональных поисковых запросах</i>\n\n"
                    + "\uD83D\uDCBC <b>Сколько людей вы хотите привлечь?</b> Укажите количество посетителей, которое вы хотите привлечь на свой сайт. Минимум: 20.000\n\n"
                    + "\uD83D\uDCB0 <b>Цена за посетителя:</b> Теперь давайте раскроем карты – всего 1$ за 1000 посетителей! Поднимите свою активность и добейтесь цифрового успеха с минимальными затратами! \uD83D\uDE80\uD83D\uDCA1";
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToBoostButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_BOOST_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToBoostButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text(editMessageTextText)
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    private void editBuyerCheckMessage(long chatId, int messageId){
        userCheckSteps.put(chatId, new Pair<>(1, null));

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToBoostButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_CHECK_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToBoostButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("\uD83C\uDF10 <b>Проверка Позиции в Поисковиках!</b>\n\n"
                        + "Отлично, мы готовы начать проверку позиции вашего сайта. \uD83D\uDE80\n\n"
                        + "\uD83D\uDCBB <b>Введите URL вашего сайта, и мы предоставим вам подробный отчет о текущей позиции в поисковых результатах.</b> \uD83D\uDD0D\n\n"
                        + "Давайте убедимся, что ваш сайт находится на верном пути к топовым позициям! \uD83C\uDF1F")
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }

    private void editBoostMessage(long chatId, int messageId){
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton googleSearchButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.START_BOOST_CALLBACK_DATA + " " + BoostSociety.GOOGLE)
                .text("\uD83D\uDD0E Google Search")
                .build();

        InlineKeyboardButton yandexSearchButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.START_BOOST_CALLBACK_DATA + " " + BoostSociety.YANDEX)
                .text("\uD83D\uDD0D Yandex Search")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(googleSearchButton);
        keyboardButtonsRow1.add(yandexSearchButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessage = EditMessageText.builder()
                .text("\uD83D\uDCC8 <b>Поднимите активность сейчас!</b>\n" +
                        "\n" +
                        "Теперь вы ближе к максимальной видимости. Наши передовые технологии искусственного интеллекта гарантируют точность и эффективность.\n" +
                        "\n" +
                        "✨ <b>Технологии ИИ:</b> Инструменты, основанные на передовых технологиях искусственного интеллекта, обеспечивают выдающиеся результаты.\n" +
                        "\n" +
                        "\uD83D\uDE80 <b>Выбор пути:</b> <i>Google Search</i> и <i>Yandex Search</i> – два мощных метода набора трафика. Максимизируйте видимость и достигайте успеха в цифровом маркетинге! \uD83D\uDCA1\uD83C\uDF10")
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(editMessage);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    private void handleCheckPrivilege(long chatId){
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton replenishmentButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.REPLENISHMENT_CALLBACK_DATA)
                .text("\uD83D\uDCB0 Пополнить баланс")
                .build();

        InlineKeyboardButton faqButton = InlineKeyboardButton.builder()
                .callbackData(UserCallback.FAQ_CALLBACK_DATA)
                .text("\uD83D\uDCDA Как получить привилегию?")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();

        keyboardButtonsRow1.add(replenishmentButton);
        keyboardButtonsRow2.add(faqButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);
        rowList.add(keyboardButtonsRow2);

        inlineKeyboardMarkup.setKeyboard(rowList);

        SendMessage message = SendMessage.builder()
                .text("\uD83C\uDF1F <b>Доступ к Инструментам</b>\n\n"
                        + "Извините, но для полноценного доступа к этим инструментам вам необходима привилегия \"Заказчик\". \uD83D\uDEE0\uFE0F\uD83D\uDCBC\n\n"
                        + "\uD83D\uDD10 <b>Как получить привилегию?</b> Пополните свой баланс на 100$, и вы получите статус \"Заказчик\" с набором эксклюзивных инструментов для успешного продвижения вашего сайта! \uD83D\uDE80\uD83D\uDCB0"
                )
                .chatId(chatId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(message);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }


    private void editBuyerGenerateMessage(long chatId, int messageId, GenerateMethod generateMethod){
        userGenerateSteps.put(chatId, generateMethod);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToGenerateButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_GENERATE_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToGenerateButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);


        EditMessageText editMessageText = EditMessageText.builder()
                .text("\uD83C\uDFF7 <b>Генерация тегов: Поехали в Детали!</b>\n\n"
                        + "Теперь, когда мы готовы к генерации тегов, <b>введите ключевое слово</b> для вашего уникального тега. Это будет ваш путь к максимальной релевантности и привлечению целевой аудитории! \uD83D\uDE80\uD83D\uDCA1")
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(inlineKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    public void handleCancelReplenishment(long chatId, int messageId){
        EditMessageText editMessageText = EditMessageText.builder()
                .text("❌ <b>Платеж успешно отменен</b>")
                .chatId(chatId)
                .messageId(messageId)
                .parseMode("html")
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    public void handleSuccessReplenishment(long chatId, int messageId, double replenishmentBalance){
        EditMessageText editMessageText = EditMessageText.builder()
                .text("✅ <b>Вы успешно пополнили свой баланс на</b> " + replenishmentBalance + "$")
                .chatId(chatId)
                .messageId(messageId)
                .parseMode("html")
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }
    }

    private void editAdminAddBalanceMessage(long chatId, int messageId, User user) {
        if (!user.getRole().equals(UserRole.ADMIN)) {
            return;
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToAdminButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("➕ <b>Введите айди пользователя в системе, которому вы хотите добавить баланс</b>")
                .chatId(chatId)
                .messageId(messageId)
                .parseMode("html")
                .replyMarkup(inlineKeyboardMarkup)
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }
    private void editAdminRemoveBalanceMessage(long chatId, int messageId, User user) {
        if (!user.getRole().equals(UserRole.ADMIN)) {
            return;
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToAdminButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("➖ <b>Введите айди пользователя в системе, которому вы хотите снять баланс</b>")
                .chatId(chatId)
                .messageId(messageId)
                .parseMode("html")
                .replyMarkup(inlineKeyboardMarkup)
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }

    private void editAdminAddAdminMessage(long chatId, int messageId, User user) {
        if (!user.getRole().equals(UserRole.ADMIN)) {
            return;
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToAdminButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("✖\uFE0F <b>Введите айди пользователя в системе, которому вы хотите добавить админа</b>")
                .chatId(chatId)
                .messageId(messageId)
                .parseMode("html")
                .replyMarkup(inlineKeyboardMarkup)
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }

    private void editAdminRemoveAdminMessage(long chatId, int messageId, User user) {
        if (!user.getRole().equals(UserRole.ADMIN)) {
            return;
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToAdminButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("➗ <b>Введите айди пользователя в системе, которому вы хотите снять админа</b>")
                .chatId(chatId)
                .messageId(messageId)
                .parseMode("html")
                .replyMarkup(inlineKeyboardMarkup)
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }

    private void editAdminAddBanMessage(long chatId, int messageId, User user) {
        if (!user.getRole().equals(UserRole.ADMIN)) {
            return;
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToAdminButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("◼\uFE0F <b>Введите айди пользователя в системе, которого вы хотите забанить</b>")
                .chatId(chatId)
                .messageId(messageId)
                .parseMode("html")
                .replyMarkup(inlineKeyboardMarkup)
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }

    private void editAdminRemoveBanMessage(long chatId, int messageId, User user) {
        if (!user.getRole().equals(UserRole.ADMIN)) {
            return;
        }

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToAdminButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToAdminButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        inlineKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("◽\uFE0F <b>Введите айди пользователя в системе, которого вы хотите разбанить</b>")
                .chatId(chatId)
                .messageId(messageId)
                .parseMode("html")
                .replyMarkup(inlineKeyboardMarkup)
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }

    private void editAdminGetUsersDataMessage(long chatId, int messageId, User user) {
        if (!user.getRole().equals(UserRole.ADMIN)) {
            return;
        }

        EditMessageText editMessageText = EditMessageText.builder()
                .text("⏳ <b>Загрузка...</b>")
                .chatId(chatId)
                .messageId(messageId)
                .parseMode("html")
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e){
            log.warn(e.getMessage());
        }

        Iterable<User> allUsers = userRepository.findAll();

        File allUsersTempFile = null;

        try {
            allUsersTempFile = File.createTempFile("tempUsersData", ".txt");
        } catch (IOException e) {
            log.warn(e.getMessage());
        }

        try (FileWriter writer = new FileWriter(allUsersTempFile)) {
            for (User key : allUsers) {
                String role = key.getRole().equals(UserRole.ADMIN) ? "Админ" : "Пользователь";
                String privilege = key.getPrivilege().equals(UserPrivilege.CUSTOMER) ? "Заказчик" : "Клиент";
                String isAccountNonLocked = !key.getIsAccountNonLocked() ? "Да" : "Нет";
                String regData = new SimpleDateFormat("dd.MM.yyyy в HH:mm").format(new Date(key.getRegisteredAt()));

                String userData = "_____________________________________\n"
                        + "ID: " + key.getId() + "\n"
                        + "Telegram CHAT-ID: " + key.getTelegramChatId() + "\n"
                        + "Баланс: " + key.getBalance() + "$\n"
                        + "Роль: " + role + "\n"
                        + "Привилегия: " + privilege + "\n"
                        + "Заблокирован: " + isAccountNonLocked + "\n"
                        + "Дата регистрации: " + regData + "\n";

                writer.write(userData);
            }
        } catch (IOException e) {
            log.warn(e.getMessage());
        }

        InputFile allUsersInputFile = new InputFile(allUsersTempFile);

        DeleteMessage deleteMessage = DeleteMessage.builder()
                .chatId(chatId)
                .messageId(messageId)
                .build();

        InlineKeyboardMarkup backKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToAdminFromGetUsersButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_ADMIN_FROM_GET_USERS_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToAdminFromGetUsersButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        backKeyboardMarkup.setKeyboard(rowList);

        SendDocument document = SendDocument.builder()
                .caption("\uD83D\uDCC1 <b>Данные пользователей</b>")
                .chatId(chatId)
                .document(allUsersInputFile)
                .replyMarkup(backKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(deleteMessage);
            execute(document);

            allUsersTempFile.delete();
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }

    private void editAdminDispatchMessage(long chatId, int messageId, User user) {
        if (!user.getRole().equals(UserRole.ADMIN)) {
            return;
        }

        InlineKeyboardMarkup backKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToAdminFromGetUsersButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_ADMIN_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToAdminFromGetUsersButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        backKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("\uD83D\uDCE2 <b>Введите сообщение, которое разошлется всем пользователям</b>")
                .chatId(chatId)
                .messageId(messageId)
                .replyMarkup(backKeyboardMarkup)
                .parseMode("html")
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }
    private void editBuyerPumpMessage(long chatId, int messageId) {
        userPumpSteps.add(chatId);

        InlineKeyboardMarkup backKeyboardMarkup = new InlineKeyboardMarkup();

        InlineKeyboardButton backToAdminFromGetUsersButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_PUMP_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();

        keyboardButtonsRow1.add(backToAdminFromGetUsersButton);

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>();

        rowList.add(keyboardButtonsRow1);

        backKeyboardMarkup.setKeyboard(rowList);

        EditMessageText editMessageText = EditMessageText.builder()
                .text("\uD83D\uDCE5 <b>Выкачать и Уникализировать!</b>\n\n"
                        + "<b>Прекрасно! Чтобы начать процесс, пожалуйста, предоставьте URL вашего лендинга.</b> \uD83C\uDF10\uD83D\uDCBB")
                .chatId(chatId)
                .messageId(messageId)
                .parseMode("html")
                .replyMarkup(backKeyboardMarkup)
                .build();

        try {
            execute(editMessageText);
        } catch (TelegramApiException e) {
            log.warn(e.getMessage());
        }
    }

}
