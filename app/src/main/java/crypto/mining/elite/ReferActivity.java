package crypto.mining.elite;

import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import crypto.mining.elite.utils.AdminManager;
import crypto.mining.elite.utils.SessionManager;
import crypto.mining.elite.utils.UserManager;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReferActivity extends AppCompatActivity {

    private TextView referCode, referCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_refer);

        SessionManager.startSessionMonitor(this);

        referCode = findViewById(R.id.referralCodeText);
        referCount = findViewById(R.id.totalReferralsText);

        loadRefer();
        setupBottomNav();

        findViewById(R.id.copyButton).setOnClickListener(v -> {
            String code = referCode.getText().toString();
            if (!code.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Referral Code", code);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(ReferActivity.this, "Referral code copied!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ReferActivity.this, "Referral code is empty", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadRefer() {
        UserManager.getInstance().init(user -> {
            if (user == null || user.referCode == null || user.referCode.isEmpty()) {
                Toast.makeText(ReferActivity.this, "Failed to load refer data", Toast.LENGTH_SHORT).show();
                return;
            }

            String referCodeStr = user.referCode.trim().toUpperCase();
            referCode.setText(referCodeStr);

            FirebaseDatabase.getInstance().getReference("users")
                    .orderByChild("usedReferCode")
                    .equalTo(referCodeStr)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot snapshot) {
                            long count = snapshot.getChildrenCount();
                            referCount.setText(String.valueOf(count));
                            sendDailyReferOnce(user.uid, count);
                        }

                        @Override
                        public void onCancelled(DatabaseError error) {
                            Toast.makeText(ReferActivity.this, "Failed to fetch refer count", Toast.LENGTH_SHORT).show();
                            referCount.setText("0");
                            Log.e("ReferEarnActivity", "Firebase error: " + error.getMessage());
                        }
                    });
        });
    }

    private void sendDailyReferOnce(String uid, long referCount) {
        SharedPreferences prefs = getSharedPreferences("daily_refer", MODE_PRIVATE);
        String lastDate = prefs.getString("last_sent_date", null);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        if (today.equals(lastDate)) {
            Log.d("ReferEarnActivity", "Daily refer already sent today");
            return;
        }

        AdminManager.getInstance().init(admin -> {
            if (admin == null || admin.baseApiUrl == null || admin.baseApiUrl.isEmpty()) {
                Toast.makeText(this, "Failed to load API URL", Toast.LENGTH_SHORT).show();
                return;
            }

            String urlStr = admin.baseApiUrl + "/get-daily-coins?uid=" + uid + "&refer=" + referCount;

            new Thread(() -> {
                try {
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(5000);
                    conn.setReadTimeout(5000);

                    int responseCode = conn.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder response = new StringBuilder();
                        String inputLine;
                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        in.close();

                        prefs.edit().putString("last_sent_date", today).apply();
                        Log.d("ReferEarnActivity", "Daily refer success: " + response.toString());
                    } else {
                        Log.e("ReferEarnActivity", "Failed to send daily refer. Response code: " + responseCode);
                    }
                } catch (Exception e) {
                    Log.e("ReferEarnActivity", "Exception in daily refer request", e);
                }
            }).start();
        });
    }

    private void setupBottomNav() {
        int active = getResources().getColor(R.color.nav_active);
        int inactive = getResources().getColor(R.color.nav_inactive);

        ((TextView) findViewById(R.id.iconHome)).setTextColor(inactive);
        ((TextView) findViewById(R.id.textHome)).setTextColor(inactive);

        ((TextView) findViewById(R.id.iconStreak)).setTextColor(inactive);
        ((TextView) findViewById(R.id.textStreak)).setTextColor(inactive);

        ((TextView) findViewById(R.id.iconWithdraw)).setTextColor(inactive);
        ((TextView) findViewById(R.id.textWithdraw)).setTextColor(inactive);

        ((TextView) findViewById(R.id.iconRefer)).setTextColor(active);
        ((TextView) findViewById(R.id.textRefer)).setTextColor(active);

        ((TextView) findViewById(R.id.iconProfile)).setTextColor(inactive);
        ((TextView) findViewById(R.id.textProfile)).setTextColor(inactive);

        findViewById(R.id.navHome).setOnClickListener(v -> startActivity(new Intent(this, HomeActivity.class)));
        findViewById(R.id.navStreak).setOnClickListener(v -> startActivity(new Intent(this, StreakActivity.class)));
        findViewById(R.id.navWithdraw).setOnClickListener(v -> startActivity(new Intent(this, WithdrawActivity.class)));
        findViewById(R.id.navProfile).setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
    }
}
