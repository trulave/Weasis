package org.weasis.core.api.image.cv;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.opencv.core.Core;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.image.util.KernelData;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.PlanarImage;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.util.FileUtil;

public class ImageProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageProcessor.class);

    /**
     * Converts/writes a Mat into a BufferedImage.
     * 
     * @param matrix
     * 
     * @return BufferedImage
     */
    public static BufferedImage toBufferedImage(Mat matrix) {
        if (matrix == null) {
            return null;
        }

        int cols = matrix.cols();
        int rows = matrix.rows();
        int type = matrix.type();
        int elemSize = CvType.ELEM_SIZE(type);
        int channels = CvType.channels(type);
        int bpp = (elemSize * 8) / channels;

        ColorSpace cs;
        WritableRaster raster;
        ComponentColorModel colorModel;
        int dataType = convertToDataType(type);

        switch (channels) {
            case 1:
                cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
                colorModel = new ComponentColorModel(cs, new int[] { bpp }, false, true, Transparency.OPAQUE, dataType);
                raster = colorModel.createCompatibleWritableRaster(cols, rows);
                break;
            case 3:
                cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                colorModel = new ComponentColorModel(cs, new int[] { bpp, bpp, bpp }, false, false, Transparency.OPAQUE,
                    dataType);
                raster = Raster.createInterleavedRaster(dataType, cols, rows, cols * channels, channels,
                    new int[] { 2, 1, 0 }, null);
                break;
            default:
                throw new UnsupportedOperationException(
                    "No implementation to handle " + matrix.channels() + " channels");
        }

        DataBuffer buf = raster.getDataBuffer();

        if (buf instanceof DataBufferByte) {
            matrix.get(0, 0, ((DataBufferByte) buf).getData());
        } else if (buf instanceof DataBufferUShort) {
            matrix.get(0, 0, ((DataBufferUShort) buf).getData());
        } else if (buf instanceof DataBufferShort) {
            matrix.get(0, 0, ((DataBufferShort) buf).getData());
        } else if (buf instanceof DataBufferInt) {
            matrix.get(0, 0, ((DataBufferInt) buf).getData());
        } else if (buf instanceof DataBufferFloat) {
            matrix.get(0, 0, ((DataBufferFloat) buf).getData());
        } else if (buf instanceof DataBufferDouble) {
            matrix.get(0, 0, ((DataBufferDouble) buf).getData());
        }
        return new BufferedImage(colorModel, raster, false, null);

    }

    public static BufferedImage toBufferedImage(PlanarImage matrix) {
        return toBufferedImage(ImageCV.toMat(matrix));
    }

    public static int convertToDataType(int cvType) {
        switch (CvType.depth(cvType)) {
            case CvType.CV_8U:
            case CvType.CV_8S:
                return DataBuffer.TYPE_BYTE;
            case CvType.CV_16U:
                return DataBuffer.TYPE_USHORT;
            case CvType.CV_16S:
                return DataBuffer.TYPE_SHORT;
            case CvType.CV_32S:
                return DataBuffer.TYPE_INT;
            case CvType.CV_32F:
                return DataBuffer.TYPE_FLOAT;
            case CvType.CV_64F:
                return DataBuffer.TYPE_DOUBLE;
            default:
                throw new java.lang.UnsupportedOperationException("Unsupported CvType value: " + cvType);
        }
    }

    public static ImageCV toMat(RenderedImage img) {
        return toMat(img, null);
    }

    public static ImageCV toMat(RenderedImage img, Rectangle region) {
        Raster raster = region == null ? img.getData() : img.getData(region);
        DataBuffer buf = raster.getDataBuffer();
        int[] samples = raster.getSampleModel().getSampleSize();
        int[] offsets;
        if (raster.getSampleModel() instanceof ComponentSampleModel) {
            offsets = ((ComponentSampleModel) raster.getSampleModel()).getBandOffsets();
        } else {
            offsets = new int[samples.length];
            for (int i = 0; i < offsets.length; i++) {
                offsets[i] = i;
            }
        }

        if (ImageToolkit.isBinary(raster.getSampleModel())) {
            ImageCV mat = new ImageCV(raster.getHeight(), raster.getWidth(), CvType.CV_8UC1);
            mat.put(0, 0, ImageToolkit.getUnpackedBinaryData(raster, raster.getBounds()));
            return mat;
        }

        if (buf instanceof DataBufferByte) {
            if (Arrays.equals(offsets, new int[] { 0, 0, 0 })) {
                List<Mat> mv = new ArrayList<>();
                Mat b = new Mat(raster.getHeight(), raster.getWidth(), CvType.CV_8UC1);
                b.put(0, 0, ((DataBufferByte) buf).getData(2));
                mv.add(b);
                Mat g = new Mat(raster.getHeight(), raster.getWidth(), CvType.CV_8UC1);
                b.put(0, 0, ((DataBufferByte) buf).getData(1));
                mv.add(g);
                Mat r = new Mat(raster.getHeight(), raster.getWidth(), CvType.CV_8UC1);
                b.put(0, 0, ((DataBufferByte) buf).getData(0));
                mv.add(r);
                ImageCV dstImg = new ImageCV();
                Core.merge(mv, dstImg);
                return dstImg;
            }

            ImageCV mat = new ImageCV(raster.getHeight(), raster.getWidth(), CvType.CV_8UC(samples.length));
            mat.put(0, 0, ((DataBufferByte) buf).getData());
            if (Arrays.equals(offsets, new int[] { 0, 1, 2 })) {
                ImageCV dstImg = new ImageCV();
                Imgproc.cvtColor(mat, dstImg, Imgproc.COLOR_RGB2BGR);
                return dstImg;
            }
            return mat;
        } else if (buf instanceof DataBufferUShort) {
            ImageCV mat = new ImageCV(raster.getHeight(), raster.getWidth(), CvType.CV_16UC(samples.length));
            mat.put(0, 0, ((DataBufferUShort) buf).getData());
            return mat;
        } else if (buf instanceof DataBufferShort) {
            ImageCV mat = new ImageCV(raster.getHeight(), raster.getWidth(), CvType.CV_16SC(samples.length));
            mat.put(0, 0, ((DataBufferShort) buf).getData());
            return mat;
        } else if (buf instanceof DataBufferInt) {
            ImageCV mat = new ImageCV(raster.getHeight(), raster.getWidth(), CvType.CV_32SC(samples.length));
            mat.put(0, 0, ((DataBufferInt) buf).getData());
            return mat;
        } else if (buf instanceof DataBufferFloat) {
            ImageCV mat = new ImageCV(raster.getHeight(), raster.getWidth(), CvType.CV_32FC(samples.length));
            mat.put(0, 0, ((DataBufferFloat) buf).getData());
            return mat;
        } else if (buf instanceof DataBufferDouble) {
            ImageCV mat = new ImageCV(raster.getHeight(), raster.getWidth(), CvType.CV_64FC(samples.length));
            mat.put(0, 0, ((DataBufferDouble) buf).getData());
            return mat;
        }

        return null;
    }

    public static Rectangle getBounds(PlanarImage img) {
        return new Rectangle(0, 0, img.width(), img.height());
    }

    public static BufferedImage convertTo(RenderedImage src, int imageType) {
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), imageType);
        Graphics2D big = dst.createGraphics();
        try {
            big.drawRenderedImage(src, AffineTransform.getTranslateInstance(0.0, 0.0));
        } finally {
            big.dispose();
        }
        return dst;
    }

    public Mat blur(Mat input, int numberOfTimes) {
        Mat sourceImage;
        Mat destImage = input.clone();
        for (int i = 0; i < numberOfTimes; i++) {
            sourceImage = destImage.clone();
            // Imgproc.blur(sourceImage, destImage, new Size(3.0, 3.0));
            process(sourceImage, destImage, 256);
        }
        return destImage;
    }

    public static ImageCV applyLUT(Mat source, byte[][] lut) {
        Mat srcImg = Objects.requireNonNull(source);
        int lutCh = Objects.requireNonNull(lut).length;
        Mat lutMat;

        if (lutCh > 1) {
            lutMat = new Mat();
            List<Mat> luts = new ArrayList<>(lutCh);
            for (int i = 0; i < lutCh; i++) {
                Mat l = new Mat(1, 256, CvType.CV_8U);
                l.put(0, 0, lut[i]);
                luts.add(l);
            }
            Core.merge(luts, lutMat);
            if (srcImg.channels() < lut.length) {
                Imgproc.cvtColor(srcImg.clone(), srcImg, Imgproc.COLOR_GRAY2BGR);
            }
        } else {
            lutMat = new Mat(1, 256, CvType.CV_8UC1);
            lutMat.put(0, 0, lut[0]);
        }

        ImageCV dstImg = new ImageCV();
        Core.LUT(srcImg, lutMat, dstImg);
        return dstImg;
    }

    public static ImageCV rescaleToByte(Mat source, double alpha, double beta) {
        ImageCV dstImg = new ImageCV();
        Objects.requireNonNull(source).convertTo(dstImg, CvType.CV_8U, alpha, beta);
        return dstImg;
    }

    public static ImageCV invertLUT(ImageCV source) {
        Objects.requireNonNull(source);
        Core.bitwise_not(source, source);
        return source;
    }

    public static ImageCV bitwiseAnd(Mat source, int src2Cst) {
        Objects.requireNonNull(source);
        ImageCV mask = new ImageCV(source.size(), source.type(), new Scalar(src2Cst));
        Core.bitwise_and(source, mask, mask);
        return mask;
    }

    public static ImageCV crop(Mat source, Rectangle area) {
        return ImageCV
            .toImageCV(Objects.requireNonNull(source).submat(new Rect(area.x, area.y, area.width, area.height)));
    }

    public static MinMaxLocResult minMaxLoc(RenderedImage source, Rectangle area) {
        Mat srcImg = toMat(Objects.requireNonNull(source), area);
        return Core.minMaxLoc(srcImg);
    }

    public static List<MatOfPoint> transformShapeToContour(Shape shape, boolean keepImageCoordinates) {
        Rectangle b = shape.getBounds();
        if (keepImageCoordinates) {
            b.x = 0;
            b.y = 0;
        }
        List<MatOfPoint> points = new ArrayList<>();
        List<Point> cvPts = new ArrayList<>();

        PathIterator iterator = new FlatteningPathIterator(shape.getPathIterator(null), 2);
        double[] pts = new double[6];
        MatOfPoint p = null;
        while (!iterator.isDone()) {
            int segType = iterator.currentSegment(pts);
            switch (segType) {
                case PathIterator.SEG_MOVETO:
                    if (p != null) {
                        p.fromArray(cvPts.toArray(new Point[cvPts.size()]));
                        points.add(p);
                    }
                    p = new MatOfPoint();
                    cvPts.add(new Point(pts[0] - b.x, pts[1] - b.y));
                    break;
                case PathIterator.SEG_LINETO:
                case PathIterator.SEG_CLOSE:
                    cvPts.add(new Point(pts[0] - b.x, pts[1] - b.y));
                    break;
                default:
                    break; // should never append with FlatteningPathIterator
            }
            iterator.next();
        }

        if (p != null) {
            p.fromArray(cvPts.toArray(new Point[cvPts.size()]));
            points.add(p);
        }
        return points;
    }

    public static double[][] meanStdDev(Mat source, Shape shape) {
        return meanStdDev(source, shape, null, null);
    }

    public static double[][] meanStdDev(Mat source, Shape shape, Integer paddingValue, Integer paddingLimit) {
        Rectangle b = shape.getBounds();
        Mat srcImg = Objects.requireNonNull(source).submat(new Rect(b.x, b.y, b.width, b.height));
        Mat mask = Mat.zeros(srcImg.size(), CvType.CV_8UC1);
        List<MatOfPoint> pts = transformShapeToContour(shape, false);
        Imgproc.fillPoly(mask, pts, new Scalar(255));

        if (paddingValue != null) {
            if (paddingLimit == null) {
                paddingLimit = paddingValue;
            } else if (paddingLimit < paddingValue) {
                int temp = paddingValue;
                paddingValue = paddingLimit;
                paddingLimit = temp;
            }
            exludePaddingValue(srcImg, mask, paddingValue, paddingLimit);
        }

        // System.out.println(mask.dump());

        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        Core.meanStdDev(srcImg, mean, stddev, mask);

        MinMaxLocResult minMax = Core.minMaxLoc(srcImg, mask);

        double[][] val = new double[4][];
        val[0] = new double[] { minMax.minVal };
        val[1] = new double[] { minMax.maxVal };
        val[2] = mean.toArray();
        val[3] = stddev.toArray();

        return val;
    }

    private static void exludePaddingValue(Mat src, Mat mask, int paddingValue, int paddingLimit) {
        Mat dst = new Mat();
        Core.inRange(src, new Scalar(paddingValue), new Scalar(paddingLimit), dst);
        Core.bitwise_not(dst, dst);
        Core.add(dst, mask, mask);
    }

    public static List<MatOfPoint> findContours(RenderedImage source, Rectangle area) {
        Mat srcImg = toMat(Objects.requireNonNull(source), area);
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierachy = new Mat();
        Imgproc.findContours(srcImg, contours, hierachy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
        return contours;
    }

    public static ImageCV scale(Mat source, Dimension dim) {
        if (Objects.requireNonNull(dim).width < 1 || dim.height < 1) {
            throw new IllegalArgumentException("Unsupported size: " + dim);
        }
        ImageCV dstImg = new ImageCV();
        Imgproc.resize(Objects.requireNonNull(source), dstImg, new Size(dim.getWidth(), dim.getHeight()));
        return dstImg;
    }

    public static ImageCV scale(Mat source, Dimension dim, Integer interpolation) {
        if (interpolation == null || interpolation < Imgproc.INTER_NEAREST || interpolation > Imgproc.INTER_LANCZOS4) {
            return scale(source, dim);
        }
        if (Objects.requireNonNull(dim).width < 1 || dim.height < 1) {
            throw new IllegalArgumentException("Unsupported size: " + dim);
        }
        ImageCV dstImg = new ImageCV();
        Imgproc.resize(Objects.requireNonNull(source), dstImg, new Size(dim.getWidth(), dim.getHeight()), 0, 0,
            interpolation);
        return dstImg;
    }

    public static ImageCV filter(Mat source, KernelData kernel) {
        Objects.requireNonNull(kernel);
        Mat srcImg = Objects.requireNonNull(source);
        Mat k = new Mat(kernel.getHeight(), kernel.getWidth(), CvType.CV_32F);
        k.put(0, 0, kernel.getData());
        ImageCV dstImg = new ImageCV();
        Imgproc.filter2D(srcImg, dstImg, -1, k);
        // TODO improve speed with dedicated call
        // Imgproc.blur(srcImg, dstImg, new Size(3,3));
        return dstImg;
    }

    public static ImageCV combineTwoImages(Mat source, Mat imgOverlay, int transparency) {
        Mat srcImg = Objects.requireNonNull(source);
        Mat src2Img = Objects.requireNonNull(imgOverlay);
        ImageCV dstImg = new ImageCV();
        Core.addWeighted(srcImg, 1.0, src2Img, transparency, 0.0, dstImg);
        return dstImg;
    }

    private static boolean isGray(Color color) {
        int r = color.getRed();
        return r == color.getGreen() && r == color.getBlue();
    }

    public static ImageCV overlay(Mat source, RenderedImage imgOverlay, Color color) {
        ImageCV srcImg = ImageCV.toImageCV(Objects.requireNonNull(source));
        Mat mask = toMat(Objects.requireNonNull(imgOverlay));
        if (isGray(color)) {
            Mat grayImg = new Mat(srcImg.size(), CvType.CV_8UC1, new Scalar(color.getRed()));
            grayImg.copyTo(srcImg, mask);
            return srcImg;
        }
        if (srcImg.channels() < 3) {
            ImageCV dstImg = new ImageCV();
            Imgproc.cvtColor(srcImg, dstImg, Imgproc.COLOR_GRAY2BGR);
            srcImg = dstImg;
        }
        Mat colorImg =
            new Mat(srcImg.size(), CvType.CV_8UC3, new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
        colorImg.copyTo(srcImg, mask);
        return srcImg;
    }

    public static BufferedImage drawShape(RenderedImage source, Shape shape, Color color) {
        Mat srcImg = toMat(Objects.requireNonNull(source));
        List<MatOfPoint> pts = transformShapeToContour(shape, true);
        Imgproc.fillPoly(srcImg, pts, new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
        return toBufferedImage(srcImg);
    }

    public static ImageCV applyShutter(Mat source, Shape shape, Color color) {
        Mat srcImg = Objects.requireNonNull(source);
        Mat mask = Mat.zeros(srcImg.size(), CvType.CV_8UC1);
        List<MatOfPoint> pts = transformShapeToContour(shape, true);
        Imgproc.fillPoly(mask, pts, new Scalar(1));
        ImageCV dstImg =
            new ImageCV(srcImg.size(), srcImg.type(), new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
        srcImg.copyTo(dstImg, mask);
        return dstImg;
    }

    public static ImageCV applyShutter(Mat source, RenderedImage imgOverlay, Color color) {
        ImageCV srcImg = ImageCV.toImageCV(Objects.requireNonNull(source));
        Mat mask = toMat(Objects.requireNonNull(imgOverlay));
        if (isGray(color)) {
            Mat grayImg = new Mat(srcImg.size(), CvType.CV_8UC1, new Scalar(color.getRed()));
            grayImg.copyTo(srcImg, mask);
            return srcImg;
        }

        if (srcImg.channels() < 3) {
            ImageCV dstImg = new ImageCV();
            Imgproc.cvtColor(srcImg, dstImg, Imgproc.COLOR_GRAY2BGR);
            srcImg = dstImg;
        }
        Mat colorImg =
            new Mat(srcImg.size(), CvType.CV_8UC3, new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
        colorImg.copyTo(srcImg, mask);
        return srcImg;
    }

    public static BufferedImage getAsImage(Area shape, RenderedImage source) {
        SampleModel sm =
            new MultiPixelPackedSampleModel(DataBuffer.TYPE_BYTE, source.getWidth(), source.getHeight(), 1);
        BufferedImage ti = new BufferedImage(source.getWidth(), source.getHeight(), sm.getDataType());
        Graphics2D g2d = ti.createGraphics();
        // Write the Shape into the TiledImageGraphics.
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.fill(shape);
        g2d.dispose();
        return ti;
    }

    public static ImageCV getRotatedImage(Mat source, int rotateCvType) {
        if (rotateCvType < 0) {
            return ImageCV.toImageCV(source);
        }
        Mat srcImg = Objects.requireNonNull(source);
        ImageCV dstImg = new ImageCV();
        Core.rotate(srcImg, dstImg, rotateCvType);
        return dstImg;
    }

    public static ImageCV flip(Mat source, int flipCvType) {
        if (flipCvType < 0) {
            return ImageCV.toImageCV(source);
        }
        Objects.requireNonNull(source);
        ImageCV dstImg = new ImageCV();
        Core.flip(source, dstImg, flipCvType);
        return dstImg;
    }

    public static ImageCV getRotatedImage(Mat source, double angle, double centerx, double centery) {
        if (MathUtil.isEqualToZero(angle)) {
            return ImageCV.toImageCV(source);
        }
        Mat srcImg = Objects.requireNonNull(source);
        Point ptCenter = new Point(centerx, centery);
        Mat rot = Imgproc.getRotationMatrix2D(ptCenter, -angle, 1.0);
        ImageCV dstImg = new ImageCV();
        // determine bounding rectangle
        Rect bbox = new RotatedRect(ptCenter, srcImg.size(), -angle).boundingRect();
        // double[] matrix = new double[rot.cols() * rot.rows()];
        // // adjust transformation matrix
        // rot.get(0, 0, matrix);
        // matrix[2] += bbox.width / 2.0 - centerx;
        // matrix[rot.cols() + 2] += bbox.height / 2.0 - centery;
        // rot.put(0, 0, matrix);
        Imgproc.warpAffine(srcImg, dstImg, rot, bbox.size());

        return dstImg;
    }

    public static ImageCV warpAffine(Mat source, Mat matrix, Size boxSize, Integer interpolation) {
        if (matrix == null) {
            return (ImageCV) source;
        }
        // System.out.println(matrix.dump());
        Mat srcImg = Objects.requireNonNull(source);
        ImageCV dstImg = new ImageCV();

        if (interpolation == null) {
            interpolation = Imgproc.INTER_LINEAR;
        }
        Imgproc.warpAffine(srcImg, dstImg, matrix, boxSize, interpolation);

        return dstImg;
    }

    /**
     * Computes Min/Max values from Image excluding range of values provided
     *
     * @param img
     * @param paddingValueMin
     * @param paddingValueMax
     * @return
     */
    public static double[] findMinMaxValues(Mat source) {
        double[] extrema = null;
        if (source != null) {
            Mat srcImg = Objects.requireNonNull(source);
            MinMaxLocResult minMax = Core.minMaxLoc(srcImg);
            extrema = new double[2];
            extrema[0] = minMax.minVal;
            extrema[1] = minMax.maxVal;
        }
        return extrema;
    }

    public static ImageCV buildThumbnail(PlanarImage source, Dimension iconDim, boolean keepRatio) {
        Objects.requireNonNull(source);
        if (Objects.requireNonNull(iconDim).width < 1 || iconDim.height < 1) {
            throw new IllegalArgumentException("Unsupported size: " + iconDim);
        }

        final double scale = Math.min(iconDim.getHeight() / source.height(), iconDim.getWidth() / source.width());
        if (scale >= 1.0) {
            return ImageCV.toImageCV(source);
        }
        if (scale < 0.005) {
            return null; // Image is too large to be converted
        }

        Size dim = keepRatio ? new Size((int) (scale * source.width()), (int) (scale * source.height()))
            : new Size(iconDim.width, iconDim.height);

        Mat srcImg = ImageCV.toMat(Objects.requireNonNull(source));
        ImageCV dstImg = new ImageCV();
        Imgproc.resize(srcImg, dstImg, dim, 0, 0, Imgproc.INTER_AREA);
        return dstImg;
    }

    public static boolean writePNM(Mat source, File file, boolean addThumb) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }

        try {
            if (addThumb) {
                writeThumbnail(source, new File(ImageFiler.changeExtension(file.getPath(), ".jpg")));
            }
            return Imgcodecs.imwrite(file.getPath(), source);
        } catch (OutOfMemoryError | CvException e) {
            LOGGER.error("Writing PNM", e); //$NON-NLS-1$
            FileUtil.delete(file);
            return false;
        }
    }

    public static boolean writeThumbnail(Mat source, File file) {
        try {
            final double scale =
                Math.min(Thumbnail.MAX_SIZE / (double) source.height(), (double) Thumbnail.MAX_SIZE / source.width());
            if (scale < 1.0) {
                Size dim = new Size((int) (scale * source.width()), (int) (scale * source.height()));
                Mat thumbnail = new Mat();
                Imgproc.resize(source, thumbnail, dim, 0, 0, Imgproc.INTER_AREA);
                return Imgcodecs.imwrite(file.getPath(), thumbnail);
            }
            return false;
        } catch (OutOfMemoryError | CvException e) {
            LOGGER.error("Writing PNM", e); //$NON-NLS-1$
            FileUtil.delete(file);
            return false;
        }
    }

    public static boolean writePNG(Mat source, File file) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }

        // TOOD handle binary
        Mat srcImg = Objects.requireNonNull(source);
        int type = srcImg.type();
        int elemSize = CvType.ELEM_SIZE(type);
        int channels = CvType.channels(type);
        int bpp = (elemSize * 8) / channels;
        if (bpp > 16 || !CvType.isInteger(type)) {
            Mat dstImg = new Mat();
            srcImg.convertTo(dstImg, CvType.CV_16SC(channels));
            srcImg = dstImg;
        }

        try {
            return Imgcodecs.imwrite(file.getPath(), srcImg);
        } catch (OutOfMemoryError | CvException e) {
            LOGGER.error("", e); //$NON-NLS-1$
            FileUtil.delete(file);
            return false;
        }
    }

    public static boolean writeImage(RenderedImage source, File file) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }

        try {
            return Imgcodecs.imwrite(file.getPath(), toMat(source));
        } catch (OutOfMemoryError | CvException e) {
            LOGGER.error("", e); //$NON-NLS-1$
            return false;
        }
    }

    public static boolean writeImage(Mat source, File file, MatOfInt params) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }

        try {
            return Imgcodecs.imwrite(file.getPath(), source, params);
        } catch (OutOfMemoryError | CvException e) {
            LOGGER.error("Writing image", e); //$NON-NLS-1$
            FileUtil.delete(file);
            return false;
        }
    }

    public static ImageCV readImage(File file) {
        if (!file.canRead()) {
            return null;
        }

        try {
            return ImageCV.toImageCV(Imgcodecs.imread(file.getPath()));
        } catch (OutOfMemoryError | CvException e) {
            LOGGER.error("Reading image", e); //$NON-NLS-1$
            return null;
        }
    }

    public void process(Mat sourceImage, Mat resultImage, int tileSize) {

        if (sourceImage.rows() != resultImage.rows() || sourceImage.cols() != resultImage.cols()) {
            throw new IllegalStateException("");
        }

        final int rowTiles = (sourceImage.rows() / tileSize) + (sourceImage.rows() % tileSize != 0 ? 1 : 0);
        final int colTiles = (sourceImage.cols() / tileSize) + (sourceImage.cols() % tileSize != 0 ? 1 : 0);

        Mat tileInput = new Mat(tileSize, tileSize, sourceImage.type());
        Mat tileOutput = new Mat(tileSize, tileSize, sourceImage.type());

        int boderType = Core.BORDER_DEFAULT;
        int mPadding = 3;

        for (int rowTile = 0; rowTile < rowTiles; rowTile++) {
            for (int colTile = 0; colTile < colTiles; colTile++) {
                Rect srcTile = new Rect(colTile * tileSize - mPadding, rowTile * tileSize - mPadding,
                    tileSize + 2 * mPadding, tileSize + 2 * mPadding);
                Rect dstTile = new Rect(colTile * tileSize, rowTile * tileSize, tileSize, tileSize);
                copyTileFromSource(sourceImage, tileInput, srcTile, boderType);
                processTileImpl(tileInput, tileOutput);
                copyTileToResultImage(tileOutput, resultImage, new Rect(mPadding, mPadding, tileSize, tileSize),
                    dstTile);
            }
        }
    }

    private void copyTileToResultImage(Mat tileOutput, Mat resultImage, Rect srcTile, Rect dstTile) {
        Point br = dstTile.br();

        if (br.x >= resultImage.cols()) {
            dstTile.width -= br.x - resultImage.cols();
            srcTile.width -= br.x - resultImage.cols();
        }

        if (br.y >= resultImage.rows()) {
            dstTile.height -= br.y - resultImage.rows();
            srcTile.height -= br.y - resultImage.rows();
        }

        Mat tileView = tileOutput.submat(srcTile);
        Mat dstView = resultImage.submat(dstTile);

        assert (tileView.rows() == dstView.rows());
        assert (tileView.cols() == dstView.cols());

        tileView.copyTo(dstView);
    }

    private void processTileImpl(Mat tileInput, Mat tileOutput) {
        Imgproc.blur(tileInput, tileOutput, new Size(7.0, 7.0));
    }

    private void copyTileFromSource(Mat sourceImage, Mat tileInput, Rect tile, int mBorderType) {
        Point tl = tile.tl();
        Point br = tile.br();

        Point tloffset = new Point();
        Point broffset = new Point();

        // Take care of border cases
        if (tile.x < 0) {
            tloffset.x = -tile.x;
            tile.x = 0;
        }

        if (tile.y < 0) {
            tloffset.y = -tile.y;
            tile.y = 0;
        }

        if (br.x >= sourceImage.cols()) {
            broffset.x = br.x - sourceImage.cols() + 1;
            tile.width -= broffset.x;
        }

        if (br.y >= sourceImage.rows()) {
            broffset.y = br.y - sourceImage.rows() + 1;
            tile.height -= broffset.y;
        }

        // If any of the tile sides exceed source image boundary we must use copyMakeBorder to make proper paddings
        // for this side
        if (tloffset.x > 0 || tloffset.y > 0 || broffset.x > 0 || broffset.y > 0) {
            Rect paddedTile = new Rect(tile.tl(), tile.br());
            assert (paddedTile.x >= 0);
            assert (paddedTile.y >= 0);
            assert (paddedTile.br().x < sourceImage.cols());
            assert (paddedTile.br().y < sourceImage.rows());

            Core.copyMakeBorder(sourceImage.submat(paddedTile), tileInput, (int) tloffset.y, (int) broffset.y,
                (int) tloffset.x, (int) broffset.x, mBorderType);
        } else {
            // Entire tile (with paddings lies inside image and it's safe to just take a region:
            sourceImage.submat(tile).copyTo(tileInput);
        }

    }

    public static ImageCV meanStack(List<ImageElement> sources) {
        if (sources.size() > 1) {
            ImageElement firstImg = sources.get(0);
            PlanarImage img = firstImg.getImage(null, false);

            Integer type = null;
            Mat mean = Mat.zeros(img.width(), img.height(), CvType.CV_32F);
            int numbSrc = sources.size();
            for (int i = 0; i < numbSrc; i++) {
                ImageElement imgElement = sources.get(i);
                PlanarImage image = imgElement.getImage(null, false);
                if (type == null) {
                    type = image.type();
                }
                if (image instanceof Mat) {
                    Imgproc.accumulate((Mat) image, mean);
                }
            }
            ImageCV dstImg = new ImageCV();
            Core.divide(mean, new Scalar(numbSrc), mean);
            mean.convertTo(dstImg, type);
            return dstImg;
        }
        return null;
    }

    public static ImageCV minStack(List<ImageElement> sources) {
        if (sources.size() > 1) {
            ImageElement firstImg = sources.get(0);
            ImageCV dstImg = new ImageCV();
            dstImg.copyTo(ImageCV.toMat(firstImg.getImage(null, false)));

            int numbSrc = sources.size();
            for (int i = 1; i < numbSrc; i++) {
                ImageElement imgElement = sources.get(i);
                PlanarImage image = imgElement.getImage(null, false);
                if (image instanceof Mat) {
                    Core.min(dstImg, (Mat) image, dstImg);
                }
            }
            return dstImg;
        }
        return null;
    }

    public static ImageCV maxStack(List<ImageElement> sources) {
        if (sources.size() > 1) {
            ImageElement firstImg = sources.get(0);
            ImageCV dstImg = new ImageCV();
            dstImg.copyTo(ImageCV.toMat(firstImg.getImage(null, false)));

            int numbSrc = sources.size();
            for (int i = 1; i < numbSrc; i++) {
                ImageElement imgElement = sources.get(i);
                PlanarImage image = imgElement.getImage(null, false);
                if (image instanceof Mat) {
                    Core.max(dstImg, (Mat) image, dstImg);
                }
            }
            return dstImg;
        }
        return null;
    }

}