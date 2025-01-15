package com.mentra.displaytext;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        Button incrementButton = findViewById(R.id.incrementButton);

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

        incrementButton.setOnClickListener(v -> {
            String currentText = transcriptionEditText.getText().toString().trim();
            String updatedText = incrementFirstNumberInText(currentText);
            transcriptionEditText.setText(updatedText);
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

    private String incrementFirstNumberInText(String text) {
        Pattern pattern = Pattern.compile("\\d+"); // Match any sequence of digits
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            String numberStr = matcher.group();
            int number = Integer.parseInt(numberStr);
            int incrementedNumber = number + 1;

            // Replace the first occurrence of the number in the text
            String updatedText = matcher.replaceFirst(String.valueOf(incrementedNumber));
            Log.d(TAG, "Incremented number: " + number + " -> " + incrementedNumber);
            return updatedText;
        } else {
            Log.d(TAG, "No number found to increment.");
            return text; // Return the original text if no number is found
        }
    }
}