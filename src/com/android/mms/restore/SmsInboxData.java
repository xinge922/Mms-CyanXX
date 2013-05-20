package com.android.mms.restore;

public class SmsInboxData {
	//public final String ID = "_id"; //INTEGER PRIMARY KEY,
	//public final String THREAD_ID = "thread_id";// INTEGER,
	public final static String ADDRESS = "address" ;// TEXT,
	//public final String PERSON = "person";// INTEGER,
	public final static String DATE = "date"; //  INTEGER,protocol INTEGER,
	public final static String READ = "read"; //INTEGER DEFAULT 0,
	//public final String STATUS = "status";// INTEGER DEFAULT -1,
	public final static String TYPE = "type";// INTEGER,
	//public final String REPLY_PATH_PRESENT = "reply_path_present";// INTEGER,
	public final static String SUBJECT = "subject";// TEXT,
	public final static String BODY = "body";// TEXT,
	public final static String SERVICE_CENTER = "service_center";// TEXT
	
	//public long mId;
	//public long mThreadId;
	public String mAddress; 
	//public int mPerson;
	public long mDate;
	public int mRead;
	//public int mStatus;
	public int mType;
	//public int mReply;
	public String mSubject;
	public String mBody;
	public String mServiceCenter;
	
	
	SmsInboxData(String address, long date, int read,int type,String subject,String body,String serviceCenter ){
		mAddress = address;
		mDate = date;
		mRead = read; 
		mType = type;
		mSubject = subject; 
		mBody = body;
		mServiceCenter = serviceCenter;
	}
	
	SmsInboxData(){
		
	}
	
	
}
