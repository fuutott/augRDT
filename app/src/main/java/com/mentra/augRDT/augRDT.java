package com.mentra.augRDT;


import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent; // Import KeyEvent
import android.content.Intent;
import android.os.IBinder; // Import IBinder
import android.os.Binder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONObject;

import org.greenrobot.eventbus.Subscribe;

import com.augmentos.augmentoslib.AugmentOSLib;
import com.augmentos.augmentoslib.DataStreamType;
import com.augmentos.augmentoslib.SmartGlassesAndroidService;
import com.augmentos.augmentoslib.events.SpeechRecOutputEvent;


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
    private final int maxNormalTextCharsPerTranscript = 60;
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
    private final Handler autoScrollHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            // Only auto-scroll if we're in the POSTS view
            if (currentView == ViewState.POSTS && !posts.isEmpty()) {
                selectedIndex = (selectedIndex + 1) % posts.size(); // loops back to 0 when reaching end
                updatePostsDisplay();
            }
            // Schedule again for 5 seconds later
            autoScrollHandler.postDelayed(this, 5000);
        }
    };
    private void startAutoScroll() {
        autoScrollHandler.removeCallbacks(autoScrollRunnable);
        autoScrollHandler.postDelayed(autoScrollRunnable, 5000);
    }
    private void stopAutoScroll() {
        autoScrollHandler.removeCallbacks(autoScrollRunnable);
    }
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
        augmentOSLib.requestTranscription("English");
        //augmentOSLib.requestGlassesSideTaps();
        //augmentOSLib.requestSmartRingButtonTaps();
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

    @Subscribe
    public void onSpeechTranscriptionTranscript(SpeechRecOutputEvent event) {
        String text = event.text;
        String languageCode = event.languageCode;
        long time = event.timestamp;
        boolean isFinal = event.isFinal;


        if (isFinal) {
            if(text.toLowerCase().contains("go up")){
                stopAutoScroll();
                handleUp();
            } else if (text.toLowerCase().contains("go down")) {
                stopAutoScroll();
                handleDown();
            } else if (text.toLowerCase().contains("select")) {
                stopAutoScroll();
                handleSelect();
            } else if (text.toLowerCase().contains("go back")) {
                stopAutoScroll();
                currentView = ViewState.POSTS;
                selectedIndex = 0;
                updatePostsDisplay();

            }

        }
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
        stopAutoScroll();
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
                () -> augmentOSLib.sendTextWall(newLiveCaption),
                true, false, true));
    }
    public void sendCenteredText(final String newLiveCaption) {
        //String caption = normalTextTranscriptProcessor.processString(newLiveCaption);

        displayQueue.addTask(new DisplayQueue.Task(
                () -> augmentOSLib.sendTextWall(newLiveCaption),
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
                String manual = "Autoscroll posts until it hears commands:\n" +
                        "     Go up\n" +
                        "     Go down\n" +
                        "     Select - enter comments\n" +
                        "     Go back - go back to post list";

                posts.add(new Post(
                        "augRDT",
                        manual,
                        0,
                        "",
                        "",
                        "",
                        "0"
                ));


                for (int i = 0; i < children.length(); i++) {
                    JSONObject post = children.getJSONObject(i).getJSONObject("data");
                    posts.add(new Post(
                            post.getString("id"),
                            post.getString("title"),
                            post.getInt("ups"),
                            post.getString("permalink"),
                            post.getString("subreddit_name_prefixed"),
                            post.getString("author"),
                            post.getString("created")
                    ));
                }
                Log.d(TAG, "fp 4 " + children.length());
                mainHandler.post(() -> {
                    Log.d(TAG, "fp 5");
                    hideLoading();
                    updatePostsDisplay();
                    startAutoScroll();
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
                comments.add(new Comment("Back to posts", "home","0",0)); // Back button

                for (int i = 0; i < commentsArray.length(); i++) {
                    JSONObject child = commentsArray.getJSONObject(i);
                    // Only process if this node is a comment (kind = "t1")
                    if (!child.getString("kind").equals("t1")) {
                        continue;
                    }
                    JSONObject commentData = child.getJSONObject("data");
                    // Some items (e.g. "more") may not have a body so skip them
                    if (!commentData.has("body")) {
                        continue;
                    }
                    comments.add(new Comment(
                            commentData.getString("body"),
                            commentData.getString("author"),
                            commentData.getString("created"),
                            commentData.getInt("ups")

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
        String displayText = "";
        if(selectedIndex !=0) {
            String createdString = post.created;
            double doubleValue = Double.parseDouble(createdString);
            long createdTimeSeconds = (long) doubleValue;

            long currentTimeSeconds = System.currentTimeMillis() / 1000L;
            long diffSeconds = currentTimeSeconds - createdTimeSeconds;
            long diffHours = diffSeconds / 3600L;

            displayText = (selectedIndex + 1) + "/" + posts.size() + " ▲ " + post.ups + " in " + post.subreddit_name_prefixed + "\n" + post.title + "\n" + diffHours + " hours ago by " + post.author; // Combine title and ups
        } else {
            displayText = post.title;
        }
        sendTextWallLiveCaption(displayText);


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
        String displayText = "";
        if(selectedIndex !=0){
            String createdString = comment.created;
            double doubleValue = Double.parseDouble(createdString);
            long createdTimeSeconds = (long) doubleValue;

            long currentTimeSeconds = System.currentTimeMillis() / 1000L;
            long diffSeconds = currentTimeSeconds - createdTimeSeconds;
            long diffHours = diffSeconds / 3600L;

            displayText = (selectedIndex + 1)+ "/" + comments.size() + " " +  comment.author + " " +  comment.ups +" points "+ diffHours + " hours ago\n" + comment.text; // ▲▼
        } else {
            displayText = (selectedIndex + 1)+ "/" + comments.size() + " " +comment.text; // ▲▼
        }

        //String displayText = comment.author + comment.ups + " ▼";
        sendTextWallLiveCaption(displayText);



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
        String subreddit_name_prefixed;
        String author;
        String created;
        Post(String id, String title, int ups, String permalink, String subreddit_name_prefixed, String author, String created) {
            this.id = id;
            this.title = title;
            this.ups = ups;
            this.permalink = permalink;
            this.subreddit_name_prefixed = subreddit_name_prefixed;
            this.author = author;
            this.created = created;
        }
    }



    private static class Comment {
        String text;
        String author;
        String created;
        int ups;

        Comment(String text, String author,String created, int ups) {
            this.text = text;
            this.author = author;
            this.created = created;
            this.ups = ups;
        }
    }
}
