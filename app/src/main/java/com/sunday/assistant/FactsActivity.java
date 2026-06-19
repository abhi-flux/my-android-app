package com.sunday.assistant;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class FactsActivity extends AppCompatActivity {

    private SundayDatabase db;
    private LinearLayout factsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_facts);

        db = new SundayDatabase(this);
        factsContainer = findViewById(R.id.factsContainer);

        loadFacts();
    }

    private void loadFacts() {
        factsContainer.removeAllViews();
        List<String> facts = db.getAllFacts();

        if (facts.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No memories saved yet.");
            empty.setTextColor(Color.LTGRAY);
            empty.setTextSize(16);
            factsContainer.addView(empty);
            return;
        }

        for (String fact : facts) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, 16, 0, 16);

            TextView factText = new TextView(this);
            factText.setText(fact);
            factText.setTextColor(Color.WHITE);
            factText.setTextSize(16);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            factText.setLayoutParams(textParams);

            Button deleteButton = new Button(this);
            deleteButton.setText("Delete");
            deleteButton.setOnClickListener(v -> {
                db.deleteFact(fact);
                loadFacts();
            });

            row.addView(factText);
            row.addView(deleteButton);
            factsContainer.addView(row);
        }
    }
}
