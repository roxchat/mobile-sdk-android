package chat.rox.android.sdk.impl;

public interface AccessChecker {
    AccessChecker EMPTY = new AccessChecker(){
        @Override
        public void checkAccess() {

        }
    };

    void checkAccess();
}
