package com.github.barteksc.pdfviewer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.github.barteksc.pdfviewer.model.SearchRecord;
import com.github.barteksc.pdfviewer.model.SearchRecordItem;
import com.github.barteksc.pdfviewer.util.Util;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * A View to paint PDF selections, [magnifier] and search highlights
 */
public class PDocSelection extends View {
    public boolean supressRecalcInval;
    PDFView pDocView;
    float drawableWidth = 60;
    float drawableHeight = 30;
    float drawableDeltaW = drawableWidth / 4;
    Paint rectPaint;
    Paint rectFramePaint;
    Paint rectHighlightPaint;

    /**
     * Small Canvas for magnifier.
     * {@link Canvas#clipPath ClipPath} fails if the canvas it too high. ( will never happen in this project. )
     * see <a href="https://issuetracker.google.com/issues/132402784">issuetracker</a>)
     */
    Canvas cc;
    Bitmap PageCache;
    BitmapDrawable PageCacheDrawable;

    Path magClipper;
    RectF magClipperR;
    float magFactor = 1.5f;
    int magW = 560;
    int magH = 280;
    /**
     * output image
     */
    Drawable frameDrawable;
    private float framew;
    private final PointF vCursorPos = new PointF();

    private final RectF tmpPosRct = new RectF();


    //public PDocPageResultsProvider searchCtx;

    public PDocSelection(Context context) {
        super(context);
        init();
    }

    public PDocSelection(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PDocSelection(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        rectPaint = new Paint();
        rectPaint.setColor(ContextCompat.getColor(getContext(), R.color.selection_color));
        //rectPaint.setColor(0xffffff00);
        rectHighlightPaint = new Paint();
        rectHighlightPaint.setColor(ContextCompat.getColor(getContext(), R.color.highlight_color));
        rectPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DARKEN));
        rectHighlightPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DARKEN));
        rectFramePaint = new Paint();
        rectFramePaint.setColor(0xccc7ab21);
        rectFramePaint.setStyle(Paint.Style.STROKE);
        rectFramePaint.setStrokeWidth(0.5f);
    }


    private void initMagnifier() {
        //setLayerType(LAYER_TYPE_NONE,null);
        cc = new Canvas(PageCache = Bitmap.createBitmap(magW, magH, Bitmap.Config.ARGB_8888));
        PageCacheDrawable = new BitmapDrawable(getResources(), PageCache);
        frameDrawable = getResources().getDrawable(R.drawable.frame);
        framew = getResources().getDimension(R.dimen.framew);
        magClipper = new Path();
        magClipperR = new RectF(PageCacheDrawable.getBounds());
        magClipper.reset();
        magClipperR.set(0, 0, magW, magH);
        magClipper.addRoundRect(magClipperR, framew + 5, framew + 5, Path.Direction.CW);
    }

    int rectPoolSize = 0;

    ArrayList<ArrayList<RectF>> rectPool = new ArrayList<>();

    ArrayList<RectF> magSelBucket = new ArrayList<>();

    public void resetSel() {
        //  CMN.Log("resetSel", pDocView.selPageSt, pDocView.selPageEd, pDocView.selStart, pDocView.selEnd);

        if (pDocView != null && pDocView.pdfFile != null && pDocView.hasSelection) {
            // FIXED: Properly swap page indices if needed
            boolean b1 = pDocView.selPageEd < pDocView.selPageSt;
            if (b1) {
                int temp = pDocView.selPageEd;
                pDocView.selPageEd = pDocView.selPageSt;
                pDocView.selPageSt = temp;
            }
            
            // FIXED: Properly swap selection indices if needed
            if (b1 || (pDocView.selPageEd == pDocView.selPageSt && pDocView.selEnd < pDocView.selStart)) {
                int temp = pDocView.selStart;
                pDocView.selStart = pDocView.selEnd;
                pDocView.selEnd = temp;
            }
            
            int pageCount = pDocView.selPageEd - pDocView.selPageSt;
            int sz = rectPool.size();
            ArrayList<RectF> rectPagePool;
            for (int i = 0; i <= pageCount; i++) {
                if (i >= sz) {
                    rectPool.add(rectPagePool = new ArrayList<>());
                } else {
                    rectPagePool = rectPool.get(i);
                }
                int selSt = i == 0 ? pDocView.selStart : 0;
                int selEd = i == pageCount ? pDocView.selEnd : -1;
                // PDocument.PDocPage page = pDocView.pdfFile.mPDocPages[selPageSt + i];

                pDocView.dragPinchManager.getSelRects(rectPagePool, selSt, selEd);//+10
            }
            recalcHandles();
            rectPoolSize = pageCount + 1;
        } else {
            rectPoolSize = 0;
        }
        if (!supressRecalcInval) {
            invalidate();
        }
    }

    public void recalcHandles() {
        PDFView page = pDocView;
        
        int st = pDocView.selStart;
        int ed = pDocView.selEnd;
        int dir = pDocView.selPageEd - pDocView.selPageSt;
        dir = (int) Math.signum(dir == 0 ? ed - st : dir);
        if (dir != 0) {
            // FIXED: Prepare text for the start page of the selection
            long tidStart = page.dragPinchManager.prepareText(pDocView.selPageSt);
            if (pDocView.isNotCurrentPage(tidStart)) {
                return;
            }
            
            String atext = page.dragPinchManager.allText;
            int len = atext.length();
            if (st >= 0 && st < len) {
                char c;
                while (((c = atext.charAt(st)) == '\r' || c == '\n') && st + dir >= 0 && st + dir < len) {
                    st += dir;
                }
            }
            page.getCharPos(pDocView.handleLeftPos, st, pDocView.selPageSt);
            pDocView.lineHeightLeft = pDocView.handleLeftPos.height() / 2;
            page.getCharLoosePos(pDocView.handleLeftPos, st, pDocView.selPageSt);

            // FIXED: Prepare text for the end page of the selection
            page = pDocView;
            long tidEnd = page.dragPinchManager.prepareText(pDocView.selPageEd);
            if (pDocView.isNotCurrentPage(tidEnd)) {
                return;
            }
            atext = page.dragPinchManager.allText;
            len = atext.length();
            int delta = -1;
            if (ed >= 0 && ed < len) {
                char c;
                dir *= -1;
                while (((c = atext.charAt(ed)) == '\r' || c == '\n') && ed + dir >= 0 && ed + dir < len) {
                    delta = 0;
                    ed += dir;
                }
            }//"RectF(373.0, 405.0, 556.0, 434.0)"
            //CMN.Log("getCharPos", page.allText.substring(ed+delta, ed+delta+1));
            page.getCharPos(pDocView.handleRightPos, ed + delta, pDocView.selPageEd);
            pDocView.lineHeightRight = pDocView.handleRightPos.height() / 2;
            page.getCharLoosePos(pDocView.handleRightPos, ed + delta, pDocView.selPageEd);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (pDocView == null) {
            return;
        }
        try {
            RectF VR = tmpPosRct;
            Matrix matrix = pDocView.matrix;

            // Draw search highlights for all pages with results
            if (pDocView.isSearching && pDocView.pdfFile != null) {
                drawSearchHighlights(canvas, VR);
            }

            // Draw text selection highlights and handles
            if (pDocView.hasSelection && pDocView.pdfFile != null) {
                drawTextSelection(canvas, VR, matrix);
            }
        } catch (Exception e) {
            Log.e("PDF_TEXT_SELECTION", "onDraw: ", e);
        }
    }

    /**
     * Draw search highlights for all visible pages with search results.
     * Uses a simple, direct coordinate transformation approach.
     */
    private void drawSearchHighlights(Canvas canvas, RectF tempRect) {
        // Get all pages with search results (current page and adjacent pages)
        ArrayList<SearchRecord> searchRecordList = getSearchRecords();
        
        for (SearchRecord record : searchRecordList) {
            if (record != null) {
                // Load the text data for this page if not already loaded
                pDocView.getAllMatchOnPage(record);
                
                int page = record.currentPage != -1 ? record.currentPage : pDocView.currentPage;
                ArrayList<SearchRecordItem> data = (ArrayList<SearchRecordItem>) record.data;
                
                if (data != null) {
                    // Draw highlights for each search result on this page
                    int dataSize = data.size();
                    for (int j = 0; j < dataSize; j++) {
                        try {
                            SearchRecordItem item = data.get(j);
                            if (item == null) continue;
                            
                            RectF[] rects = item.rects;
                            if (rects != null) {
                                // Draw each highlight rectangle
                                for (RectF sourceRect : rects) {
                                    // Convert PDF coordinates to view coordinates
                                    pDocView.sourceToViewRectFFSearch(sourceRect, tempRect, page);
                                    
                                    // Draw the highlight directly - no complex matrix transformation needed
                                    canvas.drawRect(tempRect, rectHighlightPaint);
                                }
                            }
                        } catch (IndexOutOfBoundsException e) {
                            // Handle concurrent modification gracefully
                            Log.e("PDF_TEXT_SELECTION", "Search data modified during rendering", e);
                            break;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Draw text selection highlights and handles.
     * Uses the same matrix transformation as before for consistency.
     */
    private void drawTextSelection(Canvas canvas, RectF VR, Matrix matrix) {
        // Draw left handle
        pDocView.sourceToViewRectFF(pDocView.handleLeftPos, VR);
        float left = VR.left + drawableDeltaW;
        pDocView.handleLeft.setBounds((int) (left - drawableWidth), (int) VR.bottom, (int) left, (int) (VR.bottom + drawableHeight));
        pDocView.handleLeft.draw(canvas);
        
        // Draw right handle
        pDocView.sourceToViewRectFF(pDocView.handleRightPos, VR);
        left = VR.right - drawableDeltaW;
        pDocView.handleRight.setBounds((int) left, (int) VR.bottom, (int) (left + drawableWidth), (int) (VR.bottom + drawableHeight));
        pDocView.handleRight.draw(canvas);
        
        pDocView.sourceToViewCoord(pDocView.sCursorPos, vCursorPos);
        
        // Draw selection highlight rectangles
        for (int i = 0; i < rectPoolSize; i++) {
            ArrayList<RectF> rectPage = rectPool.get(i);
            for (RectF rI : rectPage) {
                pDocView.sourceToViewRectFF(rI, VR);
                matrix.reset();
                int bmWidth = (int) rI.width();
                int bmHeight = (int) rI.height();
                pDocView.setMatrixArray(pDocView.srcArray, 0, 0, bmWidth, 0, bmWidth, bmHeight, 0, bmHeight);
                pDocView.setMatrixArray(pDocView.dstArray, VR.left, VR.top, VR.right, VR.top, VR.right, VR.bottom, VR.left, VR.bottom);
                
                matrix.setPolyToPoly(pDocView.srcArray, 0, pDocView.dstArray, 0, 4);
                matrix.postRotate(0, pDocView.getScreenWidth(), pDocView.getScreenHeight());
                
                canvas.save();
                canvas.concat(matrix);
                VR.set(0, 0, bmWidth, bmHeight);
                canvas.drawRect(VR, rectPaint);
                canvas.restore();
            }
        }
    }

    /**
     * To draw search result after and before current page
     **/
    private ArrayList<SearchRecord> getSearchRecords() {
        ArrayList<SearchRecord> list = new ArrayList<>();
        int currentPage = pDocView.getCurrentPage();
        if (Util.indexExists(pDocView.getPageCount(), currentPage - 1)) {
            int index = currentPage - 1;

            if (pDocView.searchRecords.containsKey(index)) {
                SearchRecord searchRecordPrev = pDocView.searchRecords.get(index);
                if (searchRecordPrev != null)
                    searchRecordPrev.currentPage = index;
                list.add(searchRecordPrev);
            }
        }
        list.add(pDocView.searchRecords.get(currentPage));

        if (Util.indexExists(pDocView.getPageCount(), currentPage + 1)) {
            int indexNext = currentPage + 1;
            if (pDocView.searchRecords.containsKey(indexNext)) {
                SearchRecord searchRecordNext = pDocView.searchRecords.get(indexNext);
                if (searchRecordNext != null)
                    searchRecordNext.currentPage = indexNext;
                list.add(pDocView.searchRecords.get(indexNext));
            }
        }


        return list;
    }


}
