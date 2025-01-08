package com.mentra.displaytext;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.teamopensmartglasses.augmentoslib.AugmentOSLib;
import com.teamopensmartglasses.augmentoslib.SmartGlassesAndroidService;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class DisplayTextService extends SmartGlassesAndroidService {
    public static final String TAG = "DisplayTextService";

    public AugmentOSLib augmentOSLib;
    ArrayList<String> responsesBuffer;
    ArrayList<String> transcriptsBuffer;
    ArrayList<String> responsesToShare;
    Handler debugTranscriptsHandler = new Handler(Looper.getMainLooper());
    private boolean debugTranscriptsRunning = false;

    private DisplayQueue displayQueue;

    private Handler transcribeLanguageCheckHandler;
    private Handler userInputCheckHandler; // Handler for user input checks
    private final int maxNormalTextCharsPerTranscript = 30;
    private final TranscriptProcessor normalTextTranscriptProcessor = new TranscriptProcessor(maxNormalTextCharsPerTranscript);

    private String lastKnownInputText = "";  // Track last displayed input

    public DisplayTextService() {
        super();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize AugmentOSLib
        augmentOSLib = new AugmentOSLib(this);

        // Initialize handlers
        transcribeLanguageCheckHandler = new Handler(Looper.getMainLooper());

        // Start periodic tasks
        startUserInputCheckTask();

        displayQueue = new DisplayQueue();

        responsesBuffer = new ArrayList<>();
        responsesToShare = new ArrayList<>();
        responsesBuffer.add("Welcome to AugmentOS.");
        transcriptsBuffer = new ArrayList<>();

        Log.d(TAG, "Convoscope service started");

        completeInitialization();
    }

    public void completeInitialization() {
        Log.d(TAG, "COMPLETE CONVOSCOPE INITIALIZATION");
        displayQueue.startQueue();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy: Called");
        augmentOSLib.deinit();

        if (displayQueue != null) displayQueue.stopQueue();

        if (debugTranscriptsRunning) {
            debugTranscriptsHandler.removeCallbacksAndMessages(null);
        }
        if (userInputCheckHandler != null) {
            userInputCheckHandler.removeCallbacksAndMessages(null);
        }
        if (transcribeLanguageCheckHandler != null) {
            transcribeLanguageCheckHandler.removeCallbacksAndMessages(null);
        }

        Log.d(TAG, "ran onDestroy");
        super.onDestroy();
    }

    // Renamed method to send text to smart glasses
    public void sendTextWallLiveCaption(final String newLiveCaption) {
        String caption = normalTextTranscriptProcessor.processString(newLiveCaption);

        displayQueue.addTask(new DisplayQueue.Task(
                () -> augmentOSLib.sendDoubleTextWall(caption, ""),
                true, false, true));
    }

    private void startUserInputCheckTask() {
        userInputCheckHandler = new Handler(Looper.getMainLooper());
        userInputCheckHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                String currentInputText = PreferenceManager
                        .getDefaultSharedPreferences(getApplicationContext())
                        .getString(getString(R.string.SHARED_PREF_TRANSCRIPTION_TEXT), "");

                if (!currentInputText.equals(lastKnownInputText)) {
                    lastKnownInputText = currentInputText;
                    sendTextWallLiveCaption(lastKnownInputText);
                }
                userInputCheckHandler.postDelayed(this, 333);
            }
        }, 0);
    }

    public static class TranscriptProcessor {
        private final int maxCharsPerLine;
        private final int maxLines = 3;
        private final Deque<String> lines;

        public TranscriptProcessor(int maxCharsPerLine) {
            this.maxCharsPerLine = maxCharsPerLine;
            this.lines = new ArrayDeque<>();
        }

        public String processString(String newText) {
            newText = (newText == null) ? "" : newText.trim();
            List<String> wrapped = wrapText(newText, maxCharsPerLine);
            for (String chunk : wrapped) {
                appendToLines(chunk);
            }
            return getTranscript();
        }

        private void appendToLines(String chunk) {
            if (lines.isEmpty()) {
                lines.addLast(chunk);
            } else {
                String lastLine = lines.removeLast();
                String candidate = lastLine.isEmpty() ? chunk : lastLine + " " + chunk;

                if (candidate.length() <= maxCharsPerLine) {
                    lines.addLast(candidate);
                } else {
                    lines.addLast(lastLine);
                    lines.addLast(chunk);
                }
            }
            while (lines.size() > maxLines) {
                lines.removeFirst();
            }
        }

        private List<String> wrapText(String text, int maxLineLength) {
            List<String> result = new ArrayList<>();
            while (!text.isEmpty()) {
                if (text.length() <= maxLineLength) {
                    result.add(text);
                    break;
                } else {
                    int splitIndex = maxLineLength;
                    while (splitIndex > 0 && text.charAt(splitIndex) != ' ') {
                        splitIndex--;
                    }
                    if (splitIndex == 0) {
                        splitIndex = maxLineLength;
                    }
                    String chunk = text.substring(0, splitIndex).trim();
                    result.add(chunk);
                    text = text.substring(splitIndex).trim();
                }
            }
            return result;
        }

        public String getTranscript() {
            List<String> allLines = new ArrayList<>(lines);
            int linesToPad = maxLines - allLines.size();
            for (int i = 0; i < linesToPad; i++) {
                allLines.add("");
            }
            String finalString = String.join("\n", allLines);
            lines.clear();
            return finalString;
        }
    }
}
