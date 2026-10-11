package com.my.televip.settings.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import com.my.televip.Configs.ConfigItem;
import com.my.televip.Configs.ConfigManager;
import com.my.televip.audio;
import com.my.televip.language.Keys;
import com.my.televip.language.Translator;
import com.my.televip.logging.Logger;
import com.my.televip.settings.controller.SettingsController;
import com.my.televip.utils.DialogUtils;
import com.my.televip.utils.Utils;
import com.my.televip.virtuals.ActionBar.AlertDialog;
import com.my.televip.virtuals.Theme;
import com.my.televip.virtuals.messenger.browser.Browser;
import com.my.televip.virtuals.ui.Cells.ExpandableTextCheckCell;
import com.my.televip.virtuals.ui.Cells.HeaderCell;
import com.my.televip.virtuals.ui.Cells.ShadowSectionCell;
import com.my.televip.virtuals.ui.Cells.TextCheckCell;
import com.my.televip.virtuals.ui.Cells.TextInfoCell;
import com.my.televip.virtuals.ui.Cells.TextSettingsCell;

import java.util.Locale;

/** Turns each of ConfigManager's items into a row of TeleVip's settings list. */
public class SettingsAdapter {

    private static boolean isLongText = false;

    public static int getRowCount() {
        return ConfigManager.getItems().size();
    }

    /** One row: its view, and the cell inside it that {@link #bind} fills in. */
    public static final class Row {
        final View itemView;
        HeaderCell header;
        TextCheckCell check;
        TextSettingsCell settings;
        ExpandableTextCheckCell expandable;
        TextInfoCell info;

        private Row(View itemView) {
            this.itemView = itemView;
        }
    }

    /** An empty row for the item at {@code position}, made of the client's own cells. */
    public static Row createRow(Context context, int position) {
        Row row;
        switch (ConfigManager.getItems().get(position).getType()) {
            case ConfigItem.SWITCH: {
                TextCheckCell cell = SettingsCells.createTextCheckCell(context);
                row = new Row(cell.getView());
                row.check = cell;
                break;
            }
            case ConfigItem.TEXT: {
                TextSettingsCell cell = SettingsCells.createTextSettingsCell(context);
                row = new Row(cell.getView());
                row.settings = cell;
                break;
            }
            case ConfigItem.DIVIDER:
                row = new Row(new ShadowSectionCell(context).getView());
                break;
            case ConfigItem.INFO: {
                TextInfoCell cell = SettingsCells.createTextInfoCell(context);
                row = new Row(cell);
                row.info = cell;
                break;
            }
            case ConfigItem.EXPANDABLE_SWITCH: {
                ExpandableTextCheckCell cell = SettingsCells.createExpandableTextCheckCell(context);
                row = new Row(cell);
                row.expandable = cell;
                break;
            }
            case ConfigItem.HEADER:
            default: {
                HeaderCell cell = SettingsCells.createHeaderCell(context);
                row = new Row(cell.getView());
                row.header = cell;
                break;
            }
        }
        return row;
    }

    /** Shows the item at {@code position} in {@code row} and wires up its clicks. */
    public static void bind(Row row, SettingsController settingsController, int position) {
        try {
            ConfigItem item = ConfigManager.getItems().get(position);
            int viewType = item.getType();

            switch (viewType) {
                case ConfigItem.HEADER:
                    row.header.setText(Translator.get(item.getKey()));
                    break;

                case ConfigItem.SWITCH:
                    TextCheckCell textCheck = row.check;
                    if (item.getValue() != null) {
                        textCheck.setTextAndValueAndCheck(
                                Translator.get(item.getKey()),
                                item.getValue(),
                                item.isEnable(),
                                true,
                                false
                        );
                    } else if (item.isRestartRequired()) {
                        textCheck.setTextAndValueAndCheck(
                                Translator.get(item.getKey()),
                                Translator.get(Keys.RestartRequired),
                                item.isEnable(),
                                true,
                                false
                        );
                    } else {
                        textCheck.setTextAndCheck(
                                Translator.get(item.getKey()),
                                item.isEnable(),
                                false
                        );
                    }
                    TextView title = textCheck.getTextView();
                    if (title != null) {   // long titles wrap where the title is a TextView
                        title.setLines(0);
                        title.setMaxLines(0);
                        title.setSingleLine(false);
                        title.setEllipsize(null);
                    }
                    break;
                case ConfigItem.EXPANDABLE_SWITCH:
                    row.expandable.addChildren(item);

                    break;
                case ConfigItem.TEXT:
                    TextSettingsCell settingsCell = row.settings;
                    if (item.getKey().equals(Keys.Calendar)) {
                        String value = null;
                        switch (item.getCustomCalendar()) {
                            case 0:
                                value = Translator.get(Keys.Gregorian);
                                break;
                            case 1:
                                value = Translator.get(Keys.Hijri);
                                break;
                            case 2:
                                value = Translator.get(Keys.Persian);
                                break;
                        }
                        settingsCell.setTextAndValue(Translator.get(item.getKey()), value, false, false);
                    } else {
                        settingsCell.setText(Translator.get(item.getKey()), false);
                        settingsCell.getTextView().setTextColor(Theme.getTextBlueColor());
                    }
                    break;
                case ConfigItem.DIVIDER:
                    row.itemView.setBackgroundColor(Theme.getBackgroundGrayColor());
                    break;
                case ConfigItem.INFO:
                    TextView textView = row.info.getTextView();
                    if (item.getKey().equals(Keys.OfflineVisibilityInfo)) {
                        textView.setMaxLines(2);
                        textView.setEllipsize(TextUtils.TruncateAt.END);
                        textView.setText(Translator.get(Keys.OfflineVisibilityInfo));
                        if (textView.getMaxLines() == 2) {
                            isLongText = false;
                        }
                        textView.setOnClickListener(v -> {
                            if (!isLongText) {
                                textView.setMaxLines(Integer.MAX_VALUE);
                                textView.setEllipsize(null);
                                isLongText = true;
                            } else {
                                textView.setMaxLines(2);
                                textView.setEllipsize(TextUtils.TruncateAt.END);
                                textView.setText(Translator.get(Keys.OfflineVisibilityInfo));
                                isLongText = false;
                            }
                        });
                    }
                    break;
            }
            row.itemView.setOnLongClickListener(v -> {
                playAudio(settingsController.getContext());
                return true;
            });

            row.itemView.setOnClickListener(v -> {

                if (viewType == ConfigItem.SWITCH) {
                    boolean checked = !row.check.isChecked();
                    row.check.setChecked(checked);
                    item.setEnable(checked);
                    item.run();

                    if (checked && Keys.SecretMediaSave.equals(item.getKey())) {
                        showSecretMediaSaveDialog(settingsController.getContext());
                    }
                } else if (viewType == ConfigItem.TEXT) {
                    switch (item.getKey()) {
                        case Keys.DeveloperChannel:
                            Browser.openUrl(settingsController.getContext(), "https://t.me/t_l0_e");
                            settingsController.hide();
                            break;
                        case Keys.RestartApp:
                            Intent intent = settingsController.getContext()
                                    .getPackageManager()
                                    .getLaunchIntentForPackage(Utils.pkgName);

                            if (intent != null) {
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                settingsController.getContext().startActivity(intent);
                            }

                            ((Activity) settingsController.getContext()).finishAffinity();
                            android.os.Process.killProcess(android.os.Process.myPid());
                            break;
                        case Keys.Calendar:
                            Dialog dlg = DialogUtils.createSingleChoiceDialog((Activity) settingsController.getContext(), new String[]{
                                            Translator.get(Keys.Gregorian), Translator.get(Keys.Hijri), Translator.get(Keys.Persian)},
                                    Translator.get(Keys.Calendar), item.getCustomCalendar(), (dialog, which) -> {
                                        item.setCustomCalendar(which);
                                        item.run();
                                        bind(row, settingsController, position);
                                    });
                            dlg.show();
                            break;
                    }
                }
            });

        } catch (Throwable t){
            Logger.e(t);
        }

    }

    public static void playAudio(Context context) {
        if (audio.playing) {
            audio.stop();
        } else {
            audio.start();
            DialogUtils.showQuranAlert(context);
        }
    }

    private static void showSecretMediaSaveDialog(Context context) {
        try {
            AlertDialog dialog = new AlertDialog(context);
            dialog.setTitle(Translator.get(Keys.SecretMediaSave));

            String lang = Locale.getDefault().getLanguage();
            boolean isIndonesian = "in".equalsIgnoreCase(lang) || "id".equalsIgnoreCase(lang);
            String message = isIndonesian
                    ? "Saat membuka media rahasia / view-once:\n\n1. Pastikan langsung menyimpannya ke galeri (menu titik tiga > Save to gallery) sebelum menutup foto.\n2. Begitu foto dibuka, status di pengirim otomatis langsung berubah menjadi \"Foto Telah Kadaluarsa\".\n3. Balon foto di chat akan hilang setelah ditutup, namun foto yang sudah disimpan ke galeri tetap aman di HP Anda."
                    : Translator.get(Keys.SecretMediaSaveInfo);

            dialog.setMessage(message);
            dialog.setPositiveButton(Translator.get(Keys.Done), null);
            dialog.show();
        } catch (Throwable t) {
            Logger.e(t);
        }
    }

}
