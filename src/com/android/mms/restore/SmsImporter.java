package com.android.mms.restore;
import com.android.mms.R;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.Vector;

import android.app.AlertDialog;
import android.app.ProgressDialog;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Handler;
import android.os.PowerManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;

public class SmsImporter {
	private final static String LOG_TAG = "SmsImporter";
	private Context mParentContext;

	private ProgressDialog mProgressDialog;
	
	private Handler mHandler = new Handler();

    private static final String SMS_COL_SEPARATOR = "\r\n";
    private static final String SMS_DATA_SEPARATOR = ":";
    
    
    private static final String SMS_PROPERTY_BEGIN = "BEGIN";
    private static final String SMS_PROPERTY_END = "END";
    private static final String SMS_PROPERTY_VERSION = "VERSION";
    
    private static final String SMS_DATA_SMS = "ANDROID_SMS_BACKUP";
    private static final String SMS_DATA_VERSION_V01 = "0.1";
	
    private final String FILE_NAME_EXTENSION = "sms";
    
    private List<SmsInboxData> mSmsDataList;
    
	public SmsImporter(Context ctx){
		mParentContext = ctx;		
	}
	
	class SmsFile {
	    private String mName;
	    private String mCanonicalPath;
	    private long mLastModified;

	    public SmsFile(String name, String canonicalPath, long lastModified) {
	        mName = name;
	        mCanonicalPath = canonicalPath;
	        mLastModified = lastModified;
	    }

	    public String getName() {
	        return mName;
	    }

	    public String getCanonicalPath() {
	        return mCanonicalPath;
	    }

	    public long getLastModified() {
	        return mLastModified;
	    }
	}
	
    private void displayScanErrorMessage(String failureReason) {
        new AlertDialog.Builder(mParentContext)
            .setTitle(R.string.scanning_sdcard_failed_title)
            .setMessage(mParentContext.getString(R.string.scanning_sdcard_failed_message,
                    failureReason))
            .setPositiveButton(android.R.string.ok, null)
            .show();
    }
    
    protected void processLine(String aLine){
        //use a second Scanner to parse the content of each line 
        Scanner lineScanner = new Scanner(aLine);
        lineScanner.useDelimiter(SMS_DATA_SEPARATOR);
        if ( lineScanner.hasNext() ){
          String name = lineScanner.next();
          String value = lineScanner.next();        
        }
        lineScanner.close();
      }

    
    private class SmsReadThread extends Thread
    		implements DialogInterface.OnCancelListener {
		private String mCanonicalPath;
		private ContentResolver mResolver;
		private boolean mCanceled;
		private PowerManager.WakeLock mWakeLock;
		
		

		public SmsReadThread(String canonicalPath) {
		    mCanonicalPath = canonicalPath;		   
           
            mResolver = mParentContext.getContentResolver();
            PowerManager powerManager = (PowerManager)mParentContext.getSystemService(
                    Context.POWER_SERVICE);
            mWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_DIM_WAKE_LOCK |
                    PowerManager.ON_AFTER_RELEASE, LOG_TAG);
            
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
        
        @Override
        public void run() {
            mWakeLock.acquire();
            // Some malicious vCard data may make this thread broken
            // (e.g. OutOfMemoryError).
            // Even in such cases, some should be done.
            try {
                if (mCanonicalPath != null) {
                    //mProgressDialog.setProgressNumberFormat("");
                    mProgressDialog.setProgress(0);                 
                    
                    mSmsDataList = parserSmsDataFile(mCanonicalPath);                    
                    mProgressDialog.dismiss();
                    
                	mHandler.post(new Runnable() {
                        public void run() {
                        	String title = mParentContext.getString(R.string.write_sms_title);
                            String message = mParentContext.getString(R.string.write_sms_message);
                            SmsWriteThread thread = new SmsWriteThread();
                            showRestoreSmsDialog(title,message,thread);
                            thread.start();
                        }
                	});
                	
                } 
                
            } finally {
                mWakeLock.release();
                mProgressDialog.dismiss();
            }
        }
        
        
        private int counterFileLine(String fileName){
        	DataInputStream in = null;
        	FileInputStream fstream;
        	int lineNum = 0;
			try {
				fstream = new FileInputStream(fileName);
			    // Get the object of DataInputStream
			    in = new DataInputStream(fstream);
			    
			    BufferedReader brCounter = new BufferedReader(new InputStreamReader(in));
			    
			    
			    while(brCounter.readLine() != null){
			    	lineNum ++;
			    }
			    in.close();
			    
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		    return lineNum;
		    
        }
        
        private void incrementProgres(int lineNum,int currentLine){
        	 int step = lineNum/100;
        	 
        	 if(lineNum >100 && currentLine%step == 0){
        		 mProgressDialog.incrementProgressBy(1);
        	 }else if(lineNum < 100){
        		 mProgressDialog.incrementProgressBy(100/lineNum);
        	 }
        }
        
        List<SmsInboxData> parserSmsDataFile(String fileName){
    		List<SmsInboxData> dataList = new Vector<SmsInboxData>();
    		DataInputStream in = null;
    		try {
    			
    		    FileInputStream fstream = new FileInputStream(fileName);
    		    // Get the object of DataInputStream
    		    in = new DataInputStream(fstream);
    		    
    		    int lineNum = counterFileLine(fileName),i = 0;
    		    
    		   
	            //mProgressDialog.setProgressNumberFormat(
	            //        getString(R.string.reading_vcard_contacts));
    		    
	            mProgressDialog.setIndeterminate(false);    		    
	            mProgressDialog.setMax(100); 
	            
	            
	            in = new DataInputStream(fstream);
    		    BufferedReader br = new BufferedReader(new InputStreamReader(in));
    		    String strLine;
    		    boolean entryStarted = false;
    			SmsInboxData entry = null ;
    			
    		    //Read File Line By Line
    		    while ((strLine = br.readLine()) != null)   {
    		    	
    		    	if(mCanceled){
    		    		return dataList;
    		    	}
    		    	
    		    	i++; 
    		    	incrementProgres(lineNum, i);
    		    	
    				String name = null;
    				String value = null;
                    int pos = strLine.indexOf(SMS_DATA_SEPARATOR);
                    if(pos != -1){
                        name = strLine.substring(0,pos).trim();
                        value = strLine.substring(pos+1,strLine.length()).trim();
                    }
    		        
    		        if(name == null)
    		        	continue;
    		        
    		        if(name.equals(SMS_PROPERTY_BEGIN)){
    		        	entryStarted = true;
    		        	entry = new SmsInboxData();		        	
    		        }
    		        
    		        if(name.equals(SMS_PROPERTY_END)){
    		        	entryStarted = false;
    		        	dataList.add(entry);
    		        	entry = null;
    		        }		        
    		        
    		        if(name.equals(SmsInboxData.ADDRESS) && entryStarted){
    		        	entry.mAddress = value;
    		        }
    		        
    		        if(name.equals(SmsInboxData.BODY) && entryStarted){
    		        	entry.mBody = value;
    		        }
    		        
    		        if(name.equals(SmsInboxData.SERVICE_CENTER) && entryStarted){
    		        	entry.mServiceCenter = value;
    		        }
    		        
    		        if(name.equals(SmsInboxData.SUBJECT) && entryStarted){
    		        	entry.mSubject = value;
    		        }
    		        
    		        if(name.equals(SmsInboxData.DATE) && entryStarted){
    		        	entry.mDate = Long.parseLong(value);
    		        }
    		        
    		        if(name.equals(SmsInboxData.READ) && entryStarted){
    		        	entry.mRead = Integer.parseInt(value);
    		        }
    		        
    		        if(name.equals(SmsInboxData.TYPE) && entryStarted){
    		        	entry.mType = Integer.parseInt(value);
    		        }
    		        
    			}
    		    
    		    in.close();
    		}catch (FileNotFoundException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (NumberFormatException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		} catch (IOException e) {
    			// TODO Auto-generated catch block
    			e.printStackTrace();
    		}

    		return dataList;    	
        }
    }
   
    private class SmsWriteThread extends Thread
		implements DialogInterface.OnCancelListener {
		private ContentResolver mResolver;
		private boolean mCanceled;
		private PowerManager.WakeLock mWakeLock;
		
		public SmsWriteThread() {
			
		    mResolver = mParentContext.getContentResolver();
		    PowerManager powerManager = (PowerManager)mParentContext.getSystemService(
		            Context.POWER_SERVICE);
		    mWakeLock = powerManager.newWakeLock(
		            PowerManager.SCREEN_DIM_WAKE_LOCK |
		            PowerManager.ON_AFTER_RELEASE, LOG_TAG);
		    
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
		
		@Override
		public void run() {
		    mWakeLock.acquire();

		    try {
		        
		            //mProgressDialog.setProgressNumberFormat("");
		            mProgressDialog.setProgress(0);
		            
		            //mProgressDialog.setProgressNumberFormat(
		            //        getString(R.string.reading_vcard_contacts));
		            mProgressDialog.setIndeterminate(false);
		            mProgressDialog.setMax(mSmsDataList.size());
		            String strUriInbox = "content://sms/inbox";
		            Uri uriSms = Uri.parse(strUriInbox);
		            
		            ContentResolver resolver = mParentContext.getContentResolver();
		            
		            for(int i =0; i < mSmsDataList.size(); i++ ){
		            	if(mCanceled)
		            		return;
		            	
		            	ContentValues values = new ContentValues();
		                values.put(SmsInboxData.ADDRESS, mSmsDataList.get(i).mAddress);
		                values.put(SmsInboxData.DATE, mSmsDataList.get(i).mDate);
		                values.put(SmsInboxData.TYPE, mSmsDataList.get(i).mType);
		                values.put(SmsInboxData.READ, mSmsDataList.get(i).mRead);
		                values.put(SmsInboxData.BODY, mSmsDataList.get(i).mBody);
		                values.put(SmsInboxData.SUBJECT, mSmsDataList.get(i).mSubject);
		                values.put(SmsInboxData.SERVICE_CENTER, mSmsDataList.get(i).mServiceCenter);
		                
		                resolver.insert( uriSms, values);
		                
		            	mProgressDialog.incrementProgressBy(1);
		            }		            
		            mProgressDialog.dismiss();		       
		        
		    } finally {
		        mWakeLock.release();
		        mProgressDialog.dismiss();
		    }
		}
    }
  
    private void showRestoreSmsDialog(String title, String message, DialogInterface.OnCancelListener listener) {
        mProgressDialog = new ProgressDialog(mParentContext);
        mProgressDialog.setTitle(title);
        mProgressDialog.setMessage(message);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setOnCancelListener(listener);        
        mProgressDialog.show();
    }
    
    private void importOneSmsFromSDCard(String canonicalPath) {
        String title = mParentContext.getString(R.string.reading_sms_title);
        String message = mParentContext.getString(R.string.reading_sms_message);
        SmsReadThread thread = new SmsReadThread(canonicalPath);
        showRestoreSmsDialog(title,message,thread);
        thread.start();
    }


    
    private class SmsSelectedListener implements DialogInterface.OnClickListener {
        private List<SmsFile> mSmsFileList;
        private int mCurrentIndex;

        public SmsSelectedListener(List<SmsFile> smsFileList) {
        	mSmsFileList = smsFileList;
            mCurrentIndex = 0;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
            	importOneSmsFromSDCard(mSmsFileList.get(mCurrentIndex).getCanonicalPath());
            } else if (which == DialogInterface.BUTTON_NEGATIVE) {
                return;
            } else {
                // Some file is selected.
                mCurrentIndex = which;
            }
        }
    }
    
    private void showSmsFileSelectDialog(List<SmsFile> smsFileList) {
        int size = smsFileList.size();
        DialogInterface.OnClickListener listener =
            new SmsSelectedListener(smsFileList);
        AlertDialog.Builder builder =
            new AlertDialog.Builder(mParentContext)
                .setTitle(R.string.select_sms_title)
                .setPositiveButton(android.R.string.ok, listener)
                .setOnCancelListener(null)
                .setNegativeButton(android.R.string.cancel, null);

        CharSequence[] items = new CharSequence[size];
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (int i = 0; i < size; i++) {
        	SmsFile smsFile = smsFileList.get(i);
            SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
            stringBuilder.append(smsFile.getName());
            stringBuilder.append('\n');
            int indexToBeSpanned = stringBuilder.length();
            // Smaller date text looks better, since each file name becomes easier to read.
            // The value set to RelativeSizeSpan is arbitrary. You can change it to any other
            // value (but the value bigger than 1.0f would not make nice appearance :)
            stringBuilder.append(
                        "(" + dateFormat.format(new Date(smsFile.getLastModified())) + ")");
            stringBuilder.setSpan(
                    new RelativeSizeSpan(0.7f), indexToBeSpanned, stringBuilder.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            items[i] = stringBuilder;
        }
        builder.setSingleChoiceItems(items, 0, listener);
        builder.show();
    }
	
    private class SmsScanThread extends Thread implements OnCancelListener, OnClickListener {
        private boolean mCanceled;
        private boolean mGotIOException;
        private File mRootDirectory;

        // null when search operation is canceled.
        private List<SmsFile> mSmsFiles;

        // To avoid recursive link.
        private Set<String> mCheckedPaths;
        private PowerManager.WakeLock mWakeLock;

        private class CanceledException extends Exception {
        }

        public SmsScanThread(File sdcardDirectory) {
            mCanceled = false;
            mGotIOException = false;
            mRootDirectory = sdcardDirectory;
            mCheckedPaths = new HashSet<String>();
            mSmsFiles = new Vector<SmsFile>();
            PowerManager powerManager = (PowerManager)mParentContext.getSystemService(
                    Context.POWER_SERVICE);
            mWakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_DIM_WAKE_LOCK |
                    PowerManager.ON_AFTER_RELEASE, LOG_TAG);
        }

        @Override
        public void run() {
            try {
                mWakeLock.acquire();
                getSmsFileRecursively(mRootDirectory);
            } catch (CanceledException e) {
                mCanceled = true;
            } catch (IOException e) {
                mGotIOException = true;
            } finally {
                mWakeLock.release();
            }

            if (mCanceled) {
            	mSmsFiles = null;
            }

            mProgressDialog.dismiss();

            if (mGotIOException) {
            	mHandler.post(new Runnable() {
                    public void run() {
                    	displayScanErrorMessage(mParentContext.getString(R.string.fail_reason_io_error));
                    }
            	});
            } else if (mCanceled) {
                return;
            } else {
            	mHandler.post(new Runnable() {
                    public void run() {
		            	int size = mSmsFiles.size();
		            	if(size == 0){
		            		displayScanErrorMessage(mParentContext.getString(R.string.fail_reason_no_sms_file));
		            	}else{
		            		showSmsFileSelectDialog(mSmsFiles);
		            	}
                    }
            	});
            }
        }

        private void getSmsFileRecursively(File directory)
                throws CanceledException, IOException {
            if (mCanceled) {
                throw new CanceledException();
            }

            for (File file : directory.listFiles()) {
                if (mCanceled) {
                    throw new CanceledException();
                }
                String canonicalPath = file.getCanonicalPath();
                if (mCheckedPaths.contains(canonicalPath)) {
                    continue;
                }

                mCheckedPaths.add(canonicalPath);
                
                if (file.isDirectory()) {
                	//getSmsFileRecursively(file);
                } else if (canonicalPath.toLowerCase().endsWith("." + FILE_NAME_EXTENSION) &&
                        file.canRead()){
                    String fileName = file.getName();
                    SmsFile smsFile = new SmsFile(
                            fileName, canonicalPath, file.lastModified());
                    mSmsFiles.add(smsFile);
                }
            }
        }

        public void onCancel(DialogInterface dialog) {
            mCanceled = true;
        }

        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_NEGATIVE) {
                mCanceled = true;
            }
        }
    }


	
	public void startImportSmsFromSdCard() {
        File file = new File("/sdcard");
        if (!file.exists() || !file.isDirectory() || !file.canRead()) {
            new AlertDialog.Builder(mParentContext)
                    .setTitle(R.string.no_sdcard_title)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.no_sdcard_message)
                    .setOnCancelListener(null)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } else {
            String title = mParentContext.getString(R.string.searching_sms_title);
            String message = mParentContext.getString(R.string.searching_sms_message);

            mProgressDialog = ProgressDialog.show(mParentContext, title, message, true, false);
            SmsScanThread thread = new SmsScanThread(file);
            mProgressDialog.setOnCancelListener(thread);
            thread.start();
        }
    }

	
}
