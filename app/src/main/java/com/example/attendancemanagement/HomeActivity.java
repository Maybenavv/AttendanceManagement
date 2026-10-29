
package com.example.attendancemanagement;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

import java.util.Locale;

public class HomeActivity extends AppCompatActivity {

    private static final String DATABASE_URL =
            "https://attendance-management-a116e-default-rtdb.asia-southeast1.firebasedatabase.app";

    private DatabaseReference db;
    private DatabaseReference summaryRef;
    private ValueEventListener summaryListener;

    private String role;

    private TextView title, name, email, roleLabel;
    private TextView summaryHeading, summaryValue;
    private TextView summaryDescription, detailsTitle;
    private TextView detailsDescription;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_home);

        applyInsets();

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            goToLogin();
            return;
        }

        db = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference();

        title = findViewById(R.id.homeTitle);
        name = findViewById(R.id.homeName);
        email = findViewById(R.id.homeEmail);
        roleLabel = findViewById(R.id.homeRoleLabel);
        summaryHeading = findViewById(R.id.homeSummaryHeading);
        summaryValue = findViewById(R.id.homeSummaryValue);
        summaryDescription =
                findViewById(R.id.homeSummaryDescription);
        detailsTitle = findViewById(R.id.homeDetailsTitle);
        detailsDescription =
                findViewById(R.id.homeDetailsDescription);
        progress = findViewById(R.id.homeProgress);

        email.setText(user.getEmail() == null
                ? "" : user.getEmail());

        findViewById(R.id.homeLogoutButton)
                .setOnClickListener(v -> logout());

        findViewById(R.id.homeProfilePanel)
                .setOnClickListener(v ->
                        startActivity(new Intent(
                                this, ProfileActivity.class)));

        findViewById(R.id.homeDetailsPanel)
                .setOnClickListener(v -> {

                    if ("teacher".equals(role)) {
                        startActivity(new Intent(
                                this, TeacherActivity.class));

                    } else if ("student".equals(role)) {
                        startActivity(new Intent(
                                this, StudentActivity.class));
                    }
                });

        loadAccount(user.getUid());
    }

    private void loadAccount(String uid) {

        db.child("users").child(uid).get()
                .addOnSuccessListener(snapshot -> {

                    if (!snapshot.exists()) {
                        Toast.makeText(this,
                                "Account profile missing",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    role = snapshot.child("role")
                            .getValue(String.class);

                    if (!"teacher".equals(role)
                            && !"student".equals(role)) {
                        Toast.makeText(this,
                                "Invalid account role",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    updateName(snapshot.child("name")
                            .getValue(String.class));

                    configureHome(uid);
                })
                .addOnFailureListener(e ->
                        summaryDescription.setText(
                                "Unable to load account: "
                                        + e.getMessage()));
    }

    private void updateName(String value) {
        name.setText(value == null || value.trim().isEmpty()
                ? "Welcome back" : value);
    }

    private void configureHome(String uid) {

        if ("teacher".equals(role)) {
            title.setText("Teacher Home");
            roleLabel.setText("TEACHER ACCOUNT");
            summaryHeading.setText("YOUR CLASSES");
            detailsTitle.setText("Manage Attendance  ›");
            detailsDescription.setText(
                    "Manage subjects and mark attendance");

            progress.setVisibility(View.GONE);

            loadTeacherSummary();

        } else {
            title.setText("Student Home");
            roleLabel.setText("STUDENT ACCOUNT");
            summaryHeading.setText("OVERALL ATTENDANCE");
            detailsTitle.setText("View Attendance Details  ›");
            detailsDescription.setText(
                    "Subject percentages and history");

            progress.setVisibility(View.VISIBLE);

            loadStudentSummary(uid);
        }
    }

    private void loadStudentSummary(String uid) {

        clearSummaryListener();

        summaryValue.setText("Loading...");
        summaryDescription.setText("");

        summaryRef = db.child("attendance").child(uid);

        summaryListener = new ValueEventListener() {

            @Override
            public void onDataChange(
                    @NonNull DataSnapshot snapshot) {

                int total = 0;
                int present = 0;

                for (DataSnapshot record :
                        snapshot.getChildren()) {

                    String status = record.child("status")
                            .getValue(String.class);

                    if ("present".equals(status)) {
                        present++;
                        total++;
                    } else if ("absent".equals(status)) {
                        total++;
                    }
                }

                double percentage = total == 0
                        ? 0 : present * 100.0 / total;

                summaryValue.setText(String.format(
                        Locale.US, "%.1f%%", percentage));

                progress.setProgress(
                        (int) Math.round(percentage));

                summaryDescription.setText(String.format(
                        Locale.US,
                        "%d of %d classes attended • %d absent",
                        present, total, total - present));
            }

            @Override
            public void onCancelled(
                    @NonNull DatabaseError error) {
                summaryValue.setText("--");
                summaryDescription.setText(
                        "Unable to load attendance: "
                                + error.getMessage());
            }
        };

        summaryRef.addValueEventListener(summaryListener);
    }

    private void loadTeacherSummary() {

        clearSummaryListener();

        summaryValue.setText("Loading...");
        summaryDescription.setText("");

        db.child("subjects").get()
                .addOnSuccessListener(subjectsSnapshot -> {

                    long subjectCount =
                            subjectsSnapshot.getChildrenCount();

                    db.child("users")
                            .orderByChild("role")
                            .equalTo("student")
                            .get()
                            .addOnSuccessListener(studentsSnapshot -> {

                                long studentCount =
                                        studentsSnapshot.getChildrenCount();

                                summaryValue.setText(
                                        subjectCount + " Subjects");

                                summaryDescription.setText(
                                        studentCount + " Students registered");
                            })
                            .addOnFailureListener(e -> {
                                summaryValue.setText(
                                        subjectCount + " Subjects");
                                summaryDescription.setText(
                                        "Unable to load student count: "
                                                + e.getMessage());
                            });
                })
                .addOnFailureListener(e -> {
                    summaryValue.setText("--");
                    summaryDescription.setText(
                            "Unable to load subjects: "
                                    + e.getMessage());
                });
    }

    private void clearSummaryListener() {
        if (summaryRef != null && summaryListener != null) {
            summaryRef.removeEventListener(summaryListener);
        }
        summaryRef = null;
        summaryListener = null;
    }

    @Override
    protected void onResume() {
        super.onResume();

        FirebaseUser user =
                FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            goToLogin();
            return;
        }

        if (db != null && role != null) {
            db.child("users").child(user.getUid())
                    .child("name").get()
                    .addOnSuccessListener(snapshot ->
                            updateName(snapshot.getValue(String.class)));

            if ("teacher".equals(role)) {
                loadTeacherSummary();
            }
        }
    }

    private void logout() {
        FirebaseAuth.getInstance().signOut();
        goToLogin();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void applyInsets() {
        View root = findViewById(R.id.homeRoot);

        int left = root.getPaddingLeft();
        int top = root.getPaddingTop();
        int right = root.getPaddingRight();
        int bottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(
                root, (view, insets) -> {

                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                                    | WindowInsetsCompat.Type.displayCutout());

                    view.setPadding(
                            left + bars.left,
                            top + bars.top,
                            right + bars.right,
                            bottom + bars.bottom);

                    return insets;
                });

        ViewCompat.requestApplyInsets(root);
    }

    @Override
    protected void onDestroy() {
        clearSummaryListener();
        super.onDestroy();
    }
}

