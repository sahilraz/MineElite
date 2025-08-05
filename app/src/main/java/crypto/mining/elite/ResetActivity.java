package crypto.mining.elite;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class ResetActivity extends AppCompatActivity {

    private EditText resetEmailEditText;
    private Button resetPasswordButton;
    private FirebaseAuth mAuth;

    private final String defaultButtonText = "Reset Password";  // Optional: Can load from string resource

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset);

        resetEmailEditText = findViewById(R.id.resetEmailEditText);
        resetPasswordButton = findViewById(R.id.resetPasswordButton);
        mAuth = FirebaseAuth.getInstance();

        resetPasswordButton.setOnClickListener(view -> {
            String email = resetEmailEditText.getText().toString().trim();

            // Validate input
            if (TextUtils.isEmpty(email)) {
                Toast.makeText(this, "Please enter your email", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, "Enter a valid email address", Toast.LENGTH_SHORT).show();
                return;
            }

            // Disable button and show loading
            resetPasswordButton.setEnabled(false);
            resetPasswordButton.setText(R.string.please_wait);

            // Send reset link
            mAuth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        // Re-enable button and reset text
                        resetPasswordButton.setEnabled(true);
                        resetPasswordButton.setText(defaultButtonText);

                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Reset link sent to your email", Toast.LENGTH_LONG).show();
                            finish(); // Optional: close activity
                        } else {
                            String error = task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Failed to send reset email";
                            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }
}
