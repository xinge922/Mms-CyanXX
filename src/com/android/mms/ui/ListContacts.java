
package com.android.mms.ui;

import com.android.mms.R;

import java.util.Vector;

import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;

import android.database.Cursor;

import android.os.Bundle;

import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.Contacts.Phones;

import android.text.Annotation;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;

import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;

import android.widget.TextView;
import android.widget.Toast;


public class ListContacts extends ListActivity 
 implements OnClickListener{
    /** Called when the activity is first created. */
	private static final String TAG = "ListContacts";
	
	ContactsListAdapter ContactsAdp;
	
    public static final class ContactsListItemViews {
        TextView nameView;
        TextView labelView;
        TextView numberView;
        CheckBox selectView;
    }
	
    static final String[] PHONES_PROJECTION = new String[] {
        Phone._ID, 
    	 Phone.DISPLAY_NAME,
         Phone.LABEL, 
         Phone.NUMBER, 
         Phone.TYPE, 
    }; 
    
    static final int PHONE_NAME_IDX = 1; 
    static final int PHONE_LABEL_IDX = 2; 
    static final int PHONE_NUMBER_IDX = 3; 
    static final int PHONE_TYPE_IDX = 4; 
    
    private static final class PhoneInfo{
    	String name;
    	String number;
    }
    
    private final String BTN_OK_TAG = "TAG_OK"; 
    private final String BTN_CANCEL_TAG = "TAG_CANCEL"; 
    
    public  static final String CONTACTS_LIST_DATA_TAG = "CONTACTS_LIST_DATA";
    
    private Vector<PhoneInfo> phoneList;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.contacts_list);
        
        Button btnOk = (Button) findViewById(R.id.ok);
        
        Button btnCancel = (Button) findViewById(R.id.cancel);
        btnOk.setTag(BTN_OK_TAG);
        btnCancel.setTag(BTN_CANCEL_TAG);
        
        btnOk.setOnClickListener(this);
        btnCancel.setOnClickListener(this);
        
        phoneList = new Vector<PhoneInfo>();
        
        final ListView list = getListView();
        list.setFastScrollEnabled(false); 
        StringBuilder where = new StringBuilder();
        where.append(Phone.NUMBER + " not NULL"); 
        
        String sort =  Phone.DISPLAY_NAME + "  COLLATE LOCALIZED ASC";
        Cursor c = getContentResolver().query(Phone.CONTENT_URI, PHONES_PROJECTION, where.toString(), null, sort);       
        ContactsAdp = new ContactsListAdapter(this,R.layout.contacts_list_item,c);        
        setListAdapter(ContactsAdp);        
    }
   

    
    final class ContactsListAdapter extends ResourceCursorAdapter 
    {

		public ContactsListAdapter(Context context, int layout, Cursor c) {
			super(context, layout, c);
			
		}

       @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = super.newView(context, cursor, parent);            
            // Get the views to bind to
            ContactsListItemViews views = new ContactsListItemViews();
            views.nameView = (TextView) view.findViewById(R.id.name);	            
            views.labelView = (TextView) view.findViewById(R.id.label);
            views.numberView = (TextView) view.findViewById(R.id.number);
            views.selectView = (CheckBox) view.findViewById(R.id.select);            
            view.setTag(views);
            
            return view;
        }  

		@Override
		public void bindView(View view, Context context, Cursor c) {
			 final ContactsListItemViews views = (ContactsListItemViews) view.getTag();	
			 String name = c.getString(PHONE_NAME_IDX);
			 String number = c.getString(PHONE_NUMBER_IDX);
			 
			 if(name == null)
				 name = number;
			 
			 views.nameView.setText(name);
			 views.labelView.setText(Phones.getDisplayLabel(context, c.getInt(PHONE_TYPE_IDX), c.getString(PHONE_LABEL_IDX)));
			 views.numberView.setText(number);

			 views.selectView.setClickable(false);
			 
			 if(isPhoneSelect(name,number))
				 views.selectView.setChecked(true);
			 else
				 views.selectView.setChecked(false);
		}		
    }

    protected void onListItemClick(ListView l, View view, int position, long id) {
		final ContactsListItemViews views = (ContactsListItemViews) view.getTag();
		if(!views.selectView.isChecked()){
			PhoneInfo phoneItem = new PhoneInfo();
			phoneItem.name = views.nameView.getText().toString();
			phoneItem.number = views.numberView.getText().toString();			
			phoneList.add(phoneItem);
		}else{
			deletePhoneFromList(views.nameView.getText().toString(),views.numberView.getText().toString());
		}
		views.selectView.setChecked(!views.selectView.isChecked());		
    }
    
    private boolean isPhoneSelect(String name, String number){
    	if(name == null || number == null)
    		return false;
    	
		for(int i = 0 ; i < phoneList.size(); i++){
			if(name.equals(phoneList.get(i).name) 
					&& number.equals(phoneList.get(i).number)){
				return true;
			}
		}
		return false;
    }
    
    private void deletePhoneFromList(String name , String number){
    	if(name == null || number == null)
    		return;
    	
		for(int i = 0 ; i < phoneList.size(); i++){
			if(name.equals(phoneList.get(i).name) 
					&& number.equals(phoneList.get(i).number)){
				phoneList.remove(i);
				break;
			}
		}
    }
    
    public final CharSequence convertToString(String name, String number) {
    	
        if (number.length() == 0) {
            return number;
        }
        
        if (name == null) {
            name = "";
        }
        
        String nameAndNumber ;
        
        if (!name.equals(number)) {
        	nameAndNumber= name + " <" + number + ">";
        } else {
        	nameAndNumber = number;
        } 


        SpannableString out = new SpannableString(nameAndNumber);
        int len = out.length();

        if (!TextUtils.isEmpty(name)) {
            out.setSpan(new Annotation("name", name), 0, len,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            out.setSpan(new Annotation("name", number), 0, len,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }        
        out.setSpan(new Annotation("number", number), 0, len,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        return out;
    }

	public void onClick(View v) {
		String tag = (String) v.getTag();
		Intent  intent = new Intent();	
        SpannableStringBuilder sb = new SpannableStringBuilder();
		
		if(tag.equals(BTN_OK_TAG)){			
			int i;
			for( i = 0 ; i < phoneList.size(); i++){
					sb.append(convertToString(phoneList.get(i).name,phoneList.get(i).number));				
                    sb.append(',');
			}			
			intent.putExtra(CONTACTS_LIST_DATA_TAG, sb);
			setResult(RESULT_OK, intent);		
		}else if(tag.equals(BTN_CANCEL_TAG)){
			setResult(RESULT_CANCELED);
		}
		finish();
	}


}
