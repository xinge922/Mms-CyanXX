package com.android.mms.restore;
import com.android.mms.R;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.Scanner;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

public class SmsExporter {
	private final static String LOG_TAG = "MmsExporter";
	
	private final int mFileIndexMinimum = 1;
	private final int mFileIndexMaximum = 100;
	
	private final String mFileNameExtension = "sms";
	private final String mFileNamePrefix = "xinge_";
	private final String mFileNameSuffix = "";
	private final String mTargetDirectory = "/sdcard";
	
	private Context mParentContext;

	private ProgressDialog mProgressDialog;
	
	public SmsExporter(Context ctx){
		mParentContext = ctx;		
	}

    private class ConfirmListener implements DialogInterface.OnClickListener {
        private String mFileName;

        public ConfirmListener(String fileName) {
            mFileName = fileName;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                startExport(mFileName);
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            }
        }
    }
	
    private void displayReadingSmsDialog(DialogInterface.OnCancelListener listener,
            String fileName) {
        String title = mParentContext.getString(R.string.exporting_sms_list_title);
        String message = mParentContext.getString(R.string.exporting_sms_list_message, fileName);
        mProgressDialog = new ProgressDialog(mParentContext);
        mProgressDialog.setTitle(title);
        mProgressDialog.setMessage(message);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setOnCancelListener(listener);
        mProgressDialog.show();
    }
    
    public void startExport(String fileName){
    	ActualExportThread thread = new ActualExportThread(fileName);
    	displayReadingSmsDialog(thread, fileName);
    	thread.start();
    }
    
    private class SmsExporterImpl{
    	private ContentResolver mContentResolver;
    	private OutputStream mOutputStream;  // mWriter will close this.
    	private Writer mWriter;
    	
    	private Cursor mCursor;
        private int mAddressColumn;
        private int mDateColumn;
        private int mReadColumn;
        private int mTypeColumn;
        private int mSubjectColumn;
        private int mBodyColumn;
        private int mServiceCenterColumn;    	
        
        private static final String SMS_COL_SEPARATOR = "\r\n";
        private static final String SMS_DATA_SEPARATOR = ":";
        
        
        private static final String SMS_PROPERTY_BEGIN = "BEGIN";
        private static final String SMS_PROPERTY_END = "END";
        private static final String SMS_PROPERTY_VERSION = "VERSION";
        
        private static final String SMS_DATA_SMS = "ANDROID_SMS_BACKUP";
        private static final String SMS_DATA_VERSION_V01 = "0.1";
 
        
    	SmsExporterImpl(ContentResolver resolver, OutputStream outputStream){
    	       mContentResolver = resolver;
    	        mOutputStream = outputStream;	
    	}
    	
        public boolean init() {
            try {
                mWriter = new BufferedWriter(
                        new OutputStreamWriter(mOutputStream, "UTF-8"));
            } catch (UnsupportedEncodingException e1) {
                Log.e(LOG_TAG, "Unsupported charset: " + "UTF-8");
                return false;
            }

            final String[] projection = new String[] {
                    SmsInboxData.ADDRESS,
                    SmsInboxData.DATE,
                    SmsInboxData.READ,
                    SmsInboxData.TYPE,
                    SmsInboxData.SUBJECT,
                    SmsInboxData.BODY,
                    SmsInboxData.SERVICE_CENTER
            };
            
            String strUri = "content://sms/";
            Uri uriSms = Uri.parse(strUri);  //If you want to access all SMS, just replace the uri string to "content://sms/"
            
            mCursor = mContentResolver.query(uriSms, projection, null, null, null);
            if (mCursor == null || !mCursor.moveToFirst()) {
                if (mCursor != null) {
                    try {
                        mCursor.close();
                    } catch (SQLiteException e) {
                    }
                    mCursor = null;
                }
                //mErrorReason = "Getting database information failed.";
                return false;
            }
            
            mAddressColumn = mCursor.getColumnIndex(SmsInboxData.ADDRESS);
            mDateColumn  = mCursor.getColumnIndex(SmsInboxData.DATE) ;
            mReadColumn = mCursor.getColumnIndex(SmsInboxData.READ);
            mTypeColumn = mCursor.getColumnIndex(SmsInboxData.TYPE);
            mSubjectColumn = mCursor.getColumnIndex(SmsInboxData.SUBJECT);
            mBodyColumn = mCursor.getColumnIndex(SmsInboxData.BODY);
            mServiceCenterColumn = mCursor.getColumnIndex(SmsInboxData.SERVICE_CENTER);
            
            return true;
        }
        
        @Override
        public void finalize() {
            terminate();           
        }

        public void terminate() {
            if (mWriter != null) {
                try {
                    // Flush and sync the data so that a user is able to pull the SDCard just after the
                    // export.
                    mWriter.flush();
                    if (mOutputStream != null && mOutputStream instanceof FileOutputStream) {
                        try {
                            ((FileOutputStream)mOutputStream).getFD().sync();
                        } catch (IOException e) {
                        }
                    }
                    mWriter.close();
                } catch (IOException e) {
                }
            }
            if (mCursor != null) {
                try {
                    mCursor.close();
                } catch (SQLiteException e) {
                }
                mCursor = null;
            }
        }
        
        
        public int getCount() {
            if (mCursor == null) {
                return 0;
            }
            return mCursor.getCount();
        }

        public boolean isAfterLast() {
            if (mCursor == null) {
                return false;
            }
            return mCursor.isAfterLast();
        }
        
        void appendSmsLine(StringBuilder builder, String fieldName, String data){
        	if(data == null)
        		return ;
        	
        	builder.append(fieldName);
            builder.append(SMS_DATA_SEPARATOR);
            builder.append(data);
            builder.append(SMS_COL_SEPARATOR);
        }
        
        public boolean exportOnuSmsData(){
        	
        	 try {
        		 SmsInboxData smsData = new SmsInboxData();
        		 
        		 smsData.mAddress = mCursor.getString(mAddressColumn);
        		 smsData.mBody = mCursor.getString(mBodyColumn);
        		 smsData.mSubject = mCursor.getString(mSubjectColumn);
        		 smsData.mServiceCenter = mCursor.getString(mServiceCenterColumn);

        		 smsData.mDate = mCursor.getLong(mDateColumn);
        		 smsData.mRead = mCursor.getInt(mReadColumn);
        		 smsData.mType = mCursor.getInt(mTypeColumn);
        		 

                 mCursor.moveToNext();

                 String smsString = convertSmsToFlatString(smsData);
                 try {
                     mWriter.write(smsString);
                 } catch (IOException e) {
                     Log.e(LOG_TAG, "IOException occurred during exportOneContactData: " +
                             e.getMessage());
                     //mErrorReason = "IOException occurred: " + e.getMessage();
                     return false;
                 }
             } catch (OutOfMemoryError error) {
                 // Maybe some data (e.g. photo) is too big to have in memory. But it should be rare.
                 Log.e(LOG_TAG, "OutOfMemoryError occured. ");
                 System.gc();
             }

        	
        	return true;
        }
        
        private String convertSmsToFlatString(SmsInboxData data){
            StringBuilder builder = new StringBuilder();
            appendSmsLine(builder, SMS_PROPERTY_BEGIN, SMS_DATA_SMS);
            appendSmsLine(builder, SMS_PROPERTY_VERSION, SMS_DATA_VERSION_V01);

	        appendSmsLine(builder, SmsInboxData.ADDRESS, data.mAddress);
	        
	        appendSmsLine(builder, SmsInboxData.DATE, Long.toString(data.mDate));
	        appendSmsLine(builder, SmsInboxData.READ, Integer.toString(data.mRead));
	        appendSmsLine(builder, SmsInboxData.TYPE, Integer.toString(data.mType));
	  
	        appendSmsLine(builder, SmsInboxData.SUBJECT, data.mSubject);
	        
	
	        appendSmsLine(builder, SmsInboxData.BODY, data.mBody);
	        appendSmsLine(builder, SmsInboxData.SERVICE_CENTER, data.mServiceCenter);
            
            appendSmsLine(builder, SMS_PROPERTY_END, SMS_DATA_SMS);
            
            return builder.toString();
        }
    	
    }
    
    private class ActualExportThread extends Thread
    	implements DialogInterface.OnCancelListener {
		private PowerManager.WakeLock mWakeLock;
		private String mFileName;
		private boolean mCanceled = false;
		
		public ActualExportThread(String fileName) {
		    mFileName = fileName;
		    PowerManager powerManager = (PowerManager)mParentContext.getSystemService(
		            Context.POWER_SERVICE);
		    mWakeLock = powerManager.newWakeLock(
		            PowerManager.SCREEN_DIM_WAKE_LOCK |
		            PowerManager.ON_AFTER_RELEASE, LOG_TAG);
		}
		
		@Override
		public void run() {
		    mWakeLock.acquire();	
		    
		    SmsExporterImpl exporterImpl = null;
		    try {
		        OutputStream outputStream = null;
		        try {
		            outputStream = new FileOutputStream(mFileName);
		        } catch (FileNotFoundException e) {
		            //String reason = getString(R.string.fail_reason_could_not_open_file,
		             //       mFileName, e.getMessage());
		            //mParentHandler.post(new ErrorMessageDisplayRunnable(reason));
		            return;
		        }
		
		        TelephonyManager telephonyManager =
		            (TelephonyManager)mParentContext.getSystemService(
		                    Context.TELEPHONY_SERVICE);
		
		        exporterImpl = new SmsExporterImpl(mParentContext.getContentResolver(),
		                outputStream);
		
		        if (!exporterImpl.init()) {
		            //String reason = getString(R.string.fail_reason_could_not_initialize_exporter,
		             //       exporterImpl.getErrorReason());
		            //mParentHandler.post(new ErrorMessageDisplayRunnable(reason));
		            return;
		        }
		
		        int size = exporterImpl.getCount();
		
		        //mProgressDialog.setProgressNumberFormat(
		        //		mParentContext.getString(R.string.exporting_sms_list_progress));
		        mProgressDialog.setMax(size);
		        mProgressDialog.setProgress(0);
		
		        while (!exporterImpl.isAfterLast()) {
		            if (mCanceled) {
		                return;
		            }
		            if (!exporterImpl.exportOnuSmsData()) {
		                Log.e(LOG_TAG, "Failed to read a contact.");
		                //String reason = getString(R.string.fail_reason_error_occurred_during_export,
		                //        exporterImpl.getErrorReason());
		               // mParentHandler.post(new ErrorMessageDisplayRunnable(reason));
		                return;
		            }
		            mProgressDialog.incrementProgressBy(1);
		        }
		    } finally {
		        if (exporterImpl != null) {
		            exporterImpl.terminate();
		        }
		        mWakeLock.release();
		        mProgressDialog.dismiss();
		    }
		    
		}
		
		@Override
		public void finalize() {
		    if (mWakeLock != null && mWakeLock.isHeld()) {
		        mWakeLock.release();
		    }
		}
		
		public void onCancel(DialogInterface dialog) {
		    mCanceled = true;
		}
	}

    
    
	public void startExportSmsToSdCard(){
        File targetDirectory = new File(mTargetDirectory);

        if (!(targetDirectory.exists() &&
                targetDirectory.isDirectory() &&
                targetDirectory.canRead()) &&
                !targetDirectory.mkdirs()) {
            new AlertDialog.Builder(mParentContext)
                    .setTitle(R.string.no_sdcard_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.no_sdcard_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } else {
            String fileName = getAppropriateFileName(mTargetDirectory);
            if (TextUtils.isEmpty(fileName)) {
                return;
            }

            new AlertDialog.Builder(mParentContext)
                .setTitle(R.string.confirm_export_title)
                .setMessage(mParentContext.getString(R.string.confirm_export_message, fileName))
                .setPositiveButton(android.R.string.ok, new ConfirmListener(fileName))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        }
	}
	
    private String getAppropriateFileName(final String destDirectory) {
        int fileNumberStringLength = 0;
        {
            // Calling Math.Log10() is costly.
            int tmp;
            for (fileNumberStringLength = 0, tmp = mFileIndexMaximum; tmp > 0;
                fileNumberStringLength++, tmp /= 10) {
            }
        }
        
        String bodyFormat = "%s%0" + fileNumberStringLength + "d%s";

        
        String possibleBody = String.format(bodyFormat,mFileNamePrefix, 1, mFileNameSuffix);

        // Note that this logic assumes that the target directory is case insensitive.
        // As of 2009-07-16, it is true since the external storage is only sdcard, and
        // it is formated as FAT/VFAT.
        // TODO: fix this.
        for (int i = mFileIndexMinimum; i <= mFileIndexMaximum; i++) {
            boolean numberIsAvailable = true;
            // SD Association's specification seems to require this feature, though we cannot
            // have the specification since it is proprietary...
            String body = null;
            
            body = String.format(bodyFormat, mFileNamePrefix, i, mFileNameSuffix);
            File file = new File(String.format("%s/%s.%s",
                    destDirectory, body, mFileNameExtension));
            if (file.exists()) {
                numberIsAvailable = false;
                continue;
            }
           
            if (numberIsAvailable) {
                return String.format("%s/%s.%s", destDirectory, body, mFileNameExtension);
            }
        }
        
        displayErrorMessage(mParentContext.getString(R.string.fail_reason_too_many_sms_backup));
        return null;
    }
    
    
    private void displayErrorMessage(String failureReason) {
        new AlertDialog.Builder(mParentContext)
            .setTitle(R.string.exporting_sms_failed_title)
            .setMessage(mParentContext.getString(R.string.exporting_sms_failed_message,
                    failureReason))
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }

}
