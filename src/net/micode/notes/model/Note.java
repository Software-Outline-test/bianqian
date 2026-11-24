/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.model;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;
import net.micode.notes.tool.SafeLog;

import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.CallNote;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.Notes.TextNote;

import java.util.ArrayList;


public class Note {
    private ContentValues mNoteDiffValues;
    private NoteData mNoteData;
    private static final String TAG = "Note";
    /**
     * Create a new note id for adding a new note to databases
     */
    public static synchronized long getNewNoteId(Context context, long folderId) {
        // Create a new note in the database
        ContentValues values = new ContentValues();
        long createdTime = System.currentTimeMillis();
        values.put(NoteColumns.CREATED_DATE, createdTime);
        values.put(NoteColumns.MODIFIED_DATE, createdTime);
        values.put(NoteColumns.TYPE, Notes.TYPE_NOTE);
        values.put(NoteColumns.LOCAL_MODIFIED, 1);
        values.put(NoteColumns.PARENT_ID, folderId);
        try {
            Uri uri = context.getContentResolver().insert(Notes.CONTENT_NOTE_URI, values);
            if (uri != null) {
                long noteId = ContentUris.parseId(uri);
                if (noteId > 0) {
                    return noteId;
                }
            }
        } catch (RuntimeException e) {
            SafeLog.error(TAG, "Create note failed", e);
        }
        Log.e(TAG, "Could not create note");
        return 0;
    }

    public Note() {
        mNoteDiffValues = new ContentValues();
        mNoteData = new NoteData();
    }

    public void setNoteValue(String key, String value) {
        mNoteDiffValues.put(key, value);
        mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
        mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
    }

    public void setTextData(String key, String value) {
        mNoteData.setTextData(key, value);
    }

    public void setTextDataId(long id) {
        mNoteData.setTextDataId(id);
    }

    public long getTextDataId() {
        return mNoteData.mTextDataId;
    }

    public void setCallDataId(long id) {
        mNoteData.setCallDataId(id);
    }

    public void setCallData(String key, String value) {
        mNoteData.setCallData(key, value);
    }

    public boolean isLocalModified() {
        return mNoteDiffValues.size() > 0 || mNoteData.isLocalModified();
    }

    public boolean syncNote(Context context, long noteId) {
        if (noteId <= 0) {
            throw new IllegalArgumentException("Wrong note id:" + noteId);
        }

        if (!isLocalModified()) {
            return true;
        }

        /**
         * In theory, once data changed, the note should be updated on {@link NoteColumns#LOCAL_MODIFIED} and
         * {@link NoteColumns#MODIFIED_DATE}. For data safety, though update note fails, we also update the
         * note data info
         */
        try {
            if (context.getContentResolver().update(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), mNoteDiffValues,
                    null, null) != 1) {
                Log.e(TAG, "Could not update note");
                return false;
            }
            if (mNoteData.isLocalModified()
                    && mNoteData.pushIntoContentResolver(context, noteId) == null) {
                return false;
            }
        } catch (RuntimeException e) {
            SafeLog.error(TAG, "Save failed", e);
            return false;
        }

        mNoteDiffValues.clear();
        return true;
    }

    private class NoteData {
        private long mTextDataId;

        private ContentValues mTextDataValues;

        private long mCallDataId;

        private ContentValues mCallDataValues;

        private static final String TAG = "NoteData";

        public NoteData() {
            mTextDataValues = new ContentValues();
            mCallDataValues = new ContentValues();
            mTextDataId = 0;
            mCallDataId = 0;
        }

        boolean isLocalModified() {
            return mTextDataValues.size() > 0 || mCallDataValues.size() > 0;
        }

        void setTextDataId(long id) {
            if(id <= 0) {
                throw new IllegalArgumentException("Text data id should larger than 0");
            }
            mTextDataId = id;
        }

        void setCallDataId(long id) {
            if (id <= 0) {
                throw new IllegalArgumentException("Call data id should larger than 0");
            }
            mCallDataId = id;
        }

        void setCallData(String key, String value) {
            mCallDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        void setTextData(String key, String value) {
            mTextDataValues.put(key, value);
            mNoteDiffValues.put(NoteColumns.LOCAL_MODIFIED, 1);
            mNoteDiffValues.put(NoteColumns.MODIFIED_DATE, System.currentTimeMillis());
        }

        Uri pushIntoContentResolver(Context context, long noteId) {
            /**
             * Check for safety
             */
            if (noteId <= 0) {
                throw new IllegalArgumentException("Wrong note id:" + noteId);
            }

            ArrayList<ContentProviderOperation> operations = new ArrayList<ContentProviderOperation>();
            int textIndex = -1;
            int callIndex = -1;
            if (mTextDataValues.size() > 0) {
                ContentValues values = new ContentValues(mTextDataValues);
                values.put(DataColumns.NOTE_ID, noteId);
                textIndex = operations.size();
                if (mTextDataId == 0) {
                    values.put(DataColumns.MIME_TYPE, TextNote.CONTENT_ITEM_TYPE);
                    operations.add(ContentProviderOperation.newInsert(Notes.CONTENT_DATA_URI)
                            .withValues(values).build());
                } else {
                    operations.add(ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mTextDataId)).withValues(values)
                            .withExpectedCount(1).build());
                }
            }
            if (mCallDataValues.size() > 0) {
                ContentValues values = new ContentValues(mCallDataValues);
                values.put(DataColumns.NOTE_ID, noteId);
                callIndex = operations.size();
                if (mCallDataId == 0) {
                    values.put(DataColumns.MIME_TYPE, CallNote.CONTENT_ITEM_TYPE);
                    operations.add(ContentProviderOperation.newInsert(Notes.CONTENT_DATA_URI)
                            .withValues(values).build());
                } else {
                    operations.add(ContentProviderOperation.newUpdate(ContentUris.withAppendedId(
                            Notes.CONTENT_DATA_URI, mCallDataId)).withValues(values)
                            .withExpectedCount(1).build());
                }
            }
            try {
                ContentProviderResult[] results = context.getContentResolver().applyBatch(
                        Notes.AUTHORITY, operations);
                if (results == null || results.length != operations.size()) {
                    return null;
                }
                if (textIndex >= 0) {
                    if (results[textIndex] == null || (mTextDataId == 0
                            ? results[textIndex].uri == null : results[textIndex].count == null
                            || results[textIndex].count != 1)) {
                        return null;
                    }
                }
                if (callIndex >= 0) {
                    if (results[callIndex] == null || (mCallDataId == 0
                            ? results[callIndex].uri == null : results[callIndex].count == null
                            || results[callIndex].count != 1)) {
                        return null;
                    }
                }
                if (textIndex >= 0) {
                    if (mTextDataId == 0) {
                        mTextDataId = ContentUris.parseId(results[textIndex].uri);
                    }
                    mTextDataValues.clear();
                }
                if (callIndex >= 0) {
                    if (mCallDataId == 0) {
                        mCallDataId = ContentUris.parseId(results[callIndex].uri);
                    }
                    mCallDataValues.clear();
                }
                return ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId);
            } catch (RemoteException e) {
                SafeLog.error(TAG, "Could not save note data", e);
            } catch (OperationApplicationException e) {
                SafeLog.error(TAG, "Could not save note data", e);
            }
            return null;
        }
    }
}
