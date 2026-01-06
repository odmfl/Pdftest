/**
 * Copyright 2016 Bartosz Schiller
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.barteksc.sample;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.barteksc.pdfviewer.PDFView;
import com.github.barteksc.pdfviewer.PDocSelection;
import com.github.barteksc.pdfviewer.listener.OnLoadCompleteListener;
import com.github.barteksc.pdfviewer.listener.OnPageChangeListener;
import com.github.barteksc.pdfviewer.listener.OnPageErrorListener;
import com.github.barteksc.pdfviewer.scroll.DefaultScrollHandle;
import com.shockwave.pdfium.PdfDocument;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.NonConfigurationInstance;
import org.androidannotations.annotations.OnActivityResult;
import org.androidannotations.annotations.ViewById;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

@SuppressLint("NonConstantResourceId")
@EActivity(R.layout.activity_main)

public class PDFViewActivity extends AppCompatActivity implements OnPageChangeListener, OnLoadCompleteListener, SearchView.OnQueryTextListener
        , OnPageErrorListener {

    private static final String TAG = PDFViewActivity.class.getSimpleName();

    private final static int REQUEST_CODE = 42;
    public static final int PERMISSION_CODE = 42042;
    
    /** Maximum length for debug log preview of copied text */
    private static final int DEBUG_PREVIEW_LENGTH = 50;

    public static final String SAMPLE_FILE = "sample.pdf";
    public static final String READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";

    @ViewById
    PDFView pdfView;
    @ViewById
    PDocSelection sv;

    @ViewById
    LinearLayout search_controller;
    @ViewById
    ImageButton prev;
    @ViewById
    ImageButton next;
    @ViewById
    TextView search_result_count;

    @ViewById
    LinearLayout copy_controller;
    @ViewById
    Button copy_button;

    @NonConfigurationInstance
    Uri uri;

    @NonConfigurationInstance
    Integer pageNumber = 0;

    String pdfFileName;

    int searchPage = -1;
    int currentResultIndex = 0;
    List<Integer> searchResultPages = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }


    void pickFile() {
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                READ_EXTERNAL_STORAGE);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{READ_EXTERNAL_STORAGE},
                    PERMISSION_CODE
            );

            return;
        }

        launchPicker();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.options, menu);

        MenuItem searchItem = menu.findItem(R.id.search);

        SearchView searchView = (SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(this);

        searchView.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
                pdfView.clearSearch();
                searchPage = -1;
                currentResultIndex = 0;
                searchResultPages.clear();
                search_controller.setVisibility(View.GONE);
                return false;
            }
        });

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId_ = item.getItemId();
        if (itemId_ == R.id.pickFile) {
            pickFile();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    void launchPicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/pdf");
        try {
            startActivityForResult(intent, REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            //alert user that file manager not working
            Toast.makeText(this, R.string.toast_pick_file_error, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Update the search result count display to show current result position
     */
    public void updateSearchResultCount() {
        if (searchResultPages.isEmpty()) {
            search_result_count.setText(getString(R.string.search_no_results));
        } else {
            String resultText = getString(R.string.search_result_format, 
                    currentResultIndex + 1, searchResultPages.size());
            search_result_count.setText(resultText);
        }
    }

    @AfterViews
    void afterViews() {
        // Previous search result button - cycles backwards through search results
        prev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (pdfView.isSearching && !searchResultPages.isEmpty()) {
                    currentResultIndex--;
                    if (currentResultIndex < 0) {
                        currentResultIndex = searchResultPages.size() - 1;
                    }
                    int page = searchResultPages.get(currentResultIndex);
                    pdfView.jumpTo(page);
                    searchPage = page;
                    
                    // Set the active search result for highlighting (always first result on the page for now)
                    pdfView.setActiveSearchResult(page, 0);
                    
                    updateSearchResultCount();
                }
            }
        });
        
        // Next search result button - cycles forward through search results
        next.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (pdfView.isSearching && !searchResultPages.isEmpty()) {
                    currentResultIndex++;
                    if (currentResultIndex >= searchResultPages.size()) {
                        currentResultIndex = 0;
                    }
                    int page = searchResultPages.get(currentResultIndex);
                    pdfView.jumpTo(page);
                    searchPage = page;
                    
                    // Set the active search result for highlighting (always first result on the page for now)
                    pdfView.setActiveSearchResult(page, 0);
                    
                    updateSearchResultCount();
                }
            }
        });
        
        // Listen for search completion to update UI
        pdfView.setOnSearchListener(new PDFView.OnSearchListener() {
            @Override
            public void onSearchCompleted(int resultCount) {
                updateSearchResults();
            }
        });
        pdfView.setSelectionPaintView(sv);
        pdfView.setBackgroundColor(Color.LTGRAY);
        if (uri != null) {
            displayFromUri(uri);
        } else {
            displayFromAsset(SAMPLE_FILE);
        }
        setTitle(pdfFileName);

        pdfView.setOnSelection(new PDFView.OnSelection() {
            @Override
            public void onSelection(boolean hasSelection) {
                if (hasSelection) {
                    setTitle("Select Text");
                    setTitleColor(ContextCompat.getColor(PDFViewActivity.this, android.R.color.holo_blue_bright));
                    // Show copy button when text is selected
                    copy_controller.setVisibility(View.VISIBLE);
                } else {
                    setTitle(pdfFileName);
                    setTitleColor(ContextCompat.getColor(PDFViewActivity.this, android.R.color.white));
                    // Hide copy button when no text is selected
                    copy_controller.setVisibility(View.GONE);
                }
            }
        });

        // Copy button click handler
        copy_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copySelectedText();
            }
        });

        pdfView.setUserTouchCallback(new PDFView.UserTouchCallback() {

            @Override
            public void onDownTouch() {
                Log.d(TAG, "onDownTouch:");
            }

            @Override
            public void onScroll(float distanceX, float distanceY) {
                Log.d(TAG, "onScroll:\n    distanceX =" + " " + distanceX + "\n    distanceY = " + distanceY);
            }

            @Override
            public void onUp() {
                Log.d(TAG, "onUp:");
            }
        });
    }

    private void displayFromAsset(String assetFileName) {
        pdfFileName = assetFileName;

        pdfView.fromAsset(SAMPLE_FILE)
                .defaultPage(pageNumber)
                .onPageChange(this)
                .enableAnnotationRendering(true)
                .onLoad(this)
                .scrollHandle(new DefaultScrollHandle(this))
                .swipeHorizontal(true)
                .pageSnap(true)
                .autoSpacing(true)
                .pageFling(true)
                .spacing(10) // in dp
                .spacingTop(24)
                .onTap(e -> {
                    Toast.makeText(PDFViewActivity.this, "Click", Toast.LENGTH_SHORT).show();
                    return false;
                })
                .spacingBottom(24)
                .onPageError(this)
                .load();

    }


    private void displayFromUri(Uri uri) {
        pdfFileName = getFileName(uri);

        pdfView.fromUri(uri)
                .defaultPage(pageNumber)
                .onPageChange(this)
                .enableAnnotationRendering(true)
                .onLoad(this)
                .scrollHandle(new DefaultScrollHandle(this))
                .swipeHorizontal(true)
                .pageSnap(true)
                .autoSpacing(true)
                .pageFling(true)
                .spacing(10) // in dp
                .spacingTop(24)
                .spacingBottom(24)
                .onPageError(this)
                .load();
    }

    @OnActivityResult(REQUEST_CODE)
    public void onResult(int resultCode, Intent intent) {
        if (resultCode == RESULT_OK) {
            uri = intent.getData();
            displayFromUri(uri);
        }
    }

    @Override
    public void onPageChanged(int page, int pageCount) {
        pageNumber = page;
        setTitle(String.format("%s %s / %s", pdfFileName, page + 1, pageCount));
        // Refresh highlights when page changes
        if (pdfView.isSearching) {
            pdfView.redrawSel();
        }
    }

    public String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }

    @Override
    public void loadComplete(int nbPages) {
        PdfDocument.Meta meta = pdfView.getDocumentMeta();
        Log.e(TAG, "title = " + meta.getTitle());
        Log.e(TAG, "author = " + meta.getAuthor());
        Log.e(TAG, "subject = " + meta.getSubject());
        Log.e(TAG, "keywords = " + meta.getKeywords());
        Log.e(TAG, "creator = " + meta.getCreator());
        Log.e(TAG, "producer = " + meta.getProducer());
        Log.e(TAG, "creationDate = " + meta.getCreationDate());
        Log.e(TAG, "modDate = " + meta.getModDate());

        printBookmarksTree(pdfView.getTableOfContents(), "-");
        
        // Clear search state when new document is loaded
        searchResultPages.clear();
        currentResultIndex = 0;
        searchPage = -1;

    }

    public void printBookmarksTree(List<PdfDocument.Bookmark> tree, String sep) {
        for (PdfDocument.Bookmark b : tree) {

            Log.e(TAG, String.format("%s %s, p %d", sep, b.getTitle(), b.getPageIdx()));

            if (b.hasChildren()) {
                printBookmarksTree(b.getChildren(), sep + "-");
            }
        }
    }

    /**
     * Listener for response to user permission request
     *
     * @param requestCode  Check that permission request code matches
     * @param permissions  Permissions that requested
     * @param grantResults Whether permissions granted
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_CODE) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchPicker();
            }
        }
    }

    @Override
    public void onPageError(int page, Throwable t) {
        Log.e(TAG, "Cannot load page " + page);
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        // Validate search query
        if (s == null || s.trim().isEmpty()) {
            Toast.makeText(PDFViewActivity.this, R.string.search_no_results, Toast.LENGTH_SHORT).show();
            return false;
        }
        
        Toast.makeText(PDFViewActivity.this, R.string.searching, Toast.LENGTH_SHORT).show();
        // Start the search - results will be reported via OnSearchListener
        pdfView.search(s);
        
        return false;
    }
    
    /**
     * Called when search completes via OnSearchListener.
     * Updates the UI to show search results.
     */
    private void updateSearchResults() {
        searchResultPages.clear();
        currentResultIndex = 0;
        
        // Get all pages with search results
        if (pdfView.searchRecords.isEmpty()) {
            search_controller.setVisibility(View.GONE);
            search_result_count.setText("");
            Toast.makeText(PDFViewActivity.this, R.string.search_no_results, Toast.LENGTH_SHORT).show();
        } else {
            // Sort the page numbers
            searchResultPages = new ArrayList<>(pdfView.searchRecords.keySet());
            Collections.sort(searchResultPages);
            
            search_controller.setVisibility(View.VISIBLE);
            
            // Jump to first result and set it as active
            if (!searchResultPages.isEmpty()) {
                searchPage = searchResultPages.get(0);
                pdfView.jumpTo(searchPage);
                
                // Set the first result on the first page as active
                pdfView.setActiveSearchResult(searchPage, 0);
            }
            
            updateSearchResultCount();
            
            String message = getString(R.string.search_results_found, searchResultPages.size());
            Toast.makeText(PDFViewActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onQueryTextChange(String s) {


        return false;
    }

    /**
     * Copy the currently selected text to the system clipboard.
     * Shows a toast message confirming the copy operation with character count.
     */
    private void copySelectedText() {
        try {
            String selectedText = pdfView.getSelection();
            if (selectedText != null && !selectedText.trim().isEmpty()) {
                // Store length once to avoid repeated calls and ensure consistency
                final int textLength = selectedText.length();
                
                // Get the clipboard manager
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                
                // Check if clipboard service is available
                if (clipboard == null) {
                    Toast.makeText(this, R.string.clipboard_unavailable, Toast.LENGTH_SHORT).show();
                    return;
                }
                
                // Create a clip with the selected text
                ClipData clip = ClipData.newPlainText("PDF Text", selectedText);
                
                // Set the clip to the clipboard
                clipboard.setPrimaryClip(clip);
                
                // Show confirmation toast with character count using string resource
                String message = getString(R.string.text_copied_with_count, textLength);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                
                // Log for debugging (only if debug logging is enabled and text has content)
                if (Log.isLoggable(TAG, Log.DEBUG) && textLength > 0) {
                    try {
                        int previewLength = Math.min(DEBUG_PREVIEW_LENGTH, textLength);
                        String preview = selectedText.substring(0, previewLength);
                        String suffix = textLength > DEBUG_PREVIEW_LENGTH ? "..." : "";
                        Log.d(TAG, "Copied text: " + preview + suffix);
                    } catch (StringIndexOutOfBoundsException e) {
                        // Handle rare concurrent modification case
                        Log.d(TAG, "Copied text (length: " + textLength + ")");
                    }
                }
            } else {
                Toast.makeText(this, R.string.no_text_selected, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error copying text", e);
            String errorMessage = getString(R.string.error_copying_text, e.getMessage());
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
        }
    }
}
