/*
    Matthias Müller <muellmat@gmail.com>
    https://github.com/muellmat/OctoEye

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.features2d.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class OctoEye {

    public final static int WIDTH  = 320;
    public final static int HEIGHT = 240;

    public static Scalar BLACK      = new Scalar(000,000,000);
    public static Scalar GRAY       = new Scalar(128,128,128);
    public static Scalar WHITE      = new Scalar(255,255,255);
    public static Scalar RED        = new Scalar(255,000,000);
    public static Scalar GREEN      = new Scalar(000,255,000);
    public static Scalar BLUE       = new Scalar(000,000,255);
    public static Scalar YELLOW     = new Scalar(255,255,000);
    public static Scalar PURPLE     = new Scalar(175,000,255);
    public static Scalar ORANGE     = new Scalar(255,175,000);
    public static Scalar LIGHTRED   = new Scalar(255,000,128);
    public static Scalar LIGHTGREEN = new Scalar(128,255,128);
    public static Scalar LIGHTBLUE  = new Scalar(128,255,255);

    private int[] starBitMask = {
            0,0,0,0,0,1,1,0,0,0,0,0,
            0,1,1,0,0,1,1,0,0,1,1,0,
            0,1,1,1,0,1,1,0,1,1,1,0,
            0,0,1,1,1,1,1,1,1,1,0,0,
            0,0,0,1,1,1,1,1,1,0,0,0,
            1,1,1,1,1,1,1,1,1,1,1,1,
            1,1,1,1,1,1,1,1,1,1,1,1,
            0,0,0,1,1,1,1,1,1,0,0,0,
            0,0,1,1,1,1,1,1,1,1,0,0,
            0,1,1,1,0,1,1,0,1,1,1,0,
            0,1,1,0,0,1,1,0,0,1,1,0,
            0,0,0,0,0,1,1,0,0,0,0,0
    };

    private int[] ringBitMask = {
            0,0,0,0,1,1,1,1,0,0,0,0,
            0,0,1,1,1,1,1,1,1,1,0,0,
            0,1,1,1,1,0,0,1,1,1,1,0,
            0,1,1,0,0,0,0,0,0,1,1,0,
            1,1,1,0,0,0,0,0,0,1,1,1,
            1,1,0,0,0,0,0,0,0,0,1,1,
            1,1,0,0,0,0,0,0,0,0,1,1,
            1,1,1,0,0,0,0,0,0,1,1,1,
            0,1,1,0,0,0,0,0,0,1,1,0,
            0,1,1,1,1,0,0,1,1,1,1,0,
            0,0,1,1,1,1,1,1,1,1,0,0,
            0,0,0,0,1,1,1,1,0,0,0,0
    };

    private long start = 0, end = 0, time = 0;
    private Mat src, dst, dbg, dst2, tmp1, tmp2;

    private RotatedRect pupil = null;
    private Point axisA = null;
    private Point axisB = null;
    private int pupilMajorAxis = 0;
    private int pupilMinorAxis = 0;
    private boolean star = false;
    private boolean ring = false;

    private boolean debug = true;

    public OctoEye(byte buffer[]) {
        start = System.currentTimeMillis();
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        src = new Mat(HEIGHT,WIDTH,CvType.CV_8UC1);
        src.put(0,0,buffer);

        tmp1 = new Mat(src.rows(),src.cols(),src.type());
        tmp2 = new Mat(src.rows(),src.cols(),src.type());

        dst = new Mat(src.rows(),src.cols(),CvType.CV_8UC3);
        Imgproc.cvtColor(src, dst,Imgproc.COLOR_GRAY2BGR);

        if (debug) {
            dbg = new Mat(src.rows(),src.cols(),CvType.CV_8UC3);
            dst.copyTo(dbg);
        }

        detectSymbols();
        detectPupil();

        end = System.currentTimeMillis();
        time = end-start;
    }

    public static boolean between(double x, double min, double max) {
        return x>=min && x<=max;
    }

    private void detectPupil() {
        // min and max pupil radius
        int r_min = 2;
        int r_max = 45;

        // min and max pupil diameter
        int d_min = 2*r_min;
        int d_max = 2*r_max;

        // min and max pupil area
        double area;
        double a_min = Math.PI*r_min*r_min;
        double a_max = Math.PI*r_max*r_max;

        // histogram stuff
        List<Mat> images;
        MatOfInt channels;
        Mat mask;
        Mat hist;
        MatOfInt mHistSize;
        MatOfFloat mRanges;

        // contour and circle stuff
        Rect rect = null;
        Rect rectMin;
        Rect rectMax;
        List<MatOfPoint> contours;
        MatOfPoint3 circles;

        // pupil center
        Point p;

        // ellipse test points
        Point v;
        Point r;
        Point s;

        // rect points
        Point tl;
        Point br;

        // pupil edge detection
        Vector<Point> pointsTest;
        Vector<Point> pointsEllipse;
        Vector<Point> pointsRemoved;

        // temporary variables
        double distance;
        double rad;
        double length;
        int x;
        int y;
        int tmp;
        byte buff[];



        // -------------------------------------------------------------------------------------------------------------
        // step 1
        // blur the image to reduce noise

        Imgproc.medianBlur(src,tmp1,25);



        // -------------------------------------------------------------------------------------------------------------
        // step 2
        // locate the pupil with feature detection and compute a histogram for each,
        // the best feature will be used as rough pupil location (rectMin)

        int score  = 0;
        int winner = 0;

        // feature detection
        MatOfKeyPoint matOfKeyPoints = new MatOfKeyPoint();
        FeatureDetector blobDetector = FeatureDetector.create(FeatureDetector.MSER); // Maximal Stable Extremal Regions
        blobDetector.detect(tmp1,matOfKeyPoints);
        List<KeyPoint> keyPoints = matOfKeyPoints.toList();

        // histogram calculation
        for (int i=0; i<keyPoints.size(); i++) {
            x = (int)keyPoints.get(i).pt.x;
            y = (int)keyPoints.get(i).pt.y;
            tl = new Point(x-5>=0?x-5:0,y-5>=0?y-5:0);
            br = new Point(x+5<WIDTH?x+5:WIDTH-1,y+5<HEIGHT?y+5:HEIGHT-1);

            images = new ArrayList<Mat>();
            images.add(tmp1.submat(new Rect(tl,br)));
            channels = new MatOfInt(0);
            mask = new Mat();
            hist = new Mat();
            mHistSize = new MatOfInt(256);
            mRanges = new MatOfFloat(0f,256f);
            Imgproc.calcHist(images,channels,mask,hist,mHistSize,mRanges);

            tmp = 0;
            for (int j=0; j<256/3; j++) {
                tmp += (256/3-j)*(int)hist.get(j,0)[0];
            }
            if (tmp>=score) {
                score = tmp;
                winner = i;
                rect = new Rect(tl,br);
            }

            if (debug) {
                // show features (orange)
                Core.circle(dbg,new Point(x,y),3,ORANGE);
            }
        }
        if (rect==null) {
            return;
        }
        rectMin = rect.clone();

        if (debug) {
            // show rectMin (red)
            Core.rectangle(dbg,rectMin.tl(),rect.br(),RED,1);
        }



        // -------------------------------------------------------------------------------------------------------------
        // step 3
        // compute a rectMax (blue) which is larger than the pupil

        int margin = 32;

        rect.x      = rect.x-margin;
        rect.y      = rect.y-margin;
        rect.width  = rect.width +2*margin;
        rect.height = rect.height+2*margin;

        rectMax = rect.clone();

        if (debug) {
            // show features (orange)
            Core.rectangle(dbg,rectMax.tl(),rectMax.br(),BLUE);
        }



        // -------------------------------------------------------------------------------------------------------------
        // step 4
        // blur the image again

        Imgproc.medianBlur(src, tmp1,7);
        Imgproc.medianBlur(tmp1,tmp1,3);
        Imgproc.medianBlur(tmp1,tmp1,3);
        Imgproc.medianBlur(tmp1,tmp1,3);



        // -------------------------------------------------------------------------------------------------------------
        // step 5
        // detect edges

        Imgproc.Canny(tmp1,tmp2,40,50);



        // -------------------------------------------------------------------------------------------------------------
        // step 6
        // from pupil center to maxRect borders, find all edge points, compute a first ellipse

        p = new Point(rectMin.x+rectMin.width/2,rectMin.y+rectMin.height/2);
        pointsTest    = new Vector<Point>();
        pointsEllipse = new Vector<Point>();
        pointsRemoved = new Vector<Point>();
        buff = new byte[tmp2.rows()*tmp2.cols()];
        tmp2.get(0,0,buff);

        length = Math.min(p.x-rectMax.x-3,p.y-rectMax.y-3);
        length = Math.sqrt(2*Math.pow(length,2));
        Point z = new Point(p.x,p.y-length);
        for (int i=0; i<360; i+=15) {
            rad = Math.toRadians(i);
            x = (int)(p.x+Math.cos(rad)*(z.x-p.x)-Math.sin(rad)*(z.y-p.y));
            y = (int)(p.y+Math.sin(rad)*(z.x-p.x)-Math.cos(rad)*(z.y-p.y));
            pointsTest.add(new Point(x,y));
        }

        if (debug) {
            for (int i=0; i<pointsTest.size(); i++) {
                Core.line(dbg,p,pointsTest.get(i),GRAY,1);
                Core.rectangle(dbg,rectMin.tl(),rectMin.br(),GREEN,1);
                Core.rectangle(dbg,rectMax.tl(),rectMax.br(),BLUE,1);
            }
            Core.rectangle(dbg,rectMin.tl(),rectMin.br(),BLACK,-1);
            Core.rectangle(dbg,rectMin.tl(),rectMin.br(),RED,1);
            Core.rectangle(dbg,rectMax.tl(),rectMax.br(),BLUE);
        }

        // p: Ursprung ("Mittelpunkt" der Ellipse)
        // v: Zielpunkt (Testpunkt)
        // r: Richtungsvektor PV
        for (int i=0; i < pointsTest.size(); i++) {
            v = new Point(pointsTest.get(i).x, pointsTest.get(i).y);
            r = new Point(v.x-p.x,v.y-p.y);
            length = Math.sqrt(Math.pow(p.x-v.x,2)+Math.pow(p.y-v.y,2));
            boolean found = false;
            for (int j=0; j<Math.round(length); j++) {
                s  = new Point(Math.rint(p.x+(double)j/length*r.x),Math.rint(p.y+(double)j/length*r.y));
                s.x = Math.max(1,Math.min(s.x,WIDTH -2));
                s.y = Math.max(1,Math.min(s.y,HEIGHT-2));
                tl = new Point(s.x-1,s.y-1);
                br = new Point(s.x+1,s.y+1);
                buff = new byte[3*3];
                rect = new Rect(tl,br);
                try {
                    (tmp2.submat(rect)).get(0,0,buff);
                    for (int k=0; k<3*3; k++) {
                        if (Math.abs(buff[k])==1) {
                            pointsEllipse.add(s);
                            found = true;
                            break;
                        }
                    }
                } catch (Exception e) {
                    break;
                }
                if (found) {
                    break;
                }
            }
        }

        double e_min = Double.POSITIVE_INFINITY;
        double e_max = 0;
        double e_med = 0;
        for (int i=0; i<pointsEllipse.size(); i++) {
            v = pointsEllipse.get(i);
            length = Math.sqrt(Math.pow(p.x-v.x,2)+Math.pow(p.y-v.y,2));
            e_min = (length<e_min) ? length : e_min;
            e_max = (length>e_max) ? length : e_max;
            e_med = e_med+length;
        }
        e_med = e_med/pointsEllipse.size();
        if (pointsEllipse.size() >= 5) {
            Point[] points1 = new Point[pointsEllipse.size()];
            for (int i=0; i<pointsEllipse.size(); i++) {
                points1[i] = pointsEllipse.get(i);
            }
            MatOfPoint2f points2 = new MatOfPoint2f();
            points2.fromArray(points1);
            pupil = Imgproc.fitEllipse(points2);
        }

        if (debug) {
            Core.ellipse(dbg,pupil,PURPLE,2);
        }



        // -------------------------------------------------------------------------------------------------------------
        // step 7
        // remove some outlier points and compute the ellipse again

        try {
            for (int i=1; i<=4; i++) {
                distance = 0;
                int remove = 0;
                for (int j=pointsEllipse.size()-1; j>=0; j--) {
                    v = pointsEllipse.get(j);
                    length = Math.sqrt(Math.pow(v.x-pupil.center.x,2)+Math.pow(v.y-pupil.center.y,2));
                    if (length>distance) {
                        distance = length;
                        remove = j;
                    }
                }
                v = pointsEllipse.get(remove);
                pointsEllipse.removeElementAt(remove);
                pointsRemoved.add(v);
            }
        } catch (Exception e) {
            // something went wrong, return null
            reset();
            return;
        }
        if (pointsEllipse.size()>=5) {
            Point[] points1 = new Point[pointsEllipse.size()];
            for (int i=0; i<pointsEllipse.size(); i++) {
                points1[i] = pointsEllipse.get(i);
            }
            MatOfPoint2f points2 = new MatOfPoint2f();
            points2.fromArray(points1);
            pupil = Imgproc.fitEllipse(points2);

            Point[] vertices = new Point[4];
            pupil.points(vertices);
            double d1 = Math.sqrt(Math.pow(vertices[1].x-vertices[0].x,2)+Math.pow(vertices[1].y-vertices[0].y,2));
            double d2 = Math.sqrt(Math.pow(vertices[2].x-vertices[1].x,2)+Math.pow(vertices[2].y-vertices[1].y,2));

            if (d1>=d2) {
                pupilMajorAxis = (int)(d1/2);
                pupilMinorAxis = (int)(d2/2);
                axisA = new Point(vertices[1].x+(vertices[2].x-vertices[1].x)/2,vertices[1].y+(vertices[2].y-vertices[1].y)/2);
                axisB = new Point(vertices[0].x+(vertices[1].x-vertices[0].x)/2,vertices[0].y+(vertices[1].y-vertices[0].y)/2);
            } else {
                pupilMajorAxis = (int)(d2/2);
                pupilMinorAxis = (int)(d1/2);
                axisB = new Point(vertices[1].x+(vertices[2].x-vertices[1].x)/2,vertices[1].y+(vertices[2].y-vertices[1].y)/2);
                axisA = new Point(vertices[0].x+(vertices[1].x-vertices[0].x)/2,vertices[0].y+(vertices[1].y-vertices[0].y)/2);
            }
        }

        double ratio = (double)pupilMinorAxis/(double)pupilMajorAxis;
        if (ratio<0.75 || 2*pupilMinorAxis<=d_min || 2*pupilMajorAxis>=d_max) {
            // something went wrong, return null
            reset();
            return;
        }

        // pupil found
        if (debug) {
            Core.ellipse(dbg,pupil,GREEN,2);
            Core.line(dbg,pupil.center,axisA,RED, 2);
            Core.line(dbg,pupil.center,axisB,BLUE,2);
            Core.circle(dbg,pupil.center,1,GREEN,0);

            x = 5;
            y = 5;
            Core.rectangle(dbg,new Point(x,y),new Point(x+80+4,y+10),BLACK,-1);
            Core.rectangle(dbg,new Point(x+2,y+2),new Point(x+2+pupilMajorAxis,y+4),RED, -1);
            Core.rectangle(dbg,new Point(x+2,y+6),new Point(x+2+pupilMinorAxis,y+8),BLUE,-1);

            for (int i=pointsEllipse.size()-1; i>=0; i--) {
                Core.circle(dbg,pointsEllipse.get(i),2,ORANGE,-1);
            }
            for (int i=pointsRemoved.size()-1; i>=0; i--) {
                Core.circle(dbg,pointsRemoved.get(i),2,PURPLE,-1);
            }
        }
        Core.ellipse(dst,pupil,GREEN,2);
        Core.circle(dst,pupil.center,1,GREEN,0);
    }

    private boolean detectSymbol(int[] shape, int ystart) {
        // create a 12x12 pixel buffer for the area of a star or ring shape
        byte buff[] = new byte[12*12];

        // fetch the area of a star or ring shape from the image and write it to the buffer
        Mat m = src.submat(new Rect(WIDTH-30,ystart,12,12));
        m.get(0,0,buff);

        int shapePixel    = -64;
        int correctPixels =   0;
        int totalPixels   =   0;
        for (int i=0; i<12*12; i++) {
            correctPixels += buff[i]==shapePixel && shape[i]==1 ? 1 : 0;
            totalPixels += shape[i];
        }
        return correctPixels==totalPixels && correctPixels>0;
    }

    private void detectSymbols() {
        this.star = detectSymbol(starBitMask,18);
        this.ring = detectSymbol(ringBitMask,33);
    }

    private void reset() {
        // something went wrong, return null
        pupil = null;
        axisA = null;
        axisB = null;
        pupilMajorAxis = 0;
        pupilMinorAxis = 0;
    }

    public long getTime() {
        return time;
    }

    public int getDiameter() {
        return 2*pupilMajorAxis;
    }

    public boolean isStar() {
        return star;
    }

    public boolean isRing() {
        return ring;
    }

    public int getPupilMajorAxis() {
        return pupilMajorAxis;
    }

    public int getPupilMinorAxis() {
        return pupilMinorAxis;
    }

    public RotatedRect getPupil() {
        return pupil;
    }

    public Mat getSrc() {
        return src;
    }

    public Mat getDst() {
        return dst;
    }

    public Mat getDbg() {
        return dbg;
    }

    public Mat getDst2x() {
        dst2 = new Mat(src.rows()*2,src.cols()*2,CvType.CV_8UC3);
        Imgproc.resize(dst,dst2,new Size(src.cols()*2,src.rows()*2));
        return dst2;
    }

    public Mat getTmp1() {
        return tmp1;
    }

    public Mat getTmp2() {
        return tmp2;
    }

    public BufferedImage getBufferedImage(Mat m) {
        byte[] buffer = new byte[m.rows()*m.cols()*m.channels()];
        m.get(0,0,buffer);

        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (m.channels()>1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
            for (int i=0; i<buffer.length; i=i+3) {
                byte b = buffer[i];
                buffer[i] = buffer[i+2];
                buffer[i+2] = b;
            }
        }

        BufferedImage image = new BufferedImage(m.cols(),m.rows(),type);
        System.arraycopy(buffer,0,((DataBufferByte)image.getRaster().getDataBuffer()).getData(),0,buffer.length);

        return image;
    }
}
