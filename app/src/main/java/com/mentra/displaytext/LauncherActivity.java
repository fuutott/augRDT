package com.mentra.displaytext;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

public class LauncherActivity extends AppCompatActivity {

    private static final String TAG = "LauncherActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .remove(getString(R.string.SHARED_PREF_TRANSCRIPTION_TEXT))
                .apply();

        setContentView(R.layout.activity_main);
        initializeUIComponents();
    }

    private void initializeUIComponents() {
        Context mContext = this;
        EditText transcriptionEditText = findViewById(R.id.transcriptionEditText);

        transcriptionEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No action needed before text changes
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // No action needed during text changes
            }

            @Override
            public void afterTextChanged(Editable s) {
                String newText = s.toString();
                Log.d(TAG, "Transcription updated: " + newText);
                saveTranscription(mContext, newText);
            }
        });
    }

    public static void saveTranscription(Context context, String transcriptionText) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(
                        context.getResources().getString(R.string.SHARED_PREF_TRANSCRIPTION_TEXT),
                        transcriptionText
                )
                .apply();
    }
}
