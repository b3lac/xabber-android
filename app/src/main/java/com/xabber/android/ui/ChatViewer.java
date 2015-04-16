/**
 * Copyright (c) 2013, Redsolution LTD. All rights reserved.
 *
 * This file is part of Xabber project; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License, Version 3.
 *
 * Xabber is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License,
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.xabber.android.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.ViewPager;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;

import com.xabber.android.data.ActivityManager;
import com.xabber.android.data.Application;
import com.xabber.android.data.account.OnAccountChangedListener;
import com.xabber.android.data.entity.BaseEntity;
import com.xabber.android.data.extension.archive.MessageArchiveManager;
import com.xabber.android.data.extension.attention.AttentionManager;
import com.xabber.android.data.intent.EntityIntentBuilder;
import com.xabber.android.data.message.AbstractChat;
import com.xabber.android.data.message.MessageManager;
import com.xabber.android.data.message.OnChatChangedListener;
import com.xabber.android.data.notification.NotificationManager;
import com.xabber.android.data.roster.OnContactChangedListener;
import com.xabber.android.ui.adapter.ChatScrollIndicatorAdapter;
import com.xabber.android.ui.adapter.ChatViewerAdapter;
import com.xabber.android.ui.helper.ManagedActivity;
import com.xabber.android.ui.helper.StatusBarPainter;
import com.xabber.androiddev.R;

import java.util.Collection;
import java.util.HashSet;

/**
 * Chat activity.
 * <p/>
 *
 * @author alexander.ivanov
 */
public class ChatViewer extends ManagedActivity implements OnChatChangedListener,
        OnContactChangedListener, OnAccountChangedListener, ViewPager.OnPageChangeListener,
        ChatViewerAdapter.FinishUpdateListener, RecentChatFragment.RecentChatFragmentInteractionListener,
        ChatViewerFragment.ChatViewerFragmentListener {

    /**
     * Attention request.
     */
    private static final String ACTION_ATTENTION = "com.xabber.android.data.ATTENTION";

    private static final String SAVED_ACCOUNT = "com.xabber.android.ui.ChatViewer.SAVED_ACCOUNT";
    private static final String SAVED_USER = "com.xabber.android.ui.ChatViewer.SAVED_USER";
    private static final String SAVED_EXIT_ON_SEND = "com.xabber.android.ui.ChatViewer.EXIT_ON_SEND";

    private boolean exitOnSend;

    private String extraText = null;

    ChatScrollIndicatorAdapter chatScrollIndicatorAdapter;
    ChatViewerAdapter chatViewerAdapter;

    ViewPager viewPager;

    Collection<ChatViewerFragment> registeredChats = new HashSet<>();
    Collection<RecentChatFragment> recentChatFragments = new HashSet<>();

    private String actionWithAccount = null;
    private String actionWithUser = null;

    private StatusBarPainter statusBarPainter;

    private boolean isChatSelected;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (isFinishing()) {
            return;
        }

        Intent intent = getIntent();
        String account = getAccount(intent);
        String user = getUser(intent);

        if (hasAttention(intent)) {
            AttentionManager.getInstance().removeAccountNotifications(account, user);
        }

        if (savedInstanceState != null) {
            if (account == null || user == null) {
                account = savedInstanceState.getString(SAVED_ACCOUNT);
                user = savedInstanceState.getString(SAVED_USER);
            }
            exitOnSend = savedInstanceState.getBoolean(SAVED_EXIT_ON_SEND);
        }

        setContentView(R.layout.activity_chat_viewer);

        statusBarPainter = new StatusBarPainter(this);

        if (account != null && user != null) {
            chatViewerAdapter = new ChatViewerAdapter(getFragmentManager(), account, user, this);
            isChatSelected = true;
        } else {
            chatViewerAdapter = new ChatViewerAdapter(getFragmentManager(), this);
            isChatSelected = false;
        }

        chatScrollIndicatorAdapter = new ChatScrollIndicatorAdapter(this,
                (LinearLayout)findViewById(R.id.chat_scroll_indicator));
        chatScrollIndicatorAdapter.update(chatViewerAdapter.getRealCount());

        viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.setAdapter(chatViewerAdapter);
        viewPager.setOnPageChangeListener(this);
        viewPager.getBackground().setAlpha(30);

        selectPage(account, user, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Application.getInstance().addUIListener(OnChatChangedListener.class, this);
        Application.getInstance().addUIListener(OnContactChangedListener.class, this);
        Application.getInstance().addUIListener(OnAccountChangedListener.class, this);

        Intent intent = getIntent();
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            extraText = intent.getStringExtra(Intent.EXTRA_TEXT);

            if (extraText != null) {
                intent.removeExtra(Intent.EXTRA_TEXT);
                exitOnSend = true;
            }
        }

        if (actionWithAccount != null && actionWithUser != null) {
            updateActionBar(actionWithAccount);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SAVED_ACCOUNT, actionWithAccount);
        outState.putString(SAVED_USER, actionWithUser);
        outState.putBoolean(SAVED_EXIT_ON_SEND, exitOnSend);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Application.getInstance().removeUIListener(OnChatChangedListener.class, this);
        Application.getInstance().removeUIListener(OnContactChangedListener.class, this);
        Application.getInstance().removeUIListener(OnAccountChangedListener.class, this);
        MessageManager.getInstance().removeVisibleChat();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (isFinishing())
            return;

        chatViewerAdapter.updateChats();
        chatScrollIndicatorAdapter.update(chatViewerAdapter.getRealCount());

        String account = getAccount(intent);
        String user = getUser(intent);
        if (account == null || user == null) {
            return;
        }

        selectPage(account, user, false);
    }

    private void selectPage(String account, String user, boolean smoothScroll) {
        int position = chatViewerAdapter.getPageNumber(account, user);
        selectPage(position, smoothScroll);
    }

    private void selectPage(int position, boolean smoothScroll) {
        onPageSelected(position);
        viewPager.setCurrentItem(position, smoothScroll);
    }

    private static String getAccount(Intent intent) {
        String value = EntityIntentBuilder.getAccount(intent);
        if (value != null)
            return value;
        // Backward compatibility.
        return intent.getStringExtra("com.xabber.android.data.account");
    }

    private static String getUser(Intent intent) {
        String value = EntityIntentBuilder.getUser(intent);
        if (value != null)
            return value;
        // Backward compatibility.
        return intent.getStringExtra("com.xabber.android.data.user");
    }

    private static boolean hasAttention(Intent intent) {
        return ACTION_ATTENTION.equals(intent.getAction());
    }

    public static Intent createIntent(Context context, String account, String user) {
        return new EntityIntentBuilder(context, ChatViewer.class).setAccount(account).setUser(user).build();
    }

    public static Intent createIntent(Context context) {
        return new EntityIntentBuilder(context, ChatViewer.class).build();
    }

    public static Intent createClearTopIntent(Context context, String account, String user) {
        Intent intent = createIntent(context, account, user);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }

    /**
     * Create intent to send message.
     * <p/>
     * Contact list will not be shown on when chat will be closed.
     *
     * @param context
     * @param account
     * @param user
     * @param text    if <code>null</code> then user will be able to send a number
     *                of messages. Else only one message can be send.
     * @return
     */
    public static Intent createSendIntent(Context context, String account, String user, String text) {
        Intent intent = ChatViewer.createIntent(context, account, user);
        intent.setAction(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        return intent;
    }

    public static Intent createAttentionRequestIntent(Context context, String account, String user) {
        Intent intent = ChatViewer.createClearTopIntent(context, account, user);
        intent.setAction(ACTION_ATTENTION);
        return intent;
    }

    @Override
    public void onChatChanged(final String account, final String user, final boolean incoming) {
        String currentAccount = null;
        String currentUser = null;
        AbstractChat chatByPageNumber = chatViewerAdapter.getChatByPageNumber(viewPager.getCurrentItem());

        if (chatByPageNumber != null) {
            currentAccount = chatByPageNumber.getAccount();
            currentUser = chatByPageNumber.getUser();
        }

        if (chatViewerAdapter.updateChats()) {
            selectPage(currentAccount, currentUser, false);
            chatScrollIndicatorAdapter.update(chatViewerAdapter.getRealCount());

        } else {
            updateRegisteredChats();
            updateRegisteredRecentChatsFragments();

            for (ChatViewerFragment chat : registeredChats) {
                if (chat.isEqual(account, user)) {
                    chat.playIncomingAnimation();
                }
            }
        }

        if (actionWithAccount != null && actionWithAccount.equals(account)
                && actionWithUser != null && actionWithUser.equals(user)) {
            updateActionBar(account);
        }
    }

    @Override
    public void onContactsChanged(Collection<BaseEntity> entities) {
        updateRegisteredChats();
        updateRegisteredRecentChatsFragments();

        if (actionWithAccount != null && actionWithUser != null) {
            updateActionBar(actionWithAccount);
        }
    }

    @Override
    public void onAccountsChanged(Collection<String> accounts) {
        updateRegisteredChats();
        updateRegisteredRecentChatsFragments();

        if (actionWithAccount != null && actionWithUser != null) {
            updateActionBar(actionWithAccount);
        }
    }

    void onSent() {
        if (exitOnSend) {
            close();
        }
    }

    void close() {
        finish();
        if (!Intent.ACTION_SEND.equals(getIntent().getAction())) {
            ActivityManager.getInstance().clearStack(false);
            if (!ActivityManager.getInstance().hasContactList(this)) {
                startActivity(ContactList.createIntent(this));
            }
        }
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

    }

    @Override
    public void onPageSelected(int position) {
        hideKeyboard(this);

        AbstractChat selectedChat = chatViewerAdapter.getChatByPageNumber(position);
        chatScrollIndicatorAdapter.select(chatViewerAdapter.getRealPagePosition(position));

        isChatSelected = selectedChat != null;

        if (isChatSelected) {
            actionWithAccount = selectedChat.getAccount();
            actionWithUser = selectedChat.getUser();
        } else {
            actionWithAccount = null;
            actionWithUser = null;
        }

        if (!isChatSelected) {
            statusBarPainter.restore();
            MessageManager.getInstance().removeVisibleChat();
            return;
        }

        updateActionBar(actionWithAccount);

        MessageManager.getInstance().setVisibleChat(actionWithAccount, actionWithUser);

        MessageArchiveManager.getInstance().requestHistory(
                actionWithAccount, actionWithUser, 0,
                MessageManager.getInstance().getChat(actionWithAccount, actionWithUser).getRequiredMessageCount());

        NotificationManager.getInstance().removeMessageNotification(actionWithAccount, actionWithUser);
    }

    private void updateActionBar(String account) {
        statusBarPainter.updateWithAccountName(account);
    }

    private void updateRegisteredChats() {
        for (ChatViewerFragment chat : registeredChats) {
            chat.updateChat();
        }
    }

    private void updateRegisteredRecentChatsFragments() {
        for (RecentChatFragment recentChatFragment : recentChatFragments) {
            recentChatFragment.updateChats(chatViewerAdapter.getActiveChats());
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }


    public void registerChat(ChatViewerFragment chat) {
        registeredChats.add(chat);
    }

    public void unregisterChat(ChatViewerFragment chat) {
        registeredChats.remove(chat);
    }

    public void registerRecentChatsList(RecentChatFragment recentChatFragment) {
        recentChatFragments.add(recentChatFragment);
    }

    public void unregisterRecentChatsList(RecentChatFragment recentChatFragment) {
        recentChatFragments.remove(recentChatFragment);
    }


    @Override
    public void onChatViewAdapterFinishUpdate() {
        insertExtraText();

        updateRegisteredChats();

        Fragment currentFragment = chatViewerAdapter.getCurrentFragment();

        if (isChatSelected) {
            if (!(currentFragment instanceof ChatViewerFragment)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Debug message")
                        .setMessage("Recent chats selected, but contact chat expected.")
                        .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        });
                builder.create().show();
            } else if (!((ChatViewerFragment) currentFragment).isEqual(actionWithAccount, actionWithUser)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Debug message")
                        .setMessage("Wrong contact chat selected.")
                        .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        });
                builder.create().show();
            }

        } else {
            if (!(currentFragment instanceof RecentChatFragment)) {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Debug message")
                        .setMessage("Contact chat selected, but recent chats expected.")
                        .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        });
                builder.create().show();
            }
        }
    }

    private void insertExtraText() {

        if (extraText == null) {
            return;
        }

        boolean isExtraTextInserted = false;

        for (ChatViewerFragment chat : registeredChats) {
            if (chat.isEqual(actionWithAccount, actionWithUser)) {
                chat.setInputText(extraText);
                isExtraTextInserted = true;
            }
        }

        if (isExtraTextInserted) {
            extraText = null;
        }
    }

    @Override
    public void onChatSelected(AbstractChat chat) {
        selectPage(chat.getAccount(), chat.getUser(), true);
    }
    public ChatViewerAdapter getChatViewerAdapter() {
        return chatViewerAdapter;
    }

    public static void hideKeyboard(Activity activity) {
        // Check if no view has focus:
        View view = activity.getCurrentFocus();
        if (view != null) {
            InputMethodManager inputManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }

    @Override
    public void onCloseChat() {
        close();
    }
}
