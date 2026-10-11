package com.my.televip.features.otherFeatures;

import android.view.View;

import com.my.televip.Class.ClassLoad;
import com.my.televip.Class.ClassNames;
import com.my.televip.base.AbstractMethodHook;
import com.my.televip.hooks.HMethod;
import com.my.televip.logging.Logger;
import com.my.televip.obfuscate.AutomationResolver;
import com.my.televip.reflect.XReflect;
import com.my.televip.virtuals.ui.PhotoViewer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class AlwaysSaveMedia {
    public static void init() {
        try {
            Class<?> pvClass = ClassLoad.getClass(ClassNames.PHOTO_VIEWER);
            if (pvClass != null) {
                AbstractMethodHook hook = new AbstractMethodHook() {
                    @Override
                    protected void afterMethod(MethodHookParam param) {
                        try {
                            final PhotoViewer photoViewer = new PhotoViewer(param.thisObject);
                            View galleryButton = photoViewer.getGalleryButton();
                            if (galleryButton != null) {
                                galleryButton.setVisibility(View.VISIBLE);
                                ensureParentMenuVisible(param.thisObject, galleryButton);
                            }
                        } catch (Throwable e) {
                            Logger.e(e);
                        }
                    }
                };

                HMethod.hookMethod(pvClass, AutomationResolver.resolve("PhotoViewer", "setIsAboutToSwitchToIndex", AutomationResolver.ResolverType.Method), AutomationResolver.merge(AutomationResolver.resolveObject("setIsAboutToSwitchToIndex", new Class<?>[]{int.class, boolean.class, boolean.class, boolean.class}), hook));

                String threeArg = AutomationResolver.resolveOverload("PhotoViewer", "setIsAboutToSwitchToIndexIZZ", "setIsAboutToSwitchToIndex");
                Method m3 = XReflect.findMethodExactIfExists(pvClass, threeArg, new Class<?>[]{int.class, boolean.class, boolean.class});
                if (m3 != null) {
                    HMethod.hookMethod(m3, hook);
                }
            }
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

    private static void ensureParentMenuVisible(Object photoViewerObj, View galleryButton) {
        try {
            View parent = (View) galleryButton.getParent();
            for (Field f : photoViewerObj.getClass().getDeclaredFields()) {
                if (f.getType().getName().contains("ActionBarMenuItem")) {
                    f.setAccessible(true);
                    Object item = f.get(photoViewerObj);
                    if (item instanceof View) {
                        View itemView = (View) item;
                        if (parent != null) {
                            for (Field itemField : item.getClass().getDeclaredFields()) {
                                itemField.setAccessible(true);
                                if (itemField.get(item) == parent) {
                                    itemView.setVisibility(View.VISIBLE);
                                    return;
                                }
                            }
                        }
                        for (Field itemField : item.getClass().getDeclaredFields()) {
                            if (itemField.getType().getName().contains("ActionBarPopupWindowLayout")) {
                                itemField.setAccessible(true);
                                if (itemField.get(item) != null) {
                                    itemView.setVisibility(View.VISIBLE);
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
}