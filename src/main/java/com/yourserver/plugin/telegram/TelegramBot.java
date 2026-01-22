package com.yourserver.plugin.telegram;

import com.yourserver.plugin.Main;
import com.yourserver.plugin.database.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;

import java.util.*;

public class TelegramBot extends TelegramLongPollingBot {
    private final Main plugin;
    private final String botUsername;
    
    // Хранилище состояния анкет
    private final Map<Long, String> userStates = new HashMap<>();
    private final Map<Long, Map<String, String>> userAnketaData = new HashMap<>();
    
    // ID администратора для отправки заявок (ваш ID)
    private static final long ADMIN_ID = 6690949251L;
    
    // Состояния анкеты
    private static final String STATE_ANKETA_NICKNAME = "anketa_nickname";
    private static final String STATE_ANKETA_AGE = "anketa_age";
    private static final String STATE_ANKETA_PLAYTIME = "anketa_playtime";
    private static final String STATE_ANKETA_MOTIVATION = "anketa_motivation";
    private static final String STATE_ANKETA_ABOUT = "anketa_about";
    
    public TelegramBot(String token, String botUsername, Main plugin) {
        super(token);
        this.plugin = plugin;
        this.botUsername = botUsername;
        registerBot();
    }
    
    private void registerBot() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);
            plugin.getLogger().info("Telegram бот @" + botUsername + " запущен успешно!");
        } catch (TelegramApiException e) {
            plugin.getLogger().severe("Ошибка запуска бота: " + e.getMessage());
        }
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            // Обработка нажатий на кнопки
            handleCallbackQuery(update.getCallbackQuery().getData(), update.getCallbackQuery().getFrom().getId(), update);
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            long chatId = update.getMessage().getChatId();
            String text = update.getMessage().getText().trim();
            
            // Проверяем, находится ли пользователь в процессе заполнения анкеты
            if (userStates.containsKey(chatId)) {
                handleAnketaResponse(chatId, text, update);
                return;
            }
            
            // Обработка команд
            if (text.equals("/start")) {
                sendWelcomeMessage(chatId);
            } else if (text.startsWith("/register")) {
                handleRegistration(chatId, text);
            } else if (text.equals("/anketa") || text.equals("/анкета")) {
                startAnketa(chatId);
            }
        }
    }
    
    // ==================== ОБРАБОТКА АНКЕТ ====================
    
    private void startAnketa(long chatId) {
        // Проверяем, не в вайт-листе ли уже пользователь
        PlayerData playerData = plugin.getDatabaseManager().getPlayerByTelegramId(chatId);
        if (playerData != null) {
            sendMessageToChat(chatId, "✅ Вы уже в вайт-листе! Ваш аккаунт Minecraft: " + playerData.getUsername(), false);
            return;
        }
        
        // Начинаем анкету
        userStates.put(chatId, STATE_ANKETA_NICKNAME);
        userAnketaData.put(chatId, new HashMap<>());
        
        // Сохраняем информацию о пользователе
        userAnketaData.get(chatId).put("tg_id", String.valueOf(chatId));
        userAnketaData.get(chatId).put("tg_username", getUserName(update) != null ? getUserName(update) : "Не указан");
        
        // Первый вопрос
        sendMessageToChat(chatId, "📝 *Начинаем заполнение анкеты*\n\n" +
                "Шаг 1 из 5\n" +
                "Введите ваш игровой никнейм (как на сервере Minecraft):", true);
    }
    
    private void handleAnketaResponse(long chatId, String text, Update update) {
        String state = userStates.get(chatId);
        
        switch (state) {
            case STATE_ANKETA_NICKNAME:
                handleNicknameStep(chatId, text);
                break;
            case STATE_ANKETA_AGE:
                handleAgeStep(chatId, text);
                break;
            case STATE_ANKETA_PLAYTIME:
                handlePlaytimeStep(chatId, text);
                break;
            case STATE_ANKETA_MOTIVATION:
                handleMotivationStep(chatId, text);
                break;
            case STATE_ANKETA_ABOUT:
                handleAboutStep(chatId, text);
                break;
        }
    }
    
    private void handleNicknameStep(long chatId, String nickname) {
        if (nickname.length() < 2 || nickname.length() > 20) {
            sendMessageToChat(chatId, "❌ Никнейм должен быть от 2 до 20 символов. Попробуйте еще раз:", false);
            return;
        }
        
        // Проверяем, не занят ли никнейм
        PlayerData existingPlayer = plugin.getDatabaseManager().getPlayerByUsername(nickname);
        if (existingPlayer != null) {
            sendMessageToChat(chatId, "❌ Этот никнейм уже занят. Пожалуйста, выберите другой:", false);
            return;
        }
        
        userAnketaData.get(chatId).put("nickname", nickname);
        userStates.put(chatId, STATE_ANKETA_AGE);
        
        sendMessageToChat(chatId, "✅ Никнейм принят: " + nickname + "\n\n" +
                "Шаг 2 из 5\n" +
                "Сколько вам лет?", false);
    }
    
    private void handleAgeStep(long chatId, String ageText) {
        try {
            int age = Integer.parseInt(ageText);
            if (age < 10 || age > 80) {
                sendMessageToChat(chatId, "❌ Пожалуйста, введите реальный возраст (от 10 до 80 лет):", false);
                return;
            }
            
            userAnketaData.get(chatId).put("age", String.valueOf(age));
            userStates.put(chatId, STATE_ANKETA_PLAYTIME);
            
            // Создаем клавиатуру с вариантами времени
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            
            List<InlineKeyboardButton> row1 = new ArrayList<>();
            row1.add(createButton("1-3 часа", "playtime_1-3"));
            row1.add(createButton("3-5 часов", "playtime_3-5"));
            
            List<InlineKeyboardButton> row2 = new ArrayList<>();
            row2.add(createButton("5-10 часов", "playtime_5-10"));
            row2.add(createButton("10-15 часов", "playtime_10-15"));
            
            List<InlineKeyboardButton> row3 = new ArrayList<>();
            row3.add(createButton("15-20 часов", "playtime_15-20"));
            row3.add(createButton("20+ часов", "playtime_20+"));
            
            List<InlineKeyboardButton> row4 = new ArrayList<>();
            row4.add(createButton("✏️ Написать свой вариант", "playtime_custom"));
            
            keyboard.add(row1);
            keyboard.add(row2);
            keyboard.add(row3);
            keyboard.add(row4);
            
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(keyboard);
            
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText("Шаг 3 из 5\n" +
                    "Сколько часов в неделю вы обычно играете в Minecraft?\n\n" +
                    "Выберите один из вариантов ниже или напишите свой ответ:");
            message.setReplyMarkup(markup);
            
            try {
                execute(message);
            } catch (TelegramApiException e) {
                plugin.getLogger().warning("Ошибка отправки сообщения с кнопками: " + e.getMessage());
            }
            
        } catch (NumberFormatException e) {
            sendMessageToChat(chatId, "❌ Пожалуйста, введите число (ваш возраст):", false);
        }
    }
    
    private void handlePlaytimeStep(long chatId, String playtime) {
        userAnketaData.get(chatId).put("playtime", playtime);
        userStates.put(chatId, STATE_ANKETA_MOTIVATION);
        
        sendMessageToChat(chatId, "Шаг 4 из 5\n" +
                "Почему вы хотите попасть на наш сервер?\n" +
                "Опишите кратко ваши цели и ожидания (минимум 20 символов):", false);
    }
    
    private void handleMotivationStep(long chatId, String motivation) {
        if (motivation.length() < 20) {
            sendMessageToChat(chatId, "❌ Пожалуйста, напишите более развернутый ответ (от 20 символов):", false);
            return;
        }
        
        userAnketaData.get(chatId).put("motivation", motivation);
        userStates.put(chatId, STATE_ANKETA_ABOUT);
        
        sendMessageToChat(chatId, "Шаг 5 из 5\n" +
                "Расскажите немного о себе.\n" +
                "Чем увлекаетесь? В какие игры еще играете? (Необязательно, но это повысит ваши шансы):", false);
    }
    
    private void handleAboutStep(long chatId, String about) {
        userAnketaData.get(chatId).put("about", about);
        userAnketaData.get(chatId).put("timestamp", new Date().toString());
        
        // Формируем итоговое сообщение
        Map<String, String> data = userAnketaData.get(chatId);
        String anketaText = "📋 *НОВАЯ АНКЕТА НА ВАЙТ-ЛИСТ*\n\n" +
                "👤 *Никнейм в Minecraft:* " + data.get("nickname") + "\n" +
                "🆔 *Telegram ID:* " + data.get("tg_id") + "\n" +
                "🔗 *Telegram:* @" + data.get("tg_username") + "\n" +
                "🎂 *Возраст:* " + data.get("age") + "\n" +
                "⏰ *Игровое время (в неделю):* " + data.get("playtime") + "\n" +
                "📅 *Дата подачи:* " + data.get("timestamp") + "\n\n" +
                "🎯 *Мотивация:*\n" + data.get("motivation") + "\n\n" +
                "💬 *О себе:*\n" + data.get("about");
        
        // Отправляем заявку администратору (вашему аккаунту)
        sendAnketaToAdmin(chatId, data.get("nickname"), anketaText);
        
        // Отправляем подтверждение пользователю
        sendMessageToChat(chatId, "✅ Ваша анкета успешно отправлена на рассмотрение администраторам!\n" +
                "Ожидайте ответа в этом чате. Обычно это занимает от нескольких часов до суток.", false);
        
        // Очищаем состояние
        userStates.remove(chatId);
        userAnketaData.remove(chatId);
    }
    
    private void sendAnketaToAdmin(long userChatId, String nickname, String anketaText) {
        // Формируем кнопки для администратора
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createButton("✅ Принять", "anketa_accept_" + userChatId + "_" + nickname));
        row.add(createButton("❌ Отклонить", "anketa_reject_" + userChatId + "_" + nickname));
        
        keyboard.add(row);
        
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(keyboard);
        
        // Отправляем администратору
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(ADMIN_ID)); // Ваш ID
        message.setText(anketaText);
        message.setParseMode("Markdown");
        message.setReplyMarkup(markup);
        
        try {
            execute(message);
            plugin.getLogger().info("Анкета от " + nickname + " отправлена администратору");
        } catch (TelegramApiException e) {
            plugin.getLogger().severe("Ошибка отправки анкеты администратору: " + e.getMessage());
            sendMessageToChat(userChatId, "❌ Произошла ошибка при отправке анкеты. Попробуйте позже.", false);
        }
    }
    
    // ==================== ОБРАБОТКА КНОПОК ====================
    
    private void handleCallbackQuery(String callbackData, long chatId, Update update) {
        try {
            // Отвечаем на callback (убираем часики)
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(update.getCallbackQuery().getId());
            answer.setShowAlert(false);
            execute(answer);
            
            if (callbackData.startsWith("playtime_")) {
                handlePlaytimeButton(callbackData, chatId, update);
            } else if (callbackData.startsWith("anketa_")) {
                handleAnketaAdminButton(callbackData, chatId, update);
            } else if (callbackData.startsWith("confirm_")) {
                handleConfirmation(chatId, callbackData.replace("confirm_", ""), true, update);
            } else if (callbackData.startsWith("deny_")) {
                handleConfirmation(chatId, callbackData.replace("deny_", ""), false, update);
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка обработки callback: " + e.getMessage());
        }
    }
    
    private void handlePlaytimeButton(String callbackData, long chatId, Update update) {
        try {
            // Редактируем сообщение, убирая кнопки
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(String.valueOf(chatId));
            editMessage.setMessageId(update.getCallbackQuery().getMessage().getMessageId());
            
            if (callbackData.equals("playtime_custom")) {
                // Пользователь хочет написать свой вариант
                editMessage.setText("✏️ *Напишите, сколько часов в неделю вы обычно играете:*\n" +
                        "(Например: '2-3 часа в день', 'по 4 часа в выходные' и т.д.)");
                editMessage.setParseMode("Markdown");
                execute(editMessage);
            } else {
                // Пользователь выбрал готовый вариант
                String playtime = getPlaytimeText(callbackData);
                userAnketaData.get(chatId).put("playtime", playtime);
                userStates.put(chatId, STATE_ANKETA_MOTIVATION);
                
                editMessage.setText("⏰ *Игровое время:* " + playtime + "\n\n" +
                        "Шаг 4 из 5\n" +
                        "Почему вы хотите попасть на наш сервер?\n" +
                        "Опишите кратко ваши цели и ожидания (минимум 20 символов):");
                editMessage.setParseMode("Markdown");
                execute(editMessage);
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка обработки кнопки времени: " + e.getMessage());
        }
    }
    
    private void handleAnketaAdminButton(String callbackData, long chatId, Update update) {
        try {
            // Парсим данные: anketa_accept_1234567890_Nickname или anketa_reject_1234567890_Nickname
            String[] parts = callbackData.split("_");
            if (parts.length < 4) return;
            
            String action = parts[1]; // accept или reject
            long userChatId = Long.parseLong(parts[2]);
            String nickname = parts[3];
            
            // Редактируем сообщение у админа
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(String.valueOf(chatId));
            editMessage.setMessageId(update.getCallbackQuery().getMessage().getMessageId());
            
            if (action.equals("accept")) {
                // Принимаем заявку
                // Здесь можно добавить логику добавления в вайт-лист
                // Например: plugin.getWhitelistManager().addPlayer(nickname, userChatId);
                
                editMessage.setText(update.getCallbackQuery().getMessage().getText() + 
                        "\n\n---\n*Статус: ПРИНЯТА ✅*");
                editMessage.setParseMode("Markdown");
                execute(editMessage);
                
                // Уведомляем пользователя
                sendMessageToChat(userChatId, "🎉 *Поздравляем! Ваша анкета одобрена.*\n\n" +
                        "Теперь вы в вайт-листе! Заходите на сервер.", true);
                
            } else if (action.equals("reject")) {
                // Отклоняем заявку
                editMessage.setText(update.getCallbackQuery().getMessage().getText() + 
                        "\n\n---\n*Статус: ОТКЛОНЕНА ❌*");
                editMessage.setParseMode("Markdown");
                execute(editMessage);
                
                // Уведомляем пользователя
                sendMessageToChat(userChatId, "❌ *К сожалению, ваша анкета не была одобрена.*\n\n" +
                        "Вы можете уточнить правила сервера и подать ее снова через неделю.", true);
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка обработки кнопки администратора: " + e.getMessage());
        }
    }
    
    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================
    
    private String getPlaytimeText(String callbackData) {
        switch (callbackData) {
            case "playtime_1-3": return "1-3 часа в неделю";
            case "playtime_3-5": return "3-5 часов в неделю";
            case "playtime_5-10": return "5-10 часов в неделю";
            case "playtime_10-15": return "10-15 часов в неделю";
            case "playtime_15-20": return "15-20 часов в неделю";
            case "playtime_20+": return "20+ часов в неделю";
            default: return "Не указано";
        }
    }
    
    private InlineKeyboardButton createButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }
    
    private String getUserName(Update update) {
        if (update.hasCallbackQuery()) {
            return update.getCallbackQuery().getFrom().getUserName();
        } else if (update.hasMessage()) {
            return update.getMessage().getFrom().getUserName();
        }
        return null;
    }
    
    private void sendWelcomeMessage(long chatId) {
        PlayerData playerData = plugin.getDatabaseManager().getPlayerByTelegramId(chatId);
        
        if (playerData != null) {
            // Пользователь уже в вайт-листе
            String messageText = "✅ *Вы уже в вайт-листе!*\n\n" +
                               "Ваш аккаунт Minecraft: `" + playerData.getUsername() + "`\n" +
                               "Добро пожаловать на сервер!";
            
            sendMessageToChat(chatId, messageText, true);
        } else {
            // Пользователь не в вайт-листе - предлагаем анкету
            List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();
            
            InlineKeyboardButton anketaButton = new InlineKeyboardButton();
            anketaButton.setText("📝 Заполнить анкету");
            anketaButton.setCallbackData("start_anketa");
            row.add(anketaButton);
            
            keyboard.add(row);
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(keyboard);
            
            String messageText = "❌ *Вы еще не в вайт-листе.*\n\n" +
                               "Чтобы попасть на сервер, необходимо заполнить анкету.\n\n" +
                               "Также вы можете зарегистрироваться командой:\n" +
                               "`/register ВашИгровойНик`\n\n" +
                               "*Для регистрации через команду:*\n" +
                               "1. Игрок должен быть онлайн на сервере\n" +
                               "2. Указывайте никнейм точно как в игре";
            
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText(messageText);
            message.enableMarkdown(true);
            message.setReplyMarkup(markup);
            
            try {
                execute(message);
            } catch (TelegramApiException e) {
                plugin.getLogger().warning("Ошибка отправки приветствия: " + e.getMessage());
            }
        }
    }
    
    // Остальные существующие методы остаются без изменений...
    // (sendLoginConfirmation, handleRegistration, handleConfirmation и другие)
    
    private void sendLoginConfirmation(long chatId, Player player) {
        String uuidShort = player.getUniqueId().toString().substring(0, 8);
        String messageText = "🔐 *Запрос на вход*\n\n" +
                           "Игрок: `" + player.getName() + "`\n" +
                           "Время: " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n\n" +
                           "Разрешить вход на сервер?";
        
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();
        
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton confirmBtn = new InlineKeyboardButton();
        confirmBtn.setText("✅ Разрешить");
        confirmBtn.setCallbackData("confirm_" + uuidShort);
        row.add(confirmBtn);
        
        InlineKeyboardButton denyBtn = new InlineKeyboardButton();
        denyBtn.setText("❌ Запретить");
        denyBtn.setCallbackData("deny_" + uuidShort);
        row.add(denyBtn);
        
        keyboard.add(row);
        keyboardMarkup.setKeyboard(keyboard);
        
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(messageText);
        message.enableMarkdown(true);
        message.setReplyMarkup(keyboardMarkup);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            plugin.getLogger().warning("Ошибка отправки подтверждения: " + e.getMessage());
        }
    }

    private void handleRegistration(long chatId, String text) {
        String[] parts = text.split(" ", 2);
        if (parts.length < 2) {
            sendMessageToChat(chatId, "❌ *Используйте:* `/register ВашИгровойНик`", true);
            return;
        }

        String playerName = parts[1].trim();
        Player player = Bukkit.getPlayerExact(playerName);
        
        if (player == null || !player.isOnline()) {
            sendMessageToChat(chatId, "❌ Игрок `" + playerName + "` не найден на сервере.\n\n" +
                                    "Убедитесь, что:\n" +
                                    "1. Игрок онлайн на сервере\n" +
                                    "2. Ник указан *точно* как в игре", true);
            return;
        }

        PlayerData data = plugin.getDatabaseManager().getPlayer(player.getUniqueId());
        if (data == null) {
            sendMessageToChat(chatId, "❌ Ошибка: данные игрока не найдены в базе.", false);
            return;
        }

        PlayerData existingByTelegram = plugin.getDatabaseManager().getPlayerByTelegramId(chatId);
        if (existingByTelegram != null) {
            sendMessageToChat(chatId, 
                "❌ *Ваш Telegram уже привязан к другому игроку!*\n\n" +
                "Текущая привязка: `" + existingByTelegram.getUsername() + "`\n" +
                "Если это ошибка, обратитесь к администратору.\n" +
                "Один Telegram может быть привязан только к одному аккаунту Minecraft.",
                true
            );
            return;
        }

        if (data.getTelegramId() != null) {
            sendMessageToChat(chatId, 
                "❌ *Игрок уже зарегистрирован!*\n\n" +
                "Игрок `" + playerName + "` уже привязан к другому Telegram аккаунту.\n" +
                "Один аккаунт Minecraft может быть привязан только к одному Telegram.\n\n",
                true
            );
            
            player.sendMessage("§c⚠ Кто-то пытается зарегистрировать ваш аккаунт на другой Telegram!");
            player.sendMessage("§cЕсли это не вы - немедленно сообщите администратору!");
            return;
        }

        if (plugin.getProtectionManager().isProtected(player.getUniqueId())) {
            PlayerData onlineData = plugin.getDatabaseManager().getPlayer(player.getUniqueId());
            if (onlineData != null && onlineData.getTelegramId() != null) {
                sendMessageToChat(chatId, 
                    "❌ *Этот игрок уже зарегистрирован и онлайн!*\n\n" +
                    "Если вы законный владелец аккаунта:\n" +
                    "1. Выйдите с сервера\n" +
                    "2. Снова зайдите\n" +
                    "3. Подтвердите вход через текущий Telegram",
                    true
                );
                return;
            }
        }

        data.setTelegramId(chatId);
        plugin.getDatabaseManager().savePlayer(data);
        
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player finalTargetPlayer = Bukkit.getPlayerExact(playerName);
            if (finalTargetPlayer != null && finalTargetPlayer.isOnline()) {
                plugin.getProtectionManager().unprotectPlayer(finalTargetPlayer);
                finalTargetPlayer.sendMessage("§a§l✅ РЕГИСТРАЦИЯ УСПЕШНА!");
                finalTargetPlayer.sendMessage("§fTelegram аккаунт привязан. Добро пожаловать на сервер!");
                finalTargetPlayer.sendTitle("§a§lДОБРО ПОЖАЛОВАТЬ", "§fНаслаждайтесь игрой!", 10, 70, 10);
                plugin.getLogger().info("Защита снята для игрока в основном потоке: " + playerName);
                
                plugin.getLogger().warning("РЕГИСТРАЦИЯ: Игрок " + playerName + " (UUID: " + player.getUniqueId() + ") привязан к Telegram ID: " + chatId);
            }
        });

        sendMessageToChat(chatId, 
            "✅ *Регистрация успешна!*\n\n" +
            "Игрок: `" + playerName + "`\n" +
            "Привязан к вашему Telegram аккаунту.\n\n" +
            "⚠ *Важно:*\n" +
            "• Эта привязка постоянна\n" +
            "• Сменить Telegram можно только через администратора\n" +
            "• Не передавайте доступ к своему Telegram\n\n" +
            "Теперь вы можете играть на сервере!", 
            true
        );
        
        plugin.getLogger().info("Игрок " + playerName + " зарегистрирован через Telegram ID: " + chatId);
    }
    
    private void handleConfirmation(long chatId, String uuidPrefix, boolean approve, Update update) {
        try {
            Player targetPlayer = null;
            
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.getUniqueId().toString().startsWith(uuidPrefix)) {
                    targetPlayer = onlinePlayer;
                    break;
                }
            }
            
            if (targetPlayer == null) {
                sendMessageToChat(chatId, "❌ Игрок не найден или время подтверждения истекло.", false);
                return;
            }
            
            String telegramUsername = "Пользователь";
            if (update.getCallbackQuery().getFrom().getUserName() != null) {
                telegramUsername = update.getCallbackQuery().getFrom().getUserName();
            }
            
            plugin.getLoginConfirmManager().handleConfirmation(targetPlayer.getUniqueId(), approve, telegramUsername);
            
            if (approve) {
                sendMessageToChat(chatId, "✅ Вход разрешён для игрока: " + targetPlayer.getName(), false);
            } else {
                sendMessageToChat(chatId, "❌ Вход запрещён для игрока: " + targetPlayer.getName(), false);
            }
            
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка обработки подтверждения: " + e.getMessage());
            sendMessageToChat(chatId, "❌ Произошла ошибка при обработке команды.", false);
        }
    }
    
    public void sendMessageToChat(long chatId, String text, boolean enableMarkdown) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        if (enableMarkdown) {
            message.enableMarkdown(true);
        }
        try {
            execute(message);
        } catch (TelegramApiException e) {
            plugin.getLogger().warning("Не удалось отправить сообщение в Telegram: " + e.getMessage());
        }
    }
    
    @Override
    public String getBotUsername() {
        return botUsername;
    }
    
    public void shutdown() {
        plugin.getLogger().info("Telegram бот завершает работу...");
        userStates.clear();
        userAnketaData.clear();
    }
}
