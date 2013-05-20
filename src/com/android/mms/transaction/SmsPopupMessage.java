package com.android.mms.transaction;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Contacts;
import android.provider.Contacts.PeopleColumns;
import android.telephony.PhoneNumberUtils;
import android.text.format.DateUtils;
import com.android.mms.data.Contact;




public class SmsPopupMessage {
	  private static final String PREFIX = "com.android.mms.";
	  private static final String EXTRAS_FROM_ADDRESS = PREFIX + "EXTRAS_FROM_ADDRESS";
	  private static final String EXTRAS_MESSAGE_BODY = PREFIX + "EXTRAS_MESSAGE_BODY";
	  private static final String EXTRAS_TIMESTAMP    = PREFIX + "EXTRAS_TIMESTAMP";
	  private static final String EXTRAS_UNREAD_COUNT = PREFIX + "EXTRAS_UNREAD_COUNT";
	  private static final String EXTRAS_THREAD_ID    = PREFIX + "EXTRAS_THREAD_ID";
	  private static final String EXTRAS_CONTACT_ID   = PREFIX + "EXTRAS_CONTACT_ID";
	  private static final String EXTRAS_CONTACT_NAME = PREFIX + "EXTRAS_CONTACT_NAME";
	  private static final String EXTRAS_MESSAGE_TYPE = PREFIX + "EXTRAS_MESSAGE_TYPE";
	  private static final String EXTRAS_MESSAGE_ID   = PREFIX + "EXTRAS_MESSAGE_ID";
	

      public static final Uri MMS_SMS_CONTENT_URI = Uri.parse("content://mms-sms/");
      public static final Uri THREAD_ID_CONTENT_URI =
          Uri.withAppendedPath(MMS_SMS_CONTENT_URI, "threadID");
      public static final Uri CONVERSATION_CONTENT_URI =
          Uri.withAppendedPath(MMS_SMS_CONTENT_URI, "conversations");

      // Message types
      public static final int MESSAGE_TYPE_SMS = 0;
      public static final int MESSAGE_TYPE_MMS = 1;

      public static final int READ_THREAD = 1;

      // Main message object private vars
      private Context context;
      private String fromAddress = null;
      private String messageBody = null;
      private long timestamp = 0;
      private int unreadCount = 2;
      private long threadId = 0;
      private String contactId = null;
      private String contactName = null;

      private long messageId = 0;

      public SmsPopupMessage(Context _context, String _fromAddress, String _messageBody,
              long threadId,long _timestamp, int count) {
          context = _context;
          this.fromAddress = _fromAddress;
          this.messageBody = _messageBody;
          this.timestamp = _timestamp;
          this.threadId = threadId;		   

          contactId = getPersonIdFromPhoneNumber(fromAddress);
          contactName =  Contact.get(fromAddress, false).getName();
          messageId = findMessageId(threadId);
          unreadCount = count;
          if (contactName == null) {
              contactName = context.getString(android.R.string.unknownName);
          }
      }

      public long findMessageId(long threadId) {
          long id = 0;		    
          if (threadId > 0) {
              Cursor cursor = context.getContentResolver().query(
                      ContentUris.withAppendedId(CONVERSATION_CONTENT_URI, threadId),
                      new String[] { "_id", "date", "thread_id" },
                      null,
                      null, "date desc");

              if (cursor != null) {
                  try {
                      if (cursor.moveToFirst()) {
                          id = cursor.getLong(0);
                          //Log.v("Timestamp = " + cursor.getLong(1));
                      }
                  } finally {
                      cursor.close();
                  }
              }
          }
          return id;
      }




      public  void setThreadRead(Context context) {

          if (threadId > 0) {
              ContentValues values = new ContentValues(1);
              values.put("read", READ_THREAD);

              ContentResolver cr = context.getContentResolver();
              int result = 0;
              try {
                  result = cr.update(
                          ContentUris.withAppendedId(CONVERSATION_CONTENT_URI, threadId),
                          values, null, null);
              } catch (Exception e) {

              }		      
          }
      }

      public  String getPersonName(String id, String address) {

          // Check for id, if null return the formatting phone number as the name
          if (id == null) {
              if (address != null) {
                  return PhoneNumberUtils.formatNumber(address);
              } else {
                  return null;
              }
          }

          Cursor cursor = context.getContentResolver().query(
                  Uri.withAppendedPath(Contacts.People.CONTENT_URI, id),
                  new String[] { PeopleColumns.DISPLAY_NAME }, null, null, null);
          if (cursor != null) {
              try {
                  if (cursor.getCount() > 0) {
                      cursor.moveToFirst();
                      String name = cursor.getString(0);

                      return name;
                  }
              } finally {
                  cursor.close();
              }
          }

          if (address != null) {
              return PhoneNumberUtils.formatNumber(address);
          }
          return null;
      }

      public  String getPersonIdFromPhoneNumber(String address) {
          if (address == null)
              return null;

          Cursor cursor = context.getContentResolver().query(
                  Uri.withAppendedPath(Contacts.Phones.CONTENT_FILTER_URL, address),
                  new String[] { Contacts.Phones.PERSON_ID }, null, null, null);

          if (cursor != null) {
              try {
                  if (cursor.getCount() > 0) {
                      cursor.moveToFirst();
                      Long id = Long.valueOf(cursor.getLong(0));

                      return (String.valueOf(id));
                  }
              } finally {
                  cursor.close();
              }
          }
          return null;
      }

      public SmsPopupMessage(Context _context, Bundle b) {
          context = _context;
          fromAddress = b.getString(EXTRAS_FROM_ADDRESS);
          messageBody = b.getString(EXTRAS_MESSAGE_BODY);
          timestamp = b.getLong(EXTRAS_TIMESTAMP);
          contactId = b.getString(EXTRAS_CONTACT_ID);
          contactName = b.getString(EXTRAS_CONTACT_NAME);
          unreadCount = b.getInt(EXTRAS_UNREAD_COUNT, 1);
          threadId = b.getLong(EXTRAS_THREAD_ID, 0);
          messageId = b.getLong(EXTRAS_MESSAGE_ID, 0);
      }

      /**
       * Convert all SmsMmsMessage data to an extras bundle to send via an intent
       */
      public Bundle toBundle() {
          Bundle b = new Bundle();
          b.putString(EXTRAS_FROM_ADDRESS, fromAddress);
          b.putString(EXTRAS_MESSAGE_BODY, messageBody);
          b.putLong(EXTRAS_TIMESTAMP, timestamp);
          b.putString(EXTRAS_CONTACT_ID, contactId);
          b.putString(EXTRAS_CONTACT_NAME, contactName);	    
          b.putInt(EXTRAS_UNREAD_COUNT, unreadCount);
          b.putLong(EXTRAS_THREAD_ID, threadId);	    
          b.putLong(EXTRAS_MESSAGE_ID, messageId);
          return b;
      }


      public int getUnreadCount() {
          return unreadCount;
      }

      public long getTimestamp() {
          return timestamp;
      }

      public CharSequence getFormattedTimestamp() {
          return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME);
      }

      public String getContactName() {
          if (contactName == null) {
              contactName = context.getString(android.R.string.unknownName);
          }
          return contactName;
      }

      public String getMessageBody() {
          if (messageBody == null) {
              messageBody = "";
          }
          return messageBody;
      }

      public String getContactId() {
          return contactId;
      }

      public String getAddress() {
          return fromAddress;
      }

      public long getMessageId(){
          return messageId;
      }

      public long getThreadId()
      {
          return threadId;
      }
}

