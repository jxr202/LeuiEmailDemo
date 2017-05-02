package com.letv.commons.chip;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Filterable;
import android.widget.ListAdapter;
import android.widget.MultiAutoCompleteTextView;

import com.leui.email_demo.BaseRecipientAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Jxr33 on 2017-03-15
 */
public class RecipientEditTextView extends MultiAutoCompleteTextView {

    private static final String TAG = "RecipientEditTextView";

    private Context mContext;
    private Tokenizer mTokenizer;

    public RecipientEditTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    @Override
    public void setTokenizer(Tokenizer t) {
        super.setTokenizer(t);
        mTokenizer = t;
    }

    @Override
    protected void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter);
        Log.i(TAG, "onTextChanged.. text: " + text);

        if (!TextUtils.isEmpty(text)) {
            int e = getSelectionEnd();
            int s = mTokenizer.findTokenStart(text, e);
            String newAddress = text.toString().substring(s);
            if (newAddress.contains("@")) {
                Log.i(TAG, "onTextChanged.. newAddress: " + newAddress);
                if (hasRepeatAddress()) {
                    getText().delete(s, e);
                }
            }
        }
    }

    private boolean hasRepeatAddress() {
        ArrayList<String> addresses = new ArrayList<>();
        HashMap<String, Integer> addressMap = new HashMap<>();
        Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(getText().toString());
        for (int i = 0; i < tokens.length; i ++) {
            String address = tokens[i].getAddress();
            if (!TextUtils.isEmpty(address) && address.contains("@")) {
                addresses.add(address);
                addressMap.put(address,i);
            }
        }
        Log.i(TAG, "hasRepeatAddress.. listSize: " + addresses.size() + ", mapSize: " + addressMap.size());
        return addresses.size() != addressMap.size();
    }


    @Override
    public <T extends ListAdapter & Filterable> void setAdapter(T adapter) {
        super.setAdapter(adapter);
        BaseRecipientAdapter baseAdapter = (BaseRecipientAdapter) adapter;
        baseAdapter.registerUpdateObserver(new BaseRecipientAdapter.EntriesUpdatedObserver() {
            @Override
            public void onChanged(List<RecipientEntry> entries) {
                if(entries != null && entries.size() == 0){
                    Log.i(TAG, "onChanged.. dismissDropDown..");
                    dismissDropDown();
                }else if(entries != null && entries.size() > 0 && !isPopupShowing() && mContext instanceof Activity && !((Activity) mContext).isDestroyed()){
                    Log.i(TAG, "onChanged.. showDropDown..");
                    showDropDown();
                }
            }
        });
    }


}
