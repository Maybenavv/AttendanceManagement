
package com.example.attendancemanagement;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class TeacherActivity extends AppCompatActivity {

    private static final String DATABASE_URL =
            "https://attendance-management-a116e-default-rtdb.asia-southeast1.firebasedatabase.app";

    private LinearLayout studentList;
    private Spinner subjectSpinner;
    private DatabaseReference database;

    private DatabaseReference usersRef;
    private DatabaseReference subjectsRef;

    private ValueEventListener usersListener;
    private ValueEventListener subjectsListener;

    private final Map<String, TextView> statusViews =
            new LinkedHashMap<>();

    private final ArrayList<Subject> subjects =
            new ArrayList<>();

    private final String today =
            new SimpleDateFormat(
                    "yyyy-MM-dd", Locale.US
            ).format(new Date());

    private static class Subject {
        String code;
        String name;

        Subject(String code, String name) {
            this.code = code;
            this.name = name;
        }

        @NonNull
        @Override
        public String toString() {
            return name + " (" + code + ")";
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_teacher);

        applyInsets();

        FirebaseUser teacher =
                FirebaseAuth.getInstance().getCurrentUser();

        if (teacher == null) {
            backToLogin();
            return;
        }

        database = FirebaseDatabase
                .getInstance(DATABASE_URL)
                .getReference();

        usersRef = database.child("users");
        subjectsRef = database.child("subjects");

        studentList = findViewById(R.id.studentList);
        subjectSpinner = findViewById(R.id.subjectSpinner);

        TextView dateLabel = findViewById(R.id.dateLabel);
        Button logout = findViewById(R.id.logoutButton);
        Button addSubject =
                findViewById(R.id.addSubjectButton);

        dateLabel.setText("Date: " + today);

        logout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            backToLogin();
        });

        addSubject.setOnClickListener(v ->
                showAddSubjectDialog());

        subjectSpinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {

                    @Override
                    public void onItemSelected(
                            AdapterView<?> parent,
                            View view,
                            int position,
                            long id) {
                        refreshAttendance();
                    }

                    @Override
                    public void onNothingSelected(
                            AdapterView<?> parent) {
                    }
                });

        showListMessage("Checking teacher access...");

        usersRef.child(teacher.getUid())
                .child("role")
                .get()
                .addOnSuccessListener(snapshot -> {

                    String role =
                            snapshot.getValue(String.class);

                    if ("teacher".equals(role)) {
                        loadSubjects();
                        loadStudents();
                    } else {
                        Toast.makeText(this,
                                "Teacher access required",
                                Toast.LENGTH_LONG).show();
                        FirebaseAuth.getInstance().signOut();
                        backToLogin();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            "Role check failed: "
                                    + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    backToLogin();
                });
    }

    private Subject selectedSubject() {

        int position =
                subjectSpinner.getSelectedItemPosition();

        if (position < 0 ||
                position >= subjects.size()) {
            return null;
        }

        return subjects.get(position);
    }

    private void loadSubjects() {

        subjectsListener = new ValueEventListener() {

            @Override
            public void onDataChange(
                    @NonNull DataSnapshot snapshot) {

                Subject previous = selectedSubject();
                String previousCode =
                        previous == null ? null : previous.code;

                subjects.clear();

                for (DataSnapshot item :
                        snapshot.getChildren()) {

                    String code = item.child("code")
                            .getValue(String.class);

                    String name = item.child("name")
                            .getValue(String.class);

                    if (code == null || name == null)
                        continue;

                    subjects.add(new Subject(code, name));
                }

                ArrayAdapter<Subject> adapter =
                        new ArrayAdapter<>(
                                TeacherActivity.this,
                                android.R.layout.simple_spinner_item,
                                subjects
                        );

                adapter.setDropDownViewResource(
                        android.R.layout.simple_spinner_dropdown_item
                );

                subjectSpinner.setAdapter(adapter);

                int selected = 0;

                if (previousCode != null) {
                    for (int i = 0; i < subjects.size(); i++) {
                        if (previousCode.equals(
                                subjects.get(i).code)) {
                            selected = i;
                            break;
                        }
                    }
                }

                if (!subjects.isEmpty()) {
                    subjectSpinner.setSelection(selected);
                }

                refreshAttendance();
            }

            @Override
            public void onCancelled(
                    @NonNull DatabaseError error) {

                Toast.makeText(TeacherActivity.this,
                        "Unable to load subjects: "
                                + error.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        };

        subjectsRef.addValueEventListener(subjectsListener);
    }

    private void showAddSubjectDialog() {

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(
                dp(20), dp(8), dp(20), dp(4));

        EditText codeInput = new EditText(this);
        codeInput.setHint("Subject code (e.g. 24CSA685)");
        codeInput.setSingleLine(true);

        EditText nameInput = new EditText(this);
        nameInput.setHint("Subject name");
        nameInput.setSingleLine(true);

        layout.addView(codeInput);
        layout.addView(nameInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add New Subject")
                .setView(layout)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Add Subject", null)
                .create();

        dialog.setOnShowListener(unused -> {

            dialog.getButton(
                    AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener(v -> {

                String code = codeInput.getText()
                        .toString().trim()
                        .toUpperCase(Locale.ROOT);

                String name = nameInput.getText()
                        .toString().trim();

                if (!code.matches("[A-Z0-9_-]{1,40}")) {
                    codeInput.setError(
                            "Use letters, numbers, - or _");
                    return;
                }

                if (name.isEmpty() ||
                        name.length() > 120) {
                    nameInput.setError(
                            "Enter a subject name (max 120 characters)");
                    return;
                }

                DatabaseReference subjectRef =
                        subjectsRef.child(code);

                subjectRef.get()
                        .addOnSuccessListener(snapshot -> {

                            if (snapshot.exists()) {
                                codeInput.setError(
                                        "Subject code already exists");
                                return;
                            }

                            Map<String, Object> values =
                                    new HashMap<>();

                            values.put("code", code);
                            values.put("name", name);

                            subjectRef.setValue(values)
                                    .addOnSuccessListener(done -> {
                                        Toast.makeText(this,
                                                        "Subject added",
                                                        Toast.LENGTH_SHORT)
                                                .show();
                                        dialog.dismiss();
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(this,
                                                            "Failed: "
                                                                    + e.getMessage(),
                                                            Toast.LENGTH_LONG)
                                                    .show());
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(this,
                                        "Unable to check subject: "
                                                + e.getMessage(),
                                        Toast.LENGTH_LONG).show());
            });
        });

        dialog.show();
    }

    private void loadStudents() {

        showListMessage("Loading students...");

        usersListener = new ValueEventListener() {

            @Override
            public void onDataChange(
                    @NonNull DataSnapshot snapshot) {

                studentList.removeAllViews();
                statusViews.clear();

                if (!snapshot.hasChildren()) {
                    showListMessage(
                            "No student profiles found.");
                    return;
                }

                for (DataSnapshot student :
                        snapshot.getChildren()) {

                    String uid = student.getKey();
                    String name = student.child("name")
                            .getValue(String.class);

                    if (uid == null) continue;

                    if (name == null || name.isEmpty()) {
                        name = "Student";
                    }

                    addStudentCard(uid, name);
                }

                refreshAttendance();
            }

            @Override
            public void onCancelled(
                    @NonNull DatabaseError error) {

                studentList.removeAllViews();
                statusViews.clear();

                showListMessage(
                        "Unable to load students: "
                                + error.getMessage());
            }
        };

        usersRef.orderByChild("role")
                .equalTo("student")
                .addValueEventListener(usersListener);
    }

    private void addStudentCard(
            String uid, String name) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                dp(16), dp(14), dp(16), dp(14));

        card.setBackgroundResource(R.drawable.card_bg);

        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(-1, -2);

        params.bottomMargin = dp(12);
        studentList.addView(card, params);

        TextView studentName = new TextView(this);
        studentName.setText(name);
        studentName.setTextSize(16);
        studentName.setTextColor(
                Color.parseColor("#17376D"));
        studentName.setTypeface(null, Typeface.BOLD);

        card.addView(studentName);

        TextView status = new TextView(this);
        status.setText("Not marked");
        status.setTextSize(13);
        status.setTextColor(Color.GRAY);
        status.setPadding(0, dp(8), 0, dp(10));

        card.addView(status);
        statusViews.put(uid, status);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        Button present = new Button(this);
        Button absent = new Button(this);

        present.setText("Present");
        absent.setText("Absent");

        present.setTextColor(Color.WHITE);
        absent.setTextColor(Color.WHITE);

        present.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.parseColor("#16A34A")));

        absent.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        Color.parseColor("#DC2626")));

        LinearLayout.LayoutParams first =
                new LinearLayout.LayoutParams(0, -2, 1);

        LinearLayout.LayoutParams second =
                new LinearLayout.LayoutParams(0, -2, 1);

        second.leftMargin = dp(10);

        actions.addView(present, first);
        actions.addView(absent, second);

        card.addView(actions);

        present.setOnClickListener(v ->
                saveAttendance(uid, "present"));

        absent.setOnClickListener(v ->
                saveAttendance(uid, "absent"));
    }

    private void refreshAttendance() {

        Subject subject = selectedSubject();

        for (Map.Entry<String, TextView> entry :
                statusViews.entrySet()) {

            String uid = entry.getKey();
            TextView statusView = entry.getValue();

            if (subject == null) {
                statusView.setText(
                        "Add or select a subject first");
                statusView.setTextColor(Color.GRAY);
                continue;
            }

            String key = attendanceKey(subject.code);

            statusView.setText("Checking attendance...");
            statusView.setTextColor(Color.GRAY);

            database.child("attendance")
                    .child(uid)
                    .child(key)
                    .child("status")
                    .get()
                    .addOnSuccessListener(snapshot -> {

                        Subject current = selectedSubject();

                        if (current == null ||
                                !subject.code.equals(current.code))
                            return;

                        String saved =
                                snapshot.getValue(String.class);

                        displayStatus(statusView, saved);
                    })
                    .addOnFailureListener(e -> {
                        Subject current = selectedSubject();

                        if (current == null ||
                                !subject.code.equals(current.code))
                            return;

                        statusView.setText(
                                "Unable to read attendance");
                        statusView.setTextColor(
                                Color.parseColor("#DC2626"));
                    });
        }
    }

    private String attendanceKey(String code) {
        return today + "_" +
                code.toLowerCase(Locale.ROOT);
    }

    private void saveAttendance(
            String studentUid, String attendanceStatus) {

        Subject subject = selectedSubject();

        if (subject == null) {
            Toast.makeText(this,
                    "Please add or select a subject",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser teacher =
                FirebaseAuth.getInstance().getCurrentUser();

        if (teacher == null) {
            backToLogin();
            return;
        }

        String key = attendanceKey(subject.code);

        Map<String, Object> record = new HashMap<>();

        record.put("date", today);
        record.put("subject", subject.name);
        record.put("subjectId", subject.code);
        record.put("status", attendanceStatus);
        record.put("teacherUid", teacher.getUid());

        database.child("attendance")
                .child(studentUid)
                .child(key)
                .setValue(record)
                .addOnSuccessListener(unused -> {

                    Subject current = selectedSubject();

                    if (current != null &&
                            subject.code.equals(current.code)) {

                        TextView statusView =
                                statusViews.get(studentUid);

                        if (statusView != null) {
                            displayStatus(
                                    statusView,
                                    attendanceStatus);
                        }
                    }

                    Toast.makeText(this,
                            "Attendance saved",
                            Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Save failed: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
    }

    private void displayStatus(
            TextView view, String status) {

        if ("present".equals(status)) {
            view.setText("Present");
            view.setTextColor(
                    Color.parseColor("#16A34A"));

        } else if ("absent".equals(status)) {
            view.setText("Absent");
            view.setTextColor(
                    Color.parseColor("#DC2626"));

        } else {
            view.setText("Not marked");
            view.setTextColor(Color.GRAY);
        }
    }

    private void showListMessage(String message) {
        studentList.removeAllViews();

        TextView text = new TextView(this);
        text.setText(message);
        text.setTextSize(14);
        text.setTextColor(
                Color.parseColor("#64748B"));
        text.setPadding(0, dp(12), 0, dp(12));

        studentList.addView(text);
    }

    private void applyInsets() {
        View root = findViewById(R.id.teacherRoot);

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

    private int dp(int value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics().density);
    }

    private void backToLogin() {
        startActivity(
                new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    protected void onDestroy() {

        if (subjectsRef != null &&
                subjectsListener != null) {
            subjectsRef.removeEventListener(
                    subjectsListener);
        }

        if (usersRef != null &&
                usersListener != null) {
            usersRef.orderByChild("role")
                    .equalTo("student")
                    .removeEventListener(usersListener);
        }

        super.onDestroy();
    }
}
