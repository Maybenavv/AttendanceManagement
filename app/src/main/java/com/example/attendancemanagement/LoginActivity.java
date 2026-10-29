
package com.example.attendancemanagement;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "AttendanceLogin";

    private static final String DATABASE_URL =
            "https://attendance-management-a116e-default-rtdb.asia-southeast1.firebasedatabase.app";

    private FirebaseAuth auth;

    private TextInputEditText email;
    private TextInputEditText password;
    private Button login;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        setContentView(R.layout.activity_login);

        // Initialize Firebase Authentication
        auth = FirebaseAuth.getInstance();

        // Initialize UI elements
        email = findViewById(R.id.emailInput);
        password = findViewById(R.id.passwordInput);
        login = findViewById(R.id.loginButton);
        progress = findViewById(R.id.progress);

        // Check required UI elements
        if (email == null ||
                password == null ||
                login == null ||
                progress == null) {

            Log.e(TAG, "Required login views are missing");

            Toast.makeText(
                    this,
                    "Login layout error: Check XML IDs",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        // Hide loading indicator initially
        setLoading(false);

        // Sign in only when the button is clicked
        login.setOnClickListener(v -> signIn());

        // Automatic login intentionally disabled.
        // Existing Firebase sessions are not automatically routed.
    }

    private void signIn() {

        String emailAddress =
                String.valueOf(email.getText()).trim();

        String passwordValue =
                String.valueOf(password.getText());

        // Validate inputs
        if (emailAddress.isEmpty()) {
            email.setError("Enter your email");
            email.requestFocus();
            return;
        }

        if (passwordValue.isEmpty()) {
            password.setError("Enter your password");
            password.requestFocus();
            return;
        }

        // Show spinner and disable Sign In button
        setLoading(true);

        auth.signInWithEmailAndPassword(
                        emailAddress,
                        passwordValue
                )
                .addOnCompleteListener(this, task -> {

                    if (task.isSuccessful()) {

                        Log.d(
                                TAG,
                                "Firebase authentication successful"
                        );

                        // Check account role
                        routeUser();

                    } else {

                        setLoading(false);

                        Exception error = task.getException();

                        Log.e(
                                TAG,
                                "Firebase authentication failed",
                                error
                        );

                        toast(
                                "Login failed: " +
                                        (error != null
                                                ? error.getMessage()
                                                : "Unknown error")
                        );
                    }
                });
    }

    private void routeUser() {

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            setLoading(false);
            toast("Unable to get signed-in user");
            return;
        }

        // Keep spinner visible while checking role
        setLoading(true);

        FirebaseDatabase
                .getInstance(DATABASE_URL)
                .getReference("users")
                .child(user.getUid())
                .child("role")
                .get()
                .addOnCompleteListener(this, task -> {

                    if (!task.isSuccessful()) {

                        setLoading(false);

                        Log.e(
                                TAG,
                                "Database role lookup failed",
                                task.getException()
                        );

                        toast("Unable to read account role");
                        return;
                    }

                    if (task.getResult() == null ||
                            !task.getResult().exists()) {

                        setLoading(false);

                        toast("Account profile not found");
                        return;
                    }

                    String role = task.getResult()
                            .getValue(String.class);

                    if ("teacher".equals(role) ||
                            "student".equals(role)) {

                        Log.d(
                                TAG,
                                "Opening HomeActivity for: " + role
                        );

                        // Open shared Home page
                        Intent intent = new Intent(
                                LoginActivity.this,
                                HomeActivity.class
                        );

                        // Remove Login from back stack
                        intent.addFlags(
                                Intent.FLAG_ACTIVITY_NEW_TASK |
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                        );

                        startActivity(intent);
                        finish();

                    } else {

                        setLoading(false);

                        toast("Invalid account role");
                    }
                });
    }

    private void setLoading(boolean loading) {

        if (login == null || progress == null) {
            return;
        }

        // Prevent repeated login attempts
        login.setEnabled(!loading);

        // Show spinner only during authentication
        progress.setVisibility(
                loading ? View.VISIBLE : View.GONE
        );
    }

    private void toast(String message) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }
}
