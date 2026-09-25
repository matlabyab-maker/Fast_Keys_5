package com.fastkeys1;

import android.app.AlertDialog;
import android.graphics.*;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.view.*;
import android.content.DialogInterface;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
import java.util.*;

public class FastKeysKeyboardView extends View {
    private final FastKeysInputMethodService service;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler();
    private float gap, keyH;
    private boolean caps = false;
    private Runnable repeat;
    // User-controlled keyboard resize mode. Height is persisted locally.
    private boolean resizeMode = false;
    private boolean resizingKeyboard = false;
    private float resizeStartY = 0f;
    private int resizeStartHeight = 0;
    private final int defaultHeightDp = 320;
    private final int minHeightDp = 220;
    private final int maxHeightDp = 620;
    // Visual key-press light; this changes only the pressed-key appearance.
    private boolean pressGlow = false;
    private boolean pressHeld = false;
    private float pressL, pressT, pressR, pressB;
    private final Runnable clearPressGlow = () -> {
        if (!pressHeld) {
            pressGlow = false;
            invalidate();
        }
    };
    private final String[] suggestions = new String[6];
    private float suggestionH;
    private static final String[][] WORDS = {
        {"سلام","سلامت","سلامتی"},{"من","منم","منطقه"},{"این","اینجا","اینجانب"},
        {"برای","برنامه","بررسی"},{"کیبورد","کیبوردی","کیبوردها"},{"است","استفاده","استان"},
        {"یک","یکی","یکم"},{"دارم","دارد","دارند"},{"می","میرم","میز"},{"خوب","خوبه","خوبی"},
        {"تایپ","تایپی","تایپ کردن"},{"کلمه","کلمات","کلمه‌های"}
    };
    private final int BG=Color.rgb(239,238,232), DEFAULT_KEY=Color.rgb(250,249,244),
            BLUE=Color.rgb(20,112,235), NAVY=Color.rgb(18,38,78), BLACK=Color.rgb(25,29,34),
            GREEN=Color.rgb(45,205,55), ENTER_BG=Color.rgb(225,238,255), BACKSPACE_BG=Color.rgb(255,232,232), NUMBER_BG=Color.rgb(232,231,224), SPACE_BG=Color.rgb(255,232,120);
    private boolean englishMode = false;
    private int KEY;
    private boolean nastaliqEnabled = false;
    private Typeface nastaliqTypeface;

    public FastKeysKeyboardView(FastKeysInputMethodService s){
        super(s);
        service=s;
        KEY = service.getSharedPreferences("fast_keys_settings", android.content.Context.MODE_PRIVATE)
                .getInt("keyboard_key_color", DEFAULT_KEY);
        setBackgroundColor(BG);
        try { nastaliqTypeface = Typeface.createFromAsset(getContext().getAssets(), "NotoNastaliqUrdu-Regular.ttf"); } catch (Exception ignored) { nastaliqTypeface = Typeface.create("serif", Typeface.NORMAL); }
        nastaliqEnabled = service.getSharedPreferences("fast_keys_settings", 0).getBoolean("nastaliq", false);
        int savedAlpha = service.getSharedPreferences("fast_keys_settings", 0).getInt("keyboard_alpha", 100);
        setAlpha(Math.max(1, Math.min(100, savedAlpha)) / 100f);
        post(() -> applyKeyboardHeightDp(savedKeyboardHeightDp()));
    }

    private void txt(Canvas c,String s,float x,float y,float size,int color){
        p.setTypeface(nastaliqEnabled && nastaliqTypeface != null ? nastaliqTypeface : Typeface.create("sans",Typeface.NORMAL));
        p.setTextSize(size);
        p.setColor(color);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(s,x,y-(p.ascent()+p.descent())/2,p);
    }

    private void key(Canvas c,float l,float t,float r,float b,String label,int color,boolean square){
        p.setColor(KEY);
        p.setStyle(Paint.Style.FILL);
        float rad=square?3:7;
        c.drawRoundRect(l,t,r,b,rad,rad,p);
        p.setColor(Color.rgb(205,204,199));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1);
        c.drawRoundRect(l,t,r,b,rad,rad,p);
        p.setStyle(Paint.Style.FILL);
        if(label!=null&&!label.isEmpty())
            txt(c,label,(l+r)/2,(t+b)/2,Math.min(22,(b-t)*.42f),color);
    }

    private void keyWithBackground(Canvas c,float l,float t,float r,float b,String label,int textColor,int backgroundColor,boolean square){
        p.setColor(backgroundColor); p.setStyle(Paint.Style.FILL);
        float rad=square?3:7; c.drawRoundRect(l,t,r,b,rad,rad,p);
        p.setColor(Color.rgb(205,204,199)); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1);
        c.drawRoundRect(l,t,r,b,rad,rad,p); p.setStyle(Paint.Style.FILL);
        if(label!=null&&!label.isEmpty()) txt(c,label,(l+r)/2,(t+b)/2,Math.min(22,(b-t)*.42f),textColor);
    }

    private void magnifierIcon(Canvas c,float cx,float cy,float size,boolean active){
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2,size*.10f));
        p.setColor(active?GREEN:NAVY);
        c.drawCircle(cx-size*.10f,cy-size*.10f,size*.25f,p);
        c.drawLine(cx+size*.08f,cy+size*.08f,cx+size*.30f,cy+size*.30f,p);
        p.setStyle(Paint.Style.FILL);
    }

    private boolean drawerOpen = false;

    private void showDrawer() {
        final LinearLayout panel = new LinearLayout(service);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(18, 12, 18, 12);

        TextView title = new TextView(service);
        title.setText("امکانات");
        title.setTextSize(20);
        title.setTextColor(BLACK);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(-1, 54));

        ScrollView scroll = new ScrollView(service);
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(service);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        Button transparency = drawerButton("شفافیت کیبورد");
        Button palette = drawerButton("رنگ کیبورد");
        Button emoji = drawerButton("انتخاب Emoji");
        Button emojiMaker = drawerButton("ساخت Emoji");
        Button nastaliq = drawerButton(nastaliqEnabled ? "فونت نستعلیق: روشن" : "فونت نستعلیق: خاموش");
        Button nastaliqKeyboard = drawerButton("کیبورد نستعلیق");
        Button steering = drawerButton("فرمان ماشین");
        Button arabic = drawerButton("حرکت‌ها و صداهای عربی");
        Button history = drawerButton("تاریخچه کلیپ‌بورد (۱۰۰)");
        Button magnifierButton = drawerButton("ذره‌بین");
        Button resize = drawerButton("Resize / Float");
        Button mouse = drawerButton("موس صفحه وب");

        Button recorder = drawerButton("ضبط صدای Fast Keys");
        Button[] buttons={transparency,palette,emoji,emojiMaker,nastaliq,nastaliqKeyboard,steering,arabic,history,magnifierButton,resize,mouse,recorder};
        for(Button b:buttons) list.addView(b);

        final PopupWindow popup = new PopupWindow(panel,
                Math.min((int)(Math.max(320,getWidth()) * 0.94f), 700),
                Math.min(dp(520), Math.max(dp(260), getHeight() - dp(12))), false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        popup.setOutsideTouchable(true);
        popup.setTouchable(true);
        popup.setFocusable(false); // Do not steal focus from the editor / close the IME.
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        popup.setElevation(8f);

        transparency.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showTransparency));
        palette.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showColorPalette));
        emoji.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showEmojiPicker));
        emojiMaker.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showEmojiMaker));
        nastaliq.setOnClickListener(v -> {
            nastaliqEnabled = !nastaliqEnabled;
            service.getSharedPreferences("fast_keys_settings", 0).edit().putBoolean("nastaliq", nastaliqEnabled).apply();
            popup.dismiss(); invalidate();
        });
        nastaliqKeyboard.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showNastaliqKeyboard));
        steering.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showSteeringWheel));
        arabic.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showArabicHarakat));
        history.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showClipboardHistory));
        magnifierButton.setOnClickListener(v -> {
            popup.dismiss();
            service.launchScreenMagnifier();
        });
        resize.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showResizeFloatInfo));
        mouse.setOnClickListener(v -> showAndKeepKeyboard(popup, this::showMouseControls));
        recorder.setOnClickListener(v -> { popup.dismiss(); service.toggleRecorder(); });

        popup.showAtLocation(this, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, dp(6));
        drawerOpen = true;
        popup.setOnDismissListener(() -> drawerOpen = false);
    }

    private void showAndKeepKeyboard(PopupWindow popup, Runnable action) {
        popup.dismiss();
        postDelayed(action, 80);
    }

    private Button drawerButton(String text) {
        Button b = new Button(service);
        b.setText(text);
        b.setTextSize(16);
        b.setTextColor(NAVY);
        b.setAllCaps(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 58);
        lp.setMargins(0, 3, 0, 3);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    private void applyKeyboardHeightDp(int heightDp) {
        int clamped = Math.max(minHeightDp, Math.min(maxHeightDp, heightDp));
        android.view.ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) lp = new android.view.ViewGroup.LayoutParams(-1, dp(clamped));
        lp.height = dp(clamped);
        lp.width = -1;
        setLayoutParams(lp);
        requestLayout();
        invalidate();
        service.getSharedPreferences("fast_keys_settings", 0).edit()
                .putInt("keyboard_height_dp", clamped).apply();
    }

    private int savedKeyboardHeightDp() {
        return service.getSharedPreferences("fast_keys_settings", 0)
                .getInt("keyboard_height_dp", defaultHeightDp);
    }

    private void enterResizeMode() {
        resizeMode = true;
        invalidate();
    }

    private void exitResizeMode() {
        resizeMode = false;
        resizingKeyboard = false;
        invalidate();
    }

    private void showResizeFloatInfo() {
        LinearLayout root=new LinearLayout(service);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,16,24,16);

        TextView title=new TextView(service);
        title.setText("Resize / Float");
        title.setTextSize(20); title.setGravity(Gravity.CENTER); title.setTextColor(BLACK);
        root.addView(title,new LinearLayout.LayoutParams(-1,54));

        TextView info=new TextView(service);
        info.setText("برای تغییر اندازه، ابتدا «تغییر اندازه» را بزنید و سپس گوشه پایین‌راست کیبورد را بکشید.\nReset اندازه پیش‌فرض را برمی‌گرداند.");
        info.setTextSize(16);
        root.addView(info,new LinearLayout.LayoutParams(-1,110));

        Button resize=new Button(service);
        resize.setText("تغییر اندازه");
        root.addView(resize);

        Button reset=new Button(service);
        reset.setText("Reset");
        reset.setOnClickListener(v -> {
            service.getSharedPreferences("fast_keys_settings",0).edit().remove("keyboard_height_dp").apply();
            applyKeyboardHeightDp(defaultHeightDp);
            exitResizeMode();
        });
        root.addView(reset);

        Button okay=new Button(service);
        okay.setText("Okay");
        root.addView(okay);

        AlertDialog d=new AlertDialog.Builder(service).setView(root).create();
        resize.setOnClickListener(v->{ enterResizeMode(); d.dismiss(); });
        okay.setOnClickListener(v->{ exitResizeMode(); d.dismiss(); });
        d.show();
    }

    private void showClipboardHistory() {
        final List<String> items = service.getClipboardHistory();
        LinearLayout root = new LinearLayout(service);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 8, 12, 8);
        TextView title = new TextView(service);
        title.setText("تاریخچه کلیپ‌بورد — ۱۰۰ مورد آخر");
        title.setTextSize(19); title.setTextColor(BLACK); title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, 54));
        ScrollView scroll = new ScrollView(service);
        LinearLayout list = new LinearLayout(service); list.setOrientation(LinearLayout.VERTICAL);
        if (items.isEmpty()) {
            TextView empty = new TextView(service); empty.setText("هنوز موردی در تاریخچه نیست"); empty.setGravity(Gravity.CENTER); empty.setTextSize(17);
            list.addView(empty, new LinearLayout.LayoutParams(-1, 80));
        } else {
            for (int i=0;i<items.size();i++) {
                final String item=items.get(i);
                Button b=drawerButton((i+1)+"  "+item.replace("\n"," "));
                b.setOnClickListener(v -> {
                    service.pasteHistory(item);
                });
                list.addView(b);
            }
        }
        scroll.addView(list); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        Button close=drawerButton("بستن"); root.addView(close);
        final PopupWindow popup=new PopupWindow(root, Math.min(dp(380), Math.max(dp(300), getWidth()-dp(16))), Math.min(dp(620), Math.max(dp(360), getHeight()-dp(16))), false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        close.setOnClickListener(v -> popup.dismiss());
        popup.showAtLocation(this, Gravity.CENTER, 0, 0);
    }

    private void showColorPalette() {
        final int[] colors = {
                Color.rgb(250,249,244), Color.rgb(255,255,255), Color.rgb(245,245,245),
                Color.rgb(255,244,230), Color.rgb(255,235,235), Color.rgb(235,245,255),
                Color.rgb(235,250,240), Color.rgb(245,238,255), Color.rgb(255,248,205),
                Color.rgb(225,240,235), Color.rgb(235,235,225), Color.rgb(225,230,240)
        };
        LinearLayout root=new LinearLayout(service); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(14,8,14,8);
        TextView title=new TextView(service); title.setText("رنگ کیبورد"); title.setTextSize(19); title.setTextColor(NAVY); title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,52));
        GridLayout grid=new GridLayout(service); grid.setColumnCount(4);
        for(int color:colors){
            Button b=new Button(service); b.setText(""); b.setBackgroundColor(color);
            b.setOnClickListener(v->{ KEY=color; service.getSharedPreferences("fast_keys_settings",0).edit().putInt("keyboard_key_color",color).apply(); invalidate(); });
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=70; lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); lp.setMargins(4,4,4,4); grid.addView(b,lp);
        }
        root.addView(grid,new LinearLayout.LayoutParams(-1,250));
        Button close=drawerButton("بستن"); root.addView(close,new LinearLayout.LayoutParams(-1,58));
        PopupWindow popup=new PopupWindow(root,Math.min(dp(380),Math.max(dp(300),getWidth()-dp(16))),Math.min(dp(430),Math.max(dp(330),getHeight()-dp(16))),false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE)); popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true); popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        close.setOnClickListener(v->popup.dismiss()); popup.showAtLocation(this,Gravity.CENTER,0,0);
    }

    private void showTransparency() {
        LinearLayout root=new LinearLayout(service); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(28,10,28,10);
        TextView title=new TextView(service); title.setText("شفافیت کیبورد"); title.setTextSize(19); title.setTextColor(BLACK); title.setGravity(Gravity.CENTER); root.addView(title,new LinearLayout.LayoutParams(-1,52));
        SeekBar bar=new SeekBar(service); bar.setMax(99); int current=Math.max(1,Math.min(100,Math.round(getAlpha()*100f))); bar.setProgress(current-1); root.addView(bar,new LinearLayout.LayoutParams(-1,56));
        TextView value=new TextView(service); value.setText(current+"%"); value.setTextSize(17); value.setTextColor(BLACK); value.setGravity(Gravity.CENTER); root.addView(value,new LinearLayout.LayoutParams(-1,48));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar b,int progress,boolean fromUser){int v=progress+1; value.setText(v+"%"); setAlpha(v/100f); service.getSharedPreferences("fast_keys_settings",0).edit().putInt("keyboard_alpha",v).apply();} public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){} });
        Button close=drawerButton("بستن"); root.addView(close,new LinearLayout.LayoutParams(-1,58));
        PopupWindow popup=new PopupWindow(root,Math.min(dp(380),Math.max(dp(300),getWidth()-dp(16))),Math.min(dp(300),Math.max(dp(260),getHeight()-dp(16))),false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE)); popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true); popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        close.setOnClickListener(v->popup.dismiss()); popup.showAtLocation(this,Gravity.CENTER,0,0);
    }

    private void showEmojiPicker() {
        final android.view.inputmethod.InputConnection target = service.getCurrentInputConnection();
        final String[] emojis = {
                "😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇","🙂","🙃",
                "😉","😌","😍","🥰","😘","😗","😙","😚","😋","😛","😝","😜",
                "🤪","🤨","🧐","🤓","😎","🤩","🥳","😏","😒","😞","😔","😟",
                "😕","🙁","☹️","😣","😖","😫","😩","🥺","😢","😭","😤","😠",
                "😡","🤬","🤯","😳","🥵","🥶","😱","😨","😰","😥","😓","🤗",
                "🤔","🤭","🤫","🤥","😶","😐","😑","😬","🙄","😯","😦","😧",
                "😮","😲","🥱","😴","🤤","😪","😵","🤐","🥴","🤢","🤮","🤧",
                "😷","🤒","🤕","❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎",
                "💔","❣️","💕","💞","💓","💗","💖","💘","💝","💟","👍","👎",
                "👏","🙌","🙏","🤝","👌","✌️","🤞","🤟","🤘","🤙","👋","💪",
                "🔥","⭐","✨","🎉","🎊","💯","✅","❌","⚡","🌹","🌸","🌺"
        };
        LinearLayout root=new LinearLayout(service); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(8,8,8,8);
        TextView title=new TextView(service); title.setText("انتخاب Emoji"); title.setTextSize(20); title.setGravity(Gravity.CENTER); title.setTextColor(NAVY);
        root.addView(title,new LinearLayout.LayoutParams(-1,54));
        ScrollView scroll=new ScrollView(service); GridLayout grid=new GridLayout(service); grid.setColumnCount(6); grid.setPadding(6,6,6,6);
        for(String emoji:emojis){
            Button b=new Button(service); b.setText(emoji); b.setTextSize(24); b.setAllCaps(false);
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=dp(52); lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); lp.setMargins(1,1,1,1); grid.addView(b,lp);
            b.setOnClickListener(v -> service.type(emoji));
        }
        scroll.addView(grid); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        Button close=drawerButton("بستن"); root.addView(close,new LinearLayout.LayoutParams(-1,58));
        final PopupWindow popup=new PopupWindow(root,Math.min((int)(getWidth()*0.96f),720),Math.min(dp(520),Math.max(dp(300),getHeight()-dp(10))),false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE)); popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true); popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        close.setOnClickListener(v->popup.dismiss());
        popup.showAtLocation(this,Gravity.TOP|Gravity.CENTER_HORIZONTAL,0,dp(6));
    }

    private void showNastaliqKeyboard() {
        final String[] rows = {"ضصثقفغعهخحجچ","شسیبلتاکگمنپ","ظطزرذدئؤء،؟"};
        LinearLayout root = new LinearLayout(service); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(8,8,8,8);
        TextView title = new TextView(service); title.setText("کیبورد نستعلیق"); title.setTextSize(20); title.setGravity(Gravity.CENTER); title.setTextColor(NAVY);
        root.addView(title,new LinearLayout.LayoutParams(-1,56));
        for(String row : rows){
            LinearLayout line = new LinearLayout(service); line.setGravity(Gravity.CENTER); line.setOrientation(LinearLayout.HORIZONTAL);
            for(int i=0;i<row.length();i++){
                final String ch=String.valueOf(row.charAt(i)); Button b=new Button(service); b.setText(ch); b.setTextSize(24); b.setTypeface(nastaliqTypeface);
                b.setAllCaps(false); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(58),1f); lp.setMargins(2,2,2,2); line.addView(b,lp); b.setOnClickListener(v->{ if (service.getCurrentInputConnection()!=null) service.getCurrentInputConnection().commitText(ch,1); });
            }
            root.addView(line);
        }
        Button space=drawerButton("Space"); space.setOnClickListener(v->service.type(" ")); root.addView(space);
        new AlertDialog.Builder(service).setView(root).setNegativeButton("بستن",null).show();
    }

    private void showEmojiMaker() {
        final android.view.inputmethod.InputConnection target = service.getCurrentInputConnection();
        LinearLayout root = new LinearLayout(service);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 10, 24, 10);

        TextView title = new TextView(service);
        title.setText("ساخت Emoji");
        title.setTextSize(19);
        title.setTextColor(BLACK);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, 52));

        EditText input = new EditText(service);
        input.setHint("ترکیب Emoji را وارد کنید");
        input.setTextSize(24);
        input.setGravity(Gravity.CENTER);
        root.addView(input, new LinearLayout.LayoutParams(-1, 70));

        GridLayout quick = new GridLayout(service);
        quick.setColumnCount(4);
        String[] parts = {"😀","😂","❤️","👍","🔥","⭐","✨","😎"};
        for (String part : parts) {
            Button b = new Button(service);
            b.setText(part);
            b.setTextSize(22);
            b.setAllCaps(false);
            b.setOnClickListener(v -> input.append(part));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = 62;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(2, 2, 2, 2);
            quick.addView(b, lp);
        }
        root.addView(quick, new LinearLayout.LayoutParams(-1, 132));

        new AlertDialog.Builder(service)
                .setView(root)
                .setPositiveButton("ساخت و درج", (d, which) -> {
                    String emojiText = input.getText().toString();
                    if (!emojiText.isEmpty()) service.type(emojiText);
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private void showSteeringWheel() {
        final SteeringView wheel = new SteeringView(service);
        final PopupWindow popup = new PopupWindow(wheel, 360, 430, false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        popup.setOutsideTouchable(false);
        popup.setElevation(12f);
        wheel.setPopup(popup);
        popup.showAtLocation(this, Gravity.CENTER, 0, 0);
    }

    private class SteeringView extends View {
        private final Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private PopupWindow popup;
        private float cx, cy, radius;
        private float downX, downY, startX, startY;
        private boolean resizing;
        private long lastMove;
        private RectF upRect = new RectF(), downRect = new RectF(), leftRect = new RectF(), rightRect = new RectF();
        private RectF closeRect = new RectF(), padRect = new RectF();

        SteeringView(android.content.Context c) {
            super(c);
            setBackgroundColor(Color.WHITE);
        }

        void setPopup(PopupWindow p) { popup = p; }

        private void button(Canvas c, RectF r, String label) {
            wp.setStyle(Paint.Style.FILL);
            wp.setColor(Color.rgb(245,245,245));
            c.drawRoundRect(r, 14, 14, wp);
            wp.setStyle(Paint.Style.STROKE);
            wp.setStrokeWidth(2);
            wp.setColor(NAVY);
            c.drawRoundRect(r, 14, 14, wp);
            wp.setStyle(Paint.Style.FILL);
            wp.setTextAlign(Paint.Align.CENTER);
            wp.setTextSize(30);
            wp.setColor(NAVY);
            c.drawText(label, r.centerX(), r.centerY()-(wp.ascent()+wp.descent())/2, wp);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float w=getWidth(), h=getHeight();
            wp.setStyle(Paint.Style.FILL);
            wp.setColor(Color.rgb(255,255,255));
            c.drawRect(0,0,w,h,wp);

            // Close button on the mouse control window itself.
            closeRect.set(w-62, 10, w-10, 54);
            wp.setColor(Color.rgb(235,235,235));
            c.drawRoundRect(closeRect, 12, 12, wp);
            wp.setColor(NAVY); wp.setTextSize(18); wp.setTextAlign(Paint.Align.CENTER);
            c.drawText("Close", closeRect.centerX(), closeRect.centerY()-(wp.ascent()+wp.descent())/2, wp);

            float padSize=Math.min(w-90, 260);
            float padLeft=(w-padSize)/2f;
            float padTop=72;
            padRect.set(padLeft,padTop,padLeft+padSize,padTop+padSize);
            wp.setColor(Color.rgb(245,247,250));
            c.drawRoundRect(padRect, 22, 22, wp);
            wp.setStyle(Paint.Style.STROKE); wp.setStrokeWidth(3); wp.setColor(NAVY);
            c.drawRoundRect(padRect,22,22,wp); wp.setStyle(Paint.Style.FILL);
            wp.setColor(Color.rgb(210,214,220));
            c.drawLine(padRect.centerX(),padRect.top+18,padRect.centerX(),padRect.bottom-18,wp);
            c.drawLine(padRect.left+18,padRect.centerY(),padRect.right-18,padRect.centerY(),wp);
            wp.setColor(NAVY);
            c.drawCircle(padRect.centerX(),padRect.centerY(),24,wp);

            float bs=58, gapB=10;
            float bx=w/2f-bs/2f;
            upRect.set(bx, padRect.bottom+16, bx+bs, padRect.bottom+16+bs);
            downRect.set(bx, upRect.bottom+gapB, bx+bs, upRect.bottom+gapB+bs);
            leftRect.set(bx-bs-gapB, upRect.top, bx-gapB, upRect.bottom);
            rightRect.set(bx+bs+gapB, upRect.top, bx+2*bs+gapB, upRect.bottom);
            button(c,upRect,"↑"); button(c,downRect,"↓"); button(c,leftRect,"←"); button(c,rightRect,"→");

            wp.setColor(Color.rgb(130,130,130));
            c.drawRect(w-22,h-22,w-4,h-4,wp);
        }

        private void moveBy(float dx, float dy) {
            if(Math.abs(dx)>Math.abs(dy))
                service.move(dx<0?KeyEvent.KEYCODE_DPAD_LEFT:KeyEvent.KEYCODE_DPAD_RIGHT);
            else
                service.move(dy<0?KeyEvent.KEYCODE_DPAD_UP:KeyEvent.KEYCODE_DPAD_DOWN);
        }

        private void handleButton(float x,float y){
            if(closeRect.contains(x,y)){ popup.dismiss(); return; }
            if(upRect.contains(x,y)){ service.move(KeyEvent.KEYCODE_DPAD_UP); return; }
            if(downRect.contains(x,y)){ service.move(KeyEvent.KEYCODE_DPAD_DOWN); return; }
            if(leftRect.contains(x,y)){ service.move(KeyEvent.KEYCODE_DPAD_LEFT); return; }
            if(rightRect.contains(x,y)){ service.move(KeyEvent.KEYCODE_DPAD_RIGHT); return; }
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float x=e.getX(), y=e.getY();
            if(e.getAction()==MotionEvent.ACTION_DOWN){
                handleButton(x,y);
                if(closeRect.contains(x,y)||upRect.contains(x,y)||downRect.contains(x,y)||leftRect.contains(x,y)||rightRect.contains(x,y)) return true;
                downX=x; downY=y; startX=getTranslationX(); startY=getTranslationY();
                resizing=(x>getWidth()-35 && y>getHeight()-35);
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                if(resizing){
                    int nw=Math.max(300,(int)(getWidth()+x-downX));
                    int nh=Math.max(360,(int)(getHeight()+y-downY));
                    popup.setWidth(nw); popup.setHeight(nh);
                    downX=x; downY=y;
                }else if(padRect.contains(x,y)){
                    moveBy(x-downX,y-downY);
                    downX=x; downY=y;
                }else{
                    setTranslationX(startX+x-downX);
                    setTranslationY(startY+y-downY);
                }
                invalidate();
                return true;
            }
            if(e.getAction()==MotionEvent.ACTION_UP){
                resizing=false;
                return true;
            }
            return true;
        }
    }

    private void showArabicHarakat() {
        final String[][] groups = {
                {"َ","فتحه / صدای کوتاه a"},
                {"ِ","کسره / صدای کوتاه i"},
                {"ُ","ضمه / صدای کوتاه u"},
                {"ْ","سکون"},
                {"ّ","تشدید"},
                {"ً","تنوین فتح"},
                {"ٍ","تنوین کسر"},
                {"ٌ","تنوین ضم"},
                {"ٓ","مدّ"},
                {"ٰ","الف خنجری"},
                {"ٔ","همزه بالا"},
                {"ٕ","همزه پایین"},
                {"ٖ","نشان کوچک زیر"},
                {"ٗ","نشان کوچک بالا"},
                {"َا","صدای بلند آ / ا"},
                {"ِی","صدای بلند ای / ی"},
                {"ُو","صدای بلند او / و"},
                {"ٱ","الف وصل"}
        };

        LinearLayout root = new LinearLayout(service);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 10, 20, 10);

        TextView title = new TextView(service);
        title.setText("حرکت‌ها و صداهای عربی");
        title.setTextSize(20);
        title.setTextColor(BLACK);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 54));

        GridLayout grid = new GridLayout(service);
        grid.setColumnCount(3);
        for (String[] item : groups) {
            Button b = new Button(service);
            b.setText(item[0] + "\n" + item[1]);
            b.setTextSize(14);
            b.setTextColor(NAVY);
            b.setAllCaps(false);
            final String mark = item[0];
            b.setOnClickListener(v -> service.type(mark));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = 72;
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(3, 3, 3, 3);
            grid.addView(b, lp);
        }
        root.addView(grid, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(service)
                .setView(root)
                .setNegativeButton("بستن", null)
                .create();
        dialog.show();
    }

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        gap=dp(3);
        // Six normal key rows plus a half-height suggestion row.
        keyH=(h-gap*8f)/6.5f;
        suggestionH=keyH*0.62f;

        drawKeyboard(c);

        if (resizeMode) {
            // Visible bottom-right resize grip; it appears only in the explicit resize mode.
            p.setColor(NAVY);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            float g = Math.min(28f, Math.min(w, h) * 0.06f);
            c.drawLine(w - g, h - 7, w - 7, h - g, p);
            c.drawLine(w - g - 7, h - 7, w - 7, h - g - 7, p);
            p.setStyle(Paint.Style.FILL);
        }

        if (pressGlow) drawPressGlow(c);

    }

    private void drawKeyboard(Canvas c){
        float w=getWidth();
        float y=gap;

        float[] wt={.55f,1.45f,1.55f,1.05f,1.0f,1.0f,1.0f,1.25f,.55f};
        String[] top={"","Copy All","Copy Screen","Paste","Cut","Undo","Redo","100\nHistory","⌄"};
        row(c,y,wt,top);
        float wtSum = 0f;
        for (float value : wt) wtSum += value;
        drawMicrophone(c, gap, y, gap + wt[0] * ((w-gap*(wt.length+1))/wtSum), y + keyH);

        y+=keyH+gap;
        String[] sug=suggestions;
        for(int i=0;i<6;i++)
            key(c,i*w/6f+gap/2f,y,(i+1)*w/6f-gap/2f,y+suggestionH,sug[i],BLUE,false);

        y+=suggestionH+gap;
        float[] w2={.55f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,1.45f};
        String[] sy={"⌃","!\n۱","@\n۲","#\n۳","$\n۴","%\n۵","^\n۶","&\n۷","*\n۸","(\n۹",")\n۰","_\n-","+\n=","⌫"};
        float total2=0; for(float q:w2) total2+=q;
        float ww2=(w-gap*(w2.length+1))/total2, xx2=gap;
        for(int i=0;i<w2.length;i++){
            float cw=w2[i]*ww2; String s2=sy[i];
            int bg2=(i>=1 && i<=11)?NUMBER_BG:KEY; if(i==13) bg2=BACKSPACE_BG;
            if(s2.contains("\n")){
                keyWithBackground(c,xx2,y,xx2+cw,y+keyH,"",NAVY,bg2,false); String[] aa=s2.split("\n");
                int numberColor = (i>=1 && i<=12) ? Color.rgb(125,78,38) : NAVY;
                txt(c,aa[0],xx2+cw/2,y+keyH*.35f,Math.min(20,keyH*.3f),numberColor);
                txt(c,aa[1],xx2+cw/2,y+keyH*.7f,Math.min(20,keyH*.3f),numberColor);
            } else keyWithBackground(c,xx2,y,xx2+cw,y+keyH,s2,NAVY,bg2,false);
            xx2+=cw+gap;
        }

        y+=keyH+gap;
        drawArrow(c,0,y,keyH,keyH,"↑");
        drawCapsAndMagnifierRow(c,y);

        y+=keyH+gap;
        drawArrow(c,0,y,keyH,keyH,"↓");
        float enterW = keyH + gap;
        String[] r4=englishMode ? new String[]{"a","s","d","f","g","h","j","k","l",";","\""} : new String[]{"ش","س","ی","ب","ل","ت","ا","ک","گ","؛","»"};
        rowFromRightReserved(c,y,keyH,r4,enterW);

        y+=keyH+gap;
        String[] r5=englishMode ? new String[]{"z","x","c","v","b","n","m",",",".","/","?"} : new String[]{"،","ژ","ذ","ز","د","چ","پ","و","ن","م","/\n؟"};
        rowFromRightReservedWithEscape(c,y,keyH,r5,enterW);
        keyWithBackground(c,w-enterW,y-keyH-gap,w,y+keyH+gap,"Enter",NAVY,ENTER_BG,false);

        y+=keyH+gap;
        float[] bw={1,1,1,3.9f,1.35f,1.35f,1.15f,1.15f};
        String[] b={"!#@","◎","😀","Space","←","→","↑","↓"};
        float totalB=0; for(float q:bw) totalB+=q;
        float unitB=(w-gap*(bw.length+1))/totalB, xb=gap;
        for(int i=0;i<b.length;i++){
            float cb=bw[i]*unitB;
            int tc=(i==0)?Color.RED:NAVY;
            int bg=(i==3)?SPACE_BG:KEY;
            keyWithBackground(c,xb,y,xb+cb,y+keyH,b[i],tc,bg,false);
            xb+=cb+gap;
        }
    }

    private void drawCapsAndMagnifierRow(Canvas c,float y){
        float left=keyH+gap;
        float available=getWidth()-left-gap;
        float capsW=keyH*.70f;
        float remaining=available-capsW-gap*14f;
        float normalW=remaining/13f;
        float x=left+gap;

        key(c,x,y,x+capsW,y+keyH,"",NAVY,false);
        txt(c,"Caps",x+capsW/2,y+keyH/2,Math.min(13,keyH*.25f),NAVY);
        if(caps){
            p.setColor(GREEN);
            c.drawCircle(x+10,y+10,4,p);
        }
        x+=capsW+gap;

        String[] letters=englishMode ? new String[]{"Q","W","E","R","T","Y","U","I","O","P","[\n{","]\n}","|\n\\"} : new String[]{"ض","ص","ث","ق","ف","غ","ع","ه","خ","ج","ح","ط","ظ"};
        for(String s:letters){
            if(s.contains("\n")){
                key(c,x,y,x+normalW,y+keyH,"",NAVY,false);
                String[] a=s.split("\\n");
                txt(c,a[0],x+normalW/2,y+keyH*.35f,Math.min(20,keyH*.3f),NAVY);
                txt(c,a[1],x+normalW/2,y+keyH*.7f,Math.min(20,keyH*.3f),NAVY);
            }else{
                key(c,x,y,x+normalW,y+keyH,s,BLUE,false);
            }
            x+=normalW+gap;
        }
    }

    private void drawMicrophone(Canvas c,float l,float t,float r,float b){
        float cx=(l+r)/2f, cy=(t+b)/2f;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(3f,keyH*.09f)); p.setStrokeCap(Paint.Cap.ROUND); p.setColor(FastKeysInputMethodService.isRecordingNow()?Color.rgb(190,25,25):NAVY);
        c.drawRoundRect(cx-keyH*.13f, cy-keyH*.28f, cx+keyH*.13f, cy+keyH*.10f, keyH*.13f, keyH*.13f, p);
        c.drawArc(cx-keyH*.25f, cy-keyH*.10f, cx+keyH*.25f, cy+keyH*.32f, 0, 180, false, p);
        c.drawLine(cx, cy+keyH*.30f, cx, cy+keyH*.43f, p);
        c.drawLine(cx-keyH*.15f, cy+keyH*.43f, cx+keyH*.15f, cy+keyH*.43f, p);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawMousePointer(Canvas c,float l,float t,float r,float b){
        float cx=l+(r-l)*.50f;
        float cy=t+(b-t)*.50f;
        float s=Math.min(r-l,b-t)*.34f;
        Path pointer=new Path();
        pointer.moveTo(cx-s*.65f, cy-s);
        pointer.lineTo(cx-s*.65f, cy+s*.72f);
        pointer.lineTo(cx-s*.08f, cy+s*.30f);
        pointer.lineTo(cx+s*.22f, cy+s*.92f);
        pointer.lineTo(cx+s*.50f, cy+s*.72f);
        pointer.lineTo(cx+s*.20f, cy+s*.12f);
        pointer.lineTo(cx+s*.82f, cy+s*.12f);
        pointer.close();
        p.setStyle(Paint.Style.FILL);
        p.setColor(NAVY);
        c.drawPath(pointer,p);
    }

    private void row(Canvas c,float y,float[] weights,String[] labels){
        float total=0;
        for(float q:weights)total+=q;
        float ww=(getWidth()-gap*(weights.length+1))/total,x=gap;
        for(int i=0;i<weights.length;i++){
            float cw=ww*weights[i];
            String s=labels[i];
            if(s.contains("\n")){
                key(c,x,y,x+cw,y+keyH,"",NAVY,false);
                String[] a=s.split("\\n");
                txt(c,a[0],x+cw/2,y+keyH*.35f,Math.min(20,keyH*.3f),NAVY);
                txt(c,a[1],x+cw/2,y+keyH*.7f,Math.min(20,keyH*.3f),NAVY);
            }else{
                key(c,x,y,x+cw,y+keyH,s,NAVY,false);
            }
            x+=cw+gap;
        }
    }

    private void rowFrom(Canvas c,float y,float left,String[] labels){
        float x=left+gap,available=getWidth()-left-gap;
        float ww=(available-gap*(labels.length+1))/labels.length;
        for(String s:labels){
            key(c,x,y,x+ww,y+keyH,s,BLUE,false);
            x+=ww+gap;
        }
    }

    private void rowFromRightReserved(Canvas c,float y,float left,String[] labels,float reservedRight){
        float x=left+gap;
        float available=getWidth()-left-reservedRight-gap;
        float ww=(available-gap*(labels.length+1))/labels.length;
        for(String s:labels){
            if(s.contains("\n")){
                key(c,x,y,x+ww,y+keyH,"",BLUE,false);
                String[] a=s.split("\n");
                txt(c,a[0],x+ww/2,y+keyH*.35f,Math.min(20,keyH*.3f),NAVY);
                txt(c,a[1],x+ww/2,y+keyH*.7f,Math.min(20,keyH*.3f),NAVY);
            } else key(c,x,y,x+ww,y+keyH,s,BLUE,false);
            x+=ww+gap;
        }
    }

    private void rowFromRightReservedWithEscape(Canvas c,float y,float left,String[] labels,float reservedRight){
        float x=left+gap;
        float available=getWidth()-left-reservedRight-gap;
        float escW=keyH*.70f;
        float remaining=available-escW-gap;
        float ww=(remaining-gap*(labels.length+1))/labels.length;
        key(c,x,y,x+escW,y+keyH,"Escape",NAVY,false);
        x+=escW+gap;
        for(String s:labels){
            if(s.contains("\n")){
                key(c,x,y,x+ww,y+keyH,"",BLUE,false);
                String[] a=s.split("\n");
                txt(c,a[0],x+ww/2,y+keyH*.35f,Math.min(20,keyH*.3f),NAVY);
                txt(c,a[1],x+ww/2,y+keyH*.7f,Math.min(20,keyH*.3f),NAVY);
            } else key(c,x,y,x+ww,y+keyH,s,BLUE,false);
            x+=ww+gap;
        }
    }

    private void drawArrow(Canvas c,float x,float y,float ww,float hh,String s){
        key(c,x,y,x+ww,y+hh,"",NAVY,true);
        txt(c,s,x+ww/2,y+hh/2,hh*.55f,BLUE);
    }

    private void drawPressGlow(Canvas c){
        float pad = Math.max(3f, keyH * .045f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(70, 255, 210, 0));
        c.drawRoundRect(pressL-pad*1.8f, pressT-pad*1.8f, pressR+pad*1.8f, pressB+pad*1.8f, 10, 10, p);
        p.setColor(Color.argb(145, 255, 190, 0));
        c.drawRoundRect(pressL-pad, pressT-pad, pressR+pad, pressB+pad, 8, 8, p);
        p.setColor(Color.argb(110, 255, 245, 120));
        c.drawRoundRect(pressL, pressT, pressR, pressB, 7, 7, p);
    }

    public void refreshSuggestions(){
        CharSequence q=service.getCurrentInputConnection()==null?null:
                service.getCurrentInputConnection().getTextBeforeCursor(80,0);
        String word="";
        if(q!=null){
            String b=q.toString();
            int i=b.length()-1;
            while(i>=0&&!Character.isWhitespace(b.charAt(i)))i--;
            word=b.substring(i+1);
        }
        Arrays.fill(suggestions,"");
        if(word.length()==0){invalidate();return;}
        int n=0;
        for(String[] group:WORDS)
            for(String x:group)
                if(x.startsWith(word)&&!x.equals(word)&&n<6)suggestions[n++]=x;
        if(n==0)
            for(String[] group:WORDS)
                for(String x:group)
                    if(x.contains(word)&&n<6)suggestions[n++]=x;
        invalidate();
    }

    private void setPressGlow(float l, float t, float r, float b, boolean held){
        pressL=l; pressT=t; pressR=r; pressB=b;
        pressHeld=held;
        pressGlow=true;
        handler.removeCallbacks(clearPressGlow);
        if (!held) handler.postDelayed(clearPressGlow, 140);
        invalidate();
    }

    private void clearPressGlowNow(){
        pressHeld=false;
        pressGlow=false;
        handler.removeCallbacks(clearPressGlow);
        invalidate();
    }

    private int getRowAt(float y){
        float t=gap;
        if(y>=t && y<=t+keyH) return 0;
        t += keyH + gap;
        if(y>=t && y<=t+suggestionH) return 1;
        t += suggestionH + gap;
        for(int row=2; row<=6; row++){
            if(y>=t && y<=t+keyH) return row;
            t += keyH + gap;
        }
        return -1;
    }

    private float rowTop(int row){
        float t=gap;
        if(row==0) return t;
        t += keyH + gap;
        if(row==1) return t;
        t += suggestionH + gap;
        return t + (row-2)*(keyH+gap);
    }

    private void pressRectFor(float x, float y, boolean held){
        float w=getWidth(); int row=getRowAt(y); if(row<0||row>6){clearPressGlowNow();return;}
        float t=rowTop(row),b=t+(row==1?suggestionH:keyH),l=gap,r;
        if(row==0){float[] wt={.55f,1.45f,1.55f,1.05f,1,1,1,1.25f,.55f};float total=0;for(float q:wt)total+=q;float u=(w-gap*(wt.length+1))/total;for(int i=0;i<wt.length;i++){r=l+u*wt[i];if(x>=l&&x<=r){setPressGlow(l,t,r,b,held);return;}l=r+gap;}return;}
        if(row==1){float cw=w/6f;int i=Math.max(0,Math.min(5,(int)(x/cw)));setPressGlow(i*cw,t,(i+1)*cw,b,held);return;}
        if(row==2){float[] wt={.55f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,1.45f};float total=0;for(float q:wt)total+=q;float u=(w-gap*(wt.length+1))/total;for(int i=0;i<wt.length;i++){r=l+u*wt[i];if(x>=l&&x<=r){setPressGlow(l,t,r,b,held);return;}l=r+gap;}return;}
        if(row==3){float left=keyH+gap,capsW=keyH*.70f,available=w-left-gap,normalW=(available-capsW-gap*14f)/13f,x0=left+gap;if(x>=x0&&x<x0+capsW){setPressGlow(x0,t,x0+capsW,b,held);return;}x0+=capsW+gap;for(int i=0;i<13;i++){if(x>=x0&&x<x0+normalW){setPressGlow(x0,t,x0+normalW,b,held);return;}x0+=normalW+gap;}return;}
        if(row==4 || row==5){
            float enterW=keyH+gap;
            if(x>w-enterW){setPressGlow(w-enterW,t-(row==5?(keyH+gap):0),w,b+(row==4?(keyH+gap):0),held);return;}
            if(row==4 && x<keyH){setPressGlow(0,t,keyH,b,held);return;}
            float left=keyH;
            int count=11;
            float available=w-left-enterW-gap;
            float escW=keyH*.70f;
            if(row==5 && x>=left+gap && x<left+gap+escW){setPressGlow(left+gap,t,left+gap+escW,b,held);return;}
            float remaining=available-escW-gap;
            float cw=(remaining-gap*(count+1))/count;
            int i=Math.max(0,Math.min(count-1,(int)((x-(left+gap+escW+gap))/(cw+gap))));
            l=left+gap+escW+gap+i*(cw+gap);
            setPressGlow(l,t,l+cw,b,held);
            return;
        }
        float[] bw={1,1,1,3.9f,1.35f,1.35f,1.15f,1.15f};float total=0;for(float q:bw)total+=q;float u=(w-gap*9)/total;for(int i=0;i<bw.length;i++){r=l+u*bw[i];if(x>=l&&x<=r){setPressGlow(l,t,r,b,held);return;}l=r+gap;}
    }

    @Override public boolean onTouchEvent(MotionEvent e){
        float x = e.getX(), y = e.getY();

        if (resizeMode) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                float grip = Math.max(42f, dp(34));
                if (x >= getWidth() - grip && y >= getHeight() - grip) {
                    resizingKeyboard = true;
                    resizeStartY = y;
                    resizeStartHeight = getHeight();
                    return true;
                }
                // In resize mode, taps outside the grip do not activate keyboard keys.
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_MOVE && resizingKeyboard) {
                int newPx = resizeStartHeight + Math.round(y - resizeStartY);
                int newDp = Math.round(newPx / getResources().getDisplayMetrics().density);
                applyKeyboardHeightDp(newDp);
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                resizingKeyboard = false;
                return true;
            }
            return true;
        }

        if(e.getAction()==MotionEvent.ACTION_DOWN){
            stopRepeat();
            pressRectFor(x, y, true);
            handle(x,y);
            // A normal tap keeps its light briefly; Backspace keeps it lit while held.
            if (!isBackspaceAt(x, y)) {
                pressHeld=false;
                handler.removeCallbacks(clearPressGlow);
                handler.postDelayed(clearPressGlow, 140);
            }
            return true;
        }
        if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){
            stopRepeat();
            clearPressGlowNow();
        }
        return true;
    }

    private boolean isBackspaceAt(float x,float y){
        float w=getWidth();
        int row=(int)((y-gap)/(keyH+gap));
        return row==2 && x>w-keyH*1.6f;
    }

    private void handle(float x,float y){
        float w=getWidth();
        int row=getRowAt(y);
        if(row<0 || row>6) return;

        if(row==0){
            float[] wt={.55f,1.45f,1.55f,1.05f,1.0f,1.0f,1.0f,1.25f,.55f};
            float total=0; for(float q:wt) total+=q;
            float unit=(w-gap*(wt.length+1))/total, x0=gap;
            for(int i=0;i<wt.length;i++){
                float r=x0+unit*wt[i];
                if(x>=x0 && x<r){
                    if(i==0) service.toggleRecorder();
                    else if(i==1) service.copyAll();
                    else if(i==2) service.copyScreen();
                    else if(i==3) service.paste();
                    else if(i==4) service.cut();
                    else if(i==5) { service.undo(); startUndoRepeat(); }
                    else if(i==6) { service.redo(); startRedoRepeat(); }
                    else if(i==7) showClipboardHistory();
                    else if(i==8) showDrawer();
                    return;
                }
                x0=r+gap;
            }
            return;
        }
        if(row==1){
            int i=Math.max(0,Math.min(5,(int)(x/(w/6f))));
            if(!suggestions[i].isEmpty()) service.replaceCurrentWord(suggestions[i]);
            return;
        }
        if(row==2){
            float[] wt={.55f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,.9f,1.45f};
            float total=0; for(float q:wt) total+=q;
            float unit=(w-gap*(wt.length+1))/total, x0=gap;
            for(int i=0;i<wt.length;i++){
                float r=x0+unit*wt[i];
                if(x>=x0 && x<r){
                    if(i==0) { caps=!caps; invalidate(); return; }
                    if(i==13){ startBackspace(); return; }
                    String[] normal={"۱","۲","۳","۴","۵","۶","۷","۸","۹","۰","-","="};
                    String[] shifted={"!","@","#","$","%","^","&","*","(",")","_","+"};
                    service.type(caps ? shifted[i-1] : normal[i-1]);
                    return;
                }
                x0=r+gap;
            }
            return;
        }
        if(row==3){
            float left=keyH+gap, capsW=keyH*.70f;
            float available=w-left-gap, normalW=(available-capsW-gap*14f)/13f;
            float x0=left+gap;
            if(x>=x0 && x<x0+capsW){ caps=!caps; invalidate(); return; }
            x0+=capsW+gap;
            String[] normal=englishMode ? new String[]{"q","w","e","r","t","y","u","i","o","p","[","]","\\"} : new String[]{"ض","ص","ث","ق","ف","غ","ع","ه","خ","ج","ح","ط","ظ"};
            String[] shifted=englishMode ? new String[]{"Q","W","E","R","T","Y","U","I","O","P","{","}","|"} : new String[]{"ض","ص","ث","ق","ف","غ","ع","ه","خ","ج","ح","ط","ظ"};
            for(int i=0;i<13;i++){
                if(x>=x0 && x<x0+normalW){ service.type(caps ? shifted[i] : normal[i]); return; }
                x0+=normalW+gap;
            }
            return;
        }
        if(row==4 || row==5){
            float enterW=keyH+gap;
            if(x>=w-enterW){ service.enter(); return; }
            float left=keyH;
            if(row==4 && x<keyH){ service.move(KeyEvent.KEYCODE_DPAD_DOWN); return; }
            if(row==5 && x<keyH){ service.move(KeyEvent.KEYCODE_DPAD_UP); return; }
            String[] normal=englishMode ? (row==4?new String[]{"a","s","d","f","g","h","j","k","l",";","\""}:new String[]{"z","x","c","v","b","n","m",",",".","/","?"})
                    : (row==4?new String[]{"ش","س","ی","ب","ل","ت","ا","ک","گ","؛","»"}:new String[]{"،","ژ","ذ","ز","د","چ","پ","و","ن","م","/"});
            String[] shifted=englishMode ? (row==4?new String[]{"A","S","D","F","G","H","J","K","L",":","\""}:new String[]{"Z","X","C","V","B","N","M","<",">","?","?"})
                    : (row==4?new String[]{"ش","س","ی","ب","ل","ت","ا","ک","گ",":",">"}:new String[]{"،","ژ","ذ","ز","د","چ","پ","و","ن","م","؟"});
            float available=w-left-enterW-gap;
            float escW=keyH*.70f;
            if(row==5 && x>=left+gap && x<left+gap+escW){ service.escape(); return; }
            float remaining=available-escW-gap;
            float cw=(remaining-gap*(normal.length+1))/normal.length;
            float x0=left+gap+escW+gap;
            for(int i=0;i<normal.length;i++){
                if(x>=x0 && x<x0+cw){ service.type(caps ? shifted[i] : normal[i]); return; }
                x0+=cw+gap;
            }
            return;
        }
        if(row==6){
            float[] bw={1,1,1,3.9f,1.35f,1.35f,1.15f,1.15f};
            float total=0; for(float q:bw) total+=q;
            float unit=(w-gap*(bw.length+1))/total, x0=gap;
            for(int i=0;i<bw.length;i++){
                float r=x0+unit*bw[i];
                if(x>=x0 && x<r){
                    if(i==0) showSymbolPicker();
                    else if(i==1) { englishMode=!englishMode; invalidate(); }
                    else if(i==2) showEmojiPicker();
                    else if(i==3) service.type(" ");
                    else if(i==4) service.moveCursorHorizontal(-1);
                    else if(i==5) service.moveCursorHorizontal(1);
                    else if(i==6) service.move(KeyEvent.KEYCODE_DPAD_UP);
                    else if(i==7) service.move(KeyEvent.KEYCODE_DPAD_DOWN);
                    return;
                }
                x0=r+gap;
            }
        }
    }

    private void showMouseControls(){
        LinearLayout root=new LinearLayout(service); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(10,8,10,8);
        TextView title=new TextView(service); title.setText("موس صفحه وب"); title.setTextSize(20); title.setGravity(Gravity.CENTER); title.setTextColor(NAVY);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView info=new TextView(service);
        info.setText(FastKeysAccessibilityService.isEnabled() ? "پد را بکشید تا نشانگر موس حرکت کند. برای کلیک، دکمه چپ یا راست را بزنید." : "برای کار واقعی موس روی صفحات وب، ابتدا دسترسی «Fast Keys Mouse» را فعال کنید.");
        info.setTextSize(14); info.setGravity(Gravity.CENTER); root.addView(info,new LinearLayout.LayoutParams(-1,dp(54)));
        LinearLayout actions=new LinearLayout(service); actions.setGravity(Gravity.CENTER);
        Button enable=new Button(service); enable.setText("فعال‌سازی موس"); enable.setAllCaps(false);
        enable.setOnClickListener(v -> { try { service.startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch(Exception ignored){} });
        actions.addView(enable,new LinearLayout.LayoutParams(0,dp(50),1f));
        root.addView(actions);
        final MousePadView pad=new MousePadView(service);
        root.addView(pad,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout clicks=new LinearLayout(service); clicks.setGravity(Gravity.CENTER);
        Button left=drawerButton("کلیک چپ"); left.setOnClickListener(v->FastKeysAccessibilityService.click(false));
        Button right=drawerButton("کلیک راست"); right.setOnClickListener(v->FastKeysAccessibilityService.click(true));
        clicks.addView(left,new LinearLayout.LayoutParams(0,dp(54),1f)); clicks.addView(right,new LinearLayout.LayoutParams(0,dp(54),1f));
        root.addView(clicks);
        Button stop=drawerButton("خاموش کردن موس");
        stop.setOnClickListener(v -> FastKeysAccessibilityService.disable());
        root.addView(stop);
        Button close=drawerButton("بستن"); root.addView(close);
        final PopupWindow popup=new PopupWindow(root,Math.min(dp(390),Math.max(dp(310),getWidth()-dp(12))),Math.min(dp(600),Math.max(dp(420),getHeight()-dp(12))),false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE)); popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        close.setOnClickListener(v->popup.dismiss()); popup.showAtLocation(this,Gravity.CENTER,0,0);
    }

    private class MousePadView extends View {
        private final Paint mp=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float lastX,lastY;
        MousePadView(Context c){super(c);setBackgroundColor(Color.rgb(245,247,250));}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c); float w=getWidth(),h=getHeight();
            mp.setStyle(Paint.Style.STROKE); mp.setStrokeWidth(dp(3)); mp.setColor(NAVY); c.drawRoundRect(dp(8),dp(8),w-dp(8),h-dp(8),dp(18),dp(18),mp);
            mp.setStyle(Paint.Style.FILL); mp.setColor(Color.LTGRAY); c.drawCircle(w/2f,h/2f,dp(20),mp);
            mp.setColor(NAVY); mp.setTextSize(dp(16)); mp.setTextAlign(Paint.Align.CENTER); c.drawText("حرکت نشانگر",w/2f,h/2f+dp(55),mp);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){float dx=e.getX()-lastX,dy=e.getY()-lastY;lastX=e.getX();lastY=e.getY();FastKeysAccessibilityService.movePointer(dx*1.8f,dy*1.8f);return true;}
            return true;
        }
    }

    private void showSymbolPicker(){
        final String[] symbols={"!","@","#","$","%","^","&","*","(",")","-","_","=","+","[","]","{","}","\\","|",";",":",",","<",".",">","/","؟","«","»"};
        LinearLayout root=new LinearLayout(service); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(10,10,10,10);
        TextView title=new TextView(service); title.setText("نمادها"); title.setTextSize(20); title.setGravity(Gravity.CENTER); title.setTextColor(NAVY);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        ScrollView scroll=new ScrollView(service);
        GridLayout grid=new GridLayout(service); grid.setColumnCount(5); grid.setPadding(4,4,4,4);
        for(String s:symbols){
            Button b=new Button(service); b.setText(s); b.setTextSize(20); b.setAllCaps(false); b.setTextColor(Color.RED);
            GridLayout.LayoutParams lp=new GridLayout.LayoutParams(); lp.width=0; lp.height=dp(58); lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); lp.setMargins(2,2,2,2); grid.addView(b,lp);
            b.setOnClickListener(v -> service.type(s));
        }
        scroll.addView(grid,new ScrollView.LayoutParams(-1,-2)); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        Button close=drawerButton("بستن"); root.addView(close);
        final PopupWindow popup=new PopupWindow(root,Math.min(dp(360),Math.max(dp(300),getWidth()-dp(16))),Math.min(dp(520),Math.max(dp(320),getHeight()-dp(16))),false);
        popup.setBackgroundDrawable(new ColorDrawable(Color.WHITE)); popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(true); popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING); popup.setElevation(10f);
        close.setOnClickListener(v->popup.dismiss());
        popup.showAtLocation(this,Gravity.CENTER,0,0);
    }

    private void startBackspace(){
        service.backspace();
        stopRepeat();
        repeat=()->{ service.backspace(); handler.postDelayed(repeat,55); };
        handler.postDelayed(repeat,350);
    }

    private void startUndoRepeat(){
        stopRepeat();
        repeat=()->{ service.undo(); handler.postDelayed(repeat,80); };
        handler.postDelayed(repeat,350);
    }

    private void startRedoRepeat(){
        stopRepeat();
        repeat=()->{ service.redo(); handler.postDelayed(repeat,80); };
        handler.postDelayed(repeat,350);
    }

    private void stopRepeat(){
        if(repeat!=null){
            handler.removeCallbacks(repeat);
            repeat=null;
        }
    }
}
