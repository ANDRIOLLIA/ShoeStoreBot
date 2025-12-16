package models;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Bot extends TelegramLongPollingBot {

    List<Integer> sizes = new ArrayList<>(List.of(40, 41, 42, 43, 44));

    private Map<String, List<Product>> userCarts = new ConcurrentHashMap<>();
    private Map<String, Product> currentSelectedItems = new ConcurrentHashMap<>();
    private Connection connection;

    private final Map<Long, Map<String, String>> productCreationData = new ConcurrentHashMap<>();
    private final Map<Long, UserState> userStates = new ConcurrentHashMap<>();

    // Список ID администраторов
    private final Set<Long> adminUsers = new HashSet<>(Arrays.asList(
            5242512520L
    ));

    private static class CatalogState {
        List<Product> products;
        int index = 0;
    }

    private final Map<Long, CatalogState> userCatalogs = new ConcurrentHashMap<>();

    private enum UserState {
        WAITING_FOR_PRODUCT_CODE,
        WAITING_FOR_PRODUCT_NAME,
        WAITING_FOR_PRODUCT_PRICE,
        WAITING_FOR_PRODUCT_DESCRIPTION,
        WAITING_FOR_PRODUCT_PHOTO_PATH,
        WAITING_FOR_PRODUCT_ID,
        WAITING_FOR_PRODUCT_ID_FOR_UPDATE,
        WAITING_FOR_PRODUCT_ID_FOR_DELETE,
        WAITING_FOR_PRODUCT_UPDATE_FIELD,
        WAITING_FOR_PRODUCT_UPDATE_VALUE,
        WAITING_FOR_BRAND_INPUT,
        NONE
    }

    // Кнопки для брендов
    private InlineKeyboardButton buttonForBalenciaga = InlineKeyboardButton.builder()
            .text("Balenciaga")
            .callbackData("men_balenciaga")
            .build();

    private InlineKeyboardButton buttonForNike = InlineKeyboardButton.builder()
            .text("Nike")
            .callbackData("men_nike")
            .build();

    private InlineKeyboardButton buttonForAdidas = InlineKeyboardButton.builder()
            .text("Adidas")
            .callbackData("men_adidas")
            .build();

    private InlineKeyboardButton buttonForGucci = InlineKeyboardButton.builder()
            .text("Gucci")
            .callbackData("men_gucci")
            .build();

    private InlineKeyboardButton buttonForWomenBalenciaga = InlineKeyboardButton.builder()
            .text("Balenciaga")
            .callbackData("women_balenciaga")
            .build();

    private InlineKeyboardButton buttonForWomenNike = InlineKeyboardButton.builder()
            .text("Nike")
            .callbackData("women_nike")
            .build();

    private InlineKeyboardButton buttonForWomenAdidas = InlineKeyboardButton.builder()
            .text("Adidas")
            .callbackData("women_adidas")
            .build();

    private InlineKeyboardButton buttonForWomenGucci = InlineKeyboardButton.builder()
            .text("Gucci")
            .callbackData("women_gucci")
            .build();

    // Метод для получения товара по ID
    private Product getProductById(int productId) {
        String sql = "SELECT * FROM products WHERE id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                // Чтение JSON массива размеров
                String sizesJson = rs.getString("sizes");
                List<Integer> productSizes = new ArrayList<>();
                if (sizesJson != null && sizesJson.startsWith("[")) {
                    sizesJson = sizesJson.replace("[", "").replace("]", "");
                    String[] sizeArray = sizesJson.split(",");
                    for (String size : sizeArray) {
                        try {
                            productSizes.add(Integer.parseInt(size.trim()));
                        } catch (NumberFormatException e) {
                            // Пропускаем некорректные размеры
                        }
                    }
                }
                if (productSizes.isEmpty()) {
                    productSizes = sizes; // Используем размеры по умолчанию
                }

                return new Product(
                        rs.getInt("id"),
                        rs.getString("product_code"),
                        rs.getString("name"),
                        rs.getInt("price"),
                        productSizes,
                        rs.getString("description"),
                        rs.getString("photo_path"),
                        rs.getString("brand"),
                        rs.getString("gender")
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    // Метод для обновления товара
    public void updateProduct(Long chatId, int productId, String field, String value) {
        String sql;

        switch (field) {
            case "name":
                sql = "UPDATE products SET name = ? WHERE id = ?";
                break;
            case "price":
                sql = "UPDATE products SET price = ? WHERE id = ?";
                break;
            case "description":
                sql = "UPDATE products SET description = ? WHERE id = ?";
                break;
            case "photo_path":
                sql = "UPDATE products SET photo_path = ? WHERE id = ?";
                break;
            case "sizes":
                sql = "UPDATE products SET sizes = ? WHERE id = ?";
                break;
            case "brand":
                sql = "UPDATE products SET brand = ? WHERE id = ?";
                break;
            case "gender":
                sql = "UPDATE products SET gender = ? WHERE id = ?";
                break;
            default:
                sendTextMessage(chatId, "❌ Некорректное поле для обновления");
                return;
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (field.equals("sizes")) {
                // Преобразуем строку размеров в JSON
                String[] sizeArray = value.split(",");
                StringBuilder jsonBuilder = new StringBuilder("[");
                for (int i = 0; i < sizeArray.length; i++) {
                    jsonBuilder.append(sizeArray[i].trim());
                    if (i < sizeArray.length - 1) {
                        jsonBuilder.append(",");
                    }
                }
                jsonBuilder.append("]");
                ps.setString(1, jsonBuilder.toString());
            } else if (field.equals("price")) {
                ps.setInt(1, Integer.parseInt(value));
            } else {
                ps.setString(1, value);
            }

            ps.setInt(2, productId);

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                sendTextMessage(chatId, "✅ Товар успешно обновлен!");
            } else {
                sendTextMessage(chatId, "❌ Товар с указанным ID не найден");
            }
        } catch (SQLException e) {
            System.out.println("Ошибка при обновлении товара: " + e.getMessage());
            sendTextMessage(chatId, "❌ Ошибка при обновлении товара: " + e.getMessage());
        } catch (NumberFormatException e) {
            sendTextMessage(chatId, "❌ Некорректное значение цены. Введите только цифры.");
        }
    }

    // Метод для удаления товара
    public void deleteProduct(Long chatId, int productId) {
        String sql = "DELETE FROM products WHERE id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, productId);

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                sendTextMessage(chatId, "✅ Товар успешно удален!");
            } else {
                sendTextMessage(chatId, "❌ Товар с указанным ID не найден");
            }
        } catch (SQLException e) {
            System.out.println("Ошибка при удалении товара: " + e.getMessage());

            if (e.getMessage().contains("foreign key constraint")) {
                sendTextMessage(chatId, "❌ Нельзя удалить товар, так как он есть в заказах или корзинах");
            } else {
                sendTextMessage(chatId, "❌ Ошибка при удалении товара: " + e.getMessage());
            }
        }
    }

    // Метод для проверки существования товара
    private boolean productExists(int productId) {
        String sql = "SELECT COUNT(*) FROM products WHERE id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    private List<Product> loadProducts(String brand, String gender) {
        List<Product> products = new ArrayList<>();

        String sql = """
                    SELECT * FROM products
                    WHERE brand = ? AND gender = ?
                    ORDER BY id
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, brand);
            ps.setString(2, gender);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                // Чтение JSON массива размеров
                String sizesJson = rs.getString("sizes");
                List<Integer> productSizes = new ArrayList<>();
                if (sizesJson != null && sizesJson.startsWith("[")) {
                    sizesJson = sizesJson.replace("[", "").replace("]", "");
                    String[] sizeArray = sizesJson.split(",");
                    for (String size : sizeArray) {
                        try {
                            productSizes.add(Integer.parseInt(size.trim()));
                        } catch (NumberFormatException e) {
                            // Пропускаем некорректные размеры
                        }
                    }
                }
                if (productSizes.isEmpty()) {
                    productSizes = sizes; // Используем размеры по умолчанию
                }

                products.add(new Product(
                        rs.getInt("id"),
                        rs.getString("product_code"),
                        rs.getString("name"),
                        rs.getInt("price"),
                        productSizes,
                        rs.getString("description"),
                        rs.getString("photo_path"),
                        rs.getString("brand"),
                        rs.getString("gender")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return products;
    }

    private void showCurrentProduct(Long chatId) {
        CatalogState state = userCatalogs.get(chatId);
        if (state == null || state.products.isEmpty()) {
            sendTextMessage(chatId, "❌ Товары не найдены");
            return;
        }

        Product p = state.products.get(state.index);

        InlineKeyboardMarkup keyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        InlineKeyboardButton.builder()
                                .text("⬅️")
                                .callbackData("prev_product")
                                .build(),
                        InlineKeyboardButton.builder()
                                .text("➡️")
                                .callbackData("next_product")
                                .build()
                ))
                .keyboardRow(List.of(buttonForAddToCart))
                .keyboardRow(List.of(buttonForReturnBack))
                .build();

        try {
            File photoFile = new File(p.getPhotoPath());
            if (photoFile.exists()) {
                SendPhoto photo = new SendPhoto();
                photo.setChatId(chatId);
                photo.setPhoto(new InputFile(photoFile));
                photo.setCaption(
                        "🛍️ *" + p.getName() + "*\n\n" +
                                "💰 Цена: " + p.getPrice() + "₽\n" +
                                "📝 " + p.getDescription() + "\n\n" +
                                "📊 Размеры: " + getSizesString(p.getSizes()) + "\n" +
                                "🏷️ Бренд: " + p.getBrand() + "\n" +
                                "👥 Категория: " + (p.getGender().equals("MEN") ? "Мужская" : "Женская")
                );
                photo.setParseMode("Markdown");
                photo.setReplyMarkup(keyboard);

                execute(photo);
            } else {
                SendMessage message = SendMessage.builder()
                        .chatId(chatId)
                        .text(
                                "🛍️ *" + p.getName() + "*\n\n" +
                                        "💰 Цена: " + p.getPrice() + "₽\n" +
                                        "📝 " + p.getDescription() + "\n\n" +
                                        "📊 Размеры: " + getSizesString(p.getSizes()) + "\n" +
                                        "🏷️ Бренд: " + p.getBrand() + "\n" +
                                        "👥 Категория: " + (p.getGender().equals("MEN") ? "Мужская" : "Женская") + "\n\n" +
                                        "❌ Фото временно недоступно"
                        )
                        .parseMode("Markdown")
                        .replyMarkup(keyboard)
                        .build();
                execute(message);
            }

            currentSelectedItems.put(chatId.toString(), p);
        } catch (Exception e) {
            e.printStackTrace();
            sendTextMessage(chatId, "❌ Ошибка при отображении товара");
        }
    }

    public void createProduct(Long chatId, int id, String productCode, String name, int price,
                              String description, String photoPath, String brand, String gender) {
        String sql = "INSERT INTO products (id, product_code, name, price, description, photo_path, brand, gender, sizes) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        System.out.println("Выполняем SQL: " + sql);

        try {
            if (connection != null && !connection.isClosed()) {
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setInt(1, id);
                    ps.setString(2, productCode);
                    ps.setString(3, name);
                    ps.setInt(4, price);
                    ps.setString(5, description);
                    ps.setString(6, photoPath);
                    ps.setString(7, brand);
                    ps.setString(8, gender);
                    ps.setString(9, "[40,41,42,43,44]"); // Размеры по умолчанию

                    int rowsAffected = ps.executeUpdate();
                    if (rowsAffected > 0) {
                        sendTextMessage(chatId, "✅ Товар успешно добавлен в базу данных!");
                    } else {
                        sendTextMessage(chatId, "❌ Ошибка при добавлении товара!");
                    }
                }
            } else {
                sendTextMessage(chatId, "❌ Нет соединения с базой данных!");
            }
        } catch (SQLException ex) {
            System.out.println("Ошибка при добавлении товара: " + ex.getMessage());
            ex.printStackTrace();

            if (ex.getMessage().contains("Duplicate entry")) {
                sendTextMessage(chatId, "❌ Ошибка: Товар с таким ID или кодом уже существует!");
            } else {
                sendTextMessage(chatId, "❌ Ошибка базы данных: " + ex.getMessage());
            }
        } catch (Exception ex) {
            System.out.println("Ошибка при добавлении товара: " + ex.getMessage());
            ex.printStackTrace();
            sendTextMessage(chatId, "❌ Ошибка: " + ex.getMessage());
        }
    }

    public void initDBConnection() {
        try {
            String url = "jdbc:mysql://localhost:3306/shoe_store_bot";
            String username = "root";
            String password = "andrEj0077";

            connection = DriverManager.getConnection(url, username, password);
            System.out.println("✅ Соединение с БД установлено!");
        } catch (Exception ex) {
            System.out.println("❌ Ошибка подключения к БД: " + ex.getMessage());
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (connection == null) {
            initDBConnection();
        }

        try {
            if (update.hasMessage() && update.getMessage().hasText()) {
                Long chatId = update.getMessage().getChatId();
                String userText = update.getMessage().getText();

                UserState currentState = userStates.get(chatId);

                if (currentState != null && currentState != UserState.NONE) {
                    handleProductCreationInput(chatId, userText, currentState);
                    return;
                }

                // Проверяем, находится ли пользователь в состоянии обратной связи
                if (userText.length() > 10 && !userText.startsWith("/") && !userText.startsWith("👟") &&
                        !userText.startsWith("🛒") && !userText.startsWith("📦") &&
                        !userText.startsWith("ℹ️") && !userText.startsWith("📞") &&
                        !userText.startsWith("⚙️")) {
                    // Если длинное сообщение и не команда - возможно это обратная связь
                    handleFeedbackMessage(chatId, userText);
                    return;
                }

                if (userText.equals("/start")) {
                    handleStartCommand(chatId);
                } else if (userText.equals("/admin") && isAdmin(chatId)) {
                    showAdminMenu(chatId);
                } else if (userText.equals("/updateproduct") && isAdmin(chatId)) {
                    userStates.put(chatId, UserState.WAITING_FOR_PRODUCT_ID_FOR_UPDATE);
                    sendTextMessage(chatId, "✏️ *Изменение товара*\n\nВведите ID товара, который хотите изменить:");
                } else if (userText.equals("/deleteproduct") && isAdmin(chatId)) {
                    userStates.put(chatId, UserState.WAITING_FOR_PRODUCT_ID_FOR_DELETE);
                    sendTextMessage(chatId, "🗑️ *Удаление товара*\n\nВведите ID товара, который хотите удалить:");
                } else if (userText.equals("/listproducts") && isAdmin(chatId)) {
                    listAllProducts(chatId);
                } else if (userText.equals("/listorders") && isAdmin(chatId)) {
                    listAllOrders(chatId);
                } else if (userText.equals("👟 Каталог товаров")) {
                    showCategories(chatId);
                } else if (userText.equals("🛒 Корзина")) {
                    showCart(chatId.toString(), chatId);
                } else if (userText.equals("📦 Мои заказы")) {
                    listUserOrders(chatId);
                } else if (userText.equals("ℹ️ О магазине")) {
                    sendAboutInfo(chatId);
                } else if (userText.equals("📞 Контакты")) {
                    sendContacts(chatId);
                } else if (userText.equals("⚙️ Админ-панель") && isAdmin(chatId)) {
                    showAdminMenu(chatId);
                } else if (userText.startsWith("/add ") && userText.length() > 5) {
                    try {
                        int productId = Integer.parseInt(userText.substring(5).trim());
                        quickAddToCart(chatId, productId);
                    } catch (NumberFormatException e) {
                        sendTextMessage(chatId, "❌ Неверный формат команды. Используйте: /add [номер_товара]");
                    }
                }
            } else if (update.hasCallbackQuery()) {
                forWorkWithButtons(update);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isAdmin(Long chatId) {
        return adminUsers.contains(chatId);
    }

    private void sendAboutInfo(Long chatId) {
        String aboutText = """
                👟 *Premium Shoes Store* 👟
                
                🎯 *Наша миссия:*
                Предоставлять самую стильную и качественную обувь от ведущих мировых брендов.
                
                ✨ *Преимущества:*
                • 100% оригинальная продукция
                • Быстрая доставка по всей России
                • Бесплатная примерка
                • Гарантия качества
                • Профессиональные консультации
                
                🏪 *Режим работы:*
                Пн-Пт: 9:00-21:00
                Сб-Вс: 10:00-20:00
                
                Мы ценим каждого клиента и гарантируем отличный сервис! 🛍️
                """;

        sendTextMessage(chatId, aboutText);
    }

    private void sendContacts(Long chatId) {
        String contactsText = """
                📞 *Контакты магазина* 📞
                
                📍 *Адрес:*
                Москва, ул. Тверская, д. 10
                
                📱 *Телефон:*
                +7 (495) 123-45-67
                
                ✉️ *Email:*
                info@premiumshoes.ru
                
                🌐 *Сайт:*
                www.premiumshoes.ru
                
                🕒 *Режим работы:*
                Ежедневно с 9:00 до 21:00
                
                💬 *Поддержка в Telegram:*
                @premiumshoes_support
                
                Мы всегда рады помочь вам! 😊
                """;

        sendTextMessage(chatId, contactsText);
    }

    private ReplyKeyboardMarkup getUserKeyboard(boolean isAdmin) {
        List<KeyboardRow> keyboard = new ArrayList<>();

        // Первый ряд
        KeyboardRow row1 = new KeyboardRow();
        row1.add("👟 Каталог товаров");
        row1.add("🛒 Корзина");
        keyboard.add(row1);

        // Второй ряд
        KeyboardRow row2 = new KeyboardRow();
        row2.add("📦 Мои заказы");
        row2.add("ℹ️ О магазине");
        keyboard.add(row2);

        // Третий ряд
        KeyboardRow row3 = new KeyboardRow();
        row3.add("📞 Контакты");
        if (isAdmin) {
            row3.add("⚙️ Админ-панель");
        }
        keyboard.add(row3);

        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        return keyboardMarkup;
    }

    private void showAdminMenu(Long chatId) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("👨‍💼 *Панель администратора*\n\nВыберите действие:")
                .parseMode("Markdown")
                .replyMarkup(keyboardForAdmin)
                .build();

        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showCategories(Long chatId) {
        try {
            File photoFile = new File("src/main/java/photos/categories.png");
            if (photoFile.exists()) {
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(chatId);
                sendPhoto.setPhoto(new InputFile(photoFile));
                sendPhoto.setCaption("👟 *Категории обуви* 👟\n\nВыберите раздел:");
                sendPhoto.setParseMode("Markdown");
                sendPhoto.setReplyMarkup(keyboardForCategories);
                execute(sendPhoto);
            } else {
                SendMessage message = SendMessage.builder()
                        .chatId(chatId)
                        .text("👟 *Категории обуви* 👟\n\nВыберите раздел:")
                        .parseMode("Markdown")
                        .replyMarkup(keyboardForCategories)
                        .build();
                execute(message);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            sendTextMessage(chatId, "👟 *Категории обуви* 👟\n\nВыберите раздел:");
        }
    }

    private void handleProductCreationInput(Long chatId, String userText, UserState currentState) {
        Map<String, String> userData = productCreationData.computeIfAbsent(chatId, k -> new HashMap<>());

        switch (currentState) {
            case WAITING_FOR_PRODUCT_CODE:
                userData.put("productCode", userText);
                userStates.put(chatId, UserState.WAITING_FOR_PRODUCT_NAME);
                sendTextMessage(chatId, "✅ Код товара сохранен: " + userText +
                        "\n\nТеперь введите название товара:\nПример: Balenciaga Track 2");
                break;

            case WAITING_FOR_PRODUCT_NAME:
                userData.put("name", userText);
                userStates.put(chatId, UserState.WAITING_FOR_PRODUCT_PRICE);
                sendTextMessage(chatId, "✅ Название товара сохранено: " + userText +
                        "\n\nТеперь введите цену товара (только цифры):\nПример: 45000");
                break;

            case WAITING_FOR_PRODUCT_PRICE:
                try {
                    int price = Integer.parseInt(userText);
                    userData.put("price", userText);
                    userStates.put(chatId, UserState.WAITING_FOR_PRODUCT_DESCRIPTION);
                    sendTextMessage(chatId, "✅ Цена товара сохранена: " + price + "₽" +
                            "\n\nТеперь введите описание товара:");
                } catch (NumberFormatException e) {
                    sendTextMessage(chatId, "❌ Пожалуйста, введите корректную цену (только цифры):\nПример: 45000");
                }
                break;

            case WAITING_FOR_PRODUCT_DESCRIPTION:
                userData.put("description", userText);
                userStates.put(chatId, UserState.WAITING_FOR_PRODUCT_PHOTO_PATH);
                sendTextMessage(chatId, "✅ Описание товара сохранено." +
                        "\n\nТеперь введите путь к фото товара:\nПример: src/main/java/photos/balenciaga/balenciaga_track_2.png");
                break;

            case WAITING_FOR_PRODUCT_PHOTO_PATH:
                userData.put("photoPath", userText);
                userStates.put(chatId, UserState.WAITING_FOR_PRODUCT_ID);
                sendTextMessage(chatId, "✅ Путь к фото сохранен." +
                        "\n\nТеперь введите ID товара (только цифры):\nПример: 1");
                break;

            case WAITING_FOR_PRODUCT_ID:
                try {
                    int id = Integer.parseInt(userText);
                    userData.put("id", userText);

                    // Проверяем, ждем ли мы ввод бренда вручную
                    if (userData.containsKey("waitingForBrandInput") && "true".equals(userData.get("waitingForBrandInput"))) {
                        userData.put("brand", userText);
                        userData.remove("waitingForBrandInput");
                        askForGender(chatId, userData);
                    } else {
                        // Иначе показываем выбор бренда через кнопки
                        userStates.put(chatId, UserState.NONE);
                        askForBrand(chatId, userData);
                    }
                } catch (NumberFormatException e) {
                    sendTextMessage(chatId, "❌ Пожалуйста, введите корректный ID (только цифры):\nПример: 1");
                }
                break;

            case WAITING_FOR_BRAND_INPUT:
                userData.put("brand", userText);
                askForGender(chatId, userData);
                break;

            case WAITING_FOR_PRODUCT_ID_FOR_UPDATE:
                try {
                    int productId = Integer.parseInt(userText);
                    if (productExists(productId)) {
                        userData.put("updateProductId", userText);
                        userStates.put(chatId, UserState.WAITING_FOR_PRODUCT_UPDATE_FIELD);

                        // Показываем меню выбора поля для изменения
                        SendMessage message = SendMessage.builder()
                                .chatId(chatId)
                                .text("✅ Товар с ID " + productId + " найден!\n\nВыберите поле для изменения:")
                                .replyMarkup(keyboardForUpdateProduct)
                                .build();
                        try {
                            execute(message);
                        } catch (Exception ex) {
                            System.out.println(ex.getMessage());
                        }
                    } else {
                        sendTextMessage(chatId, "❌ Товар с ID " + productId + " не найден!\n\nПопробуйте еще раз:");
                    }
                } catch (NumberFormatException e) {
                    sendTextMessage(chatId, "❌ Пожалуйста, введите корректный ID (только цифры):");
                }
                break;

            case WAITING_FOR_PRODUCT_ID_FOR_DELETE:
                try {
                    int productId = Integer.parseInt(userText);
                    if (productExists(productId)) {
                        // Получаем информацию о товаре перед удалением
                        Product product = getProductById(productId);
                        if (product != null) {
                            // Создаем клавиатуру с подтверждением удаления
                            InlineKeyboardMarkup confirmKeyboard = InlineKeyboardMarkup.builder()
                                    .keyboardRow(List.of(
                                            InlineKeyboardButton.builder()
                                                    .text("✅ Да, удалить")
                                                    .callbackData("confirm_delete_" + productId)
                                                    .build(),
                                            InlineKeyboardButton.builder()
                                                    .text("❌ Нет, отменить")
                                                    .callbackData("cancel_delete")
                                                    .build()
                                    ))
                                    .build();

                            SendMessage message = SendMessage.builder()
                                    .chatId(chatId)
                                    .text("⚠️ *Вы уверены, что хотите удалить товар?*\n\n" +
                                            "Товар: " + product.getName() + "\n" +
                                            "ID: " + product.getId() + "\n" +
                                            "Цена: " + product.getPrice() + "₽\n\n" +
                                            "Это действие нельзя отменить!")
                                    .parseMode("Markdown")
                                    .replyMarkup(confirmKeyboard)
                                    .build();
                            try {
                                execute(message);
                            } catch (Exception ex) {
                                System.out.println(ex.getMessage());
                            }
                        }
                    } else {
                        sendTextMessage(chatId, "❌ Товар с ID " + productId + " не найден!\n\nПопробуйте еще раз:");
                    }
                    userStates.put(chatId, UserState.NONE);
                } catch (NumberFormatException e) {
                    sendTextMessage(chatId, "❌ Пожалуйста, введите корректный ID (только цифры):");
                }
                break;

            case WAITING_FOR_PRODUCT_UPDATE_VALUE:
                String fieldToUpdate = userData.get("fieldToUpdate");
                String productIdStr = userData.get("updateProductId");

                if (fieldToUpdate != null && productIdStr != null) {
                    try {
                        int productId = Integer.parseInt(productIdStr);
                        updateProduct(chatId, productId, fieldToUpdate, userText);

                        // Очищаем данные
                        productCreationData.remove(chatId);
                        userStates.put(chatId, UserState.NONE);
                    } catch (NumberFormatException e) {
                        sendTextMessage(chatId, "❌ Ошибка при обновлении товара");
                    }
                }
                break;
        }
    }

    private void askForBrand(Long chatId, Map<String, String> userData) {
        InlineKeyboardMarkup brandKeyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        InlineKeyboardButton.builder().text("Balenciaga").callbackData("create_brand_balenciaga").build(),
                        InlineKeyboardButton.builder().text("Nike").callbackData("create_brand_nike").build()
                ))
                .keyboardRow(List.of(
                        InlineKeyboardButton.builder().text("Adidas").callbackData("create_brand_adidas").build(),
                        InlineKeyboardButton.builder().text("Gucci").callbackData("create_brand_gucci").build()
                ))
                .keyboardRow(List.of(
                        InlineKeyboardButton.builder().text("Другой").callbackData("create_brand_other").build()
                ))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("✅ ID товара сохранен: " + userData.get("id") +
                        "\n\nТеперь выберите бренд товара:")
                .replyMarkup(brandKeyboard)
                .build();

        try {
            execute(message);
            // Сохраняем временные данные
            productCreationData.put(chatId, userData);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void askForGender(Long chatId, Map<String, String> userData) {
        InlineKeyboardMarkup genderKeyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(
                        InlineKeyboardButton.builder().text("👞 Мужская").callbackData("create_gender_MEN").build(),
                        InlineKeyboardButton.builder().text("👠 Женская").callbackData("create_gender_WOMEN").build()
                ))
                .keyboardRow(List.of(
                        InlineKeyboardButton.builder().text("👫 Унисекс").callbackData("create_gender_UNISEX").build()
                ))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("✅ Бренд сохранен: " + userData.get("brand") +
                        "\n\nТеперь выберите категорию товара:")
                .replyMarkup(genderKeyboard)
                .build();

        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void askForGender(Long chatId, String brand) {
        Map<String, String> userData = productCreationData.get(chatId);
        if (userData != null) {
            userData.put("brand", brand);
            askForGender(chatId, userData);
        }
    }

    private void createProductFromData(Long chatId, Map<String, String> userData) {
        try {
            int id = Integer.parseInt(userData.get("id"));
            String productCode = userData.get("productCode");
            String name = userData.get("name");
            int price = Integer.parseInt(userData.get("price"));
            String description = userData.get("description");
            String photoPath = userData.get("photoPath");
            String brand = userData.get("brand");
            String gender = userData.get("gender");

            createProduct(chatId, id, productCode, name, price, description, photoPath, brand, gender);

            // Очищаем данные после создания
            productCreationData.remove(chatId);
            userStates.put(chatId, UserState.NONE);

        } catch (Exception e) {
            sendTextMessage(chatId, "❌ Ошибка при создании товара: " + e.getMessage());
            productCreationData.remove(chatId);
            userStates.put(chatId, UserState.NONE);
        }
    }

    private void sendTextMessage(Long chatId, String text) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .build();

        // Только для обычных сообщений возвращаем пользовательскую клавиатуру
        UserState currentState = userStates.get(chatId);
        if (currentState == null || currentState == UserState.NONE) {
            message.setReplyMarkup(getUserKeyboard(isAdmin(chatId)));
        }

        try {
            execute(message);
        } catch (Exception ex) {
            System.out.println("Ошибка отправки сообщения: " + ex.getMessage());
        }
    }

    private void handleStartCommand(Long chatId) {
        try {
            boolean isAdmin = isAdmin(chatId);
            String welcomeText;

            if (isAdmin) {
                welcomeText = "👟 Добро пожаловать в магазин премиальной обуви! 👟\n\n" +
                        "Вы вошли как *администратор*.\n\n" +
                        "Выберите действие:";
            } else {
                welcomeText = "👟 Добро пожаловать в магазин премиальной обуви! 👟\n\n" +
                        "Мы рады приветствовать вас в нашем магазине!\n\n" +
                        "Выберите действие:";
            }

            File photoFile = new File("src/main/java/photos/photo.png");
            if (photoFile.exists()) {
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(chatId);
                sendPhoto.setPhoto(new InputFile(photoFile));
                sendPhoto.setCaption(welcomeText);
                sendPhoto.setParseMode("Markdown");
                sendPhoto.setReplyMarkup(getUserKeyboard(isAdmin));
                execute(sendPhoto);
            } else {
                SendMessage message = SendMessage.builder()
                        .chatId(chatId)
                        .text(welcomeText)
                        .parseMode("Markdown")
                        .replyMarkup(getUserKeyboard(isAdmin))
                        .build();
                execute(message);
            }
            System.out.println("Отправка главного меню для пользователя " + (isAdmin ? "(админ)" : "(обычный)"));
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace();
        }
    }

    private InlineKeyboardButton buttonForCreateProduct = InlineKeyboardButton.builder()
            .text("➕ Создать товар")
            .callbackData("create_product")
            .build();

    private InlineKeyboardButton buttonForUpdateProduct = InlineKeyboardButton.builder()
            .text("✏️ Изменить товар")
            .callbackData("update_product")
            .build();

    private InlineKeyboardButton buttonForDeleteProduct = InlineKeyboardButton.builder()
            .text("🗑️ Удалить товар")
            .callbackData("delete_product")
            .build();

    private InlineKeyboardButton buttonForViewProducts = InlineKeyboardButton.builder()
            .text("📋 Просмотреть товары")
            .callbackData("view_products")
            .build();

    private InlineKeyboardButton buttonForViewOrders = InlineKeyboardButton.builder()
            .text("📊 Просмотреть заказы")
            .callbackData("view_orders")
            .build();

    private InlineKeyboardButton buttonForReturnToMain = InlineKeyboardButton.builder()
            .text("🏠 На главную")
            .callbackData("/start")
            .build();

    private InlineKeyboardMarkup keyboardForAdmin = InlineKeyboardMarkup.builder()
            .keyboardRow(List.of(buttonForCreateProduct, buttonForViewProducts))
            .keyboardRow(List.of(buttonForUpdateProduct, buttonForDeleteProduct))
            .keyboardRow(List.of(buttonForViewOrders))
            .keyboardRow(List.of(buttonForReturnToMain))
            .build();

    private InlineKeyboardButton buttonForCategories = InlineKeyboardButton.builder()
            .text("Смотреть категории")
            .callbackData("categories")
            .build();

    private InlineKeyboardButton buttonForCart = InlineKeyboardButton.builder()
            .text("🛒 Корзина")
            .callbackData("cart")
            .build();

    private InlineKeyboardButton buttonForOrders = InlineKeyboardButton.builder()
            .text("📦 Заказы")
            .callbackData("orders")
            .build();

    private InlineKeyboardButton buttonForFeedback = InlineKeyboardButton.builder()
            .text("💬 Обратная связь")
            .callbackData("feedback")
            .build();

    private InlineKeyboardButton buttonForShowMenShoes = InlineKeyboardButton.builder()
            .text("👞 Мужская обувь")
            .callbackData("men_shoes")
            .build();

    private InlineKeyboardButton buttonForShowWomenShoes = InlineKeyboardButton.builder()
            .text("👠 Женская обувь")
            .callbackData("women_shoes")
            .build();

    private InlineKeyboardButton buttonForReturnBack = InlineKeyboardButton.builder()
            .text("⬅️ Назад")
            .callbackData("back")
            .build();

    private InlineKeyboardButton buttonForAddToCart = InlineKeyboardButton.builder()
            .text("🛍️ Добавить в корзину")
            .callbackData("add_to_cart")
            .build();

    private InlineKeyboardButton buttonForClearCart = InlineKeyboardButton.builder()
            .text("🗑️ Очистить корзину")
            .callbackData("clear_cart")
            .build();

    private InlineKeyboardButton buttonForCheckout = InlineKeyboardButton.builder()
            .text("💳 Оформить заказ")
            .callbackData("checkout")
            .build();

    private InlineKeyboardMarkup keyboardForCategories = InlineKeyboardMarkup.builder()
            .keyboardRow(List.of(buttonForShowMenShoes, buttonForShowWomenShoes))
            .keyboardRow(List.of(buttonForReturnToMain))
            .build();

    private InlineKeyboardMarkup keyboardForMenShoes = InlineKeyboardMarkup.builder()
            .keyboardRow(List.of(buttonForBalenciaga, buttonForNike))
            .keyboardRow(List.of(buttonForAdidas, buttonForGucci))
            .keyboardRow(List.of(buttonForReturnBack, buttonForReturnToMain))
            .build();

    private InlineKeyboardMarkup keyboardForWomenShoes = InlineKeyboardMarkup.builder()
            .keyboardRow(List.of(buttonForWomenBalenciaga, buttonForWomenNike))
            .keyboardRow(List.of(buttonForWomenAdidas, buttonForWomenGucci))
            .keyboardRow(List.of(buttonForReturnBack, buttonForReturnToMain))
            .build();

    // Кнопки для изменения товара
    private InlineKeyboardButton buttonForUpdateProductName = InlineKeyboardButton.builder()
            .text("✏️ Название")
            .callbackData("update_product_name")
            .build();

    private InlineKeyboardButton buttonForUpdateProductPrice = InlineKeyboardButton.builder()
            .text("💰 Цена")
            .callbackData("update_product_price")
            .build();

    private InlineKeyboardButton buttonForUpdateProductDescription = InlineKeyboardButton.builder()
            .text("📝 Описание")
            .callbackData("update_product_description")
            .build();

    private InlineKeyboardButton buttonForUpdateProductPhoto = InlineKeyboardButton.builder()
            .text("📸 Фото")
            .callbackData("update_product_photo")
            .build();

    private InlineKeyboardButton buttonForUpdateProductSizes = InlineKeyboardButton.builder()
            .text("📊 Размеры")
            .callbackData("update_product_sizes")
            .build();

    private InlineKeyboardButton buttonForUpdateProductBrand = InlineKeyboardButton.builder()
            .text("🏷️ Бренд")
            .callbackData("update_product_brand")
            .build();

    private InlineKeyboardButton buttonForUpdateProductGender = InlineKeyboardButton.builder()
            .text("👥 Категория")
            .callbackData("update_product_gender")
            .build();

    // Клавиатура для изменения товара
    private InlineKeyboardMarkup keyboardForUpdateProduct = InlineKeyboardMarkup.builder()
            .keyboardRow(List.of(buttonForUpdateProductName, buttonForUpdateProductPrice))
            .keyboardRow(List.of(buttonForUpdateProductDescription, buttonForUpdateProductPhoto))
            .keyboardRow(List.of(buttonForUpdateProductSizes))
            .keyboardRow(List.of(buttonForUpdateProductBrand, buttonForUpdateProductGender))
            .keyboardRow(List.of(buttonForReturnBack))
            .build();

    public void forWorkWithButtons(Update update) {
        if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            Long chatId = update.getCallbackQuery().getMessage().getChatId();
            String userId = chatId.toString();

            System.out.println("Callback data: " + callbackData);

            try {
                if (callbackData.equals("/start")) {
                    handleStartCommand(chatId);

                } else if (callbackData.equals("create_product")) {
                    if (!isAdmin(chatId)) {
                        sendTextMessage(chatId, "❌ У вас нет прав администратора!");
                        return;
                    }

                    productCreationData.put(chatId, new HashMap<>());
                    userStates.put(chatId, UserState.WAITING_FOR_PRODUCT_CODE);

                    SendMessage message = SendMessage.builder()
                            .chatId(chatId)
                            .text("Начинаем создание товара!\n\n" +
                                    "Введите код товара (например: balenciaga_track_2):")
                            .build();

                    execute(message);
                    System.out.println("Начало создания товара");

                } else if (callbackData.equals("next_product")) {
                    CatalogState state = userCatalogs.get(chatId);
                    if (state != null && !state.products.isEmpty()) {
                        state.index = (state.index + 1) % state.products.size();
                        showCurrentProduct(chatId);
                    }

                } else if (callbackData.equals("prev_product")) {
                    CatalogState state = userCatalogs.get(chatId);
                    if (state != null && !state.products.isEmpty()) {
                        state.index = (state.index - 1 + state.products.size()) % state.products.size();
                        showCurrentProduct(chatId);
                    }

                } else if (callbackData.equals("categories")) {
                    showCategories(chatId);

                } else if (callbackData.equals("cart")) {
                    showCart(userId, chatId);

                } else if (callbackData.equals("clear_cart")) {
                    userCarts.remove(userId);
                    SendMessage message = SendMessage.builder()
                            .chatId(chatId)
                            .text("✅ Корзина очищена!")
                            .replyMarkup(getUserKeyboard(isAdmin(chatId)))
                            .build();
                    execute(message);
                    showCart(userId, chatId);

                } else if (callbackData.equals("add_to_cart")) {
                    Product currentProduct = currentSelectedItems.get(userId);
                    if (currentProduct != null) {
                        if (!userCarts.containsKey(userId)) {
                            userCarts.put(userId, new ArrayList<>());
                        }

                        userCarts.get(userId).add(currentProduct);

                        SendMessage message = SendMessage.builder()
                                .chatId(chatId)
                                .text("✅ " + currentProduct.getName() + " добавлен в корзину!")
                                .replyMarkup(getUserKeyboard(isAdmin(chatId)))
                                .build();
                        execute(message);
                    }

                } else if (callbackData.equals("men_shoes")) {
                    SendMessage message = SendMessage.builder()
                            .chatId(chatId)
                            .text("👞 *Мужская обувь*\n\nВыберите бренд:")
                            .parseMode("Markdown")
                            .replyMarkup(keyboardForMenShoes)
                            .build();
                    execute(message);

                } else if (callbackData.equals("women_shoes")) {
                    SendMessage message = SendMessage.builder()
                            .chatId(chatId)
                            .text("👠 *Женская обувь*\n\nВыберите бренд:")
                            .parseMode("Markdown")
                            .replyMarkup(keyboardForWomenShoes)
                            .build();
                    execute(message);

                } else if (callbackData.startsWith("men_") || callbackData.startsWith("women_")) {
                    String[] parts = callbackData.split("_");
                    if (parts.length >= 2) {
                        String gender = parts[0].equals("men") ? "MEN" : "WOMEN";
                        String brand = parts[1];

                        List<Product> products = loadProducts(brand, gender);

                        if (products.isEmpty()) {
                            sendTextMessage(chatId, "❌ Товары не найдены");
                            return;
                        }

                        CatalogState state = new CatalogState();
                        state.products = products;
                        userCatalogs.put(chatId, state);

                        showCurrentProduct(chatId);
                    }

                } else if (callbackData.equals("back")) {
                    showCategories(chatId);

                } else if (callbackData.equals("checkout")) {
                    handleCheckout(chatId, userId);

                } else if (callbackData.equals("orders")) {
                    listUserOrders(chatId);

                } else if (callbackData.equals("feedback")) {
                    askForFeedback(chatId);

                } else if (callbackData.equals("update_product")) {
                    if (!isAdmin(chatId)) {
                        sendTextMessage(chatId, "❌ У вас нет прав администратора!");
                        return;
                    }

                    userStates.put(chatId, UserState.WAITING_FOR_PRODUCT_ID_FOR_UPDATE);
                    sendTextMessage(chatId, "✏️ *Изменение товара*\n\nВведите ID товара, который хотите изменить:");

                } else if (callbackData.equals("delete_product")) {
                    if (!isAdmin(chatId)) {
                        sendTextMessage(chatId, "❌ У вас нет прав администратора!");
                        return;
                    }

                    userStates.put(chatId, UserState.WAITING_FOR_PRODUCT_ID_FOR_DELETE);
                    sendTextMessage(chatId, "🗑️ *Удаление товара*\n\nВведите ID товара, который хотите удалить:");

                } else if (callbackData.equals("view_products")) {
                    if (!isAdmin(chatId)) {
                        sendTextMessage(chatId, "❌ У вас нет прав администратора!");
                        return;
                    }
                    listAllProducts(chatId);

                } else if (callbackData.equals("view_orders")) {
                    if (!isAdmin(chatId)) {
                        sendTextMessage(chatId, "❌ У вас нет прав администратора!");
                        return;
                    }
                    listAllOrders(chatId);

                } else if (callbackData.startsWith("create_brand_")) {
                    if (!isAdmin(chatId)) {
                        sendTextMessage(chatId, "❌ У вас нет прав администратора!");
                        return;
                    }

                    String brand = callbackData.replace("create_brand_", "");
                    if (brand.equals("other")) {
                        sendTextMessage(chatId, "✏️ Введите название бренда:");
                        Map<String, String> userData = productCreationData.get(chatId);
                        if (userData != null) {
                            userData.put("waitingForBrandInput", "true");
                        }
                    } else {
                        askForGender(chatId, brand);
                    }

                } else if (callbackData.startsWith("create_gender_")) {
                    if (!isAdmin(chatId)) {
                        sendTextMessage(chatId, "❌ У вас нет прав администратора!");
                        return;
                    }

                    String gender = callbackData.replace("create_gender_", "");
                    Map<String, String> userData = productCreationData.get(chatId);
                    if (userData != null) {
                        userData.put("gender", gender);
                        createProductFromData(chatId, userData);
                    }

                } else if (callbackData.startsWith("update_product_")) {
                    if (!isAdmin(chatId)) {
                        sendTextMessage(chatId, "❌ У вас нет прав администратора!");
                        return;
                    }

                    // Обработка выбора поля для изменения
                    String field = callbackData.replace("update_product_", "");
                    Map<String, String> userData = productCreationData.get(chatId);

                    if (userData != null && userData.containsKey("updateProductId")) {
                        // Для поля "photo" нужно использовать "photo_path" в базе данных
                        String dbField = field.equals("photo") ? "photo_path" : field;
                        userData.put("fieldToUpdate", dbField);
                        userStates.put(chatId, UserState.WAITING_FOR_PRODUCT_UPDATE_VALUE);

                        String prompt = getUpdateFieldPrompt(field);
                        sendTextMessage(chatId, prompt);
                    }
                }else if (callbackData.startsWith("confirm_delete_")) {
                    if (!isAdmin(chatId)) {
                        sendTextMessage(chatId, "❌ У вас нет прав администратора!");
                        return;
                    }

                    int productId = Integer.parseInt(callbackData.replace("confirm_delete_", ""));
                    deleteProduct(chatId, productId);

                } else if (callbackData.equals("cancel_delete")) {
                    sendTextMessage(chatId, "✅ Удаление отменено");
                }

                System.out.println("Обработка callback завершена: " + callbackData);

            } catch (Exception ex) {
                System.out.println("Ошибка в forWorkWithButtons: " + ex.getMessage());
                ex.printStackTrace();
            }
        }
    }

    private String getUpdateFieldPrompt(String field) {
        switch (field) {
            case "name":
                return "✏️ Введите новое название товара:";
            case "price":
                return "💰 Введите новую цену товара (только цифры):";
            case "description":
                return "📝 Введите новое описание товара:";
            case "photo":  // Исправлено с "photo" на "photo_path"
                return "📸 Введите новый путь к фото товара:";
            case "sizes":
                return "📊 Введите новые размеры через запятую (например: 40,41,42,43):";
            case "brand":
                return "🏷️ Введите новый бренд товара:";
            case "gender":
                return "👥 Введите новую категорию товара (MEN/WOMEN/UNISEX):";
            default:
                return "Введите новое значение:";
        }
    }

    private void handleCheckout(Long chatId, String userId) {
        List<Product> cart = userCarts.get(userId);

        if (cart == null || cart.isEmpty()) {
            sendTextMessage(chatId, "❌ Ваша корзина пуста!");
            return;
        }

        int totalAmount = 0;
        StringBuilder orderDetails = new StringBuilder();
        orderDetails.append("✅ *Заказ оформлен!*\n\n");
        orderDetails.append("📦 *Состав заказа:*\n");

        for (int i = 0; i < cart.size(); i++) {
            Product product = cart.get(i);
            orderDetails.append(i + 1).append(". ").append(product.getName())
                    .append(" - ").append(product.getPrice()).append("₽\n");
            totalAmount += product.getPrice();
        }

        orderDetails.append("\n💰 *Итого к оплате: ").append(totalAmount).append("₽*\n\n");
        orderDetails.append("📍 *Детали доставки:*\n");
        orderDetails.append("1. С вами свяжется менеджер для уточнения адреса\n");
        orderDetails.append("2. Оплата при получении\n");
        orderDetails.append("3. Доставка 1-3 рабочих дня\n\n");
        orderDetails.append("Спасибо за покупку! 🛍️");

        userCarts.remove(userId);
        currentSelectedItems.remove(userId);

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(orderDetails.toString())
                .parseMode("Markdown")
                .replyMarkup(getUserKeyboard(isAdmin(chatId)))
                .build();

        try {
            execute(message);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    private String getSizesString(List<Integer> sizes) {
        if (sizes == null || sizes.isEmpty()) {
            return "Не указаны";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sizes.size(); i++) {
            sb.append(sizes.get(i));
            if (i < sizes.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

    private void showCart(String userId, Long chatId) {
        List<Product> cart = userCarts.get(userId);

        if (cart == null || cart.isEmpty()) {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId)
                    .text("🛒 Ваша корзина пуста!\n\nВыберите товары в разделе '👟 Каталог товаров'")
                    .replyMarkup(getUserKeyboard(isAdmin(chatId)))
                    .build();

            try {
                execute(message);
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
            return;
        }

        int totalAmount = 0;
        StringBuilder cartContent = new StringBuilder();
        cartContent.append("🛒 *Ваша корзина:*\n\n");

        for (int i = 0; i < cart.size(); i++) {
            Product product = cart.get(i);
            cartContent.append(i + 1).append(". ").append(product.getName())
                    .append(" - ").append(product.getPrice()).append("₽\n");
            totalAmount += product.getPrice();
        }

        cartContent.append("\n💰 *Итого: ").append(totalAmount).append("₽*\n\n");
        cartContent.append("Выберите действие:");

        InlineKeyboardMarkup cartKeyboard = InlineKeyboardMarkup.builder()
                .keyboardRow(List.of(buttonForClearCart))
                .keyboardRow(List.of(buttonForCheckout))
                .keyboardRow(List.of(buttonForReturnToMain))
                .build();

        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(cartContent.toString())
                .parseMode("Markdown")
                .replyMarkup(cartKeyboard)
                .build();

        try {
            execute(message);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return "@MatosyanTGBot";
    }

    @Override
    public String getBotToken() {
        return "8004012680:AAEfvyYY8R44wFfIGunrWkTFaowWxH5-zbE";
    }

    // Дополнительные методы для полной функциональности

    private void listAllProducts(Long chatId) {
        String sql = "SELECT * FROM products ORDER BY id LIMIT 50";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ResultSet rs = ps.executeQuery();

            StringBuilder productsList = new StringBuilder();
            productsList.append("📋 *Все товары в базе:*\n\n");

            int count = 0;
            while (rs.next()) {
                count++;
                productsList.append("🆔 ID: ").append(rs.getInt("id")).append("\n")
                        .append("📦 Код: ").append(rs.getString("product_code")).append("\n")
                        .append("🛍️ Название: ").append(rs.getString("name")).append("\n")
                        .append("💰 Цена: ").append(rs.getInt("price")).append("₽\n")
                        .append("🏷️ Бренд: ").append(rs.getString("brand")).append("\n")
                        .append("👥 Категория: ").append(rs.getString("gender").equals("MEN") ? "Мужская" :
                                rs.getString("gender").equals("WOMEN") ? "Женская" : "Унисекс").append("\n")
                        .append("---\n");
            }

            if (count == 0) {
                productsList.append("📭 Товаров нет в базе данных");
            } else {
                productsList.append("\nВсего товаров: ").append(count);
            }

            sendTextMessage(chatId, productsList.toString());
        } catch (Exception e) {
            e.printStackTrace();
            sendTextMessage(chatId, "❌ Ошибка при получении списка товаров: " + e.getMessage());
        }
    }

    private void listUserOrders(Long chatId) {
        String message = "📦 *Ваши заказы*\n\n" +
                "1. Заказ #001 от 15.01.2024\n" +
                "   Статус: 📦 Доставлен\n" +
                "   Сумма: 45,000₽\n" +
                "   Товары: Balenciaga Track 2\n\n" +
                "2. Заказ #002 от 20.01.2024\n" +
                "   Статус: 🚚 В пути\n" +
                "   Сумма: 38,000₽\n" +
                "   Товары: Balenciaga Speed Trainer\n\n" +
                "📊 Всего заказов: 2\n" +
                "💰 Общая сумма: 83,000₽";

        sendTextMessage(chatId, message);
    }

    private void listAllOrders(Long chatId) {
        String message = "📊 *Все заказы в системе*\n\n" +
                "1. Заказ #001\n" +
                "   Пользователь: ID 5242512520\n" +
                "   Дата: 15.01.2024\n" +
                "   Статус: 📦 Доставлен\n" +
                "   Сумма: 45,000₽\n\n" +
                "2. Заказ #002\n" +
                "   Пользователь: ID 5242512520\n" +
                "   Дата: 20.01.2024\n" +
                "   Статус: 🚚 В пути\n" +
                "   Сумма: 38,000₽\n\n" +
                "📈 Всего заказов: 2\n" +
                "💰 Общий оборот: 83,000₽";

        sendTextMessage(chatId, message);
    }

    private void askForFeedback(Long chatId) {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text("💬 *Обратная связь*\n\n" +
                        "Пожалуйста, напишите ваше сообщение (вопрос, предложение или жалобу):\n\n" +
                        "Мы ответим вам в ближайшее время!")
                .parseMode("Markdown")
                .build();

        try {
            execute(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleFeedbackMessage(Long chatId, String feedbackText) {
        String response = "✅ *Спасибо за ваше сообщение!*\n\n" +
                "Мы получили вашу обратную связь и рассмотрим её в ближайшее время.\n\n" +
                "Если потребуется, мы свяжемся с вами для уточнения деталей.\n\n" +
                "С уважением, команда Premium Shoes Store! 👟";

        sendTextMessage(chatId, response);

        // Уведомление администраторов
        notifyAdminsAboutFeedback(chatId, feedbackText);
    }

    private void notifyAdminsAboutFeedback(Long userId, String feedback) {
        String notification = "📨 *Новая обратная связь*\n\n" +
                "От пользователя: " + userId + "\n" +
                "Сообщение:\n" + feedback.substring(0, Math.min(feedback.length(), 500)) +
                (feedback.length() > 500 ? "..." : "");

        for (Long adminId : adminUsers) {
            try {
                SendMessage message = SendMessage.builder()
                        .chatId(adminId)
                        .text(notification)
                        .parseMode("Markdown")
                        .build();
                execute(message);
            } catch (Exception e) {
                System.out.println("Не удалось отправить уведомление администратору " + adminId);
            }
        }
    }

    private void quickAddToCart(Long chatId, int productId) {
        Product product = getProductById(productId);

        if (product != null) {
            String userId = chatId.toString();
            if (!userCarts.containsKey(userId)) {
                userCarts.put(userId, new ArrayList<>());
            }

            userCarts.get(userId).add(product);
            sendTextMessage(chatId, "✅ " + product.getName() + " добавлен в корзину!");
        } else {
            sendTextMessage(chatId, "❌ Товар с ID " + productId + " не найден");
        }
    }
}