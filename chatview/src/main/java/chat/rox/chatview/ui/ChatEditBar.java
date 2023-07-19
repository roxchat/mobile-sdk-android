package chat.rox.chatview.ui;

import static chat.rox.android.sdk.Message.Type.FILE_FROM_VISITOR;
import static chat.rox.android.sdk.Message.Type.VISITOR;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import chat.rox.android.sdk.BuildConfig;
import chat.rox.android.sdk.Message;
import chat.rox.android.sdk.MessageStream;
import chat.rox.android.sdk.Operator;
import chat.rox.chatview.ChatHolderActionsImpl;
import chat.rox.chatview.R;
import chat.rox.chatview.utils.AnchorMenuDialog;
import chat.rox.chatview.utils.FileHelper;
import chat.rox.chatview.utils.ViewUtils;

public class ChatEditBar extends LinearLayout implements ActivityResultCallback<ActivityResult> {
    private MessageStream stream;
    private State state;
    private ChatList.ListController listController;

    private ImageButton sendButton;
    private ImageButton editButton;

    private LinearLayout enterBar;
    private EditText editTextMessage;

    private LinearLayout editBar;
    private TextView textEditingMessage;

    private LinearLayout replyBar;
    private TextView textSenderName;
    private TextView textReplyMessage;
    private ImageView replyThumbnail;
    private TextView textReplyId;

    private ImageButton chatMenuButton;
    private AnchorMenuDialog chatMenuDialog = null;
    private AlertDialog ratingDialog;
    private RatingBar ratingBar;
    private Button ratingButton;

    private Message editedMessage = null;
    private Message quotedMessage = null;
    private FileHelper fileHelper;

    public static class FilePickerFragment extends Fragment {
        private ActivityResultCallback<ActivityResult> callback;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true); // Must be set to true

            ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (callback == null) {
                        Log.e(getClass().getSimpleName(), "Callback is null");
                        return;
                    }
                    callback.onActivityResult(result);

                    FilePickerFragment currentFragment = FilePickerFragment.this;
                    currentFragment.getParentFragmentManager()
                        .beginTransaction()
                        .remove(currentFragment)
                        .commit();
                }
            });
            FileHelper.openFilePicker(launcher);
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            callback = null;
        }

        public void setCallback(ActivityResultCallback<ActivityResult> callback) {
            this.callback = callback;
        }

        public static FilePickerFragment create(Context context, ActivityResultCallback<ActivityResult> callback) {
            Activity requiredActivity = ViewUtils.getRequiredActivity(context);
            if (!(requiredActivity instanceof AppCompatActivity)) {
                Log.e(ChatHolderActionsImpl.class.getSimpleName(), "Host activity must be inherited from " + AppCompatActivity.class.getSimpleName() + " for opening image detail screen");
                return null;
            }

            FilePickerFragment filePickerFragment = new FilePickerFragment();
            filePickerFragment.setCallback(callback);
            FragmentActivity activity = (FragmentActivity) requiredActivity;
            activity.getSupportFragmentManager()
                .beginTransaction()
                .add(filePickerFragment, null)
                .commit();

            return filePickerFragment;
        }
    }

    public ChatEditBar(Context context) {
        this(context, null);
    }

    public ChatEditBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ChatEditBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        View rootView = LayoutInflater.from(context).inflate(R.layout.view_chateditbar, this, true);

        setOrientation(VERTICAL);
        setBackgroundColor(ViewUtils.resolveAttr(R.attr.chv_bar_background, context));

        editBar = rootView.findViewById(R.id.editBar);
        replyBar = rootView.findViewById(R.id.replyBar);
        enterBar = rootView.findViewById(R.id.enterBar);

        textEditingMessage = rootView.findViewById(R.id.textViewEditText);
        textSenderName = rootView.findViewById(R.id.textViewSenderName);
        textReplyMessage = rootView.findViewById(R.id.textViewReplyText);
        replyThumbnail = rootView.findViewById(R.id.replyThumbnail);
        textReplyId = rootView.findViewById(R.id.quote_Id);
        chatMenuButton = rootView.findViewById(R.id.imageButtonChatMenu);

        initRemoveBar(rootView);
        initSendButton(rootView);
        initEditButton(rootView);
        initEditText(rootView);
        initChatMenuDialog(rootView);
        initOperatorRateDialog();

        updateUI(State.SENDING);
    }

    public void setListController(ChatList.ListController listController) {
        this.listController = listController;
    }

    public void setFileHelper(FileHelper fileHelper) {
        this.fileHelper = fileHelper;
    }

    private void initRemoveBar(View rootView) {
        ImageView deleteReplyButton = rootView.findViewById(R.id.imageButtonReplyDelete);
        deleteReplyButton.setOnClickListener(view -> updateUI(State.SENDING));

        ImageView deleteEditButton = rootView.findViewById(R.id.imageButtonEditDelete);
        deleteEditButton.setOnClickListener(view -> {
            updateUI(State.SENDING);
            editTextMessage.getText().clear();
        });

        ViewGroup layEditBody = rootView.findViewById(R.id.layEditBody);
        ViewGroup layReplyBody = rootView.findViewById(R.id.layReplyBody);
        layEditBody.setOnClickListener(view -> listController.scrollToMessage(editedMessage));
        layReplyBody.setOnClickListener(view -> listController.scrollToMessage(quotedMessage));
    }

    private void initEditButton(View rootView) {
        editButton = rootView.findViewById(R.id.imageButtonAcceptChanges);
        editButton.setOnClickListener(view -> {
            String newText = editTextMessage.getText().toString();
            editTextMessage.getText().clear();
            newText = newText.trim();
            if (newText.isEmpty()) {
                Toast.makeText(getContext(), R.string.failed_send_empty_message, Toast.LENGTH_SHORT).show();
            } else if (!newText.equals(editedMessage.getText().trim())) {
                stream.editMessage(editedMessage, newText, null);
            }
            sendingState();
        });
    }

    private void initChatMenuDialog(final View rootView) {
        chatMenuButton = rootView.findViewById(R.id.imageButtonChatMenu);
        chatMenuButton.setOnClickListener(view -> {
            if (!isDialogShown()) {
                showChatDialog();
            }
        });
    }

    private void showChatDialog() {
        AnchorMenuDialog chatMenuDialog = new AnchorMenuDialog(getContext());
        this.chatMenuDialog = chatMenuDialog;
        chatMenuDialog.setOnMenuItemClickListener(itemId -> {
            if (itemId == R.id.relLay_new_attachment) {
                FilePickerFragment.create(getContext(), this);
            } else if (itemId == R.id.relLay_rate_operator) {
                showRatingDialog();
            }
            hideChatMenu();
        });
        Animation animationRotateShow = AnimationUtils.loadAnimation(getContext(), R.anim.rotate_show);
        Animation animationRotateHide = AnimationUtils.loadAnimation(getContext(), R.anim.rotate_hide);
        chatMenuDialog.setOnShowListener(dialog -> {
            chatMenuDialog.enableItem(R.id.relLay_rate_operator, stream.getCurrentOperator() != null);
            chatMenuButton.startAnimation(animationRotateShow);
        });
        chatMenuDialog.setOnDismissListener(dialog -> chatMenuButton.startAnimation(animationRotateHide));
        chatMenuDialog.setMenu(R.layout.dialog_chat_menu, R.id.menuItems);
        chatMenuDialog.setGravity(Gravity.CENTER_HORIZONTAL);
        chatMenuDialog.show(chatMenuButton);
    }

    private void initOperatorRateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.rate_operator_title);
        View view = View.inflate(getContext(), R.layout.rating_bar, null);
        ratingBar = view.findViewById(R.id.ratingBar);
        ratingButton = view.findViewById(R.id.ratingBarButton);
        builder.setView(view);
        ratingDialog = builder.create();
    }

    public void showRatingDialog() {
        Operator operator = stream.getCurrentOperator();
        if (operator != null) {
            final Operator.Id operatorId = operator.getId();
            int rating = stream.getLastOperatorRating(operatorId);
            ratingBar.setOnRatingBarChangeListener((ratingBar, rating1, fromUser) -> ratingButton.setEnabled(rating1 != 0));
            ratingBar.setRating(rating);
            ratingButton.setEnabled(rating != 0);
            ratingDialog.show();
            ratingButton.setOnClickListener(v -> {
                if (ratingBar.getRating() != 0) {
                    ratingDialog.dismiss();
                    Toast.makeText(getContext(), R.string.rate_operator_rating_sent, Toast.LENGTH_LONG).show();
                    stream.rateOperator(operatorId, (int) ratingBar.getRating(), null);
                } else {
                    Toast.makeText(getContext(), R.string.rate_operator_rating_empty, Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    public boolean isDialogShown() {
        return chatMenuDialog != null && chatMenuDialog.isShowing();
    }

    public void hideChatMenu() {
        if (isDialogShown()) {
            chatMenuDialog.dismiss();
            Animation animationRotateHide = AnimationUtils.loadAnimation(getContext(), R.anim.rotate_hide);
            chatMenuButton.startAnimation(animationRotateHide);
        }
    }

    @Override
    public void onActivityResult(ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent intent = result.getData();
            if (intent != null) {
                Uri uri = intent.getData();
                if (uri != null) {
                    fileHelper.sendFileWithDescriptor(uri);
                }
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    public enum State {
        SENDING, EDITING, REPLYING;
    }

    public State getState() {
        return state;
    }

    public void replyState(Message message) {
        quotedMessage = message;
        if (editedMessage != null) {
            editTextMessage.getText().clear();
            editedMessage = null;
        }

        updateUI(State.REPLYING);

        textSenderName.setText(
            (quotedMessage.getType() == VISITOR || quotedMessage.getType() == FILE_FROM_VISITOR)
                ? this.getResources().getString(R.string.visitor_sender_name)
                : quotedMessage.getSenderName()
        );
        textReplyId.setText(quotedMessage.getServerSideId());
        Message.Attachment quotedMessageAttachment = quotedMessage.getAttachment();

        String replyMessage;
        if (quotedMessageAttachment != null && quotedMessageAttachment.getFileInfo().getImageInfo() != null) {
            replyMessage = getResources().getString(R.string.reply_message_with_image);
            replyThumbnail.setVisibility(View.VISIBLE);

            Message.FileInfo fileInfo = quotedMessageAttachment.getFileInfo();
            String imageUrl = fileInfo.getImageInfo().getThumbUrl();
            Glide.with(getContext())
                .load(imageUrl)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .into(replyThumbnail);
        } else {
            replyMessage = quotedMessage.getText();
            replyThumbnail.setVisibility(View.GONE);
        }

        textReplyMessage.setText(replyMessage);
    }

    public void editState(Message message) {
        editedMessage = message;
        quotedMessage = null;

        updateUI(State.EDITING);
        textEditingMessage.setText(message.getText());
        editTextMessage.setText(message.getText());
        editTextMessage.setSelection(message.getText().length());
    }

    public void sendingState() {
        editedMessage = null;
        quotedMessage = null;
        updateUI(State.SENDING);
    }

    private void updateUI(State state) {
        this.state = state;

        switch (state) {
            case SENDING:
                sendButton.setVisibility(View.VISIBLE);
                editButton.setVisibility(View.GONE);

                enterBar.setVisibility(VISIBLE);
                editBar.setVisibility(GONE);
                replyBar.setVisibility(GONE);
                break;
            case EDITING:
                sendButton.setVisibility(View.GONE);
                editButton.setVisibility(View.VISIBLE);
                editButton.setEnabled(true);
                editButton.setAlpha(1f);

                enterBar.setVisibility(VISIBLE);
                editBar.setVisibility(VISIBLE);
                replyBar.setVisibility(GONE);
                break;
            case REPLYING:
            default:
                sendButton.setVisibility(View.VISIBLE);
                editButton.setVisibility(View.GONE);

                enterBar.setVisibility(VISIBLE);
                editBar.setVisibility(GONE);
                replyBar.setVisibility(VISIBLE);
                break;
        }
    }

    public void setStream(MessageStream messageStream) {
        this.stream = messageStream;

        stream.setChatStateListener((oldState, newState) -> {
            switch (newState) {
                case CLOSED_BY_OPERATOR:
                case CLOSED_BY_VISITOR:
                    if (shouldRateOperator()) {
                        showRatingDialog();
                    }
                    break;
                case NONE:
                    if (ratingDialog.isShowing()) {
                        ratingDialog.dismiss();
                    }
                    break;
            }
        });
    }

    private boolean shouldRateOperator() {
        Operator operator = stream.getCurrentOperator();
        return operator != null && stream.getLastOperatorRating(operator.getId()) == 0;
    }

    public MessageStream getStream() {
        return stream;
    }

    private void initEditText(View rootView) {
        editTextMessage = rootView.findViewById(R.id.editTextChatMessage);
        editTextMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                String draft = editable.toString().trim();
                if (draft.isEmpty()) {
                    editButton.setAlpha(0.5f);
                    editButton.setEnabled(false);
                    sendButton.setAlpha(0.5f);
                    sendButton.setEnabled(false);
                    stream.setVisitorTyping(null);
                } else {
                    editButton.setAlpha(1f);
                    editButton.setEnabled(true);
                    sendButton.setAlpha(1f);
                    sendButton.setEnabled(true);
                    stream.setVisitorTyping(draft);
                }
            }
        });
        editTextMessage.setOnFocusChangeListener((v, hasFocus) -> {
//            if (hasFocus && (chatMenuLayout.getVisibility() == View.VISIBLE)) {
//                hideChatMenu();
//            }
        });
    }

    private void initSendButton(View rootView) {
        sendButton = rootView.findViewById(R.id.imageButtonSendMessage);
        sendButton.setOnClickListener(view -> {
            String message = editTextMessage.getText().toString();
            editTextMessage.getText().clear();
            message = message.trim();
            if (!message.isEmpty()) {
                if (BuildConfig.DEBUG && message.equals("##OPEN")) {
                    stream.startChat();
                } else {
                    if (state == State.SENDING) {
                        stream.sendMessage(message);
                    } else if (state == State.REPLYING) {
                        stream.replyMessage(message, quotedMessage);
                        sendingState();
                    }
                }
            }
        });
    }
}
