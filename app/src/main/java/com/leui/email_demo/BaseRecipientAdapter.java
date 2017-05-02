package com.leui.email_demo;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.net.Uri;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.letv.commons.chip.RecipientEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by Jxr33 on 2017-03-15
 */
public class BaseRecipientAdapter implements ListAdapter, Filterable {

    private static final String TAG = "BaseRecipientAdapter";

    private final DataSetObservable mDataSetObservable = new DataSetObservable();

    private EntriesUpdatedObserver mEntriesUpdatedObserver;
    public interface EntriesUpdatedObserver {
        void onChanged(List<RecipientEntry> entries);
    }
    public void registerUpdateObserver(EntriesUpdatedObserver observer) {
        mEntriesUpdatedObserver = observer;
    }

    private List<RecipientEntry> mEntries;
    private LayoutInflater mInflater;
    private ContentResolver mContentResolver;

    public BaseRecipientAdapter(Context context) {
        mInflater = LayoutInflater.from(context);
        mContentResolver = context.getContentResolver();
    }

    protected List<RecipientEntry> getEntries() {
        return mEntries;
    }


    @Override
    public Filter getFilter() {
        return new DefaultFilter();
    }

    protected static class TemporaryEntry {

        public final String displayName;
        public final String destination;
        public final String lookupKey;
        public final Long directoryId;

        /**
         * 从手机联系人取到的名字和Email
         * @param cursor cursor
         * @param directoryId cursor
         */
        public TemporaryEntry(Cursor cursor, Long directoryId) {
            this.displayName = cursor.getString(0);
            this.destination = cursor.getString(1);
            this.lookupKey = cursor.getString(2);
            this.directoryId = directoryId;
        }

        /**
         * 从Email APP中取到的名字和Email
         * @param cursor cursor
         */
        public TemporaryEntry(Cursor cursor) {
            this.displayName = cursor.getString(0);
            this.destination = cursor.getString(1);
            this.directoryId = null;
            this.lookupKey = null;
        }
    }

    /**
     * Used to pass results from {@link DefaultFilter#performFiltering(CharSequence)} to
     * {@link DefaultFilter#publishResults(CharSequence, android.widget.Filter.FilterResults)}
     */
    private static class DefaultFilterResult {
        public final List<RecipientEntry> entries;
        public final LinkedHashMap<String, List<RecipientEntry>> entryMap;
        public final List<RecipientEntry> nonAggregatedEntries;
        public final Set<String> existingDestinations;

        public DefaultFilterResult(List<RecipientEntry> entries,
                                   LinkedHashMap<String, List<RecipientEntry>> entryMap,
                                   List<RecipientEntry> nonAggregatedEntries,
                                   Set<String> existingDestinations) {
            this.entries = entries;
            this.entryMap = entryMap;
            this.nonAggregatedEntries = nonAggregatedEntries;
            this.existingDestinations = existingDestinations;
        }
    }

    /**
     * An asynchronous filter used for loading two data sets: email rows from the local
     * contact provider and the list of {@link ContactsContract.Directory}'s.
     */
    private final class DefaultFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            Log.d(TAG, "DefaultFilter ----> start filtering. constraint: " + constraint + ", thread:" + Thread.currentThread());

            final FilterResults results = new FilterResults();
            Cursor contactsCursor = null;
            Cursor emailContactCursor = null;

            if (TextUtils.isEmpty(constraint)) {
                return results;
            }

            try {
                contactsCursor = doQuery(constraint, 5, null/*4L*/);
                emailContactCursor = doEmailContactQuery(constraint, 6);

                if (contactsCursor == null && emailContactCursor == null) {
                    Log.w(TAG, "null cursor returned for default Email filter query.");
                } else {

                    final LinkedHashMap<String, List<RecipientEntry>> entryMap = new LinkedHashMap<>();
                    final List<RecipientEntry> nonAggregatedEntries = new ArrayList<>();
                    final Set<String> existingDestinations = new HashSet<>();

                    TemporaryEntry entry;
                    final String filterQuery = constraint.toString().toLowerCase();
                    if (contactsCursor != null) {
                        while (contactsCursor.moveToNext()) {
                            entry = new TemporaryEntry(contactsCursor, null);
                            if (!TextUtils.isEmpty(entry.destination) && entry.destination.toLowerCase().contains(filterQuery)
                                    || (!TextUtils.isEmpty(entry.displayName) && entry.displayName.toLowerCase().contains(filterQuery))) {
                                putOneEntry(entry, true, entryMap, nonAggregatedEntries, existingDestinations);
                            }
                        }
                    }

                    if (emailContactCursor != null) {
                        while (emailContactCursor.moveToNext()) {
                            entry = new TemporaryEntry(emailContactCursor);
                            if (entry.displayName.toLowerCase().contains(filterQuery) || entry.destination.toLowerCase().contains(filterQuery)) {
                                putOneEntry(entry, true, entryMap, nonAggregatedEntries, existingDestinations);
                            }
                        }
                    }
                    final List<RecipientEntry> entries = constructEntryList(entryMap);

                    results.values = new DefaultFilterResult(entries, entryMap, nonAggregatedEntries, existingDestinations);
                    results.count = 1;
                }
            } finally {
                if (contactsCursor != null) {
                    contactsCursor.close();
                }
                if(emailContactCursor != null) {
                    emailContactCursor.close();
                }
            }
            return results;
        }

        @Override
        protected void publishResults(final CharSequence constraint, FilterResults results) {
            if (results.values != null) {
                DefaultFilterResult defaultFilterResult = (DefaultFilterResult) results.values;
                updateEntries(defaultFilterResult.entries);
            } else {
                updateEntries(Collections.<RecipientEntry>emptyList());
            }
        }

        @Override
        public CharSequence convertResultToString(Object resultValue) {
            final RecipientEntry entry = (RecipientEntry)resultValue;
            final String displayName = entry.getDisplayName();
            final String emailAddress = entry.getDestination();
            if (TextUtils.isEmpty(displayName) || TextUtils.equals(displayName, emailAddress)) {
                return emailAddress;
            } else {
                return new Rfc822Token(displayName, emailAddress, null).toString();
            }
        }
    }


    private Cursor doQuery(CharSequence constraint, int limit, Long directoryId) {
        final Uri.Builder builder = ContactsContract.CommonDataKinds.Email.CONTENT_FILTER_URI.buildUpon()
                .appendPath(constraint.toString())
                .appendQueryParameter(ContactsContract.LIMIT_PARAM_KEY, String.valueOf(limit + 5));
        if (directoryId != null) {
            builder.appendQueryParameter(ContactsContract.DIRECTORY_PARAM_KEY, String.valueOf(directoryId));
        }
        builder.appendQueryParameter("name_for_primary_account", "jiangxianrong@le.com");
        builder.appendQueryParameter("type_for_primary_account", "com.android.exchange");
        final long start = System.currentTimeMillis();
        final Cursor cursor = mContentResolver.query(builder.build(), new String[] {"display_name", "data1", "lookup"}, null, null, null);
        final long end = System.currentTimeMillis();

        Log.d(TAG, "Time for autocomplete (query: " + constraint
                + ", directoryId: " + directoryId + ", num_of_results: "
                + (cursor != null ? cursor.getCount() : "null") + "): "
                + (end - start) + " ms" + ", url: " + builder.build());

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String displayName = cursor.getString(0);
                String destination = cursor.getString(1);
                Log.i(TAG, "doQuery.. displayName: " + displayName + ", destination: " + destination);
            }
            cursor.moveToPosition(-1);
        }

        return cursor;
    }

    private Cursor doEmailContactQuery(CharSequence constraint, int limit) {
        final Uri.Builder builder = Uri.parse("content://com.android.email.provider/emailcontact").buildUpon()
                .appendQueryParameter("filter-param-key", constraint.toString())
                .appendQueryParameter("limit-param-key", String.valueOf(limit));

        final long start = System.currentTimeMillis();
        final Cursor cursor = mContentResolver.query(builder.build(), new String[] {"fullName", "email"}, null, null, null);
        final long end = System.currentTimeMillis();

        Log.d(TAG, "Time for doEmailContactQuery (query: " + constraint
                + ", limit: " + limit + ", num_of_results: "
                + (cursor != null ? cursor.getCount() : "null") + "): "
                + (end - start) + " ms" + ", url: " + builder.build());

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String fullName = cursor.getString(cursor.getColumnIndex("fullName"));
                Log.i(TAG, "doEmailContactQuery.. fullName: " + fullName);
            }
            cursor.moveToPosition(-1);
        }
        return cursor;
    }

    private static void putOneEntry(TemporaryEntry entry, boolean isAggregatedEntry,
                                    LinkedHashMap<String, List<RecipientEntry>> entryMap,
                                    List<RecipientEntry> nonAggregatedEntries,
                                    Set<String> existingDestinations) {
        if (existingDestinations.contains(entry.destination)) {
            return;
        }

        existingDestinations.add(entry.destination);

        if (!isAggregatedEntry) {
            nonAggregatedEntries.add(RecipientEntry.constructTopLevelEntry(
                    entry.displayName, 0,
                    entry.destination, 0,
                    null, 0,
                    entry.directoryId, 0,
                    Uri.EMPTY, true, entry.lookupKey));
        } else if (entryMap.containsKey(entry.destination)) {
            final List<RecipientEntry> entryList = entryMap.get(entry.destination);
            entryList.add(RecipientEntry.constructSecondLevelEntry(
                    entry.displayName, 0,
                    entry.destination, 0,
                    null, 0,
                    entry.directoryId, 0,
                    null, true, entry.lookupKey));
        } else {
            final List<RecipientEntry> entryList = new ArrayList<>();
            entryList.add(RecipientEntry.constructTopLevelEntry(
                    entry.displayName, 0,
                    entry.destination, 0,
                    null, 0,
                    entry.directoryId, 0,
                    Uri.EMPTY, true, entry.lookupKey));
            entryMap.put(entry.destination, entryList);
        }
    }

    private List<RecipientEntry> constructEntryList(LinkedHashMap<String, List<RecipientEntry>> entryMap) {
        final List<RecipientEntry> entries = new ArrayList<>();
        for (Map.Entry<String, List<RecipientEntry>> mapEntry : entryMap.entrySet()) {
            final List<RecipientEntry> entryList = mapEntry.getValue();
            for (RecipientEntry entry : entryList) {
                entries.add(entry);
            }
        }
        return entries;
    }

    protected void updateEntries(List<RecipientEntry> newEntries) {
        mEntries = newEntries;
        mEntriesUpdatedObserver.onChanged(newEntries);
        mDataSetObservable.notifyChanged();
    }






    /////////////////////////////** 以下为接口所要实现的方法 **///////////////////////////////
    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return getEntries().get(position).isSelectable();
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
        mDataSetObservable.registerObserver(observer);
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
        mDataSetObservable.unregisterObserver(observer);
    }

    @Override
    public int getCount() {
        final List<RecipientEntry> entries = getEntries();
        return entries != null ? entries.size() : 0;
    }

    @Override
    public Object getItem(int position) {
        return getEntries().get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        RecipientEntry entry = mEntries.get(position);
        String displayName = entry.getDisplayName();
        final View itemView = convertView != null ? convertView : mInflater.inflate(R.layout.chips_autocomplete_recipient_dropdown_item, parent, false);
        new ViewHolder(itemView).displayNameView.setText(displayName);
        return itemView;
    }

    /**
     * A holder class the view. Uses the getters in DropdownChipLayouter to find the id of the
     * corresponding views.
     */
    protected class ViewHolder {
        public final TextView displayNameView;
        public ViewHolder(View view) {
            displayNameView = (TextView) view.findViewById(android.R.id.title);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return getEntries().get(position).getEntryType();
    }

    @Override
    public int getViewTypeCount() {
        return RecipientEntry.ENTRY_TYPE_SIZE;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }
    ////////////////////////////////////////////////////////////
}
