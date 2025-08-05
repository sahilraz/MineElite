package crypto.mining.elite.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import crypto.mining.elite.utils.AdminManager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class MiningAlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String uid = intent.getStringExtra("uid");
        String type = intent.getStringExtra("type");

        if (uid == null || type == null) return;

        AdminManager.getInstance().init(admin -> {
            if (admin == null || admin.baseApiUrl == null) return;

            String requestUrl = admin.baseApiUrl + "/?uid=" + uid + "&type=" + type;
            new Thread(() -> {
                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL(requestUrl).openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    int responseCode = connection.getResponseCode();
                    Log.d("MiningAlarmReceiver", "Request sent: " + requestUrl + " | Code: " + responseCode);
                    connection.disconnect();
                } catch (IOException e) {
                    Log.e("MiningAlarmReceiver", "Failed to send request", e);
                }
            }).start();
        });
    }
}
