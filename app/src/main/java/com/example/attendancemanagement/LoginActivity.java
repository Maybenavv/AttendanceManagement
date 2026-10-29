
package com.example.attendancemanagement;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.util.Log;

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
    private TextInputEditText email, password;
    private Button login;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_login);
        Toast.makeText(
                this,
                "Login screen loaded",
                Toast.LENGTH_LONG
        ).show();

        Log.d(TAG, "LoginActivity started");

        auth = FirebaseAuth.getInstance();

        email = findViewById(R.id.emailInput);
        password = findViewById(R.id.passwordInput);
        login = findViewById(R.id.loginButton);
        progress = findViewById(R.id.progress);

        if (email == null || password == null ||
                login == null || progress == null) {

            Log.e(TAG, "Required login views are missing");

            Toast.makeText(
                    this,
                    "Login layout error: Check XML IDs",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        login.setOnClickListener(v -> signIn());

        // Automatic routing disabled temporarily for testing.
        // Restore after confirming both dashboards work.
    }

    private void signIn() {

        String e = String.valueOf(email.getText()).trim();
        String p = String.valueOf(password.getText());

        if (e.isEmpty() || p.isEmpty()) {
            toast("Enter email and password");
            return;
        }

        setLoading(true);

        auth.signInWithEmailAndPassword(e, p)
                .addOnCompleteListener(this, task -> {

                    if (task.isSuccessful()) {
                        Log.d(TAG, "Firebase authentication successful");
                        routeUser();
                    } else {
                        setLoading(false);

                        Exception exception = task.getException();

                        Log.e(TAG, "Login failed", exception);

                        toast("Login failed: " +
                                (exception != null
                                        ? exception.getMessage()
                                        : "Unknown error"));
                    }
                });
    }

    private void routeUser() {

        FirebaseUser user = auth.getCurrentUser();

        if (user == null) {
            setLoading(false);
            return;
        }

        setLoading(true);

        FirebaseDatabase.getInstance(DATABASE_URL)
                .getReference("users")
                .child(user.getUid())
                .child("role")
                .get()
                .addOnCompleteListener(this, task -> {

                    setLoading(false);

                    if (!task.isSuccessful()) {

                        Log.e(TAG,
                                "Database role lookup failed",
                                task.getException());

                        toast("Unable to read account role");
                        return;
                    }

                    if (task.getResult() == null ||
                            !task.getResult().exists()) {

                        toast("Account profile not found");
                        return;
                    }

                    String role =
                            task.getResult().getValue(String.class);

                    Class<?> target;

                    if ("teacher".equals(role)) {
                        target = TeacherActivity.class;
                    } else if ("student".equals(role)) {
                        target = StudentActivity.class;
                    } else {
                        toast("Invalid user role");
                        return;
                    }

                    Log.d(TAG, "Opening dashboard for role: " + role);

                    startActivity(new Intent(this, target));
                    finish();
                });
    }

    private void setLoading(boolean value) {

        login.setEnabled(!value);
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
    }

    private void toast(String message) {

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
