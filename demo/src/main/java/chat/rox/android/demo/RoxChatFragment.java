package chat.rox.android.demo;

import static chat.rox.android.demo.RoxChatActivity.EXTRA_SHOW_RATING_BAR_ON_STARTUP;
import static chat.rox.android.sdk.Message.Type.FILE_FROM_OPERATOR;
import static chat.rox.android.sdk.Message.Type.FILE_FROM_VISITOR;
import static chat.rox.android.sdk.Message.Type.VISITOR;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chat.rox.android.demo.util.AnchorMenuDialog;
import chat.rox.android.demo.util.ContextMenuDialog;
import chat.rox.android.demo.util.DepartmentItemSelectedCallback;
import chat.rox.android.demo.util.EndlessScrollListener;
import chat.rox.android.demo.util.FileHelper;
import chat.rox.android.demo.util.MessageItemAnimator;
import chat.rox.android.demo.util.PayloadType;
import chat.rox.android.demo.util.SurveyDialog;
import chat.rox.android.sdk.Department;
import chat.rox.android.sdk.Message;
import chat.rox.android.sdk.MessageListener;
import chat.rox.android.sdk.MessageStream;
import chat.rox.android.sdk.MessageTracker;
import chat.rox.android.sdk.Operator;
import chat.rox.android.sdk.Survey;
import chat.rox.android.sdk.RoxError;
import chat.rox.android.sdk.RoxSession;

public class RoxChatFragment extends Fragment implements FileHelper.FileLoaderListener {
    private static final int FILE_SELECT_CODE = 0;

    private RoxSession session;
    private ListController listController;
    private Message inEdit = null;
    private Message quotedMessage = null;

    private EditText editTextMessage;
    private ImageButton sendButton;
    private ImageButton editButton;

    private LinearLayout editingLayout;
    private TextView textEditingMessage;
    private int editingMessagePosition;
    private TextView operatorNameView;

    private LinearLayout replyLayout;
    private TextView textSenderName;
    private TextView textReplyMessage;
    private int replyMessagePosition;
    private ImageView replyThumbnail;
    private TextView textReplyId;

    private ImageButton chatMenuButton;

    private RatingBar ratingBar;
    private Button ratingButton;

    private FileHelper fileHelper;

    private AnchorMenuDialog baseDialog = null;
    private AlertDialog ratingDialog;
    private SurveyDialog surveyDialog;
    private DepartmentDialog departmentDialog;
    private final List<Runnable> syncedWithServerCallbacks = new ArrayList<>();
    private final BroadcastReceiver fileDownloadedComplete = new BroadcastReceiver() {
        public void onReceive(Context ctxt, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            String messageId = downloadingFiles.get(id);
            if (messageId != null) {
                downloadingFiles.remove(id);
                listController.renderMessage(messageId);
            }
        }
    };
    private final Map<Long, String> downloadingFiles = new HashMap<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requireContext().registerReceiver(fileDownloadedComplete, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (this.session == null) {
            throw new IllegalStateException("this.session == null; Use setRoxSession before");
        }
        View rootView = inflater.inflate(R.layout.fragment_rox_chat, container, false);

        initSessionStreamListeners(rootView);
        initOperatorState(rootView);
        initChatView(rootView);
        initEditText(rootView);
        initSendButton(rootView);
        initEditButton(rootView);
        initEditingLayout(rootView);
        initDeleteEditing(rootView);
        initEditingMessageButton(rootView);
        initChatMenu(rootView);
        initReplyLayout(rootView);
        initDeleteReply(rootView);
        initReplyMessageButton(rootView);
        initOperatorRateDialog();
        initSurveyDialog();

        fileHelper = new FileHelper(this, getContext());
        checkNeedOpenRatingDialogOnStartup();

        ViewCompat.setElevation(rootView.findViewById(R.id.linLayEnterMessage), 2);
        return rootView;
    }

    public boolean isDialogShown() {
        return baseDialog != null && baseDialog.isShowing();
    }

    private void checkNeedOpenRatingDialogOnStartup() {
        Intent intent = requireActivity().getIntent();
        Bundle args = intent.getExtras();
        String type = intent.getType();
        String action = intent.getAction();

        if (args != null) {
            if (args.getBoolean(EXTRA_SHOW_RATING_BAR_ON_STARTUP, false)) {
                syncedWithServerCallbacks.add(this::showRateOperatorDialog);
            }
        }

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                handleSendText(intent);
            } else {
                handleSendFile(intent);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
            handleSendMultipleFiles(intent);
        }
    }

    private void handleSendText(Intent intent) {
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText != null) {
            syncedWithServerCallbacks.add(() -> session.getStream().sendMessage(sharedText));
        }
    }

    void handleSendFile(Intent intent) {
        Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (imageUri != null) {
            syncedWithServerCallbacks.add(() -> fileHelper.createTempFile(imageUri));
        }
    }

    void handleSendMultipleFiles(Intent intent) {
        ArrayList<Uri> imageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        if (imageUris != null) {
            for (Uri uri: imageUris) {
                syncedWithServerCallbacks.add(() -> fileHelper.createTempFile(uri));
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        session.resume();
        session.getStream().startChat();
        session.getStream().setChatRead();
    }

    @Override
    public void onStop() {
        session.pause();
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (fileHelper != null) {
            fileHelper.release();
        }
        session.destroy();
        requireContext().unregisterReceiver(fileDownloadedComplete);
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        if (getActivity() != null) {
            final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setSubtitle("");
            }
        }
        super.onDetach();
    }

    private void initSessionStreamListeners(final View rootView) {
        session.getStream().setCurrentOperatorChangeListener((oldOperator, newOperator) -> {
                if (isDialogShown()) {
                    baseDialog.enableItem(R.id.relLay_rate_operator, newOperator != null);
                }

            setOperatorAvatar(rootView, newOperator);
            String operatorName = newOperator == null ? getString(R.string.no_operator) : newOperator.getName();
            operatorNameView.setText(getString(R.string.operator_name, operatorName));
        });
        session.getStream().setVisitSessionStateListener((previousState, newState) -> {
            switch (newState) {
                case DEPARTMENT_SELECTION:
                    List<Department> departments = session.getStream().getDepartmentList();
                    if (departmentDialog == null) {
                        openDepartmentDialog(departments);
                    } else {
                        departmentDialog.dismiss();
                        departmentDialog.setDepartmentNames(getDepartmentsNames(departments));
                        departmentDialog.show(getChildFragmentManager(), DepartmentDialog.DEPARTMENT_DIALOG_TAG);
                    }
                    break;
                case IDLE_AFTER_CHAT:
                    if (departmentDialog != null) {
                        departmentDialog.dismiss();
                    }
                    break;
            }
        });
        session.getStream().setChatStateListener((oldState, newState) -> {
            switch (newState) {
                case CLOSED_BY_OPERATOR:
                case CLOSED_BY_VISITOR:
                    MessageStream stream = session.getStream();
                    Operator operator = stream.getCurrentOperator();
                    if (operator != null && stream.getLastOperatorRating(operator.getId()) == 0) {
                        showRateOperatorDialog();
                    }
                    hideEditLayout();
                    operatorNameView.setText(getString(R.string.operator_name, getString(R.string.no_operator)));
                    setOperatorAvatar(rootView, null);
                    break;
                case NONE:
                    hideEditLayout();
                    if (ratingDialog.isShowing()) {
                        ratingDialog.dismiss();
                    }
                    break;
            }
        });
        session.getStream().setSurveyListener(new MessageStream.SurveyListener() {
            @Override
            public void onSurvey(Survey survey) {
                if (ratingDialog.isShowing()) {
                    ratingDialog.dismiss();
                }
                if (!surveyDialog.isVisible()) {
                    surveyDialog.showNow(getChildFragmentManager(), "surveyDialog");
                }
            }

            @Override
            public void onNextQuestion(Survey.Question question) {
                surveyDialog.setCurrentQuestion(question);
            }

            @Override
            public void onSurveyCancelled() {
                if (surveyDialog.isVisible()) {
                    surveyDialog.dismiss();
                }
                showToast(getString(R.string.survey_finish_message), Toast.LENGTH_SHORT);
            }
        });
    }

    private void openDepartmentDialog(List<Department> departmentList) {
        if (departmentList != null) {
            departmentDialog =
                new DepartmentDialog(new DepartmentItemSelectedCallback() {
                    @Override
                    public void departmentItemSelected(int departmentPosition) {
                        List<Department> departmentList = session.getStream().getDepartmentList();
                        if (departmentList != null && !departmentList.isEmpty()) {
                            String selectedDepartment = departmentList.get(departmentPosition).getKey();
                            session.getStream().startChatWithDepartmentKey(selectedDepartment);
                        }
                    }

                    @Override
                    public void onBackPressed() {
                        requireActivity().onBackPressed();
                    }
                });
            departmentDialog.setDepartmentNames(getDepartmentsNames(departmentList));
            departmentDialog.setCancelable(false);
            departmentDialog.show(getChildFragmentManager(), DepartmentDialog.DEPARTMENT_DIALOG_TAG);
        }
    }

    private List<String> getDepartmentsNames(List<Department> departmentList) {
        final List<String> departmentNames = new ArrayList<>();
        for (Department department : departmentList) {
            departmentNames.add(department.getName());
        }
        return departmentNames;
    }

    private void initOperatorState(final View rootView) {
        if (getActivity() != null) {
            ((AppCompatActivity) getActivity()).setSupportActionBar((Toolbar) rootView.findViewById(R.id.toolbar));
            final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setDisplayShowHomeEnabled(true);
            }
        }

        final TextView typingView = rootView.findViewById(R.id.action_bar_subtitle);
        Operator currentOperator = session.getStream().getCurrentOperator();
        String operatorName = currentOperator == null
                ? getString(R.string.no_operator)
                : currentOperator.getName();
        typingView.setText(getString(R.string.operator_name, operatorName));

        session.getStream().setOperatorTypingListener(isTyping -> {
            ImageView imageView = rootView.findViewById(R.id.image_typing);
            imageView.setBackgroundResource(R.drawable.typing_animation);
            AnimationDrawable animationDrawable = (AnimationDrawable) imageView.getBackground();
            Operator operator = session.getStream().getCurrentOperator();
            String operatorName1 = operator == null
                    ? getString(R.string.no_operator)
                    : operator.getName();
            if (isTyping) {
                typingView.setText(getString(R.string.operator_typing));
                typingView.setTextColor(
                        rootView.getResources().getColor(R.color.colorTexWhenTyping));
                imageView.setVisibility(View.VISIBLE);
                animationDrawable.start();
            } else {
                typingView.setText(getString(R.string.operator_name, operatorName1));
                typingView.setTextColor(
                        rootView.getResources().getColor(R.color.white));
                imageView.setVisibility(View.GONE);
                animationDrawable.stop();
            }
        });

        setOperatorAvatar(rootView, session.getStream().getCurrentOperator());
    }

    public void setRoxSession(RoxSession session) {
        if (this.session != null) {
            throw new IllegalStateException("Rox session is already set");
        }
        this.session = session;
    }

    private void setOperatorAvatar(View view, @Nullable Operator operator) {
        if (operator != null) {
            if (operator.getAvatarUrl() != null) {
                Glide.with(getContext())
                        .load(operator.getAvatarUrl())
                        .into((ImageView) view.findViewById(R.id.sender_photo));
            } else {
                if (getContext() != null) {
                    ((ImageView) view.findViewById(R.id.sender_photo)).setImageDrawable(
                            getContext().getResources()
                                    .getDrawable(R.drawable.default_operator_avatar));
                }
            }
            view.findViewById(R.id.sender_photo).setVisibility(View.VISIBLE);
        } else {
            view.findViewById(R.id.sender_photo).setVisibility(View.GONE);
        }
    }

    private void initChatView(View rootView) {
        operatorNameView = rootView.findViewById(R.id.action_bar_subtitle);
        ProgressBar progressBar = rootView.findViewById(R.id.loading_spinner);
        progressBar.setVisibility(View.GONE);

        RecyclerView recyclerView = rootView.findViewById(R.id.chat_recycler_view);
        recyclerView.setVisibility(View.VISIBLE);

        final View syncLayout = rootView.findViewById(R.id.constLaySyncMessage);
        syncedWithServerCallbacks.add(() -> hideSyncLayout(syncLayout));

        listController = new ListController(recyclerView, progressBar, rootView);
    }

    private void initEditText(View rootView) {
        editTextMessage = rootView.findViewById(R.id.editTextChatMessage);
        editTextMessage.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                if (isDialogShown()) {
                    hideChatMenu();
                }
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
                    session.getStream().setVisitorTyping(null);
                } else {
                    editButton.setAlpha(1f);
                    editButton.setEnabled(true);
                    sendButton.setAlpha(1f);
                    sendButton.setEnabled(true);
                    session.getStream().setVisitorTyping(draft);
                }
            }
        });
        editTextMessage.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && isDialogShown()) {
                hideChatMenu();
            }
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
                    session.getStream().startChat();
                } else {
                    if (replyLayout.getVisibility() == View.GONE) {
                        session.getStream().sendMessage(message);
                    } else {
                        replyLayout.setVisibility(View.GONE);
                        session.getStream().replyMessage(
                                message,
                                quotedMessage);
                    }
                }
                if (isDialogShown()) {
                    hideChatMenu();
                }
            }
        });
    }

    private void initEditingLayout(View rootView) {
        editingLayout = rootView.findViewById(R.id.linLayEditMessage);
        textEditingMessage = rootView.findViewById(R.id.textViewEditText);
        LinearLayout editTextLayout = rootView.findViewById(R.id.linLayEditBody);
        editTextLayout.setOnClickListener(view -> listController.showMessage(editingMessagePosition));
        editingLayout.setVisibility(View.GONE);
    }

    private void initDeleteEditing(View rootView) {
        ImageView deleteEditButton = rootView.findViewById(R.id.imageButtonEditDelete);
        deleteEditButton.setOnClickListener(view -> {
            hideEditLayout();
            editTextMessage.getText().clear();
        });
    }

    private void initEditButton(View rootView) {
        editButton = rootView.findViewById(R.id.imageButtonAcceptChanges);
        editButton.setOnClickListener(view -> {
            if (inEdit == null) {
                return;
            }

            String newText = editTextMessage.getText().toString();
            editTextMessage.getText().clear();
            newText = newText.trim();
            if (newText.isEmpty()) {
                showToast(getString(R.string.failed_send_empty_message), Toast.LENGTH_SHORT);
            } else if (!newText.equals(inEdit.getText().trim())) {
                session.getStream().editMessage(inEdit, newText, null);
            }
            hideEditLayout();
        });
    }

    private void initEditingMessageButton(final View rootView) {
        ImageView editButton = rootView.findViewById(R.id.imageButtonEditMessage);
        editButton.setOnClickListener(view -> listController.showMessage(editingMessagePosition));
    }

    private void initChatMenu(final View rootView) {
        chatMenuButton = rootView.findViewById(R.id.imageButtonChatMenu);

        chatMenuButton.setOnClickListener(view -> {
            if (!isDialogShown()) {
                AnchorMenuDialog chatMenuDialog = new AnchorMenuDialog(requireContext());
                baseDialog = chatMenuDialog;
                chatMenuDialog.setOnMenuItemClickListener(itemId -> {
                    final int rateOperator = R.id.relLay_rate_operator;
                    final int newAttachment = R.id.relLay_new_attachment;

                    switch (itemId) {
                        case newAttachment:
                            openFilePicker();
                            break;
                        case rateOperator:
                            showRateOperatorDialog();
                            break;
                    }
                    hideChatMenu();
                });
                Animation animationRotateShow = AnimationUtils.loadAnimation(getContext(), R.anim.rotate_show);
                Animation animationRotateHide = AnimationUtils.loadAnimation(getContext(), R.anim.rotate_hide);
                chatMenuDialog.setOnShowListener(dialog -> {
                    chatMenuDialog.enableItem(R.id.relLay_rate_operator, session.getStream().getCurrentOperator() != null);
                    chatMenuButton.startAnimation(animationRotateShow);
                });
                chatMenuDialog.setOnDismissListener(dialog -> chatMenuButton.startAnimation(animationRotateHide));
                chatMenuDialog.setMenu(R.layout.dialog_chat_menu, R.id.menuItems);
                chatMenuDialog.setGravity(Gravity.CENTER_HORIZONTAL);
                chatMenuDialog.show(chatMenuButton);
            }
        });
    }

    private void openFilePicker() {
        hideChatMenu();
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            if (getContext() != null) {
                startActivityForResult(
                    Intent.createChooser(intent, getContext().getString(R.string.file_chooser_title)), FILE_SELECT_CODE);
            }
        } catch (android.content.ActivityNotFoundException e) {
            if (getContext() != null) {
                showToast(getContext().getString(R.string.file_chooser_not_found), Toast.LENGTH_SHORT);
            }
        }
    }

    private void showRateOperatorDialog() {
        Operator operator = session.getStream().getCurrentOperator();
        if (operator != null) {
            final Operator.Id operatorId = operator.getId();
            int rating = session.getStream().getLastOperatorRating(operatorId);
            ratingBar.setOnRatingBarChangeListener((ratingBar, rating1, fromUser) -> ratingButton.setEnabled(rating1 != 0));
            ratingBar.setRating(rating);
            ratingButton.setEnabled(rating != 0);
            ratingDialog.show();
            ratingButton.setOnClickListener(v -> {
                if (ratingBar.getRating() != 0) {
                    ratingDialog.dismiss();
                    showToast(getString(R.string.rate_operator_rating_sent), Toast.LENGTH_LONG);
                    session.getStream().rateOperator(operatorId, (int) ratingBar.getRating(), null);
                } else {
                    showToast(getString(R.string.rate_operator_rating_empty), Toast.LENGTH_LONG);
                }
            });
        }
    }

    private void showToast(String messageToast, int lengthToast) {
        Toast.makeText(getContext(), messageToast, lengthToast).show();
    }

    public void hideChatMenu() {
        if (isDialogShown()) {
            baseDialog.dismiss();
            Animation animationRotateHide = AnimationUtils.loadAnimation(getContext(), R.anim.rotate_hide);
            chatMenuButton.startAnimation(animationRotateHide);
        }
    }

    private void initReplyLayout(View rootView) {
        replyLayout = rootView.findViewById(R.id.linLayReplyMessage);
        textSenderName = rootView.findViewById(R.id.textViewSenderName);
        textReplyMessage = rootView.findViewById(R.id.textViewReplyText);
        replyThumbnail = rootView.findViewById(R.id.replyThumbnail);
        textReplyId = rootView.findViewById(R.id.quote_Id);

        ViewGroup replyTextLayout  = rootView.findViewById(R.id.linLayReplyBody);
        replyTextLayout.setOnClickListener(view -> listController.showMessage(replyMessagePosition));
        replyLayout.setVisibility(View.GONE);
    }

    private  void initDeleteReply(View rootView) {
        ImageView deleteReplyButton = rootView.findViewById(R.id.imageButtonReplyDelete);
        deleteReplyButton.setOnClickListener(view -> replyLayout.setVisibility(View.GONE));
    }

    private void initReplyMessageButton(View rootView) {
        ImageView replyButton = rootView.findViewById(R.id.imageButtonReplyMessage);
        replyButton.setOnClickListener(view -> listController.showMessage(replyMessagePosition));
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

    private void initSurveyDialog() {
        surveyDialog = new SurveyDialog();
        surveyDialog.setAnswerListener(answer -> session.getStream().sendSurveyAnswer(answer, null));
        surveyDialog.setCancelListener(() -> session.getStream().closeSurvey(null));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_SELECT_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Uri uri = data.getData();
                if (uri != null && getActivity() != null) {
                    fileHelper.loadFileDescriptor(uri);
                }
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
        if (getActivity() != null) {
            getActivity().overridePendingTransition(R.anim.pull_in_left, R.anim.push_out_right);
        }
    }

    @Override
    public void onFileLoaded(@NonNull ParcelFileDescriptor descriptor, @NonNull String filename, @NonNull String mimeType) {
        session.getStream().sendFile(descriptor.getFileDescriptor(), filename, mimeType, new MessageStream.SendFileCallback() {
            @Override
            public void onProgress(@NonNull Message.Id id, long sentBytes) {
            }

            @Override
            public void onSuccess(@NonNull Message.Id id) {
                try {
                    descriptor.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(@NonNull Message.Id id, @NonNull RoxError<SendFileError> error) {
                try {
                    descriptor.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                handleFileError(error);
            }
        });
    }

    @Override
    public void onFileLoaded(@NonNull File tempFile, @NonNull String filename, @NonNull String mimeType) {
        session.getStream().sendFile(
            tempFile,
            filename,
            mimeType,
            new MessageStream.SendFileCallback() {
                @Override
                public void onProgress(@NonNull Message.Id id, long sentBytes) {
                }

                @Override
                public void onSuccess(@NonNull Message.Id id) {
                    deleteFile(tempFile);
                }

                @Override
                public void onFailure(@NonNull Message.Id id, @NonNull RoxError<SendFileError> error) {
                    deleteFile(tempFile);
                    handleFileError(error);
                }
            });
    }

    private void handleFileError(@NonNull RoxError<MessageStream.SendFileCallback.SendFileError> error) {
        if (getContext() != null) {
            String message;
            switch (error.getErrorType()) {
                case FILE_TYPE_NOT_ALLOWED:
                    message = getContext().getString(
                        R.string.file_upload_failed_type);
                    break;
                case FILE_SIZE_EXCEEDED:
                    message = getContext().getString(
                        R.string.file_upload_failed_size);
                    break;
                case FILE_NAME_INCORRECT:
                    message = getContext().getString(
                        R.string.file_upload_failed_name);
                    break;
                case UNAUTHORIZED:
                    message = getContext().getString(
                        R.string.file_upload_failed_unauthorized);
                    break;
                case FILE_IS_EMPTY:
                    message = getContext().getString(
                        R.string.file_upload_failed_empty);
                    break;
                case UPLOADED_FILE_NOT_FOUND:
                default:
                    message = getContext().getString(R.string.file_upload_failed_unknown);
            }
            showToast(message, Toast.LENGTH_SHORT);
        }
    }

    @Override
    public void onError() {
        showToast(getString(R.string.file_upload_failed_unknown), Toast.LENGTH_SHORT);
    }

    private void deleteFile(File file) {
        if (!file.delete()) {
            Log.w(getClass().getSimpleName(), "failed to deleted file " + file.getName());
        }
    }

    private void hideSyncLayout(final View syncLayout) {
        long animationDuration = 500;
        int syncLayoutEndHeight = 0;
        ValueAnimator hideAnimation = ValueAnimator.ofInt(syncLayoutEndHeight, syncLayout.getMeasuredHeight());
        hideAnimation.addUpdateListener(valueAnimator -> syncLayout.setTranslationY((Integer) valueAnimator.getAnimatedValue()));
        hideAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                syncLayout.setVisibility(View.GONE);
            }
        });
        hideAnimation.setDuration(animationDuration);
        hideAnimation.start();
    }

    public void onEditMessageAction(Message message, int position) {
        inEdit = message;
        quotedMessage = null;

        editingMessagePosition = position;
        replyLayout.setVisibility(View.GONE);
        sendButton.setVisibility(View.GONE);
        editingLayout.setVisibility(View.VISIBLE);
        editButton.setVisibility(View.VISIBLE);
        editButton.setEnabled(true);
        editButton.setAlpha(1f);
        textEditingMessage.setText(message.getText());
        editTextMessage.setText(message.getText());
        editTextMessage.setSelection(message.getText().length());
    }

    public void onDeleteMessageAction(Message message) {
        session.getStream().deleteMessage(message, null);
        clearEditableMessage(message);
    }

    public void onReplyMessageAction(Message message, int position) {
        quotedMessage = message;
        if (inEdit != null) {
            editTextMessage.getText().clear();
            inEdit = null;
        }

        hideEditLayout();
        replyLayout.setVisibility(View.VISIBLE);
        replyMessagePosition = position;
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
                .diskCacheStrategy(DiskCacheStrategy.SOURCE)
                .into(replyThumbnail);
        } else {
            replyMessage = quotedMessage.getText();
            replyThumbnail.setVisibility(View.GONE);
        }

        textReplyMessage.setText(replyMessage);
    }

    public void onKeyBoardButtonClicked(String currentChatId, String buttonId) {
        session.getStream().sendKeyboardRequest(currentChatId, buttonId, null);
    }

    public void onOpenFile(File file, Message.FileInfo fileInfo) {
        Context context = requireContext();

        String packageName = context.getApplicationContext().getPackageName();
        Uri uri = FileProvider.getUriForFile(context, packageName + ".provider", file);
        String mime = fileInfo.getContentType();

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mime);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.cannot_open_file, Toast.LENGTH_SHORT).show();
        }
    }

    public void onDownloadFile(Message.FileInfo file, String messageId, Uri uri) {
        Context context = requireContext();
        String filename = file.getFileName();
        showToast(getString(R.string.saving_file, filename), Toast.LENGTH_SHORT);

        DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            DownloadManager.Request downloadRequest = new DownloadManager.Request(Uri.parse(file.getUrl()));
            downloadRequest.setTitle(filename);
            downloadRequest.allowScanningByMediaScanner();
            downloadRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            downloadRequest.setDestinationUri(uri);

            long request = manager.enqueue(downloadRequest);
            downloadingFiles.put(request, messageId);
        } else {
            showToast(getString(R.string.saving_failed), Toast.LENGTH_SHORT);
        }
    }

    void clearEditableMessage(Message message) {
        if (editButton.getVisibility() == View.VISIBLE && inEdit.getServerSideId().equals(message.getServerSideId())) {
            editTextMessage.getText().clear();
            hideEditLayout();
        }
    }

    private void hideEditLayout() {
        editButton.setVisibility(View.GONE);
        editingLayout.setVisibility(View.GONE);
        sendButton.setVisibility(View.VISIBLE);
    }

    private void scrollToPosition(int position) {
        listController.recyclerView.scrollToPosition(position);
    }

    private void hideKeyboard() {
        Context context = getContext();
        if (context != null) {
            InputMethodManager inputMethodManager = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (inputMethodManager != null) {
                inputMethodManager.hideSoftInputFromWindow(editTextMessage.getWindowToken(), 0);
            }
        }
    }

    void updateSentMessageDialog(Message message, int adapterPosition) {
        if (baseDialog == null || !(baseDialog instanceof ContextMenuDialog)) return;

        ContextMenuDialog contextMenuDialog = (ContextMenuDialog) baseDialog;
        contextMenuDialog.hideItems();
        boolean isFile = message.getType() == FILE_FROM_VISITOR;
        contextMenuDialog.showItem(R.id.relLayoutReply, message.canBeReplied());
        contextMenuDialog.showItem(R.id.relLayoutCopy, !isFile);
        contextMenuDialog.showItem(R.id.relLayoutEdit, message.canBeEdited() && !isFile);
        contextMenuDialog.showItem(R.id.relLayoutDelete, message.canBeEdited());
        contextMenuDialog.showItem(R.id.relLayoutDownload, false);
    }

    void updateReceivedMessageDialog(Message message, int adapterPosition) {
        if (baseDialog == null || !(baseDialog instanceof ContextMenuDialog)) return;

        ContextMenuDialog contextMenuDialog = (ContextMenuDialog) baseDialog;
        contextMenuDialog.hideItems();
        boolean isFile = message.getType() == FILE_FROM_OPERATOR;
        contextMenuDialog.showItem(R.id.relLayoutReply, message.canBeReplied());
        contextMenuDialog.showItem(R.id.relLayoutCopy, !isFile);
        contextMenuDialog.showItem(R.id.relLayoutEdit, false);
        contextMenuDialog.showItem(R.id.relLayoutDelete, false);
        contextMenuDialog.showItem(R.id.relLayoutDownload, isFile);
    }

    @SuppressLint("NonConstantResourceId")
    void openContextDialog(final int adapterPosition, Message message, View visibleView) {
        scrollToPosition(adapterPosition);

        ContextMenuDialog dynamicDialog = new ContextMenuDialog(getActivity());
        AnchorMenuDialog.OnMenuItemClickListener listener = itemId -> {
            switch (itemId) {
                case R.id.relLayoutReply:
                    onReplyMessageAction(message, adapterPosition);
                    break;
                case R.id.relLayoutEdit:
                    onEditMessageAction(message, adapterPosition);
                    break;
                case R.id.relLayoutDelete:
                    onDeleteMessageAction(message);
                    break;
                case R.id.relLayoutCopy:
                    ClipData clip = ClipData.newPlainText("message", message.getText());
                    ClipboardManager clipboard = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        showToast(getResources().getString(R.string.copied_message), Toast.LENGTH_LONG);
                    } else {
                        showToast(getResources().getString(R.string.copy_failed), Toast.LENGTH_LONG);
                    }
                    break;
                case R.id.relLayoutDownload:
                    Message.Attachment attachment = message.getAttachment();
                    if (attachment == null) break;
                    Message.FileInfo fileInfo = attachment.getFileInfo();
                    File file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = Uri.withAppendedPath(Uri.fromFile(file), fileInfo.getFileName());
                    onDownloadFile(fileInfo, message.getServerSideId(), uri);
                    break;
            }
            dynamicDialog.dismiss();
        };
        dynamicDialog.setMenu(R.layout.dialog_message_menu, R.id.context_menu_list);
        dynamicDialog.setOnMenuItemClickListener(listener);
        dynamicDialog.show(visibleView);

        baseDialog = dynamicDialog;
    }

    void onLinkClicked(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
    }

    void closeContextDialog(String messageId) {
//        if (menuController.closeContextDialog(messageId)) {
//            showToast(getString(R.string.message_deleted_toast), Toast.LENGTH_SHORT);
//        }
    }

    public void onQuoteClicked(Message.Quote quote) {
        listController.scrollToMessage(quote.getMessageId());
    }

    private class ListController implements MessageListener {
        private static final int MESSAGES_PER_REQUEST = 25;
        private final MessageTracker tracker;
        private final RecyclerView recyclerView;
        private final ProgressBar progressBar;
        private final FloatingActionButton downButton;
        private final MessagesAdapter adapter;
        private final LinearLayoutManager layoutManager;
        private final EndlessScrollListener scrollListener;
        private boolean requestingMessages;

        private ListController(
            final RecyclerView recyclerView,
            final ProgressBar progressBar,
            final View view) {

            this.recyclerView = recyclerView;
            this.progressBar = progressBar;
            this.layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, true);
            this.layoutManager.setStackFromEnd(false);
            this.recyclerView.setLayoutManager(layoutManager);
            this.recyclerView.setItemAnimator(new MessageItemAnimator());
            this.adapter = new MessagesAdapter(RoxChatFragment.this);

            this.recyclerView.setAdapter(this.adapter);
            this.tracker = session.getStream().newMessageTracker(this);

            downButton = view.findViewById(R.id.downButton);
            downButton.setOnClickListener(view1 -> {
                downButton.setVisibility(View.GONE);
                recyclerView.smoothScrollToPosition(0);
            });
            downButton.bringToFront();

            scrollListener = new EndlessScrollListener(10) {
                @Override
                public void onLoadMore(int totalItemsCount) {
                    requestMore();
                }

                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    if (dy < 0) {
                        if (downButton.isShown()) {
                            downButton.setVisibility(View.GONE);
                        }
                    } else if (isLastVisible()) {
                        readChat();
                    }
                }
            };

            MessageTracker.MessagesSyncedListener syncedListener = () -> {
                for (Runnable r : syncedWithServerCallbacks) {
                    r.run();
                }
            };
            tracker.setMessagesSyncedListener(syncedListener);

            scrollListener.setLoading(true);
            scrollListener.setDownButton(downButton);
            scrollListener.setAdapter(adapter);
            recyclerView.addOnScrollListener(scrollListener);
            requestMore(true);
        }

        private void requestMore() {
            requestMore(false);
        }

        private void requestMore(final boolean firstCall) {
            requestingMessages = true;
            progressBar.setVisibility(View.VISIBLE);
            if (firstCall) {
                recyclerView.setVisibility(View.GONE);
            }
            tracker.getNextMessages(MESSAGES_PER_REQUEST, received -> {
                requestingMessages = false;
                progressBar.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);

                if (received.size() != 0) {
                    adapter.addAll(0, received);
                    adapter.notifyItemRangeInserted(adapter.getItemCount() - 1, received.size());

                    if (firstCall) {
                        recyclerView.postDelayed(() -> {
                            recyclerView.smoothScrollToPosition(0);
                            int itemCount = layoutManager.getItemCount();
                            int lastItemVisible = layoutManager.findLastVisibleItemPosition() + 1;
                            if (itemCount == lastItemVisible) {
                                requestMore();
                            }
                        }, 100);
                    }
                    scrollListener.setLoading(false);
                }
            });
        }

        public void scrollToMessage(String messageId) {
            int index = adapter.indexOf(messageId);
            if (index != -1) {
                int position = adapter.getItemCount() - index - 1;
                layoutManager.scrollToPosition(position);
                recyclerView.post(() -> {
                    adapter.notifyItemChanged(position, PayloadType.SELECT_MESSAGE);
                });
            }
        }

        @Override
        public void messageAdded(@Nullable Message before, @NonNull Message message) {
            int ind = (before == null) ? -1 : adapter.indexOf(before);
            if (ind < 0) {
                boolean fromVisitor = message.getType() == VISITOR || message.getType() == FILE_FROM_VISITOR;
                adapter.add(message);
                adapter.notifyItemInserted(0);

                if ((fromVisitor || isLastVisible()) && !isDialogShown()) {
                    recyclerView.stopScroll();
                    recyclerView.smoothScrollToPosition(0);
                    readChat();
                } else {
                    downButton.setVisibility(View.VISIBLE);
                }
            } else {
                adapter.add(ind, message);
                adapter.notifyItemInserted(adapter.getItemCount() - ind - 1);
            }
        }

        @Override
        public void messageRemoved(@NonNull Message message) {
            int pos = adapter.indexOf(message);
            if (pos != -1) {
                adapter.remove(pos, message.getServerSideId());
                adapter.notifyItemRemoved(adapter.getItemCount() - pos);
            }
        }

        @Override
        public void messageChanged(@NonNull Message from, @NonNull Message to) {
            int ind = adapter.lastIndexOf(from);
            if (ind != -1) {
                adapter.set(ind, to);
                int position = adapter.getItemCount() - ind - 1;
                adapter.notifyItemChanged(position, new Object());
            }
        }

        @Override
        public void allMessagesRemoved() {
            int size = adapter.getItemCount();
            adapter.clear();
            adapter.notifyItemRangeRemoved(0, size);
            if (!requestingMessages) {
                requestMore();
            }
        }

        public void renderMessage(String messageId) {
            adapter.invalidateMessage(messageId);
        }

        boolean isLastVisible() {
            int position = layoutManager.findFirstVisibleItemPosition();
            return position == 0 || position == RecyclerView.NO_POSITION;
        }

        private void readChat() {
            session.getStream().setChatRead();
        }

        private void showMessage(int position) {
            recyclerView.smoothScrollToPosition(position);
        }
    }
}