package com.sunday.assistant;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_RECORD_AUDIO = 1;
    private static final int REQ_SPEECH = 2;

    private static final String SYSTEM_PROMPT =
            "You are Sunday, Abhi's personal AI assistant running on his Android phone. " +
            "Your name is Sunday. Always remember this and answer as Sunday when asked your name. " +
            "Keep answers concise and conversational.";

    private static final String[] REMEMBER_TRIGGERS = {
            "remember that ", "remember ", "don't forget that ", "don't forget ", "from now on "
    };

    private static final String[] FORGET_TRIGGERS = {
            "forget that ", "forget about ", "forget "
    };

    private TextView responseText;
    private TextToSpeech tts;
    private SundayDatabase db;
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        responseText = findViewById(R.id.responseText);
        Button talkButton = findViewById(R.id.talkButton);

        db = new SundayDatabase(this);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.US);
            }
        });

        talkButton.setOnClickListener(v -> checkPermissionAndListen());

        Button memoriesButton = findViewById(R.id.memoriesButton);
        memoriesButton.setOnClickListener(v -> startActivity(new Intent(this, FactsActivity.class)));
    }

    private void checkPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
        } else {
            startListening();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            Toast.makeText(this, "Microphone permission is needed", Toast.LENGTH_SHORT).show();
        }
    }

    private void startListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Sunday...");
        try {
            startActivityForResult(intent, REQ_SPEECH);
        } catch (Exception e) {
            Toast.makeText(this, "Speech recognition not available on this device", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SPEECH && resultCode == Activity.RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                handleSpokenInput(results.get(0));
            }
        }
    }

    private void handleSpokenInput(String spokenText) {
        String lower = spokenText.toLowerCase(Locale.US).trim();

        for (String trigger : REMEMBER_TRIGGERS) {
            if (lower.startsWith(trigger)) {
                String fact = spokenText.substring(trigger.length()).trim();
                if (!fact.isEmpty()) {
                    db.saveFact(fact);
                    speakAndShow("Got it. I'll remember that " + fact);
                    return;
                }
            }
        }

        for (String trigger : FORGET_TRIGGERS) {
            if (lower.startsWith(trigger)) {
                String phrase = spokenText.substring(trigger.length()).trim();
                String match = db.findClosestFact(phrase);
                if (match != null) {
                    db.deleteFact(match);
                    speakAndShow("Okay, I've forgotten that " + match);
                } else {
                    speakAndShow("I couldn't find a memory matching that.");
                }
                return;
            }
        }

        responseText.setText("You said: " + spokenText + "\n\nThinking...");
        askAI(spokenText);
    }

    private void speakAndShow(String text) {
        responseText.setText(text);
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sundayResponse");
        }
    }

    private void askAI(String prompt) {
        new Thread(() -> {
            try {
                db.saveMessage("user", prompt);

                List<String> facts = db.getAllFacts();
                StringBuilder factsText = new StringBuilder();
                if (!facts.isEmpty()) {
                    factsText.append(" Permanent facts you must always remember about the user: ");
                    for (String f : facts) {
                        factsText.append("- ").append(f).append(". ");
                    }
                }

                JSONArray messages = new JSONArray();
                messages.put(new JSONObject()
                        .put("role", "system")
                        .put("content", SYSTEM_PROMPT + factsText));

                JSONArray history = db.getRecentHistory(200);
                for (int i = 0; i < history.length(); i++) {
                    messages.put(history.get(i));
                }

                JSONObject body = new JSONObject()
                        .put("model", "local")
                        .put("messages", messages);

                RequestBody requestBody = RequestBody.create(
                        body.toString(), MediaType.parse("application/json"));

                Request request = new Request.Builder()
                        .url("http://127.0.0.1:8080/v1/chat/completions")
                        .post(requestBody)
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(@NonNull Call call, @NonNull IOException e) {
                        runOnUiThread(() -> responseText.setText("Network error: " + e.getMessage()));
                    }

                    @Override
                    public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                        String raw = response.body() != null ? response.body().string() : "";

                        if (response.code() == 429) {
                            runOnUiThread(() -> responseText.setText(
                                    "Sunday's catching his breath, try again in a few seconds..."));
                            return;
                        }

                        try {
                            JSONObject json = new JSONObject(raw);
                            String answer = json.getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content");

                            db.saveMessage("model", answer);

                            runOnUiThread(() -> {
                                responseText.setText(answer);
                                if (tts != null) {
                                    tts.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "sundayResponse");
                                }
                            });
                        } catch (Exception ex) {
                            runOnUiThread(() -> responseText.setText("Couldn't read response:\n" + raw));
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> responseText.setText("Error: " + e.getMessage()));
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
