package com.fastkeys1;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.ExtractedTextRequest;
import android.os.Handler;
import java.util.LinkedList;
import java.util.ArrayList;
import java.util.List;
import java.util.ArrayDeque;

public class FastKeysInputMethodService extends InputMethodService {
    private FastKeysKeyboardView keyboard;
    private final LinkedList<String> clipboardHistory = new LinkedList<>();
    private ClipboardManager clipboardManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipListener;
    private static final int MAX_HISTORY = 100;
    private interface UndoAction { void undo(InputConnection ic); }
    private final ArrayDeque<UndoAction> undoStack = new ArrayDeque<>();
    private boolean restoringUndo = false;

    private void pushUndo(UndoAction action) {
        if (restoringUndo || action == null) return;
        undoStack.push(action);
        while (undoStack.size() > 100) undoStack.removeLast();
    }

    @Override public void onCreate() {
        super.onCreate();
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
        undoStack.clear();
        if (keyboard != null) keyboard.refreshSuggestions();
    }

    public void type(String s) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || s == null || s.isEmpty()) return;
        final int length = s.length();
        ic.commitText(s, 1);
        pushUndo(ic2 -> ic2.deleteSurroundingText(length, 0));
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
        CharSequence before = ic.getTextBeforeCursor(1, 0);
        if (before == null || before.length() == 0) return;
        final String deleted = before.toString();
        ic.deleteSurroundingText(1, 0);
        pushUndo(ic2 -> ic2.commitText(deleted, 1));
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
        if (ic == null) return;
        CharSequence selected = ic.getSelectedText(0);
        String text = selected == null ? "" : selected.toString();
        if (!text.isEmpty()) {
            ic.performContextMenuAction(android.R.id.cut);
            pushUndo(ic2 -> ic2.commitText(text, 1));
        }
        refresh();
    }

    public void paste() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || clipboardManager == null || !clipboardManager.hasPrimaryClip()) return;
        CharSequence text = clipboardManager.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (text == null || text.length() == 0) return;
        String s = text.toString();
        ic.commitText(s, 1);
        pushUndo(ic2 -> ic2.deleteSurroundingText(s.length(), 0));
        refresh();
    }

    public void undo() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || undoStack.isEmpty()) return;
        UndoAction action = undoStack.pop();
        restoringUndo = true;
        try { action.undo(ic); } finally { restoringUndo = false; }
        refresh();
    }

    public void redo() {
        // Keep the existing system redo action; user-requested behavior specifically
        // changes Undo to one action at a time.
        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.performContextMenuAction(android.R.id.redo);
    }

    public void escape() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE));
        ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ESCAPE));
    }

    private void refresh() { if (keyboard != null) keyboard.refreshSuggestions(); }
}
