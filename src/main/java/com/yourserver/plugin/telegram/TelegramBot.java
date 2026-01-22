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

import java.util.ArrayList;
import java.util.List;

public class TelegramBot extends TelegramLongPollingBot {
    private final Main plugin;
    private final String botUsername;
    
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
            
            if (text.equals("/start")) {
                sendWelcomeMessage(chatId);
            } else if (text.startsWith("/register")) {
                handleRegistration(chatId, text);
            }
        }
    }
    
    // Обработка нажатий на кнопки
    private void handleCallbackQuery(String callbackData, long chatId, Update update) {
        try {
            // Удаляем кнопки (отправляем ответ на callback)
            String callbackId = update.getCallbackQuery().getId();
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackId);
            answer.setShowAlert(false);
            execute(answer);
            
            // Редактируем сообщение - удаляем кнопки
            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(String.valueOf(chatId));
            editMessage.setMessageId(update.getCallbackQuery().getMessage().getMessageId());
            
            if (callbackData.startsWith("confirm_")) {
                String uuidPrefix = callbackData.replace("confirm_", "");
                editMessage.setText("✅ Вход разрешён");
                execute(editMessage);
                handleConfirmation(chatId, uuidPrefix, true, update);
            } else if (callbackData.startsWith("deny_")) {
                String uuidPrefix = callbackData.replace("deny_", "");
                editMessage.setText("❌ Вход запрещён");
                execute(editMessage);
                handleConfirmation(chatId, uuidPrefix, false, update);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Ошибка обработки callback: " + e.getMessage());
        }
    }
    
    private void sendWelcomeMessage(long chatId) {
        String messageText = "🎮 *Добро пожаловать на " + plugin.getConfig().getString("server.name", "наш сервер") + "!*\n\n" +
                           "Для регистрации введите команду:\n" +
                           "`/register ВашИгровойНик`\n\n" +
                           "⚠ *Важно:*\n" +
                           "• Игрок должен быть онлайн на сервере\n" +
                           "• Указывайте никнейм точно как в игре";
        
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(messageText);
        message.enableMarkdown(true);
        
        try {
            execute(message);
        } catch (TelegramApiException e) {
            plugin.getLogger().warning("Ошибка отправки приветствия: " + e.getMessage());
        }
    }
    
    // Отправка запроса подтверждения с кнопками
    public void sendLoginConfirmation(long chatId, Player player) {
        String uuidShort = player.getUniqueId().toString().substring(0, 8);
        String messageText = "🔐 *Запрос на вход*\n\n" +
                           "Игрок: `" + player.getName() + "`\n" +
                           "Время: " + java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n\n" +
                           "Разрешить вход на сервер?";
        
        // Создаем инлайн-кнопки
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

        // Этот Telegram уже привязан к другому игроку?
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

        // Этот игрок уже привязан к другому Telegram?
        if (data.getTelegramId() != null) {
            sendMessageToChat(chatId, 
                "❌ *Игрок уже зарегистрирован!*\n\n" +
                "Игрок `" + playerName + "` уже привязан к другому Telegram аккаунту.\n" +
                "Один аккаунт Minecraft может быть привязан только к одному Telegram.\n\n",
                true
            );
            
            // Дополнительно: уведомляем игрока в игре
            player.sendMessage("§c⚠ Кто-то пытается зарегистрировать ваш аккаунт на другой Telegram!");
            player.sendMessage("§cЕсли это не вы - немедленно сообщите администратору!");
            return;
        }

        // Игрок уже онлайн и зарегистрирован?
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

        // ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ - РЕГИСТРИРУЕМ
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
                
                // Логируем регистрацию
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
            
            // Используем getLoginConfirmManager() из Main
            String telegramUsername = "Пользователь";
            if (update.getCallbackQuery().getFrom().getUserName() != null) {
                telegramUsername = update.getCallbackQuery().getFrom().getUserName();
            }
            
            plugin.getLoginConfirmManager().handleConfirmation(targetPlayer.getUniqueId(), approve, telegramUsername);
            
            // Отправляем ответ на callback
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
    }
}
