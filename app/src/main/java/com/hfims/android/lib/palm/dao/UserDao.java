package com.hfims.android.lib.palm.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.hfims.android.lib.palm.model.User;

import java.util.ArrayList;
import java.util.List;

public class UserDao extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "palm_users.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_USERS = "users";
    
    private static final String COLUMN_ID = "id";
    private static final String COLUMN_NAME = "name";
    private static final String COLUMN_USER_ID = "user_id";
    private static final String COLUMN_PALM_FEATURE = "palm_feature";
    private static final String COLUMN_CREATED_AT = "created_at";

    public UserDao(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_USERS + " (" +
                COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_NAME + " TEXT NOT NULL, " +
                COLUMN_USER_ID + " TEXT UNIQUE NOT NULL, " +
                COLUMN_PALM_FEATURE + " TEXT, " +
                COLUMN_CREATED_AT + " INTEGER NOT NULL" +
                ")";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    public long insertUser(User user) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NAME, user.getName());
        values.put(COLUMN_USER_ID, user.getUserId());
        values.put(COLUMN_PALM_FEATURE, user.getPalmFeature());
        values.put(COLUMN_CREATED_AT, user.getCreatedAt());

        long id = db.insert(TABLE_USERS, null, values);
        db.close();
        return id;
    }

    public User getUserById(String userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_ID, COLUMN_NAME, COLUMN_USER_ID, COLUMN_PALM_FEATURE, COLUMN_CREATED_AT};
        String selection = COLUMN_USER_ID + " = ?";
        String[] selectionArgs = {userId};

        Cursor cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);
        User user = null;

        if (cursor.moveToFirst()) {
            user = new User();
            user.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
            user.setName(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)));
            user.setUserId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)));
            user.setPalmFeature(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PALM_FEATURE)));
            user.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)));
        }

        cursor.close();
        db.close();
        return user;
    }

    public User getUserByPalmFeature(String palmFeature) {
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_ID, COLUMN_NAME, COLUMN_USER_ID, COLUMN_PALM_FEATURE, COLUMN_CREATED_AT};
        String selection = COLUMN_PALM_FEATURE + " = ?";
        String[] selectionArgs = {palmFeature};

        Cursor cursor = db.query(TABLE_USERS, columns, selection, selectionArgs, null, null, null);
        User user = null;

        if (cursor.moveToFirst()) {
            user = new User();
            user.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
            user.setName(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)));
            user.setUserId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)));
            user.setPalmFeature(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PALM_FEATURE)));
            user.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)));
        }

        cursor.close();
        db.close();
        return user;
    }

    public List<User> getAllUsers() {
        List<User> userList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {COLUMN_ID, COLUMN_NAME, COLUMN_USER_ID, COLUMN_PALM_FEATURE, COLUMN_CREATED_AT};

        Cursor cursor = db.query(TABLE_USERS, columns, null, null, null, null, COLUMN_CREATED_AT + " DESC");

        if (cursor.moveToFirst()) {
            do {
                User user = new User();
                user.setId(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)));
                user.setName(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NAME)));
                user.setUserId(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_USER_ID)));
                user.setPalmFeature(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_PALM_FEATURE)));
                user.setCreatedAt(cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT)));
                userList.add(user);
            } while (cursor.moveToNext());
        }

        cursor.close();
        db.close();
        return userList;
    }

    public boolean updateUserPalmFeature(String userId, String palmFeature) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_PALM_FEATURE, palmFeature);

        String selection = COLUMN_USER_ID + " = ?";
        String[] selectionArgs = {userId};

        int rowsAffected = db.update(TABLE_USERS, values, selection, selectionArgs);
        db.close();
        return rowsAffected > 0;
    }

    public boolean deleteUser(String userId) {
        SQLiteDatabase db = this.getWritableDatabase();
        String selection = COLUMN_USER_ID + " = ?";
        String[] selectionArgs = {userId};

        int rowsAffected = db.delete(TABLE_USERS, selection, selectionArgs);
        db.close();
        return rowsAffected > 0;
    }

    public void clearAllUsers() {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_USERS, null, null);
        db.close();
    }
}
