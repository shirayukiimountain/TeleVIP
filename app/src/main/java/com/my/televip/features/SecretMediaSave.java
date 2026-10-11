package com.my.televip.features;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;

import com.my.televip.Class.ClassNames;
import com.my.televip.Class.ClassLoad;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.base.AbstractMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.AutomationResolver;
import com.my.televip.reflect.XReflect;
import com.my.televip.virtuals.messenger.FileLoader;
import com.my.televip.virtuals.messenger.ImageReceiver;
import com.my.televip.virtuals.messenger.MessageObject;
import com.my.televip.virtuals.messenger.UserConfig;
import com.my.televip.virtuals.tgnet.ConnectionsManager;
import com.my.televip.virtuals.tgnet.TLRPC;
import com.my.televip.virtuals.ui.Cells.ChatMessageCell;
import com.my.televip.virtuals.ui.PhotoViewer;
import com.my.televip.virtuals.ui.SecretMediaViewer;

import com.my.televip.ClientChecker;
import com.my.televip.application.ApplicationLoaderHook;
import com.my.televip.Configs.ConfigPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SecretMediaSave {

    public static boolean isEnable = false;

    public static long id;
    public static File pathImage;
    private static final Map<Integer, String> secretMediaPaths = new HashMap<>();
    private static final Set<Integer> protectedMids = new HashSet<>();
    private static boolean isDeleteProtectionHooked = false;

    public static void init() {
        try {
            installDeleteProtection();
            if (!isEnable) {
                isEnable = true;
                if (ClassLoad.getClass(ClassNames.MESSAGE_OBJECT) != null) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), AutomationResolver.resolve("MessageObject", "isSecret", AutomationResolver.ResolverType.Method), new AbstractMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            if (ConfigManager.secretMediaSave.isEnable()) param.setResult(false);
                        }
                    });
                    Method needDrawBluredPreviewMethod = XReflect.findMethodExactIfExists(ClassLoad.getClass(ClassNames.MESSAGE_OBJECT), "needDrawBluredPreview");
                    if (needDrawBluredPreviewMethod != null) {
                        HMethod.hookMethod(needDrawBluredPreviewMethod, new AbstractMethodHook() {
                            @Override
                            protected void beforeMethod(MethodHookParam param) {
                                if (ConfigManager.secretMediaSave.isEnable()) param.setResult(false);
                            }
                        });
                    }
                }
                if (ClassLoad.getClass(ClassNames.CHAT_MESSAGE_CELL_DELEGATE) != null) {
                    HMethod.hookMethod(ClassLoad.getClass(ClassNames.CHAT_MESSAGE_CELL_DELEGATE), AutomationResolver.resolve("ChatActivity$ChatMessageCellDelegate", "didPressImage", AutomationResolver.ResolverType.Method), AutomationResolver.merge(AutomationResolver.resolveObject("didPressImage", new Class<?>[]{ClassLoad.getClass(ClassNames.CHAT_MESSAGE_CELL), float.class, float.class, boolean.class}), new AbstractMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            try {
                                if (ConfigManager.secretMediaSave.isEnable() && param.args[0] != null) {
                                    ChatMessageCell messageCell = new ChatMessageCell(param.args[0]);
                                    if (messageCell.getChatMessageCell() != null) {
                                        MessageObject messageObject = messageCell.getMessageObject();
                                        if (messageObject.getMessageObject() != null) {
                                            TLRPC.Message message = messageObject.getMessageOwner();
                                            if (message != null) {
                                                int origTtl = message.getTtl();
                                                int ttlSec = 0;
                                                try {
                                                    Object media = XReflect.getObjectField(message.message, "media");
                                                    if (media != null) {
                                                        ttlSec = XReflect.getIntField(media, "ttl_seconds");
                                                    }
                                                } catch (Throwable ignored) {}
                                                boolean wasSecretMedia = origTtl > 0 || ttlSec > 0 || isProtectedMid(message.getID());

                                                if (wasSecretMedia && message.getID() != 0) {
                                                    addProtectedMid(message.getID());
                                                    sendSecretReadReceipt(message.getID());
                                                }

                                                if (message.getTtl() > 0) message.setTtl(0);
                                                try {
                                                    XReflect.setIntField(message.message, "destroyTime", 0);
                                                } catch (Throwable ignored) {}
                                                try {
                                                    XReflect.setBooleanField(message.message, "noforwards", false);
                                                } catch (Throwable ignored) {}
                                                try {
                                                    Object media = XReflect.getObjectField(message.message, "media");
                                                    if (media != null) {
                                                        XReflect.setIntField(media, "ttl_seconds", 0);
                                                    }
                                                } catch (Throwable ignored) {}

                                                captureCellImage(param.args[0], message.getID());
                                            }
                                        }
                                        bindPhotoViewerToActivity(messageCell);
                                    }
                                }
                            } catch (Throwable e) {
                                Logger.e(e);
                            }
                        }
                    }));
                }

                Class<?> fileLoader = ClassLoad.getClass(ClassNames.FILE_LOADER);
                Class<?> tlMessage = ClassLoad.getClass(ClassNames.TL_MESSAGE);
                if (fileLoader != null && tlMessage != null) {
                    // Every overload the build has: R8 inlines or duplicates the short ones
                    // (Momogram has two look-alike one-argument copies), and all of them end in
                    // the three-argument one.
                    AbstractMethodHook savedPath = new AbstractMethodHook() {
                        @Override
                        protected void beforeMethod(MethodHookParam param) {
                            try {
                                if (ConfigManager.secretMediaSave.isEnable() && param.args[0] != null) {
                                    TLRPC.Message message = new TLRPC.Message(param.args[0]);
                                    int mid = message.getID();
                                    String saved = secretMediaPaths.get(mid);
                                    if (saved != null) {
                                        File f = new File(saved);
                                        if (f.exists()) {
                                            param.setResult(f);
                                            return;
                                        }
                                    }
                                    if (mid == id && pathImage != null && pathImage.exists()) {
                                        param.setResult(pathImage);
                                    }
                                }
                            } catch (Throwable e) {
                                Logger.e(e);
                            }
                        }
                    };
                    hookIfPresent(fileLoader, AutomationResolver.resolve("FileLoader", "getPathToMessage", AutomationResolver.ResolverType.Method),
                            AutomationResolver.resolveObject("getPathToMessage", new Class<?>[]{tlMessage}), savedPath);
                    hookIfPresent(fileLoader, AutomationResolver.resolveOverload("FileLoader", "getPathToMessageOZ", "getPathToMessage"),
                            new Class<?>[]{tlMessage, boolean.class}, savedPath);
                    hookIfPresent(fileLoader, AutomationResolver.resolveOverload("FileLoader", "getPathToMessageOZZ", "getPathToMessage"),
                            new Class<?>[]{tlMessage, boolean.class, boolean.class}, savedPath);
                }


                if (ConfigManager.secretMediaSave.isEnable()) SecretMediaViewer.openMedia();
            }
        } catch (Throwable e){
            Logger.e(e);
        }
    }

    private static void hookIfPresent(Class<?> owner, String name, Class<?>[] params, AbstractMethodHook hook) {
        Method method = params == null ? null : XReflect.findMethodExactIfExists(owner, name, params);
        if (method != null) HMethod.hookMethod(method, hook);
    }

    private static void bindPhotoViewerToActivity(ChatMessageCell cell) {
        try {
            if (!(cell.getChatMessageCell() instanceof View)) return;
            View view = (View) cell.getChatMessageCell();
            Context context = view.getContext();
            Activity activity = extractActivityFromContext(context);
            if (activity == null) return;

            PhotoViewer.getInstance().setParentActivity(activity);
        } catch (Throwable ignored) {}
    }

    private static Activity extractActivityFromContext(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private static void captureCellImage(Object cell, int msgId) {
        try {
            id = msgId;
            Object ir = null;
            try {
                ir = XReflect.callMethod(cell, "getPhotoImage");
            } catch (Throwable ignored) {}
            if (ir == null) {
                try {
                    ir = XReflect.getObjectField(cell, "photoImage");
                } catch (Throwable ignored) {}
            }
            if (ir != null) {
                ImageReceiver receiver = new ImageReceiver(ir);
                FileLoader fileLoader = FileLoader.getInstance(UserConfig.getSelectedAccount());
                File image = fileLoader.getLocalFile(receiver.getImageLocation());
                if (image != null && image.exists()) {
                    pathImage = image;
                    secretMediaPaths.put(msgId, image.getAbsolutePath());

                    try {
                        Context ctx = ApplicationLoaderHook.getApplicationContext();
                        if (ctx != null) {
                            File backupDir = new File(ctx.getFilesDir(), "televip_secret_media");
                            if (!backupDir.exists()) backupDir.mkdirs();
                            File backupFile = new File(backupDir, "secret_" + msgId + ".jpg");
                            if (!backupFile.exists() || backupFile.length() != image.length()) {
                                copyFile(image, backupFile);
                            }
                            if (backupFile.exists()) {
                                secretMediaPaths.put(msgId, backupFile.getAbsolutePath());
                                pathImage = backupFile;
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void copyFile(File src, File dst) {
        try (FileInputStream in = new FileInputStream(src);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (Throwable ignored) {}
    }

    public static void loadProtectedMids() {
        try {
            String saved = ConfigPreferences.getString("ProtectedSecretMids");
            if (saved != null && !saved.isEmpty()) {
                for (String s : saved.split(",")) {
                    try {
                        protectedMids.add(Integer.parseInt(s.trim()));
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    public static synchronized void addProtectedMid(int mid) {
        if (mid == 0) return;
        protectedMids.add(mid);
        try {
            StringBuilder sb = new StringBuilder();
            for (Integer id : protectedMids) {
                if (sb.length() > 0) sb.append(",");
                sb.append(id);
            }
            ConfigPreferences.putString("ProtectedSecretMids", sb.toString());
        } catch (Throwable ignored) {}
    }

    public static boolean isProtectedMid(int mid) {
        return mid != 0 && (protectedMids.contains(mid) || readMids.contains(mid));
    }

    private static final Set<Integer> readMids = new HashSet<>();

    private static void sendSecretReadReceipt(int mid) {
        try {
            if (readMids.contains(mid)) return;
            readMids.add(mid);

            Class<?> reqClass = ClassLoad.getClass(ClassNames.TL_MESSAGES_READ_MESSAGE_CONTENTS);
            if (reqClass != null) {
                Object req = reqClass.newInstance();
                @SuppressWarnings("unchecked")
                ArrayList<Integer> idList = (ArrayList<Integer>) XReflect.getObjectField(req, "id");
                if (idList == null) {
                    idList = new ArrayList<>();
                    XReflect.setObjectField(req, "id", idList);
                }
                idList.add(mid);

                HideSeen.isReadMessages = true;
                int account = UserConfig.getSelectedAccount();
                ConnectionsManager.getInstance(account).sendRequest(req, null);
            }
        } catch (Throwable e) {
            Logger.e(e);
        }
    }

    public static void installDeleteProtection() {
        if (isDeleteProtectionHooked) return;
        isDeleteProtectionHooked = true;

        loadProtectedMids();

        try {
            Class<?> messagesController = ClassLoad.getClass(ClassNames.MESSAGES_CONTROLLER);
            if (messagesController != null) {
                // 1. Hook processUpdateArray (drops MTProto TL_updateDeleteMessages from server)
                for (Method method : messagesController.getDeclaredMethods()) {
                    if (method.getParameterCount() == 5
                            && method.getParameterTypes()[0] == ArrayList.class
                            && method.getParameterTypes()[1] == ArrayList.class
                            && method.getParameterTypes()[2] == ArrayList.class
                            && method.getParameterTypes()[3] == boolean.class
                            && method.getParameterTypes()[4] == int.class) {

                        HMethod.hookMethod(method, new AbstractMethodHook() {
                            @Override
                            protected void beforeMethod(MethodHookParam param) {
                                try {
                                    if (!isFeatureActive()) return;
                                    if (param.args[0] == null) return;

                                    List<?> updates = (List<?>) param.args[0];
                                    if (updates.isEmpty()) return;

                                    ArrayList<Object> newUpdates = new ArrayList<>();
                                    boolean modified = false;

                                    for (Object item : updates) {
                                        if (item == null) continue;
                                        boolean isDeleteMessages;
                                        boolean isDeleteChannel;
                                        if (!ClientChecker.isTgnetObfuscated()) {
                                            isDeleteMessages = item.getClass().getName().contains("TL_updateDeleteMessages");
                                            isDeleteChannel = item.getClass().getName().contains("TL_updateDeleteChannelMessages");
                                        } else {
                                            isDeleteMessages = item.getClass().equals(ClassLoad.getClass(ClassNames.TL_UPDATE_DELETE_MESSAGES));
                                            isDeleteChannel = item.getClass().equals(ClassLoad.getClass(ClassNames.TL_UPDATE_DELETE_CHANNEL_MESSAGES));
                                        }

                                        if (isDeleteMessages || isDeleteChannel) {
                                            String fieldName = (!ClientChecker.isTgnetObfuscated()) ? "messages" :
                                                    AutomationResolver.resolve(isDeleteMessages ? "TL_update$TL_updateDeleteMessages" : "TL_update$TL_updateDeleteChannelMessages",
                                                            "messages", AutomationResolver.ResolverType.Field);
                                            Object origListObj = XReflect.getObjectField(item, fieldName);
                                            if (origListObj instanceof ArrayList) {
                                                @SuppressWarnings("unchecked")
                                                ArrayList<?> origList = (ArrayList<?>) origListObj;
                                                boolean removedAny = false;
                                                Iterator<?> it = origList.iterator();
                                                while (it.hasNext()) {
                                                    Object val = it.next();
                                                    if (val instanceof Integer && isProtectedMid((Integer) val)) {
                                                        it.remove();
                                                        removedAny = true;
                                                    }
                                                }
                                                if (removedAny) {
                                                    modified = true;
                                                    if (origList.isEmpty()) {
                                                        // Entire delete update is for our protected secret media: drop it!
                                                        continue;
                                                    }
                                                }
                                            }
                                        }
                                        newUpdates.add(item);
                                    }

                                    if (modified) {
                                        param.args[0] = newUpdates;
                                    }
                                } catch (Throwable t) {
                                    Logger.e(t);
                                }
                            }
                        });
                        break;
                    }
                }

                // 2. Hook deleteMessages (blocks all local/internal calls to deleteMessages for protected IDs)
                String deleteMethodName = AutomationResolver.resolve("MessagesController", "deleteMessages", AutomationResolver.ResolverType.Method);
                for (Method m : messagesController.getDeclaredMethods()) {
                    if (m.getParameterCount() >= 1 && m.getParameterTypes()[0] == ArrayList.class
                            && (m.getName().equals("deleteMessages") || (deleteMethodName != null && m.getName().equals(deleteMethodName)))) {
                        HMethod.hookMethod(m, new AbstractMethodHook() {
                            @Override
                            protected void beforeMethod(MethodHookParam param) {
                                try {
                                    if (!isFeatureActive()) return;
                                    if (param.args[0] instanceof ArrayList) {
                                        @SuppressWarnings("unchecked")
                                        ArrayList<?> list = (ArrayList<?>) param.args[0];
                                        if (list != null && !list.isEmpty()) {
                                            ArrayList<Object> filtered = new ArrayList<>();
                                            boolean filteredAny = false;
                                            for (Object o : list) {
                                                if (o instanceof Integer && isProtectedMid((Integer) o)) {
                                                    filteredAny = true;
                                                    continue;
                                                }
                                                filtered.add(o);
                                            }
                                            if (filteredAny) {
                                                if (filtered.isEmpty()) {
                                                    param.setResult(null);
                                                } else {
                                                    param.args[0] = filtered;
                                                }
                                            }
                                        }
                                    }
                                } catch (Throwable t) {
                                    Logger.e(t);
                                }
                            }
                        });
                    }
                }

                // 3. Hook createDeleteShowOnceTask
                try {
                    String taskMethod = AutomationResolver.resolve("MessagesController", "createDeleteShowOnceTask", AutomationResolver.ResolverType.Method);
                    if (taskMethod != null) {
                        for (Method m : messagesController.getDeclaredMethods()) {
                            if (m.getName().equals(taskMethod)) {
                                HMethod.hookMethod(m, new AbstractMethodHook() {
                                    @Override
                                    protected void beforeMethod(MethodHookParam param) {
                                        if (isFeatureActive()) {
                                            param.setResult(0L);
                                        }
                                    }
                                });
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // 4. Hook sendSecretMediaDelete in ChatActivity
            Class<?> chatActivity = ClassLoad.getClass(ClassNames.CHAT_ACTIVITY);
            if (chatActivity != null) {
                HMethod.hookMethod(chatActivity,
                        AutomationResolver.resolve("ChatActivity", "sendSecretMediaDelete", AutomationResolver.ResolverType.Method),
                        AutomationResolver.merge(AutomationResolver.resolveObject("sendSecretMediaDelete", new Class<?>[]{ClassLoad.getClass(ClassNames.MESSAGE_OBJECT)}),
                                new AbstractMethodHook() {
                                    @Override
                                    protected void beforeMethod(MethodHookParam param) {
                                        if (isFeatureActive()) {
                                            param.setResult(new Runnable() {
                                                @Override
                                                public void run() {}
                                            });
                                        }
                                    }
                                }));
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static boolean isFeatureActive() {
        return (ConfigManager.secretMediaSave != null && ConfigManager.secretMediaSave.isEnable())
                || (ConfigManager.preventMedia != null && ConfigManager.preventMedia.isEnable());
    }
}

