package crypto.mining.elite.utils;

import androidx.annotation.Nullable;
import crypto.mining.elite.models.UserModel;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

public class UserManager {

    private static final UserManager instance = new UserManager();
    private UserModel currentUser;

    private UserManager() {}

    public static UserManager getInstance() {
        return instance;
    }

    public interface UserCallback {
        void onUserLoaded(@Nullable UserModel user);
    }

    public void init(UserCallback callback) {
        if (currentUser != null) {
            callback.onUserLoaded(currentUser);
            return;
        }

        String uid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : null;

        if (uid == null) {
            callback.onUserLoaded(null);
            return;
        }

        FirebaseDatabase.getInstance().getReference("users").child(uid)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            currentUser = snapshot.getValue(UserModel.class);
                            callback.onUserLoaded(currentUser);
                        } else {
                            callback.onUserLoaded(null);
                        }
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        callback.onUserLoaded(null);
                    }
                });
    }

    public @Nullable UserModel getUser() {
        return currentUser;
    }
}
