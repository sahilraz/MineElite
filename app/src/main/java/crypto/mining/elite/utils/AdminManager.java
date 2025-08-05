package crypto.mining.elite.utils;

import androidx.annotation.Nullable;

import crypto.mining.elite.models.AdminModel;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class AdminManager {

    private static final AdminManager instance = new AdminManager();
    private AdminModel adminData;

    private AdminManager() {}

    public static AdminManager getInstance() {
        return instance;
    }

    public interface AdminCallback {
        void onAdminLoaded(@Nullable AdminModel admin);
    }

    public void init(AdminCallback callback) {
        if (adminData != null) {
            callback.onAdminLoaded(adminData);
            return;
        }

        FirebaseDatabase.getInstance().getReference("admin")
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            adminData = snapshot.getValue(AdminModel.class);
                            callback.onAdminLoaded(adminData);
                        } else {
                            callback.onAdminLoaded(null);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        callback.onAdminLoaded(null);
                    }
                });
    }

    public @Nullable AdminModel getAdmin() {
        return adminData;
    }
}
