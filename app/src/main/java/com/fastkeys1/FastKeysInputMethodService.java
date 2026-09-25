package com.fastkeys1;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.ExtractedTextRequest;
import android.os.Handler;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayDeque;
import android.os.SystemClock;
import android.media.MediaRecorder;
import android.widget.Toast;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import java.io.File;

public class FastKeysInputMethodService extends InputMethodService {
    private static FastKeysInputMethodService instance;
    private FastKeysKeyboardView keyboard;
    private MediaRecorder recorder;
    private File recordingFile;
    private SpeechRecognizer speechRecognizer;
    private final LinkedList<String> clipboardHistory = new LinkedList<>();
    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipListener;
    private static final int MAX_HISTORY = 100;
    private static final int MAX_UNDO = 200;
    private static final long TYPE_GROUP_MS = 60000L;
    private static class UndoOp {
        final String inserted;
        final String deleted;
        UndoOp(String inserted, String deleted) { this.inserted = inserted == null ? "" : inserted; this.deleted = deleted == null ? "" : deleted; }
    }
    private final ArrayDeque<UndoOp> undoStack = new ArrayDeque<>();
    private final ArrayDeque<UndoOp> redoStack = new ArrayDeque<>();
    private long lastTypeTime = 0L;
    private long lastDeleteTime = 0L;
    private boolean applyingHistory = false;

    private void pushUndo(String inserted, String deleted, boolean mergeTyped) {
        if (applyingHistory || ((inserted == null || inserted.isEmpty()) && (deleted == null || deleted.isEmpty()))) return;
        long now = SystemClock.uptimeMillis();
        if (mergeTyped && inserted != null && !inserted.isEmpty() && !undoStack.isEmpty() && now - lastTypeTime <= TYPE_GROUP_MS) {
            UndoOp old = undoStack.pop();
            undoStack.push(new UndoOp(old.inserted + inserted, old.deleted));
        } else if (mergeTyped && deleted != null && !deleted.isEmpty() && !undoStack.isEmpty() && now - lastDeleteTime <= TYPE_GROUP_MS) {
            UndoOp old = undoStack.pop();
            undoStack.push(new UndoOp(old.inserted, old.deleted + deleted));
        } else {
            undoStack.push(new UndoOp(inserted, deleted));
        }
        while (undoStack.size() > MAX_UNDO) undoStack.removeLast();
        redoStack.clear();
        lastTypeTime = inserted != null && !inserted.isEmpty() ? now : 0L;
        lastDeleteTime = deleted != null && !deleted.isEmpty() ? now : 0L;
    }

    private void pushRedo(UndoOp op) {
        redoStack.push(op);
        while (redoStack.size() > MAX_UNDO) redoStack.removeLast();
    }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        clipboardManager = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        clipListener = () -> captureClipboard();
        if (clipboardManager != null) clipboardManager.addPrimaryClipChangedListener(clipListener);
        captureClipboard();
    }

    @Override public View onCreateInputView() {
        // Keep the IME in the normal bottom keyboard area instead of fullscreen/extract mode.
        keyboard = new FastKeysKeyboardView(this);
        keyboard.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return keyboard;
    }

    @Override public boolean onEvaluateFullscreenMode() {
        // Fast Keys is designed as an ordinary bottom-of-screen keyboard.
        return false;
    }

    @Override public void onDestroy() {
        stopRecorder();
        if (speechRecognizer != null) { try { speechRecognizer.destroy(); } catch (Exception ignored) {} speechRecognizer = null; }
        if (instance == this) instance = null;
        if (clipboardManager != null && clipListener != null) clipboardManager.removePrimaryClipChangedListener(clipListener);
        super.onDestroy();
    }

    private void captureClipboard() {
        try {
            if (clipboardManager == null || !clipboardManager.hasPrimaryClip()) return;
            ClipData d = clipboardManager.getPrimaryClip();
            if (d == null || d.getItemCount() == 0) return;
            CharSequence t = d.getItemAt(0).coerceToText(this);
            if (t == null) return;
            String s = t.toString();
            if (s.trim().isEmpty()) return;
            clipboardHistory.remove(s);
            clipboardHistory.addFirst(s);
            while (clipboardHistory.size() > MAX_HISTORY) clipboardHistory.removeLast();
        } catch (Exception ignored) {}
    }

    public java.util.List<String> getClipboardHistory() { return new java.util.ArrayList<>(clipboardHistory); }
    public void pasteHistory(String s) { if (s != null) { type(s); } }

    @Override public void onStartInputView(android.view.inputmethod.EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void launchScreenMagnifier() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.setAction("com.fastkeys1.START_MAGNIFIER");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    public static boolean isRecordingNow() { return instance != null && instance.recorder != null; }
    public static FastKeysInputMethodService getInstance() { return instance; }

    public void toggleRecorder() {
        if (recorder != null) { stopRecorder(); return; }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                Intent i=new Intent(this, MainActivity.class);
                i.setAction("com.fastkeys1.REQUEST_RECORD_PERMISSION");
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
            } catch (Exception ignored) {}
            return;
        }
        startRecorder();
    }

    public void startRecordingIfPermitted() {
        if (recorder == null && android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) startRecorder();
    }

    private void startRecorder() {
        try {
            File dir = getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC);
            if (dir == null) dir = getFilesDir();
            if (!dir.exists()) dir.mkdirs();
            recordingFile = new File(dir, "FastKeys_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(new java.util.Date()) + ".m4a");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(128000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(recordingFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            Toast.makeText(this, "ضبط Fast Keys شروع شد", Toast.LENGTH_SHORT).show();
            if (keyboard != null) keyboard.invalidate();
        } catch (Exception e) {
            try { if (recordingFile != null) recordingFile.delete(); } catch (Exception ignored) {}
            recorder = null; recordingFile = null;
            Toast.makeText(this, "شروع ضبط ممکن نشد", Toast.LENGTH_SHORT).show();
        }
    }

    public void stopRecorder() {
        if (recorder == null) return;
        try { recorder.stop(); } catch (Exception ignored) {}
        try { recorder.reset(); recorder.release(); } catch (Exception ignored) {}
        recorder = null;
        if (recordingFile != null) Toast.makeText(this, "فایل ضبط شد: " + recordingFile.getName(), Toast.LENGTH_SHORT).show();
        recordingFile = null;
        if (keyboard != null) keyboard.invalidate();
    }

    public void requestQuickSettingsTiles() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            try {
                android.app.PendingIntent result = android.app.PendingIntent.getActivity(this, 9101, new Intent(this, MainActivity.class), android.app.PendingIntent.FLAG_UPDATE_CURRENT | (android.os.Build.VERSION.SDK_INT >= 23 ? android.app.PendingIntent.FLAG_IMMUTABLE : 0));
                android.graphics.Bitmap bm1 = android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas c1 = new android.graphics.Canvas(bm1); android.graphics.Paint pp1 = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG); pp1.setColor(android.graphics.Color.WHITE); pp1.setTextSize(42); pp1.setTextAlign(android.graphics.Paint.Align.CENTER); c1.drawText("K",32,45,pp1);
                android.graphics.Bitmap bm2 = android.graphics.Bitmap.createBitmap(64,64,android.graphics.Bitmap.Config.ARGB_8888);
                android.graphics.Canvas c2 = new android.graphics.Canvas(bm2); android.graphics.Paint pp2 = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG); pp2.setColor(android.graphics.Color.WHITE); pp2.setTextSize(42); pp2.setTextAlign(android.graphics.Paint.Align.CENTER); c2.drawText("🚗",32,45,pp2);
                android.service.quicksettings.TileService.requestAddTileService(this, new android.content.ComponentName(this, KeysTileService.class), "Fast Keys کیبورد", android.graphics.drawable.Icon.createWithBitmap(bm1), result);
                android.service.quicksettings.TileService.requestAddTileService(this, new android.content.ComponentName(this, SteeringTileService.class), "فرمان ماشین", android.graphics.drawable.Icon.createWithBitmap(bm2), result);
                android.widget.Toast.makeText(this, "درخواست افزودن دو کاشی Quick Settings ارسال شد", android.widget.Toast.LENGTH_SHORT).show();
                return;
            } catch (Exception ignored) {}
        }
        try { startActivity(new Intent("android.settings.ACTION_QUICK_SETTINGS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) {}
    }

    public void voiceSearch(String languageTag) {
        if (android.os.Build.VERSION.SDK_INT >= 23 && checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                Intent i = new Intent(this, MainActivity.class);
                i.setAction("com.fastkeys1.REQUEST_VOICE_PERMISSION");
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                i.putExtra("language", languageTag);
                startActivity(i);
            } catch (Exception ignored) {}
            return;
        }
        startVoiceSearchIfPermitted(languageTag);
    }

    public void startVoiceSearchIfPermitted(String languageTag) {
        if (android.os.Build.VERSION.SDK_INT < 23 || checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "سرویس تشخیص گفتار روی دستگاه در دسترس نیست", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(android.os.Bundle p) {}
                public void onBeginningOfSpeech() {}
                public void onRmsChanged(float rms) {}
                public void onBufferReceived(byte[] b) {}
                public void onEndOfSpeech() {}
                public void onError(int error) {
                    if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
                    if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) Toast.makeText(FastKeysInputMethodService.this, "تشخیص گفتار انجام نشد", Toast.LENGTH_SHORT).show();
                }
                public void onResults(android.os.Bundle results) {
                    java.util.ArrayList<String> r = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (r != null && !r.isEmpty() && r.get(0) != null) type(r.get(0));
                    if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
                }
                public void onPartialResults(android.os.Bundle p) {}
                public void onEvent(int a, android.os.Bundle b) {}
            });
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag == null ? "fa-IR" : languageTag);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            speechRecognizer.startListening(i);
            Toast.makeText(this, "اکنون صحبت کنید…", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
            Toast.makeText(this, "شروع جستجوی صوتی ممکن نشد", Toast.LENGTH_SHORT).show();
        }
    }

    public void voiceAssist() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        try {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOICE_ASSIST));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOICE_ASSIST));
        } catch (Exception ignored) {}
    }

    public void showInputMethodPickerSafe() {
        try {
            InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        } catch (Exception ignored) {}
    }

    private boolean isSentenceEnd(String s) {
        if (s == null || s.isEmpty()) return false;
        char c=s.charAt(s.length()-1);
        return c=='.' || c=='!' || c=='?' || c=='؟' || c=='؛' || c=='»' || c=='»';
    }

    public void type(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || s == null || s.isEmpty()) return;
        ic.commitText(s, 1);
        pushUndo(s, "", true);
        if (isSentenceEnd(s)) lastTypeTime = 0L;
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void typeTo(InputConnection ic, String s) {
        if (ic == null || s == null || s.isEmpty()) return;
        ic.commitText(s, 1);
        // A history/clipboard insertion is one complete undo unit.
        pushUndo(s, "", false);
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void replaceCurrentWord(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence before = ic.getTextBeforeCursor(80, 0);
        int n = 0;
        if (before != null) {
            String b = before.toString();
            int i = b.length() - 1;
            while (i >= 0 && !Character.isWhitespace(b.charAt(i))) { n++; i--; }
        }
        if (n > 0) ic.deleteSurroundingText(n, 0);
        ic.commitText(s + " ", 1);
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void backspace() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        CharSequence before = ic.getTextBeforeCursor(2, 0);
        String deleted = (before == null || before.length() == 0) ? "" : before.subSequence(before.length()-1, before.length()).toString();
        ic.deleteSurroundingText(1, 0);
        if (!deleted.isEmpty()) {
            pushUndo("", deleted, true);
        }
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void enter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void move(int keyCode) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    // Reliable text-cursor movement for left/right controls. Sending DPAD events
    // is not handled consistently by every Android editor, so use setSelection
    // when absolute cursor positions can be determined.
    public void moveCursorHorizontal(int direction) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || (direction != -1 && direction != 1)) return;
        try {
            CharSequence before = ic.getTextBeforeCursor(10000, 0);
            CharSequence after = ic.getTextAfterCursor(10000, 0);
            int beforeLen = before == null ? 0 : before.length();
            int afterLen = after == null ? 0 : after.length();
            int pos = beforeLen;
            int next = Math.max(0, Math.min(beforeLen + afterLen, pos + direction));
            ic.setSelection(next, next);
        } catch (Exception ignored) {
            move(direction < 0 ? KeyEvent.KEYCODE_DPAD_LEFT : KeyEvent.KEYCODE_DPAD_RIGHT);
        }
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void copyAll() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.performContextMenuAction(android.R.id.selectAll);
        ic.performContextMenuAction(android.R.id.copy);
        captureClipboard();
    }

    public void copyScreen() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        try {
            CharSequence before = ic.getTextBeforeCursor(10000, 0);
            CharSequence after = ic.getTextAfterCursor(10000, 0);
            String text = (before == null ? "" : before.toString()) + (after == null ? "" : after.toString());
            if (clipboardManager != null) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("Fast Keys", text));
                captureClipboard();
            }
        } catch (Exception ignored) {}
    }

    public void cut() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) { ic.performContextMenuAction(android.R.id.cut); refresh(); }
    }

    public void paste() {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) { ic.performContextMenuAction(android.R.id.paste); refresh(); }
    }

    public void undo() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || undoStack.isEmpty()) return;
        UndoOp op = undoStack.pop();
        applyingHistory = true;
        try {
            if (!op.inserted.isEmpty()) {
                ic.deleteSurroundingText(op.inserted.length(), 0);
            } else if (!op.deleted.isEmpty()) {
                ic.commitText(op.deleted, 1);
            }
        } finally {
            applyingHistory = false;
        }
        pushRedo(op);
        lastTypeTime = 0L; lastDeleteTime = 0L;
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void redo() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || redoStack.isEmpty()) return;
        UndoOp op = redoStack.pop();
        applyingHistory = true;
        try {
            if (!op.inserted.isEmpty()) {
                ic.commitText(op.inserted, 1);
            } else if (!op.deleted.isEmpty()) {
                ic.deleteSurroundingText(op.deleted.length(), 0);
            }
        } finally {
            applyingHistory = false;
        }
        undoStack.push(op);
        while (undoStack.size() > MAX_UNDO) undoStack.removeLast();
        lastTypeTime = 0L; lastDeleteTime = 0L;
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void escape() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE));
    }

    private void refresh() { if (keyboard != null) keyboard.refreshSuggestions(); }
}
