package com.rumax.reactnative.pdfviewer;

/*
 * Created by Maksym Rusynyk on 06/03/2018.
 *
 * This source code is licensed under the MIT license
 */

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class PDFView extends RelativeLayout {

    public final static String EVENT_ON_LOAD = "onLoad";
    public final static String EVENT_ON_ERROR = "onError";
    public final static String EVENT_ON_PAGE_CHANGED = "onPageChanged";
    public final static String EVENT_ON_SCROLLED = "onScrolled";
    private ThemedReactContext context;
    private String resource;
    private File downloadedFile;
    private AsyncDownload downloadTask = null;
    private String resourceType = null;
    private boolean sourceChanged = true;
    private ReadableMap urlProps;
    private int fadeInDuration = 0;
    private boolean enableAnnotations = false;

    private ImageView imageViewPdf;
    private TextView pageNumberTextView;
    private Button prevPageButton;
    private Button nextPageButton;
    private int pageIndex = 0;
    private PdfRenderer pdfRenderer;
    private PdfRenderer.Page currentPage;
    private ParcelFileDescriptor parcelFileDescriptor;

    public PDFView(ThemedReactContext context) {
        super(context, null);
        this.context = context;

        init();
    }

    public void loadComplete(int numberOfPages) {
        reactNativeMessageEvent(EVENT_ON_LOAD, null);
    }

    public void onError(Throwable t) {
        reactNativeMessageEvent(EVENT_ON_ERROR, "error: " + t.getMessage());
    }

    @SuppressLint("ResourceType")
    private void init() {
        imageViewPdf = new ImageView(context);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        addView(imageViewPdf, params);

        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setBackgroundColor(Color.BLACK);
        toolbar.getBackground().setAlpha(50);

        prevPageButton = new Button(context);
        prevPageButton.setText("<");
        prevPageButton.setTypeface(null, Typeface.BOLD);
        prevPageButton.setEnabled(false);
        prevPageButton.setBackgroundColor(Color.TRANSPARENT);
        prevPageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                pageIndex--;
                showPage(pageIndex);
            }
        });
        LinearLayout.LayoutParams prevPageButtonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        prevPageButtonParams.weight = 1;
        toolbar.addView(prevPageButton, prevPageButtonParams);

        pageNumberTextView = new TextView(context);
        pageNumberTextView.setTypeface(null, Typeface.BOLD);
        pageNumberTextView.setTextColor(Color.WHITE);
        pageNumberTextView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pageNumberParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        pageNumberParams.weight = 1;
        toolbar.addView(pageNumberTextView, pageNumberParams);

        nextPageButton = new Button(context);
        nextPageButton.setText(">");
        nextPageButton.setTypeface(null, Typeface.BOLD);
        nextPageButton.setTextColor(Color.WHITE);
        nextPageButton.setEnabled(false);
        nextPageButton.setBackgroundColor(Color.TRANSPARENT);
        nextPageButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                pageIndex++;
                showPage(pageIndex);
            }
        });
        LinearLayout.LayoutParams nextPageButtonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        nextPageButtonParams.weight = 1;
        toolbar.addView(nextPageButton, nextPageButtonParams);

        RelativeLayout.LayoutParams toolbarParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        toolbarParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
        toolbarParams.addRule(RelativeLayout.CENTER_HORIZONTAL, RelativeLayout.TRUE);
        addView(toolbar, toolbarParams);
    }

    private void openRenderer(File file) throws IOException {
        parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
        if (parcelFileDescriptor != null) {
            pdfRenderer = new PdfRenderer(parcelFileDescriptor);
        }
    }

    private void closeRenderer() throws IOException {
        if (null != currentPage) {
            currentPage.close();
            currentPage = null;
        }
        pdfRenderer.close();
        parcelFileDescriptor.close();
    }

    private void showPage(int index) {
        if (index < 0 || pdfRenderer.getPageCount() <= index) {
            loadComplete(index);
            return;
        }

        // Make sure to close the current page before opening another one.
        if (null != currentPage) {
            currentPage.close();
        }
        // Use `openPage` to open a specific page in PDF.
        currentPage = pdfRenderer.openPage(index);
        // Important: the destination bitmap must be ARGB (not RGB).
        Bitmap bitmap = Bitmap.createBitmap(currentPage.getWidth(), currentPage.getHeight(),
                Bitmap.Config.ARGB_8888);
        // Here, we render the page onto the Bitmap.
        // To render a portion of the page, use the second and third parameter. Pass nulls to get
        // the default result.
        // Pass either RENDER_MODE_FOR_DISPLAY or RENDER_MODE_FOR_PRINT for the last parameter.
        currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        // We are ready to show the Bitmap to user.
        imageViewPdf.setImageBitmap(bitmap);
        updateUi();
    }

    private void updateUi() {
        int index = currentPage.getIndex();
        int pageCount = pdfRenderer.getPageCount();

        prevPageButton.setEnabled(index > 0);
        prevPageButton.setTextColor(index > 0 ? Color.WHITE : Color.GRAY);
        nextPageButton.setEnabled(index < pageCount - 1);
        nextPageButton.setTextColor(index < pageCount - 1 ? Color.WHITE : Color.GRAY);

        pageNumberTextView.setText((index + 1 ) + " / " + pageCount);

        loadComplete(index);
    }

    private void reactNativeMessageEvent(String eventName, String message) {
        WritableMap event = Arguments.createMap();
        event.putString("message", message);
        reactNativeEvent(eventName, event);
    }

    private void reactNativeEvent(String eventName, WritableMap event) {
        context
                .getJSModule(RCTEventEmitter.class)
                .receiveEvent(this.getId(), eventName, event);
    }

    private void renderFromFile(String filePath) {
        try {
            if (filePath.startsWith("/")) { // absolute path, using FS
                File file = new File(filePath);
                openRenderer(file);
                showPage(pageIndex);
            } else { // from assets
                InputStream asset = context.getAssets().open(filePath);
                File file = new File(context.getCacheDir(), filePath);
                FileOutputStream output = new FileOutputStream(file);
                final byte[] buffer = new byte[1024];
                int size;
                while ((size = asset.read(buffer)) != -1) {
                    output.write(buffer, 0, size);
                }
                asset.close();
                output.close();

                openRenderer(file);
                showPage(pageIndex);
            }
        } catch (IOException e) {
            onError(e);
        }
    }

    private void renderFromUrl() {
        File dir = context.getCacheDir();
        try {
            downloadedFile = File.createTempFile("pdfDocument", "pdf", dir);
        } catch (IOException e) {
            onError(e);
            return;
        }

        downloadTask = new AsyncDownload(context, resource, downloadedFile, urlProps, new AsyncDownload.TaskCompleted() {
            @Override
            public void onComplete(Exception ex) {
                if (ex == null) {
                    renderFromFile(downloadedFile.getAbsolutePath());
                } else {
                    cleanDownloadedFile();
                    onError(ex);
                }
            }
        });
        downloadTask.execute();
    }

    public void render() {
        cleanup();

        if (resource == null) {
            onError(new IOException(Errors.E_NO_RESOURCE.getCode()));
            return;
        }

        if (resourceType == null) {
            onError(new IOException(Errors.E_NO_RESOURCE_TYPE.getCode()));
            return;
        }

        if (!sourceChanged) {
            return;
        }

        switch (resourceType) {
            case "url":
                renderFromUrl();
                break;
            case "file":
                renderFromFile(resource);
                break;
            default:
                onError(new IOException(Errors.E_INVALID_RESOURCE_TYPE.getCode() + resourceType));
                break;
        }
    }

    private void cleanup() {
        if (downloadTask != null) {
            downloadTask.cancel(true);
        }
        cleanDownloadedFile();

        if (currentPage != null) {
            try {
                closeRenderer();
            } catch (IOException ex) {
                // TODO:
            }
        }

        pageIndex = 0;
    }

    private void cleanDownloadedFile() {
        if (downloadedFile != null) {
            if (!downloadedFile.delete()) {
                onError(new IOException(Errors.E_DELETE_FILE.getCode()));
            }
            downloadedFile = null;
        }
    }

    private static boolean isDifferent(String str1, String str2) {
        if (str1 == null || str2 == null) {
            return true;
        }

        return !str1.equals(str2);
    }

    public void setResource(String resource) {
        if (isDifferent(resource, this.resource)) {
            sourceChanged = true;
        }
        this.resource = resource;
    }

    public void setResourceType(String resourceType) {
        if (isDifferent(resourceType, this.resourceType)) {
            sourceChanged = true;
        }
        this.resourceType = resourceType;
    }

    public void onDrop() {
        cleanup();
        sourceChanged = true;
    }

    public void setUrlProps(ReadableMap props) {
        this.urlProps = props;
    }

    public void setFadeInDuration(int duration) {
        this.fadeInDuration = duration;
    }

    public void setEnableAnnotations(boolean enableAnnotations) {
        this.enableAnnotations = enableAnnotations;
    }

    public void reload() {
        sourceChanged = true;
        render();
    }
}
