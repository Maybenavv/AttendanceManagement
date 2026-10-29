
package com.example.attendancemanagement;

import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.ArrayList;
import android.graphics.Typeface;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class StudentActivity extends AppCompatActivity {

    private static final String DATABASE_URL =
            "https://attendance-management-a116e-default-rtdb.asia-southeast1.firebasedatabase.app";

    private LinearLayout list;
    private TextView summary, percentageText;
    private ProgressBar attendanceProgress;

    private DatabaseReference attendanceRef;
    private ValueEventListener attendanceListener;

    private static class SubjectStats {
        String name;
        String code = "";
        int total = 0;
        int present = 0;
        ArrayList<String> history = new ArrayList<>();

        SubjectStats(String name) {
            this.name = name;
        }

        double percentage() {
            return total == 0 ? 0 :
                    present * 100.0 / total;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_student);
        findViewById(R.id.backHomeButton)
                .setOnClickListener(v -> finish());

        applyInsets();

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            backToLogin();
            return;
        }

        list = findViewById(R.id.attendanceList);
        summary = findViewById(R.id.summary);
        percentageText = findViewById(R.id.percentageText);
        attendanceProgress = findViewById(
                R.id.attendanceProgress);

        findViewById(R.id.logoutButton)
                .setOnClickListener(v -> {
                    FirebaseAuth.getInstance().signOut();
                    backToLogin();
                });

        attendanceRef = FirebaseDatabase
                .getInstance(DATABASE_URL)
                .getReference("attendance")
                .child(user.getUid());

        loadAttendance();
    }

    private void loadAttendance() {

        attendanceListener = new ValueEventListener() {

            @Override
            public void onDataChange(
                    @NonNull DataSnapshot snapshot) {

                list.removeAllViews();

                Map<String, SubjectStats> subjects =
                        new LinkedHashMap<>();

                int total = 0;
                int present = 0;

                for (DataSnapshot record :
                        snapshot.getChildren()) {

                    String status = record.child("status")
                            .getValue(String.class);

                    if (!"present".equals(status)
                            && !"absent".equals(status)) {
                        continue;
                    }

                    String name = record.child("subject")
                            .getValue(String.class);

                    String subjectId = record
                            .child("subjectId")
                            .getValue(String.class);

                    String date = record.child("date")
                            .getValue(String.class);

                    if (name == null || name.trim().isEmpty())
                        name = "Unknown Subject";

                    // Prefer a stable subject ID when available.
                    String key = subjectId != null
                            && !subjectId.isEmpty()
                            ? subjectId
                            : name.trim().toLowerCase(Locale.ROOT);

                    SubjectStats stats = subjects.get(key);

                    if (stats == null) {
                        stats = new SubjectStats(name);
                        subjects.put(key, stats);
                    }

                    if (subjectId != null)
                        stats.code = subjectId;

                    stats.total++;
                    total++;

                    if ("present".equals(status)) {
                        stats.present++;
                        present++;
                    }

                    stats.history.add(
                            (date == null ? "Unknown date" : date)
                                    + "  -  "
                                    + status.toUpperCase(Locale.ROOT)
                    );
                }

                double overall = total == 0
                        ? 0 : present * 100.0 / total;

                percentageText.setText(String.format(
                        Locale.US, "%.1f%%", overall));

                attendanceProgress.setProgress(
                        (int) Math.round(overall));

                summary.setText(String.format(
                        Locale.US,
                        "%d of %d classes attended  •  %d absent",
                        present, total, total - present
                ));

                for (SubjectStats stats :
                        subjects.values()) {
                    addSubjectCard(stats);
                }

                if (subjects.isEmpty()) {
                    showMessage(
                            "No attendance records available yet.");
                }
            }

            @Override
            public void onCancelled(
                    @NonNull DatabaseError error) {

                summary.setText(
                        "Unable to load attendance: "
                                + error.getMessage()
                );

                percentageText.setText("--");
                attendanceProgress.setProgress(0);
                list.removeAllViews();
                showMessage("Please check your connection.");
            }
        };

        attendanceRef.addValueEventListener(
                attendanceListener);
    }

    private void addSubjectCard(SubjectStats stats) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(15), dp(12), dp(15));
        card.setBackgroundResource(R.drawable.card_bg);

        LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(10);

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams detailParams =
                new LinearLayout.LayoutParams(0, -2, 1);

        TextView subjectName = new TextView(this);
        subjectName.setText(stats.name);
        subjectName.setTextSize(15);
        subjectName.setTextColor(
                Color.parseColor("#1E293B"));
        subjectName.setTypeface(null, Typeface.BOLD);

        details.addView(subjectName);

        TextView count = new TextView(this);
        count.setText(String.format(
                Locale.US,
                "%d / %d classes attended",
                stats.present, stats.total
        ));
        count.setTextSize(12);
        count.setTextColor(
                Color.parseColor("#64748B"));
        count.setPadding(0, dp(5), 0, 0);

        details.addView(count);

        if (!stats.code.isEmpty()) {
            TextView code = new TextView(this);
            code.setText(stats.code);
            code.setTextSize(12);
            code.setTextColor(
                    Color.parseColor("#2563EB"));
            code.setPadding(0, dp(4), 0, 0);
            details.addView(code);
        }

        card.addView(details, detailParams);

        CirclePercentageView circle =
                new CirclePercentageView(
                        this, stats.percentage());

        card.addView(circle,
                new LinearLayout.LayoutParams(
                        dp(62), dp(62)));

        card.setClickable(true);
        card.setFocusable(true);

        card.setOnClickListener(v ->
                showSubjectDetails(stats));

        list.addView(card, cardParams);
    }

    private void showSubjectDetails(SubjectStats stats) {

        ArrayList<String> history =
                new ArrayList<>(stats.history);

        // Date strings are YYYY-MM-DD, so lexical
        // sorting also sorts them chronologically.
        Collections.sort(history,
                Collections.reverseOrder());

        String message = String.format(
                Locale.US,
                "Attendance: %.1f%%\n" +
                        "Present: %d\nAbsent: %d\n" +
                        "Total Classes: %d\n\nHistory\n%s",
                stats.percentage(),
                stats.present,
                stats.total - stats.present,
                stats.total,
                android.text.TextUtils.join("\n", history)
        );

        new AlertDialog.Builder(this)
                .setTitle(stats.name)
                .setMessage(message)
                .setPositiveButton("Close", null)
                .show();
    }

    private class CirclePercentageView extends View {

        private final Paint paint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        private final double percentage;

        CirclePercentageView(
                StudentActivity activity, double value) {
            super(activity);
            percentage = value;
            setContentDescription(String.format(
                    Locale.US, "%.1f percent attendance", value));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(cx, cy) - dp(7);
            float stroke = dp(5);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(Color.parseColor("#E2E8F0"));

            canvas.drawCircle(cx, cy, radius, paint);

            paint.setColor(Color.parseColor(
                    percentage >= 75
                            ? "#22A06B" : "#F59E0B"
            ));

            RectF oval = new RectF(
                    cx - radius, cy - radius,
                    cx + radius, cy + radius
            );

            canvas.drawArc(
                    oval, -90,
                    (float)(percentage * 3.6),
                    false, paint
            );

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(
                    Color.parseColor("#17376D"));
            paint.setTextSize(dp(11));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(
                    android.graphics.Typeface.DEFAULT_BOLD);

            String text = String.format(
                    Locale.US, "%.0f%%", percentage);

            Paint.FontMetrics metrics =
                    paint.getFontMetrics();

            float baseline = cy -
                    (metrics.ascent + metrics.descent) / 2f;

            canvas.drawText(text, cx, baseline, paint);
        }
    }

    private void showMessage(String message) {
        TextView empty = new TextView(this);
        empty.setText(message);
        empty.setTextColor(
                Color.parseColor("#64748B"));
        empty.setTextSize(14);
        empty.setPadding(dp(12), dp(20),
                dp(12), dp(20));
        list.addView(empty);
    }

    private void applyInsets() {
        View root = findViewById(R.id.studentRoot);

        int left = root.getPaddingLeft();
        int top = root.getPaddingTop();
        int right = root.getPaddingRight();
        int bottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(
                root, (view, insets) -> {

                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                                    | WindowInsetsCompat.Type.displayCutout()
                    );

                    view.setPadding(
                            left + bars.left,
                            top + bars.top,
                            right + bars.right,
                            bottom + bars.bottom
                    );

                    return insets;
                });

        ViewCompat.requestApplyInsets(root);
    }

    private int dp(int size) {
        return Math.round(
                size * getResources()
                        .getDisplayMetrics().density
        );
    }

    private void backToLogin() {
        startActivity(
                new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {
        if (attendanceRef != null &&
                attendanceListener != null) {
            attendanceRef.removeEventListener(
                    attendanceListener);
        }
        super.onDestroy();
    }
}
