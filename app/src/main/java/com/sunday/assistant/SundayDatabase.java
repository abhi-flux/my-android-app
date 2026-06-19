package com.sunday.assistant;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class SundayDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "sunday.db";
    private static final int DB_VERSION = 1;

    private static final String TABLE_CONVERSATIONS = "conversations";
    private static final String TABLE_FACTS = "facts";

    public SundayDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_CONVERSATIONS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "role TEXT NOT NULL, " +
                "message TEXT NOT NULL, " +
                "timestamp INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_FACTS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "fact TEXT NOT NULL, " +
                "timestamp INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CONVERSATIONS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FACTS);
        onCreate(db);
    }

    public void saveMessage(String role, String message) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("role", role);
        values.put("message", message);
        values.put("timestamp", System.currentTimeMillis());
        db.insert(TABLE_CONVERSATIONS, null, values);
    }

    public JSONArray getRecentHistory(int limit) {
        JSONArray history = new JSONArray();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT role, message FROM " + TABLE_CONVERSATIONS +
                        " ORDER BY id DESC LIMIT ?",
                new String[]{String.valueOf(limit)});

        List<String[]> rows = new ArrayList<>();
        while (cursor.moveToNext()) {
            rows.add(new String[]{cursor.getString(0), cursor.getString(1)});
        }
        cursor.close();

        for (int i = rows.size() - 1; i >= 0; i--) {
            try {
                JSONObject part = new JSONObject().put("text", rows.get(i)[1]);
                JSONObject content = new JSONObject()
                        .put("role", rows.get(i)[0])
                        .put("parts", new JSONArray().put(part));
                history.put(content);
            } catch (Exception ignored) {}
        }
        return history;
    }

    public void saveFact(String fact) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("fact", fact);
        values.put("timestamp", System.currentTimeMillis());
        db.insert(TABLE_FACTS, null, values);
    }

    public List<String> getAllFacts() {
        List<String> facts = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT fact FROM " + TABLE_FACTS + " ORDER BY id ASC", null);
        while (cursor.moveToNext()) {
            facts.add(cursor.getString(0));
        }
        cursor.close();
        return facts;
    }

    public void deleteFact(String fact) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_FACTS, "fact = ?", new String[]{fact});
    }

    public String findClosestFact(String spokenPhrase) {
        List<String> facts = getAllFacts();
        String best = null;
        int bestScore = 0;
        String[] words = spokenPhrase.toLowerCase().split("\\s+");

        for (String fact : facts) {
            int score = 0;
            String lowerFact = fact.toLowerCase();
            for (String w : words) {
                if (w.length() > 3 && lowerFact.contains(w)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = fact;
            }
        }
        return best;
    }
}
