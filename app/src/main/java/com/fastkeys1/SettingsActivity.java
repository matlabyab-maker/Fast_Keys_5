package com.fastkeys1;
import android.app.Activity; import android.os.Bundle; import android.graphics.Color; import android.widget.TextView;
public class SettingsActivity extends Activity { public void onCreate(Bundle b){super.onCreate(b); TextView t=new TextView(this); t.setText("Fast_Keys_1\n\nکیبورد فارسی Fast_Keys_1"); t.setTextSize(20); t.setTextColor(Color.DKGRAY); t.setPadding(32,32,32,32); setContentView(t);} }
