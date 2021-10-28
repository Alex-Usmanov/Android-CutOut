package com.github.gabrielbb.cutout;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Pair;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.UUID;

import static android.view.View.VISIBLE;

class SaveDrawingTask extends AsyncTask<Bitmap, Void, Pair<File, Exception>> {

    private static final String SAVED_IMAGE_FORMAT = "png";
    private static final String SAVED_IMAGE_NAME = "cutout_tmp";

    private final WeakReference<CutOutActivity> activityWeakReference;

    SaveDrawingTask(CutOutActivity activity) {
        this.activityWeakReference = new WeakReference<>(activity);
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        activityWeakReference.get().loadingModal.setVisibility(VISIBLE);
    }

    @Override
    protected Pair<File, Exception> doInBackground(Bitmap... bitmaps) {
        try {
            File file = File.createTempFile(SAVED_IMAGE_NAME, SAVED_IMAGE_FORMAT, activityWeakReference.get().getApplicationContext().getCacheDir());

            try (FileOutputStream out = new FileOutputStream(file)) {
                Bitmap bitmap = bitmaps[0];
                bitmap = removeAlphaChannel(bitmap);
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out);
                return new Pair<>(file, null);
            }
        } catch (IOException e) {
            return new Pair<>(null, e);
        }
    }

    protected void onPostExecute(Pair<File, Exception> result) {
        super.onPostExecute(result);

        Intent resultIntent = new Intent();

        if (result.first != null) {
            Uri uri = Uri.fromFile(result.first);

            resultIntent.putExtra(CutOut.CUTOUT_EXTRA_RESULT, uri);
            activityWeakReference.get().setResult(Activity.RESULT_OK, resultIntent);
            activityWeakReference.get().finish();

        } else {
            activityWeakReference.get().exitWithError(result.second);
        }
    }


    public Bitmap removeAlphaChannel(Bitmap bitmap) {
        Bitmap bmp = bitmap;
        int imgHeight = bmp.getHeight();
        int imgWidth  = bmp.getWidth();
        int smallX = 0, largeX = imgWidth;
        int smallY = 0, largeY = imgHeight;
        int left = imgWidth, right = imgWidth, top = imgHeight, bottom = imgHeight;

        for(int x=0; x < imgWidth; x++) {
            for(int y=0; y < imgHeight; y++) {
                if (bmp.getPixel(x, y) != Color.TRANSPARENT) {
                    if ((x - smallX) < left) {
                        left = x - smallX;
                    }

                    if ((largeX-x) < right) {
                        right = largeX-x;
                    }

                    if ((y-smallY) < top) {
                        top = y-smallY;
                    }

                    if ((largeY-y) < bottom) {
                        bottom = largeY-y;
                    }
                }
            }
        }

        bmp = Bitmap.createBitmap(bmp,left,top,imgWidth-left-right, imgHeight-top-bottom);
        return bmp;
    }
}