package com.mentra.augRDT;

import android.content.Context;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.widget.Button;
import android.widget.Toast;

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
    private augRDT augRDT;
    private boolean isBound = false;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            augRDT.LocalBinder binder = (augRDT.LocalBinder) service;
            augRDT = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
            augRDT = null; // Important: Clear the reference
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .remove(getString(R.string.SHARED_PREF_TRANSCRIPTION_TEXT))
                .apply();

        setContentView(R.layout.activity_main);
        Button fetchPostsButton = findViewById(R.id.fetchPostsButton); // Find the button

        fetchPostsButton.setOnClickListener(v -> {
            if (isBound && augRDT != null) {
                augRDT.fetchPosts(); // Call the service method
            } else {
                Toast.makeText(this, "Service not connected", Toast.LENGTH_SHORT).show();
            }
        });

    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isBound) {
            unbindService(connection);
            isBound = false;
        }
    }

}