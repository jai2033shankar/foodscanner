/*
* FoodScanner: A free and open Food Analyzer (nutritional facts, allergens and chemicals)
*
* FoodScanner is a first-of-a-kind food analyzer offering valuable 
* information such as nutritional facts, allergens and 
* chemicals, about foods using ordinary smartphones.
*
* Authors: D. Stefanidis
* 
* Supervisor: Demetrios Zeinalipour-Yazti
*
* URL: http://foodscanner.cs.ucy.ac.cy
* Contact: foodscanner@cs.ucy.ac.cy
*
* Copyright (c) 2016, Data Management Systems Lab (DMSL), University of Cyprus.
* All rights reserved.
*
* Permission is hereby granted, free of charge, to any person obtaining a copy of
* this software and associated documentation files (the "Software"), to deal in the
* Software without restriction, including without limitation the rights to use, copy,
* modify, merge, publish, distribute, sublicense, and/or sell copies of the Software,
* and to permit persons to whom the Software is furnished to do so, subject to the
* following conditions:
*
* The above copyright notice and this permission notice shall be included in all
* copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
* OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
* FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
* AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
* LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER
* DEALINGS IN THE SOFTWARE.
*
*/

package com.dmsl.FoodScanner;

import com.googlecode.leptonica.android.Pix;
import com.dmsl.documentview.DocumentActivity;
import com.dmsl.FoodScanner.DocumentContentProvider.Columns;
import com.dmsl.FoodScanner.cropimage.CropImageActivity;
import com.dmsl.FoodScanner.cropimage.MonitoredActivity;

import android.annotation.TargetApi;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.accessibility.AccessibilityManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class BaseDocumentActivitiy extends MonitoredActivity {

    private final static String LOG_TAG = BaseDocumentActivitiy.class.getSimpleName();
    public final static String EXTRA_NATIVE_PIX = "pix_pointer";
    public final static String EXTRA_IMAGE_URI = "image_uri";
    public final static String EXTRA_ROTATION = "rotation";
    private final static String IMAGE_LOAD_PROGRESS_TAG = "image_load_progress";


    private static final int PDF_PROGRESS_DIALOG_ID = 0;
    private static final int DELETE_PROGRESS_DIALOG_ID = 1;
    protected static final int HINT_DIALOG_ID = 2;
    private static final int EDIT_TITLE_DIALOG_ID = 3;

    private static final String DIALOG_ARG_MAX = "max";
    private static final String DIALOG_ARG_MESSAGE = "message";
    private static final String DIALOG_ARG_PROGRESS = "progress";
    private static final String DIALOG_ARG_SECONDARY_PROGRESS = "secondary_progress";
    private static final String DIALOG_ARG_TITLE = "title";
    private static final String DIALOG_ARG_DOCUMENT_URI = "document_uri";

    private final static int REQUEST_CODE_MAKE_PHOTO = 0;
    private final static int REQUEST_CODE_PICK_PHOTO = 10;
    final static int REQUEST_CODE_CROP_PHOTO = 2;
    protected final static int REQUEST_CODE_OCR = 3;

    private static final String DATE_CAMERA_INTENT_STARTED_STATE = "com.dmsl.FoodScanner.android.photo.TakePhotoActivity.dateCameraIntentStarted";
    private static final String STATE_RECEIVER_REGISTERED = "state_receiver_registered";
    private static final String IMAGE_SOURCE = "image_source";
    private static Date dateCameraIntentStarted = null;
    private static final String CAMERA_PIC_URI_STATE = "com.dmsl.FoodScanner.android.photo.TakePhotoActivity.CAMERA_PIC_URI_STATE";
    private static Uri cameraPicUri = null;
    private static final String ROTATE_X_DEGREES_STATE = "com.dmsl.FoodScanner.android.photo.TakePhotoActivity.ROTATE_X_DEGREES_STATE";
    private static int rotateXDegrees = 0;
    private boolean mReceiverRegistered = false;
    private ImageSource mImageSource = ImageSource.CAMERA;


    private static class CameraResult {
        public CameraResult(int requestCode, int resultCode, Intent data, ImageSource source) {
            mRequestCode = requestCode;
            mResultCode = resultCode;
            mData = data;
            mSource = source;
        }

        private int mRequestCode;
        private int mResultCode;
        private Intent mData;
        private final ImageSource mSource;
    }

    protected abstract int getParentId();

    private ProgressDialog pdfProgressDialog;
    private ProgressDialog deleteProgressDialog;
    private AsyncTask<Void, Void, Pair<Pix, PixLoadStatus>> mBitmapLoadTask;
    private CameraResult mCameraResult;

    protected void startGallery() {
        cameraPicUri = null;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT, null);
        if (Build.VERSION.SDK_INT >= 19) {
            i.setType("image/*");
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/png", "image/jpg", "image/jpeg"});
        } else {
            i.setType("image/png,image/jpg, image/jpeg");
        }

        Intent chooser = Intent.createChooser(i, getString(R.string.image_source));
        startActivityForResult(chooser, REQUEST_CODE_PICK_PHOTO);
    }

    protected void startCamera() {
        try {
            cameraPicUri = null;
            dateCameraIntentStarted = new Date();
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            // Create an image file name
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";

            File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
            File image = null;
            try {
                if (!storageDir.exists()) {
                    storageDir.mkdirs();
                }
                image = new File(storageDir, imageFileName + ".jpg");
                if (image.exists()) {
                    image.createNewFile();
                }
                cameraPicUri = Uri.fromFile(image);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPicUri);
                startActivityForResult(intent, REQUEST_CODE_MAKE_PHOTO);
            } catch (IOException e) {
                showFileError(PixLoadStatus.IO_ERROR);
            }

        } catch (ActivityNotFoundException e) {
            showFileError(PixLoadStatus.CAMERA_APP_NOT_FOUND);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        Log.i(LOG_TAG, "onSaveInstanceState" + this);
        //remember to register the receiver again in #onRestoreInstanceState
        savedInstanceState.putBoolean(STATE_RECEIVER_REGISTERED, mReceiverRegistered);
        unRegisterImageLoadedReceiver();
        //unregister receiver before onSaveInstanceState is called!
        super.onSaveInstanceState(savedInstanceState);
        if (dateCameraIntentStarted != null) {
            savedInstanceState.putLong(DATE_CAMERA_INTENT_STARTED_STATE, dateCameraIntentStarted.getTime());
        }
        if (cameraPicUri != null) {
            savedInstanceState.putString(CAMERA_PIC_URI_STATE, cameraPicUri.toString());
        }
        savedInstanceState.putInt(IMAGE_SOURCE, mImageSource.ordinal());

        savedInstanceState.putInt(ROTATE_X_DEGREES_STATE, rotateXDegrees);

    }

    @TargetApi(11)
    @Override
    protected synchronized void onDestroy() {
        super.onDestroy();
        //cancel loading of image if the activity is destroyed for good
        if (android.os.Build.VERSION.SDK_INT >= 11 && !isChangingConfigurations() && mBitmapLoadTask != null) {
            mBitmapLoadTask.cancel(false);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        Log.i(LOG_TAG, "onRestoreInstanceState " + this);
        super.onRestoreInstanceState(savedInstanceState);

        if (savedInstanceState.containsKey(DATE_CAMERA_INTENT_STARTED_STATE)) {
            dateCameraIntentStarted = new Date(savedInstanceState.getLong(DATE_CAMERA_INTENT_STARTED_STATE));
        }
        if (savedInstanceState.containsKey(CAMERA_PIC_URI_STATE)) {
            cameraPicUri = Uri.parse(savedInstanceState.getString(CAMERA_PIC_URI_STATE));
        }
        rotateXDegrees = savedInstanceState.getInt(ROTATE_X_DEGREES_STATE);

        if (savedInstanceState.getBoolean(STATE_RECEIVER_REGISTERED)) {
            registerImageLoaderReceiver();
        }
        final int index = savedInstanceState.getInt(IMAGE_SOURCE);
        mImageSource = ImageSource.values()[index];
    }


    private void onTakePhotoActivityResult(CameraResult cameraResult) {
        if (cameraResult.mResultCode == RESULT_OK) {
            rotateXDegrees = -1;
            if (cameraResult.mRequestCode == REQUEST_CODE_MAKE_PHOTO) {
                Cursor myCursor = null;
                Date dateOfPicture = null;
                //check if there is a file at the uri we specified
                if (cameraPicUri != null) {
                    File f = new File(cameraPicUri.getPath());
                    if (f.isFile() && f.exists() && f.canRead()) {
                        //all is well
                        Log.i(LOG_TAG, "onTakePhotoActivityResult");
                        loadBitmapFromContentUri(cameraPicUri, ImageSource.CAMERA);
                        return;
                    }

                }
                //try to look up the image by querying the media content provider
                try {
                    // Create a Cursor to obtain the file Path for the large
                    // image
                    String[] largeFileProjection = {MediaStore.Images.ImageColumns._ID, MediaStore.Images.ImageColumns.DATA, MediaStore.Images.ImageColumns.ORIENTATION, MediaStore.Images.ImageColumns.DATE_TAKEN};
                    String largeFileSort = MediaStore.Images.ImageColumns._ID + " DESC";
                    myCursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, largeFileProjection, null, null, largeFileSort);
                    myCursor.moveToFirst();
                    // This will actually give you the file path location of the
                    // image.
                    String largeImagePath = myCursor.getString(myCursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATA));
                    Uri tempCameraPicUri = Uri.fromFile(new File(largeImagePath));
                    dateOfPicture = new Date(myCursor.getLong(myCursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.DATE_TAKEN)));
                    if (dateOfPicture.getTime() == 0 || (dateOfPicture.after(dateCameraIntentStarted))) {
                        cameraPicUri = tempCameraPicUri;
                        rotateXDegrees = myCursor.getInt(myCursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns.ORIENTATION));
                    }
                } catch (Exception e) {
                } finally {
                    if (myCursor != null) {
                        myCursor.close();
                    }
                }
            }

            if (cameraPicUri == null) {
                try {
                    cameraPicUri = mCameraResult.mData.getData();
                } catch (Exception e) {
                    showFileError(PixLoadStatus.CAMERA_APP_ERROR);
                }
            }

            if (cameraPicUri != null) {
                loadBitmapFromContentUri(cameraPicUri, mCameraResult.mSource);
            } else {
                showFileError(PixLoadStatus.CAMERA_NO_IMAGE_RETURNED);
            }
        }
    }

    protected void loadBitmapFromContentUri(final Uri cameraPicUri, ImageSource source) {
        mImageSource = source;
        if (mBitmapLoadTask != null) {
            mBitmapLoadTask.cancel(true);
        }
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        boolean isAccessibilityEnabled = am.isEnabled();
        boolean isExploreByTouchEnabled = AccessibilityManagerCompat.isTouchExplorationEnabled(am);
        final boolean skipCrop = isExploreByTouchEnabled && isAccessibilityEnabled;

        registerImageLoaderReceiver();
        mBitmapLoadTask = new ImageLoadAsyncTask(this, skipCrop, rotateXDegrees, cameraPicUri).execute();

    }

    private synchronized void unRegisterImageLoadedReceiver() {
        if (mReceiverRegistered) {
            Log.i(LOG_TAG, "unRegisterImageLoadedReceiver " + mMessageReceiver);
            LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
            mReceiverRegistered = false;
        }
    }


    private synchronized void registerImageLoaderReceiver() {
        if (!mReceiverRegistered) {
            Log.i(LOG_TAG, "registerImageLoaderReceiver " + mMessageReceiver);
            final IntentFilter intentFilter = new IntentFilter(ImageLoadAsyncTask.ACTION_IMAGE_LOADED);
            intentFilter.addAction(ImageLoadAsyncTask.ACTION_IMAGE_LOADING_START);
            LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver, intentFilter);
            mReceiverRegistered = true;
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (RESULT_OK == resultCode) {
            switch (requestCode) {
                case REQUEST_CODE_CROP_PHOTO: {
                    long nativePix = data.getLongExtra(EXTRA_NATIVE_PIX, 0);
                    startOcrActivity(nativePix, false);
                    break;
                }
                case REQUEST_CODE_MAKE_PHOTO:
                    mCameraResult = new CameraResult(requestCode, resultCode, data, ImageSource.CAMERA);
                    break;
                case REQUEST_CODE_PICK_PHOTO:
                    mCameraResult = new CameraResult(requestCode, resultCode, data, ImageSource.PICK);
                    break;
            }
        } else if (CropImageActivity.RESULT_NEW_IMAGE == resultCode) {
            switch (mImageSource) {
                case PICK:
                    startGallery();
                    break;
                case INTENT:
                    break;
                case CAMERA:
                    startCamera();
                    break;
            }

        }
    }

    void startOcrActivity(long nativePix, boolean accessibilityMode) {
        Intent intent = new Intent(this, OCRActivity.class);
        intent.putExtra(EXTRA_NATIVE_PIX, nativePix);
        intent.putExtra(OCRActivity.EXTRA_USE_ACCESSIBILITY_MODE, accessibilityMode);
        intent.putExtra(OCRActivity.EXTRA_PARENT_DOCUMENT_ID, getParentId());
        startActivityForResult(intent, REQUEST_CODE_OCR);
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        if (mCameraResult != null) {
            onTakePhotoActivityResult(mCameraResult);
            mCameraResult = null;
        }
    }

    // handler for received Intents for the image loaded event
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //I get quite a number of crash reports here saying that I cannot show a dialog after onSaveInstanceState.
            //However the broadcastReceiver gets unregistered in onSaveInstanceState before i call super().
            //As a workaround I check for the flag if the receiver is registered
            //Additionally i use commitAllowStateLoss as its not terribly important to preserve the state of the loading dialog
            if (mReceiverRegistered) {
                Log.i(LOG_TAG, "onReceive " + BaseDocumentActivitiy.this);
                if (intent.getAction().equalsIgnoreCase(ImageLoadAsyncTask.ACTION_IMAGE_LOADED)) {
                    unRegisterImageLoadedReceiver();
                    final long nativePix = intent.getLongExtra(ImageLoadAsyncTask.EXTRA_PIX, 0);
                    final int statusNumber = intent.getIntExtra(ImageLoadAsyncTask.EXTRA_STATUS, PixLoadStatus.SUCCESS.ordinal());
                    final boolean skipCrop = intent.getBooleanExtra(ImageLoadAsyncTask.EXTRA_SKIP_CROP, false);
                    handleLoadedImage(nativePix, PixLoadStatus.values()[statusNumber], skipCrop);
                } else if (intent.getAction().equalsIgnoreCase(ImageLoadAsyncTask.ACTION_IMAGE_LOADING_START)) {
                    showLoadingImageProgressDialog();
                }
            }
        }
    };

    private void handleLoadedImage(long nativePix, PixLoadStatus pixLoadStatus, boolean skipCrop) {
        PixLoadStatus status = pixLoadStatus;
        dismissLoadingImageProgressDialog();

        if (status == PixLoadStatus.SUCCESS) {
            if (skipCrop) {
                startOcrActivity(nativePix, true);
            } else {
                Intent actionIntent = new Intent(this, CropImageActivity.class);
                actionIntent.putExtra(BaseDocumentActivitiy.EXTRA_NATIVE_PIX, nativePix);
                actionIntent.putExtra(BaseDocumentActivitiy.EXTRA_ROTATION, rotateXDegrees);
                startActivityForResult(actionIntent, BaseDocumentActivitiy.REQUEST_CODE_CROP_PHOTO);
            }
        } else {
            showFileError(status);
        }
    }

    private void dismissLoadingImageProgressDialog() {
        Fragment prev = getSupportFragmentManager().findFragmentByTag(IMAGE_LOAD_PROGRESS_TAG);
        if (prev != null) {
            Log.i(LOG_TAG, "dismissing dialog");
            DialogFragment df = (DialogFragment) prev;
            df.dismissAllowingStateLoss();
        } else {
            Log.i(LOG_TAG, "cannot dismiss dialog. its null! " + this);
        }
    }

    private void showLoadingImageProgressDialog() {
        Log.i(LOG_TAG, "showLoadingImageProgressDialog");
        //dialog.show(getSupportFragmentManager(), null);
        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        final ProgressDialogFragment dialog = ProgressDialogFragment.newInstance(R.string.please_wait, R.string.loading_image);
        ft.add(dialog, IMAGE_LOAD_PROGRESS_TAG);
        ft.commitAllowingStateLoss();
    }

    void showFileError(PixLoadStatus status) {
        showFileError(status, null);
    }

    protected void showFileError(PixLoadStatus second, OnClickListener positiveListener) {
        int textId;
        boolean flag = true;
        switch (second) {
            case IMAGE_NOT_32_BIT:
                textId = R.string.image_not_32_bit;
                break;
            case IMAGE_FORMAT_UNSUPPORTED:
                textId = R.string.image_format_unsupported;
                break;
            case IMAGE_COULD_NOT_BE_READ:
                textId = R.string.image_could_not_be_read;
                break;
            case IMAGE_DOES_NOT_EXIST:
                textId = R.string.image_does_not_exist;
                break;
            case IO_ERROR:
                textId = R.string.gallery_io_error;
                break;
            case CAMERA_APP_NOT_FOUND:
                textId = R.string.camera_app_not_found;
                break;
            case MEDIA_STORE_RETURNED_NULL:
                textId = R.string.media_store_returned_null;
                break;
            case CAMERA_APP_ERROR:
                textId = R.string.camera_app_error;
                break;
            case CAMERA_NO_IMAGE_RETURNED:
                flag = false;
                textId = R.string.camera_no_image_returned;
                break;
            default:
                textId = R.string.error_could_not_take_photo;
        }

        if(flag){
            AlertDialog.Builder alert = new AlertDialog.Builder(this);
            alert.setTitle(R.string.error_title);
            final TextView textview = new TextView(this);
            textview.setText(textId);
            alert.setView(textview);
            alert.setPositiveButton(android.R.string.ok, positiveListener);
            alert.show();
        }
    }

    @Override
    protected Dialog onCreateDialog(int id, Bundle args) {
        switch (id) {

            case PDF_PROGRESS_DIALOG_ID:
                int max = args.getInt(DIALOG_ARG_MAX);
                String message = args.getString(DIALOG_ARG_MESSAGE);
                String title = args.getString(DIALOG_ARG_TITLE);
                pdfProgressDialog = new ProgressDialog(this);
                pdfProgressDialog.setMessage(message);
                pdfProgressDialog.setTitle(title);
                pdfProgressDialog.setIndeterminate(false);
                pdfProgressDialog.setMax(max);
                pdfProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                pdfProgressDialog.setCancelable(false);
                return pdfProgressDialog;
            case DELETE_PROGRESS_DIALOG_ID:
                max = args.getInt(DIALOG_ARG_MAX);
                message = args.getString(DIALOG_ARG_MESSAGE);
                deleteProgressDialog = new ProgressDialog(this);
                deleteProgressDialog.setMessage(message);
                deleteProgressDialog.setIndeterminate(false);
                deleteProgressDialog.setMax(max);
                deleteProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                deleteProgressDialog.setCancelable(false);
                return deleteProgressDialog;
            case EDIT_TITLE_DIALOG_ID:
                View layout = getLayoutInflater().inflate(R.layout.edit_title_dialog, null);
                final Uri documentUri = Uri.parse(args.getString(DIALOG_ARG_DOCUMENT_URI));
                final String oldTitle = args.getString(DIALOG_ARG_TITLE);
                final EditText edit = (EditText) layout.findViewById(R.id.edit_title);
                edit.setText(oldTitle);

                AlertDialog.Builder builder = new Builder(this);
                builder.setView(layout);
                builder.setTitle(R.string.edit_dialog_title);
                //builder.setIcon(R.drawable.fairy_showing);
                builder.setPositiveButton(android.R.string.ok, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final String title = edit.getText().toString();
                        saveTitle(title, documentUri);

                    }
                });
                builder.setNegativeButton(R.string.cancel, new OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                builder.show();
        }
        return super.onCreateDialog(id, args);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
        switch (id) {
            case EDIT_TITLE_DIALOG_ID:
                final Uri documentUri = Uri.parse(args.getString(DIALOG_ARG_DOCUMENT_URI));
                final String oldTitle = args.getString(DIALOG_ARG_TITLE);
                final EditText edit = (EditText) dialog.findViewById(R.id.edit_title);
                edit.setText(oldTitle);
                AlertDialog alertDialog = (AlertDialog) dialog;
                Button okButton = alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                okButton.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        final String title = edit.getText().toString();
                        saveTitle(title, documentUri);
                    }
                });
                break;
            case HINT_DIALOG_ID:
                break;
            default:
                if (args != null) {
                    final int max = args.getInt(DIALOG_ARG_MAX);
                    final int progress = args.getInt(DIALOG_ARG_PROGRESS);
                    // final int secondaryProgress =
                    // args.getInt(DIALOG_ARG_SECONDARY_PROGRESS);
                    final String message = args.getString(DIALOG_ARG_MESSAGE);
                    final String title = args.getString(DIALOG_ARG_TITLE);
                    if (id == PDF_PROGRESS_DIALOG_ID) {
                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                pdfProgressDialog.setProgress(progress);
                                pdfProgressDialog.setMax(max);
                                if (message != null) {
                                    pdfProgressDialog.setMessage(message);
                                }
                                if (title != null) {
                                    pdfProgressDialog.setTitle(title);
                                }
                            }
                        });

                    } else if (id == DELETE_PROGRESS_DIALOG_ID) {
                        runOnUiThread(new Runnable() {

                            @Override
                            public void run() {
                                deleteProgressDialog.setProgress(progress);
                                deleteProgressDialog.setMax(max);
                                if (message != null) {
                                    deleteProgressDialog.setMessage(message);
                                }
                            }
                        });
                    }
                }
        }
        super.onPrepareDialog(id, dialog, args);
    }

    protected void askUserForNewTitle(final String oldTitle, final Uri documentUri) {
        Bundle bundle = new Bundle(2);
        bundle.putString(DIALOG_ARG_TITLE, oldTitle);
        bundle.putString(DIALOG_ARG_DOCUMENT_URI, documentUri.toString());
        showDialog(EDIT_TITLE_DIALOG_ID, bundle);
    }

    private void saveTitle(final String newTitle, final Uri documentUri) {
        Uri uri = documentUri;
        if (uri == null) {
            uri = getIntent().getData();
        }
        if (uri != null) {
            SaveDocumentTask saveTask = new SaveDocumentTask(this, documentUri, newTitle);
            saveTask.execute();
        }

    }



    protected class DeleteDocumentTask extends AsyncTask<Void, Void, Integer> {
        Set<Integer> mIds = new HashSet<Integer>();
        private final static int RESULT_REMOTE_EXCEPTION = -1;
        final boolean mFinishActivity;

        public DeleteDocumentTask(Set<Integer> parentDocumentIds, final boolean finishActivityAfterExecution) {
            mIds.addAll(parentDocumentIds);
            mFinishActivity = finishActivityAfterExecution;
        }

        @Override
        protected void onPreExecute() {
            Bundle args = new Bundle(2);
            args.putInt(DIALOG_ARG_MAX, mIds.size());
            String message = getText(R.string.delete_dialog_message).toString();
            args.putString(DIALOG_ARG_MESSAGE, message);
            showDialog(DELETE_PROGRESS_DIALOG_ID, args);
            super.onPreExecute();
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == RESULT_REMOTE_EXCEPTION) {
                Toast.makeText(getApplicationContext(), getText(R.string.delete_error), Toast.LENGTH_LONG).show();
            }
            dismissDialog(DELETE_PROGRESS_DIALOG_ID);
            super.onPostExecute(result);
            if (mFinishActivity) {
                finish();
            }
        }

        private int deleteDocument(Cursor c, ContentProviderClient client) throws RemoteException {
            int index = c.getColumnIndex(Columns.ID);
            int currentId = c.getInt(index);
            Uri currentDocumentUri = Uri.withAppendedPath(DocumentContentProvider.CONTENT_URI, String.valueOf(currentId));
            index = c.getColumnIndex(Columns.PHOTO_PATH);
            String imagePath = c.getString(index);
            if (imagePath != null) {
                new File(imagePath).delete();
            }
            return client.delete(currentDocumentUri, null, null);
        }

        @Override
        protected Integer doInBackground(Void... params) {

            ContentProviderClient client = getContentResolver().acquireContentProviderClient(DocumentContentProvider.CONTENT_URI);

            int count = 0;
            int progress = 0;
            for (Integer id : mIds) {
                try {
                    Cursor c = client.query(DocumentContentProvider.CONTENT_URI, new String[]{Columns.ID, Columns.PHOTO_PATH}, Columns.PARENT_ID + "=? OR " + Columns.ID + "=?",
                            new String[]{String.valueOf(id), String.valueOf(id)}, Columns.PARENT_ID + " ASC");

                    while (c.moveToNext()) {
                        count += deleteDocument(c, client);
                    }

                } catch (RemoteException exc) {
                    return RESULT_REMOTE_EXCEPTION;
                }
                deleteProgressDialog.setProgress(++progress);
            }
            return count;
        }
    }

    public static class SaveDocumentTask extends AsyncTask<Void, Integer, Integer> {

        private final Context mContext;
        private ContentValues values = new ContentValues();
        private ArrayList<Uri> mDocumentUri = new ArrayList<>();
        private String mTitle;
        private ArrayList<Spanned> mOcrText = new ArrayList<>();
        private Toast mSaveToast;

        public SaveDocumentTask(Context context, List<Uri> documentUri, List<Spanned> ocrText) {
            mContext = context;
            this.mDocumentUri.addAll(documentUri);
            this.mTitle = null;
            this.mOcrText.addAll(ocrText);
        }

        public SaveDocumentTask(Context context, Uri documentUri, String title) {
            mContext = context;
            this.mDocumentUri.add(documentUri);
            this.mTitle = title;
        }

        @Override
        protected void onPreExecute() {
            mSaveToast = Toast.makeText(mContext, mContext.getText(R.string.saving_document), Toast.LENGTH_LONG);
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result != null && result > 0) {
                mSaveToast.setText(R.string.save_success);
            } else {
                mSaveToast.setText(R.string.save_fail);
            }
        }

        @Override
        protected Integer doInBackground(Void... params) {
            mSaveToast.show();
            int result = 0;
            for (int i = 0; i < mDocumentUri.size(); i++) {
                values.clear();
                Uri uri = mDocumentUri.get(i);
                if (mOcrText != null && i < mOcrText.size()) {
                    final String text = Html.toHtml(mOcrText.get(i));
                    values.put(Columns.OCR_TEXT, text);

                }
                if (mTitle != null) {
                    values.put(Columns.TITLE, mTitle);
                }
                publishProgress(i);
                result += mContext.getContentResolver().update(uri, values, null, null);
            }
            return result;
        }
    }

}
