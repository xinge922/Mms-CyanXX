package com.android.mms.transaction;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;

import android.app.NotificationManager;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Contacts;

import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ScrollView;
import android.view.View;
import android.view.ViewStub;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;
//Geesun
import com.android.phone.location.PhoneLocation;

import com.android.mms.R;


public class SmsPopupActivity extends Activity {
	private TextView fromTV;
	private TextView cityTV;
	private TextView messageReceivedTV;
	private TextView messageTV;
	private ScrollView messageScrollView = null;

    private ImageView photoImageView = null;

	private Drawable contactPhotoPlaceholderDrawable = null;

    private ViewStub unreadCountViewStub;
    private View unreadCountView = null;

    private ViewStub buttonsViewStub;    
    private View buttonsView = null;

    public static final String SMS_MIME_TYPE = "vnd.android-dir/mms-sms";
    
    public static final Uri MMS_SMS_CONTENT_URI = Uri.parse("content://mms-sms/");
    
    public static final Uri SMS_CONTENT_URI = Uri.parse("content://sms");
    
    public static final Uri THREAD_ID_CONTENT_URI =
      Uri.withAppendedPath(MMS_SMS_CONTENT_URI, "threadID");

    private SmsPopupMessage message;
    
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);            
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

        setContentView(R.layout.popup);

        unreadCountViewStub = (ViewStub) findViewById(R.id.UnreadCountViewStub);

        fromTV = (TextView) findViewById(R.id.FromTextView);

        cityTV = (TextView) findViewById(R.id.city);

        photoImageView = (ImageView) findViewById(R.id.FromImageView);

        messageReceivedTV = (TextView) findViewById(R.id.HeaderTextView);            


        messageScrollView = (ScrollView) findViewById(R.id.MessageScrollView);
        messageScrollView.setVisibility(View.VISIBLE);

        messageTV = (TextView) findViewById(R.id.MessageTextView);            

        buttonsViewStub = (ViewStub) findViewById(R.id.ButtonsViewStub);
        if (buttonsView == null) {
            buttonsView = buttonsViewStub.inflate();
        }
        Button btnClose = (Button) buttonsView.findViewById(R.id.button1);
        Button btnDelete = (Button) buttonsView.findViewById(R.id.button2);
        Button btnReply = (Button) buttonsView.findViewById(R.id.button3);
        btnClose.setVisibility(View.VISIBLE);
        btnDelete.setVisibility(View.VISIBLE);
        btnReply.setVisibility(View.VISIBLE); 

        btnClose.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                appFinish();
            }
        });

        btnDelete.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                showDialog(1);
            }
        });

        btnReply.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                replyMsg();
            }
        });


        Bundle b = getIntent().getExtras();
        updateInformation(b);

    }    

    private void updateInformation(Bundle b)
    {
        message = new SmsPopupMessage(this,b);

        if(message.getUnreadCount() <= 1){
            if(unreadCountView != null){
                unreadCountView.setVisibility(View.GONE);
            }
        }else{
            if (unreadCountView == null) {
                unreadCountView = unreadCountViewStub.inflate();
            }
            unreadCountView.setVisibility(View.VISIBLE);
            TextView tv = (TextView) unreadCountView.findViewById(R.id.UnreadCountTextView);

            String textWaiting =
                getString(R.string.unread_text_waiting, message.getUnreadCount() );
            tv.setText(textWaiting);
            Button inboxButton = (Button) unreadCountView.findViewById(R.id.InboxButton);
            inboxButton.setOnClickListener(new OnClickListener() {
                public void onClick(View v) {
                    gotoInbox();
                }
            });
        } 

        String headerText =
            getString(R.string.new_text_at, message.getFormattedTimestamp().toString());
        messageReceivedTV.setText(headerText);

        fromTV.setText(message.getContactName());


        runOnUiThread(new Runnable() {
            public void run() {
                String strCity = PhoneLocation.getCityFromPhone(message.getAddress());
                if(strCity != null){
                    cityTV.setText(strCity);
                }
            }
        });

        contactPhotoPlaceholderDrawable = getResources().getDrawable(android.R.drawable.ic_dialog_info);

        runOnUiThread(new Runnable() {
            public void run() {
                Bitmap contactPhoto = getPersonPhoto(message.getContactId());
                if(contactPhoto != null)
            photoImageView.setImageBitmap(contactPhoto);
                else
            photoImageView.setImageDrawable(contactPhotoPlaceholderDrawable);
            }
        }); 

        messageTV.setText(message.getMessageBody());

    }

    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Bundle b = intent.getExtras();
        updateInformation(b);
      
    }

    public  Bitmap getPersonPhoto(String id) {
        if (id == null)
            return null;

        if ("0".equals(id))
            return null;

        return Contacts.People.loadContactPhoto(this,
                Uri.withAppendedPath(Contacts.People.CONTENT_URI, id),
                android.R.drawable.ic_dialog_info, null);
    }

    protected Dialog onCreateDialog(int id) {

        return new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(getString(R.string.pref_show_delete_button_dialog_title))
            .setMessage(getString(R.string.pref_show_delete_button_dialog_text))
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    deleteMessage();
                }
            })
        .setNegativeButton(android.R.string.cancel, null)
            .create();
    }

    private void deleteMessage()
    {
        Uri deleteUri;
        deleteUri = Uri.withAppendedPath(SMS_CONTENT_URI, String.valueOf(message.getMessageId()));
        getContentResolver().delete(deleteUri, null, null);
        appFinish();
    }

    private void replyMsg()
    {
        Intent i = getSmsToIntentFromThreadId(message.getThreadId());
        startActivity(i);
        appFinish();
    }
    private void gotoInbox(){
        Intent i = getSmsIntent();
        startActivity(i);
        appFinish();
    }

    public static Intent getSmsToIntentFromThreadId( long threadId) {
        Intent popup = new Intent(Intent.ACTION_VIEW);
        int flags =
            Intent.FLAG_ACTIVITY_NEW_TASK |
            Intent.FLAG_ACTIVITY_SINGLE_TOP |
            Intent.FLAG_ACTIVITY_CLEAR_TOP;
        popup.setFlags(flags);
        if (threadId > 0) {
            popup.setData(Uri.withAppendedPath(THREAD_ID_CONTENT_URI, String.valueOf(threadId)));
        } else {
            return getSmsIntent();
        }
        return popup;
    }

    public static Intent getSmsIntent() {
        Intent conversations = new Intent(Intent.ACTION_MAIN);
        conversations.setType(SMS_MIME_TYPE);
        int flags =
            Intent.FLAG_ACTIVITY_NEW_TASK |
            Intent.FLAG_ACTIVITY_SINGLE_TOP |
            Intent.FLAG_ACTIVITY_CLEAR_TOP;
        conversations.setFlags(flags);

        return conversations;
    }

    private void appFinish(){
        NotificationManager myNM =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        myNM.cancel(MessagingNotification.NOTIFICATION_ID);
        message.setThreadRead(this);
        finish();
    }
}

