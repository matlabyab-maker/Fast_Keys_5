package com.fastkeys1;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private EditText testField;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(28, 28, 28, 28);

        TextView title = new TextView(this);
        title.setText("Fast_Keys_1\nآماده‌سازی و تست کیبورد");
        title.setTextSize(23);
        box.addView(title);

        Button settings = new Button(this);
        settings.setText("۱. فعال‌سازی Fast_Keys_1");
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        box.addView(settings);

        Button picker = new Button(this);
        picker.setText("۲. انتخاب Fast_Keys_1");
        picker.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        box.addView(picker);

        testField = new EditText(this);
        testField.setHint("اینجا لمس کنید و کیبورد را تست کنید");
        testField.setSingleLine(false);
        box.addView(testField, new LinearLayout.LayoutParams(-1, 180));

        Button show = new Button(this);
        show.setText("۳. نمایش کیبورد برای تست");
        show.setOnClickListener(v -> {
            testField.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(testField, InputMethodManager.SHOW_IMPLICIT);
        });
        box.addView(show);

        Button overlay = new Button(this);
        overlay.setText("اجازه نمایش روی برنامه‌ها");
        overlay.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()))));
        box.addView(overlay);

        setContentView(box);
    }
}
