
package com.example.attendancemanagement;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class ProfileActivity extends AppCompatActivity {

    private static final String DATABASE_URL =
            "https://attendance-management-a116e-default-rtdb.asia-southeast1.firebasedatabase.app";

    private EditText nameInput;
    private EditText currentPasswordInput;
    private EditText newPasswordInput;
    private EditText confirmPasswordInput;

    private TextView emailText;
    private TextView roleText;

    private Button saveNameButton;
    private Button changePasswordButton;

    private DatabaseReference userRef;
    private FirebaseUser user;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_profile);
        findViewById(R.id.profileLogoutButton)
                .setOnClickListener(v -> {
                    FirebaseAuth.getInstance().signOut();

                    Intent intent = new Intent(
                            this,
                            LoginActivity.class
                    );

                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK |
                                    Intent.FLAG_ACTIVITY_CLEAR_TASK
                    );

                    startActivity(intent);
                    finish();
                });
        applyInsets();

        user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) {
            backToLogin();
            return;
        }

        userRef = FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("users")
                .child(user.getUid());

        nameInput = findViewById(R.id.nameInput);
        emailText = findViewById(R.id.emailText);
        roleText = findViewById(R.id.roleText);

        currentPasswordInput =
                findViewById(R.id.currentPasswordInput);
        newPasswordInput =
                findViewById(R.id.newPasswordInput);
        confirmPasswordInput =
                findViewById(R.id.confirmPasswordInput);

        saveNameButton = findViewById(R.id.saveNameButton);
        changePasswordButton =
                findViewById(R.id.changePasswordButton);

        findViewById(R.id.backButton)
                .setOnClickListener(v -> finish());

        emailText.setText(user.getEmail() == null
                ? "Unavailable" : user.getEmail());

        loadProfile();

        saveNameButton.setOnClickListener(v -> saveName());
        changePasswordButton.setOnClickListener(
                v -> changePassword());
    }

    private void loadProfile() {

        userRef.get()
                .addOnSuccessListener(snapshot -> {

                    String name = snapshot.child("name")
                            .getValue(String.class);

                    String role = snapshot.child("role")
                            .getValue(String.class);

                    nameInput.setText(name == null ? "" : name);

                    if ("teacher".equals(role)) {
                        roleText.setText("Teacher");
                    } else if ("student".equals(role)) {
                        roleText.setText("Student");
                    } else {
                        roleText.setText("Unknown");
                    }
                })
                .addOnFailureListener(e ->
                        showMessage("Profile load failed: "
                                + e.getMessage()));
    }

    private void saveName() {

        String name = nameInput.getText()
                .toString().trim();

        if (name.isEmpty() || name.length() > 80) {
            nameInput.setError(
                    "Enter a valid name (1-80 characters)");
            return;
        }

        saveNameButton.setEnabled(false);

        // Update ONLY the name, never role or email.
        userRef.child("name")
                .setValue(name)
                .addOnCompleteListener(task -> {

                    saveNameButton.setEnabled(true);

                    if (task.isSuccessful()) {
                        showMessage("Name updated successfully");
                    } else {
                        showMessage("Update failed: " +
                                errorMessage(task.getException()));
                    }
                });
    }

    private void changePassword() {

        String currentPassword = currentPasswordInput
                .getText().toString();

        String newPassword = newPasswordInput
                .getText().toString();

        String confirmPassword = confirmPasswordInput
                .getText().toString();

        if (currentPassword.isEmpty()) {
            currentPasswordInput.setError(
                    "Enter your current password");
            return;
        }

        if (newPassword.length() < 6) {
            newPasswordInput.setError(
                    "Use at least 6 characters");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            confirmPasswordInput.setError(
                    "Passwords do not match");
            return;
        }

        if (newPassword.equals(currentPassword)) {
            newPasswordInput.setError(
                    "Choose a different password");
            return;
        }

        FirebaseUser currentUser =
                FirebaseAuth.getInstance().getCurrentUser();

        if (currentUser == null ||
                currentUser.getEmail() == null) {
            showMessage("Please sign in again");
            return;
        }

        changePasswordButton.setEnabled(false);

        AuthCredential credential =
                EmailAuthProvider.getCredential(
                        currentUser.getEmail(),
                        currentPassword
                );

        // Verify current password before changing it.
        currentUser.reauthenticate(credential)
                .addOnCompleteListener(authTask -> {

                    if (!authTask.isSuccessful()) {
                        changePasswordButton.setEnabled(true);
                        showMessage(
                                "Current password verification failed: "
                                        + errorMessage(
                                        authTask.getException()));
                        return;
                    }

                    currentUser.updatePassword(newPassword)
                            .addOnCompleteListener(updateTask -> {

                                changePasswordButton.setEnabled(true);

                                if (updateTask.isSuccessful()) {
                                    currentPasswordInput.setText("");
                                    newPasswordInput.setText("");
                                    confirmPasswordInput.setText("");

                                    showMessage(
                                            "Password updated successfully");
                                } else {
                                    showMessage(
                                            "Password update failed: "
                                                    + errorMessage(
                                                    updateTask.getException()));
                                }
                            });
                });
    }

    private String errorMessage(Exception e) {
        return e == null ? "Unknown error" : e.getMessage();
    }

    private void showMessage(String message) {
        Toast.makeText(this, message,
                Toast.LENGTH_LONG).show();
    }

    private void applyInsets() {

        View root = findViewById(R.id.profileRoot);

        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root,
                (view, insets) -> {

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

    private void backToLogin() {
        Intent intent =
                new Intent(this, LoginActivity.class);

        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}
