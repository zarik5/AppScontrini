package com.ing.software.ocr;

import android.graphics.RectF;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Size;
import android.util.Log;
import android.util.SizeF;

import com.ing.software.ocr.OcrObjects.OcrText;
import com.ing.software.ocr.OperativeObjects.RawImage;


/**
 * Utility class. These methods can't be moved to common utils as they use module-specific classes.
 */
public class OcrUtils {

    public static final boolean IS_DEBUG_ENABLED = false;
    private static final int LOG_LEVEL = 3; //A Higher level, means more things are logged

    /**
     * @author Michelon
     * Logs messages only if debug variable is enabled
     * @param level int >= 0, only messages with level <= LOG_LEVEL are logged
     * @param tag tag of the message to log, must be less than 24 chars long
     * @param message message to log
     */
    public static void log(@IntRange(from = 0) int level, @Size(max = 23) String tag, String message) {
        if (IS_DEBUG_ENABLED && level <= LOG_LEVEL)
            Log.d(tag, message);
    }

    /**
     * List blocks parsed in debug log
     * @param rawImage image containing blocks
     */
    public static void listEverything(@NonNull RawImage rawImage) {
            OcrUtils.log(2, "LIST EVERYTHING", "###########################\nINTRODUCTION");
            for (OcrText text : rawImage.getIntroTexts()) {
                OcrUtils.log(2, "introduction", text.text());
            }
            OcrUtils.log(2, "LIST EVERYTHING", "###########################\nPRODUCTS");
            for (OcrText text : rawImage.getProductsTexts()) {
                OcrUtils.log(2, "products", text.text());
            }
            OcrUtils.log(2, "LIST EVERYTHING", "###########################\nPRICES");
            for (OcrText text : rawImage.getPricesTexts()) {
                OcrUtils.log(2, "prices", text.text());
            }
            OcrUtils.log(2, "LIST EVERYTHING", "###########################\nCONCLUSION");
            for (OcrText text : rawImage.getConclusionTexts()) {
                OcrUtils.log(2, "conclusion", text.text());
            }
            OcrUtils.log(2, "LIST EVERYTHING", "###########################");
        }

    /**
     * Extract size of rawImage
     * @param image source rawimage. Not null.
     * @return size of source rawimage
     */
    static SizeF imageToSize(RawImage image) {
        return new SizeF(image.getWidth(), image.getHeight());
    }

    /**
     * @author Michelon
     * Create a new rect extending source rect with chosen percentage (on width and height of chosen rect)
     * Or extending of chosen pixels on both sides (if param is negative)
     * Note: Min value for top and left is 0
     * @param rect source rect. Not null
     * @param percentHeight chosen percentage for height. or pixels if negative int
     * @param percentWidth chosen percentage for width. or pixels if negative int
     * @return new extended rectangle
     */
    public static RectF extendRect(@NonNull RectF rect, int percentHeight, int percentWidth) {
        float top;
        float bottom;
        float right;
        float left;
        if (percentHeight > 0) {
            float extendedHeight = rect.height() * percentHeight / 100;
            top = rect.top - extendedHeight / 2;
            bottom = rect.bottom + extendedHeight/2;
        } else {
            top = rect.top - Math.abs(percentHeight);
            bottom = rect.bottom + Math.abs(percentHeight);
        }
        if (percentWidth > 0) {
            float extendedWidth = rect.width() * percentWidth / 100;
            left = rect.left - extendedWidth / 2;
            right = rect.right + extendedWidth/2;
        } else {
            left = rect.left - Math.abs(percentWidth);
            right = rect.right + Math.abs(percentWidth);
        }
        if (left<0)
            left = 0;
        if (top < 0)
            top = 0;
        //Doesn't matter if bottom and right are outside the photo
        return new RectF(left, top, right, bottom);
    }
}
