package crypto.mining.elite.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;

import crypto.mining.elite.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

public class SessionManager {

    private static ValueEventListener sessionListener;

    public static void startSessionMonitor(@NonNull final Activity activity) {
        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) return;

        String localSessionId = activity.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                .getString("sessionId", null);

        if (localSessionId == null) return;

        DatabaseReference sessionRef = FirebaseDatabase.getInstance()
                .getReference("users").child(uid).child("sessionId");

        // Detach previous listener to avoid multiple triggers
        if (sessionListener != null) {
            sessionRef.removeEventListener(sessionListener);
        }

        sessionListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String remoteSessionId = snapshot.getValue(String.class);
                if (remoteSessionId == null || !remoteSessionId.equals(localSessionId)) {
                    Toast.makeText(activity, "Logged out from another device", Toast.LENGTH_LONG).show();
                    FirebaseAuth.getInstance().signOut();

                    activity.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                            .edit().remove("sessionId").apply();

                    Intent intent = new Intent(activity, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(intent);
                    activity.finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Optional: Handle error
            }
        };

        sessionRef.addValueEventListener(sessionListener);
    }
}
