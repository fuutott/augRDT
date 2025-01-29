package com.mentra.augRDT;

import android.content.Context;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent; // Import KeyEvent
import android.content.Intent;
import android.os.IBinder; // Import IBinder
import androidx.preference.PreferenceManager;
import android.content.BroadcastReceiver;
import android.app.Service;
import android.os.Binder;
import android.os.IBinder;

import com.teamopensmartglasses.augmentoslib.AugmentOSLib;
import com.teamopensmartglasses.augmentoslib.DataStreamType;
import com.teamopensmartglasses.augmentoslib.SmartGlassesAndroidService;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

public class augRDT extends SmartGlassesAndroidService {
    public static final String TAG = "augRDT";

    public AugmentOSLib augmentOSLib;
    ArrayList<String> responsesBuffer;
    ArrayList<String> transcriptsBuffer;
    ArrayList<String> responsesToShare;
    Handler debugTranscriptsHandler = new Handler(Looper.getMainLooper());
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean debugTranscriptsRunning = false;

    private DisplayQueue displayQueue;

    private Handler transcribeLanguageCheckHandler;
    private Handler userInputCheckHandler; // Handler for user input checks
    private final int maxNormalTextCharsPerTranscript = 30;
    private final TranscriptProcessor normalTextTranscriptProcessor = new TranscriptProcessor(maxNormalTextCharsPerTranscript);

    private String lastKnownInputText = "";  // Track last displayed input
  //  private BroadcastReceiver keyEventReceiver; // Declare receiver
    private enum ViewState { POSTS, COMMENTS }
    private ViewState currentView = ViewState.POSTS;
    private int selectedIndex = 0;
    private List<Post> posts = new ArrayList<>();
    private List<Comment> comments = new ArrayList<>();
    private boolean isLoading = false;

    // Media key codes
    // private static final int MEDIA_PLAY_PAUSE = 85;
    // private static final int MEDIA_PREVIOUS = 88;
    // private static final int MEDIA_NEXT = 87;
    private static final int MEDIA_PLAY_PAUSE = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
    private static final int MEDIA_PREVIOUS = KeyEvent.KEYCODE_MEDIA_PREVIOUS;
    private static final int MEDIA_NEXT = KeyEvent.KEYCODE_MEDIA_NEXT;
    private final IBinder binder = new LocalBinder();

    public augRDT() {
        super();
    }
    public class LocalBinder extends Binder {
        augRDT getService() {
            return augRDT.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize AugmentOSLib
        augmentOSLib = new AugmentOSLib(this);
        augmentOSLib.subscribe(DataStreamType.GLASSES_SIDE_TAP, this::processSideTap);
        augmentOSLib.subscribe(DataStreamType.SMART_RING_BUTTON, this::processRingButton);
        Log.d(TAG, "init 1");
        // Initialize handlers
        transcribeLanguageCheckHandler = new Handler(Looper.getMainLooper());

        // Start periodic tasks
        //startUserInputCheckTask();

        displayQueue = new DisplayQueue();
        Log.d(TAG, "init 2");
        responsesBuffer = new ArrayList<>();
        responsesToShare = new ArrayList<>();
        responsesBuffer.add("Welcome to AugmentOS.");
        transcriptsBuffer = new ArrayList<>();
        Log.d(TAG, "init 3");
        Log.d(TAG, "Convoscope service started");
        displayQueue.startQueue();
        Log.d(TAG, "init 4");
        //completeInitialization();
        showLoading();
        Log.d(TAG, "init 5");
        fetchPosts();
        Log.d(TAG, "init 6");
        /* keyEventReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "kmb0");
                if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
                    Log.d(TAG, "kmb1");
                    KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                    Log.d(TAG, "kmb2");
                    if (event != null && event.getAction() == KeyEvent.ACTION_DOWN) {
                        int keyCode = event.getKeyCode();
                        Log.d(TAG, "kmb3" + keyCode);
                        Log.d(TAG, "Key pressed: " + keyCode);
                        handleKeyEvent(keyCode);
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
        registerReceiver(keyEventReceiver, filter);
         */
    }



    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TAG, "onKeyDown: Key pressed: " + keyCode);
        handleKeyEvent(keyCode);
        return true;
    }
    @Override


    public void onDestroy() {
/*
       if (keyEventReceiver != null) {
            unregisterReceiver(keyEventReceiver);
        }

*/
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

    public void sendRowsCard(final String[] newLiveCaption) {
       // String caption = normalTextTranscriptProcessor.processString(newLiveCaption);

        displayQueue.addTask(new DisplayQueue.Task(
                () -> augmentOSLib.sendRowsCard(newLiveCaption),
                true, false, true));
    }
    public void sendTextWallLiveCaption(final String newLiveCaption) {
        String caption = normalTextTranscriptProcessor.processString(newLiveCaption);

        displayQueue.addTask(new DisplayQueue.Task(
                () -> augmentOSLib.sendDoubleTextWall(caption, ""),
                true, false, true));
    }
    public void sendCenteredText(final String newLiveCaption) {
        String caption = normalTextTranscriptProcessor.processString(newLiveCaption);

        displayQueue.addTask(new DisplayQueue.Task(
                () -> augmentOSLib.sendCenteredText(caption),
                true, false, true));
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
    private void handleKeyEvent(int keyCode) {
        switch (keyCode) {
            case MEDIA_PREVIOUS:
                handleUp();
                break;
            case MEDIA_NEXT:
                handleDown();
                break;
            case MEDIA_PLAY_PAUSE:
                handleSelect();
                break;
            // ... other key codes
        }
    }
    private void showLoading() {
        isLoading = true;
        sendCenteredText("Loading...");
        Log.d(TAG, "sl 1");
    }

    private void hideLoading() {
        isLoading = false;
    }
    public void fetchPosts() {
        Log.d(TAG, "fp 1");
        new Thread(() -> {
            try {
                Log.d(TAG, "fp 2");
                URL url = new URL("https://www.reddit.com/top.json?limit=20?t=hour");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "AugmentOS-Reddit-Browser/1.0");

                InputStream responseStream = connection.getInputStream();
                String json = new Scanner(responseStream).useDelimiter("\\A").next();

                JSONObject data = new JSONObject(json).getJSONObject("data");
                JSONArray children = data.getJSONArray("children");
                Log.d(TAG, "fp 3");
                posts.clear();
                for (int i = 0; i < children.length(); i++) {
                    JSONObject post = children.getJSONObject(i).getJSONObject("data");
                    posts.add(new Post(
                            post.getString("id"),
                            post.getString("title"),
                            post.getInt("ups"),
                            post.getString("permalink")
                    ));
                }
                Log.d(TAG, "fp 4 " + children.length());
                mainHandler.post(() -> {
                    Log.d(TAG, "fp 5");
                    hideLoading();
                    updatePostsDisplay();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error fetching posts: " + e.getMessage());
                mainHandler.post(() -> sendCenteredText("Error loading posts"));
            }
        }).start();
    }

    private void fetchComments(String permalink) {
        showLoading();
        new Thread(() -> {
            try {
                URL url = new URL("https://www.reddit.com" + permalink + ".json");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "AugmentOS-Reddit-Browser/1.0");

                InputStream responseStream = connection.getInputStream();
                String json = new Scanner(responseStream).useDelimiter("\\A").next();

                JSONArray responseArray = new JSONArray(json);
                JSONObject commentsData = responseArray.getJSONObject(1).getJSONObject("data");
                JSONArray commentsArray = commentsData.getJSONArray("children");

                comments.clear();
                comments.add(new Comment("Back to posts", 0)); // Back button
                for (int i = 0; i < commentsArray.length(); i++) {
                    JSONObject comment = commentsArray.getJSONObject(i).getJSONObject("data");
                    comments.add(new Comment(
                            comment.getString("body"),
                            comment.getInt("ups")
                    ));
                }

                mainHandler.post(() -> {
                    hideLoading();
                    currentView = ViewState.COMMENTS;
                    selectedIndex = 0;
                    updateCommentsDisplay();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error fetching comments: " + e.getMessage());
                mainHandler.post(() -> sendCenteredText("Error loading comments"));
            }
        }).start();
    }
    private void updatePostsDisplay() {
        Log.d(TAG, "upd 1  ");
        if (isLoading || posts.isEmpty()) return;
        Log.d(TAG, "upd 2  ");

        if (selectedIndex >= posts.size()) {
            selectedIndex = posts.size() - 1; // Prevent index out of bounds
        }
        if (selectedIndex < 0) {
            selectedIndex = 0; // Prevent negative index
        }

        Post post = posts.get(selectedIndex);
        String displayText = post.title + "\n" + post.ups + " ▲"; // Combine title and ups
        sendTextWallLiveCaption(displayText);


/*
        List<String> postItems = new ArrayList<>();


        for (int i = 0; i < posts.size(); i++) {
            Post post = posts.get(i);
            String prefix = (i == selectedIndex) ? "> " : "  ";
            String title = post.title != null ? post.title : ""; // Handle potential null title
            title = title.length() > 50 ? title.substring(0, 47) + "..." : title;

            String item = prefix + post.ups + " ▲ " + title; // Combine parts

            if (item != null) { // Check if the combined item is null (unlikely, but good to be sure)
                postItems.add(item);
            } else {
                Log.e(TAG, "Item is null!");
            }
        }


        Log.d(TAG, "upd 3  ");
        if (postItems.size() > 0) { // Make sure the array is not empty or null
            Log.d(TAG, "upd 4  ");
            sendRowsCard(postItems.toArray(new String[0]));
        } else {
            Log.d(TAG, "upd 5  ");
            Log.w(TAG, "postItems is empty!");
            sendCenteredText("No posts to display."); // Or a more user-friendly message
        }
*/
    }

    private void updateCommentsDisplay() {
        if (isLoading || comments.isEmpty()) return;
        if (selectedIndex >= comments.size()) {
            selectedIndex = comments.size() - 1;
        }
        if (selectedIndex < 0) {
            selectedIndex = 0;
        }

        Comment comment = comments.get(selectedIndex);
        String displayText = comment.text + "\n" + comment.ups + " ▼";
        sendTextWallLiveCaption(displayText);

/*
        List<String> commentItems = new ArrayList<>();
        for (int i = 0; i < comments.size(); i++) {
            Comment comment = comments.get(i);
            String prefix = (i == selectedIndex) ? "> " : "  ";
            String text = comment.text;

            if (i > 0) { // Skip truncating for back button
                text = text.replaceAll("\n", " ");
                text = text.length() > 50 ? text.substring(0, 47) + "..." : text;
            }

            commentItems.add(prefix + comment.ups + " ▼ " + text);
        }
        sendRowsCard(commentItems.toArray(new String[0]));
 */

    }

    @Override
    public IBinder onBind(Intent intent) {
        super.onBind(intent);
        return null; // Or return your binder if needed
    }
    private void handleUp() {
        if (selectedIndex > 0) {
            selectedIndex--;
            updateDisplay();
        }
    }

    private void handleDown() {
        int maxIndex = currentView == ViewState.POSTS ?
                posts.size() - 1 : comments.size() - 1;
        if (selectedIndex < maxIndex) {
            selectedIndex++;
            updateDisplay();
        }
    }

    private void handleSelect() {
        if (currentView == ViewState.POSTS) {
            if (selectedIndex < posts.size()) {
                fetchComments(posts.get(selectedIndex).permalink);
            }
        } else {
            if (selectedIndex == 0) { // Back to posts
                currentView = ViewState.POSTS;
                selectedIndex = 0;
                updatePostsDisplay();
            }
        }
    }

    private void updateDisplay() {
        if (currentView == ViewState.POSTS) {
            updatePostsDisplay();
        } else {
            updateCommentsDisplay();
        }
    }

    private void processSideTap(int numTaps, boolean sideOfGlasses, long timestamp) {
        Log.d(TAG, "processSideTap 1  " +numTaps + sideOfGlasses + timestamp);
        switch (numTaps) {
            case 1:
                if(sideOfGlasses){ // assuming 0 for left
                    handleDown();
                }
                else{
                    handleUp();
                }
            case 2:
                handleSelect();
                break;
        }
    }
    private void processRingButton(int buttonId, long time, boolean isDown){
        Log.d(TAG, "processRingButton 1  " +buttonId + isDown + time);
    }
    private static class Post {
        String id;
        String title;
        int ups;
        String permalink;

        Post(String id, String title, int ups, String permalink) {
            this.id = id;
            this.title = title;
            this.ups = ups;
            this.permalink = permalink;
        }
    }

    private static class Comment {
        String text;
        int ups;

        Comment(String text, int ups) {
            this.text = text;
            this.ups = ups;
        }
    }
}
