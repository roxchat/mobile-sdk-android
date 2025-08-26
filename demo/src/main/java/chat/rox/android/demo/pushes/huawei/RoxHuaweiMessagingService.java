package chat.rox.android.demo.pushes.huawei;

import android.util.Log;

import com.huawei.hms.push.HmsMessageService;
import com.huawei.hms.push.RemoteMessage;

import org.json.JSONException;
import org.json.JSONObject;

import chat.rox.android.demo.pushes.RoxPushSender;
import chat.rox.android.sdk.Rox;
import chat.rox.android.sdk.impl.backend.RoxInternalLog;

public class RoxHuaweiMessagingService extends HmsMessageService {
    private static final String TAG = "PushDemoLog";

    /**
     * When an app calls the getToken method to apply for a token from the server,
     * if the server does not return the token during current method calling, the server can return the token through this method later.
     * This method callback must be completed in 10 seconds. Otherwise, you need to start a new Job for callback processing.
     *
     * @param token token
     */
    @Override
    public void onNewToken(String token) {
    }

    @Override
    public void onMessageDelivered(String s, Exception e) {
        super.onMessageDelivered(s, e);
    }

    /**
     * This method is used to receive downstream data messages.
     * This method callback must be completed in 10 seconds. Otherwise, you need to start a new Job for callback processing.
     *
     * @param message RemoteMessage
     */
    @Override
    public void onMessageReceived(RemoteMessage message) {
        if (message == null) {
            Log.e(TAG, "Received message entity is null!");
            return;
        }

        String pushPayload = null;
        try {
            JSONObject jsonObject = new JSONObject(message.getData());
            JSONObject pushBody = jsonObject.getJSONObject("pushbody");
            pushPayload = pushBody.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            RoxInternalLog.getInstance().log("Push error: " + e.getMessage(), Rox.SessionBuilder.RoxLogVerbosityLevel.ERROR);

        }
        if (pushPayload != null) {
            RoxPushSender.getInstance().onPushMessage(
                getApplicationContext(),
                Rox.parseFcmPushNotification(pushPayload)
            );
        }
    }

    @Override
    public void onMessageSent(String msgId) {
    }

    @Override
    public void onSendError(String msgId, Exception exception) {
    }

    @Override
    public void onTokenError(Exception e) {
        super.onTokenError(e);
    }
}
