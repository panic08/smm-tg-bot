package ru.marthastudios.smmtgbot.bot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.marthastudios.smmtgbot.api.LolzteamApi;
import ru.marthastudios.smmtgbot.callback.BackCallback;
import ru.marthastudios.smmtgbot.callback.ReplenishmentCallback;
import ru.marthastudios.smmtgbot.callback.UserCallback;
import ru.marthastudios.smmtgbot.enums.ReplenishmentMethod;
import ru.marthastudios.smmtgbot.enums.UserPrivilege;
import ru.marthastudios.smmtgbot.enums.UserRole;
import ru.marthastudios.smmtgbot.model.User;
import ru.marthastudios.smmtgbot.pojo.Pair;
import ru.marthastudios.smmtgbot.property.LolzteamProperty;
import ru.marthastudios.smmtgbot.property.TelegramBotProperty;
import ru.marthastudios.smmtgbot.repository.UserRepository;
import ru.marthastudios.smmtgbot.service.ReplenishmentService;

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
    private LolzteamApi lolzteamApi;
    @Autowired
    private LolzteamProperty lolzteamProperty;
    @Autowired
    @Lazy
    private ReplenishmentService replenishmentService;
    private static final Map<Long, ReplenishmentMethod> userReplenishmentSteps = new HashMap<>();
    public static final Map<Long, Integer> replenishmentCache = new HashMap<>();


    public TelegramBot(TelegramBotProperty telegramBotProperty) {
        this.telegramBotProperty = telegramBotProperty;
        List<BotCommand> listOfCommands = new ArrayList<>();

        listOfCommands.add(new BotCommand("/start", "\uD83D\uDCD6 Главное меню"));
        listOfCommands.add(new BotCommand("/myprofile", "\uD83D\uDC64 Мой профиль"));
        listOfCommands.add(new BotCommand("/boost", "\uD83D\uDCC8 Поднять активность"));
        listOfCommands.add(new BotCommand("/generate", "\uD83C\uDFF7 Генерация тегов"));
        listOfCommands.add(new BotCommand("/analyze", "\uD83D\uDCCA Анализировать"));
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
                            .privilege(UserPrivilege.DEFAULT)
                            .registeredAt(System.currentTimeMillis())
                            .build());
                }

                switch (text){
                    case "/start" -> {
                        sendStartMessage(chatId);
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
                }

                if (userReplenishmentSteps.get(chatId) != null){
                    if (replenishmentCache.get(chatId) != null){
                        handleCancelReplenishment(chatId, replenishmentCache.get(chatId));
                    }

                    ReplenishmentMethod replenishmentMethod = userReplenishmentSteps.get(chatId);

                    double amount = Double.parseDouble(text);

                    if (amount < 1){
                        return;
                    }

                    String replenishmentLink = null;
                    String replenishmentSecret = UUID.randomUUID().toString();

                    if (replenishmentMethod.equals(ReplenishmentMethod.ZELENKA)){
                        replenishmentLink = String.format(lolzteamApi.PAYMENT_URL_FORMAT, lolzteamProperty.getNickname(), replenishmentSecret, amount);
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
                                    + "Отличный выбор! Ты выбрал способ оплаты " +  (replenishmentMethod.equals(ReplenishmentMethod.ZELENKA) ? "<b>Zelenka guru</b>" : "<b>Cryptomus</b>") +", а сумма в <b>" + amount + "</b>$ - просто идеальная!\n\n" +
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

                    User finalUser = user;
                    Integer finalSendedMessageId = sendedMessageId;

                    new Thread(() -> {
                        replenishmentCache.put(chatId, finalSendedMessageId);
                        replenishmentService.handleLolzteamReplenishment(chatId, replenishmentSecret, finalUser);
                    }).start();
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

                    case BackCallback.BACK_TO_MY_PROFILE_CALLBACK_DATA -> {
                        editMyProfileMessage(chatId, messageId, user);
                        return;
                    }
                    case BackCallback.BACK_TO_REPLENISHMENT_CALLBACK_DATA -> {
                        userReplenishmentSteps.remove(chatId);

                        editReplenishmentMessage(chatId, messageId);
                        return;
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
            }
        }).start();
    }

    private void sendStartMessage(long chatId){
        SendMessage message = SendMessage.builder()
                .text("\uD83C\uDF1F <b>Я - бот SmmSearch, ваш верный гид в мире эффективного SMM.</b>\n" +
                        "\n" +
                        "\uD83D\uDE80 <b>Погружайтесь в уникальные возможности моих инструментов:</b> <i>генератор ключевых слов, мощные методы поднятия сайта, точный мониторинг позиции в поисковых результатах, выкачка лендингов с последующей уникализацией, и, конечно же, глубокий анализ SEO для достижения выдающихся результатов.</i> \n" +
                        "\n" +
                        "\uD83D\uDEE0 <b>Используйте мои передовые инструменты и давайте вместе построим стратегию, которая приведет ваш проект к вершинам успеха!</b> \n" +
                        "\n" +
                        "\uD83D\uDCA1✨ <b>Не теряйте времени, начинайте свой путь к успешному SMM прямо сейчас!</b> \uD83D\uDE80\uD83C\uDF10")
                .chatId(chatId)
                .replyMarkup(getDefaultReplyKeyboardMarkup())
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

        //todo
        InlineKeyboardButton googleSearchButton = InlineKeyboardButton.builder()
                .callbackData("skuf")
                .text("\uD83D\uDD0E Google Search")
                .build();

        InlineKeyboardButton yandexSearchButton = InlineKeyboardButton.builder()
                .callbackData("skuf")
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

    private ReplyKeyboardMarkup getDefaultReplyKeyboardMarkup(){
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();

        keyboardMarkup.setSelective(true);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);

        KeyboardButton boostButton = new KeyboardButton("\uD83D\uDCC8 Поднять активность");
        KeyboardButton tagButton = new KeyboardButton("\uD83C\uDFF7 Генерация тегов");
        KeyboardButton analysisButton = new KeyboardButton("\uD83D\uDCCA Анализировать");
        KeyboardButton checkButton = new KeyboardButton("\uD83D\uDD0D Проверка позиции");
        KeyboardButton downloadButton = new KeyboardButton("\uD83D\uDCE5 Выкачать и уникализировать");
        KeyboardButton profileButton = new KeyboardButton("\uD83D\uDC64 Мой профиль");

        KeyboardRow keyboardRow1 = new KeyboardRow();
        KeyboardRow keyboardRow2 = new KeyboardRow();
        KeyboardRow keyboardRow3 = new KeyboardRow();
        KeyboardRow keyboardRow4 = new KeyboardRow();
        KeyboardRow keyboardRow5 = new KeyboardRow();

        keyboardRow1.add(boostButton);

        keyboardRow2.add(tagButton);
        keyboardRow2.add(analysisButton);

        keyboardRow3.add(checkButton);

        keyboardRow4.add(downloadButton);

        keyboardRow5.add(profileButton);

        List<KeyboardRow> keyboardRows = new ArrayList<>();

        keyboardRows.add(keyboardRow1);
        keyboardRows.add(keyboardRow2);
        keyboardRows.add(keyboardRow3);
        keyboardRows.add(keyboardRow4);
        keyboardRows.add(keyboardRow5);

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
                        "\uD83D\uDCCA <b>Анализировать</b>\n" +
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

        InlineKeyboardButton zelenkaGuruButton = InlineKeyboardButton.builder()
                .callbackData(ReplenishmentCallback.REPLENISHMENT_METHOD_CALLBACK_DATA + " " + ReplenishmentMethod.ZELENKA)
                .text("Zelenka.Guru")
                .build();

        InlineKeyboardButton cryptomusButton = InlineKeyboardButton.builder()
                .callbackData(ReplenishmentCallback.REPLENISHMENT_METHOD_CALLBACK_DATA + " " + ReplenishmentMethod.CRYPTOMUS)
                .text("Cryptomus")
                .build();

        InlineKeyboardButton backToMyProfileButton = InlineKeyboardButton.builder()
                .callbackData(BackCallback.BACK_TO_MY_PROFILE_CALLBACK_DATA)
                .text("◀\uFE0F Назад")
                .build();

        List<InlineKeyboardButton> keyboardButtonsRow1 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow2 = new ArrayList<>();
        List<InlineKeyboardButton> keyboardButtonsRow3 = new ArrayList<>();

        keyboardButtonsRow1.add(zelenkaGuruButton);
        keyboardButtonsRow2.add(cryptomusButton);
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

    public void handleCancelReplenishment(long chatId, int messageId){
        replenishmentCache.remove(chatId);

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
        replenishmentCache.remove(chatId);

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
}
