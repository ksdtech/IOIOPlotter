package mobi.ioio.plotter_app;

import android.util.Log;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

/**
 * Created by pzingg on 5/6/16.
 */
public class MatDebug {

    public static String showMatRow8U(Mat mat, int row) {
        if (mat == null) {
            return null;
        }
        int channels = mat.channels();
        if (channels != 1 && channels != 3) {
            return null;
        }

        int rows = mat.rows();
        if (row < 0 || row >= rows) {
            return null;
        }
        int cols = mat.cols();
        if (cols > 60) {
            cols = 60;
        }
        byte[] cell = new byte[channels];
        String s = "[ ";
        for (int j = 0; j < cols; ++j) {
            if (j > 0) {
                s += ", ";
            }
            mat.get(row, j, cell);
            if (channels == 1) {
                int v0 = cell[0] & 0xFF;
                s += v0;
            } else {
                int v0 = cell[0] & 0xFF;
                int v1 = cell[0] & 0xFF;
                int v2 = cell[0] & 0xFF;
                s += "( " + v1 + ", " + v2 + ", " + v2 + " )";
            }
        }
        s += " ]";
        return s;
    }

    public static String showMatRow16S(Mat mat, int row) {
        if (mat == null) {
            return null;
        }
        int channels = mat.channels();
        if (channels != 1 && channels != 3) {
            return null;
        }

        int rows = mat.rows();
        if (row < 0 || row >= rows) {
            return null;
        }
        int cols = mat.cols();
        if (cols > 60) {
            cols = 60;
        }
        short[] cell = new short[channels];
        String s = "[ ";
        for (int j = 0; j < cols; ++j) {
            if (j > 0) {
                s += ", ";
            }
            mat.get(row, j, cell);
            if (channels == 1) {
                s += cell[0];
            } else {
                s += "( " + cell[0] + ", " + cell[1] + ", " + cell[2] + " )";
            }
        }
        s += " ]";
        return s;
    }

    public static String showMatRow64F(Mat mat, int row) {
        if (mat == null) {
            return null;
        }
        int channels = mat.channels();
        if (channels != 1 && channels != 3) {
            return null;
        }

        int rows = mat.rows();
        if (row < 0 || row >= rows) {
            return null;
        }
        int cols = mat.cols();
        if (cols > 60) {
            cols = 60;
        }
        double[] cell = new double[channels];
        String s = "[ ";
        for (int j = 0; j < cols; ++j) {
            if (j > 0) {
                s += ", ";
            }
            mat.get(row, j, cell);
            if (channels == 1) {
                s += cell[0];
            } else {
                s += "( " + cell[0] + ", " + cell[1] + ", " + cell[2] + " )";
            }
        }
        s += " ]";
        return s;
    }

    public static void logMat(String tag, Mat mat) {
        if (mat == null) {
            return;
        }
        Log.v(tag, debugMat(mat));

        int rows = mat.rows();
        if (rows > 60) {
            rows = 60;
        }
        int depth = mat.depth();
        if (depth == CvType.depth(CvType.CV_8U)) {
            for (int i = 0; i < rows; ++i) {
                String row = showMatRow8U(mat, i);
                if (row == null) {
                    break;
                }
                Log.v(tag, row);
            }
        } else if (depth == CvType.depth(CvType.CV_16S)) {
            for (int i = 0; i < rows; ++i) {
                String row = showMatRow16S(mat, i);
                if (row == null) {
                    break;
                }
                Log.v(tag, row);
            }
        } else if (depth == CvType.depth(CvType.CV_64F)) {
            for (int i = 0; i < rows; ++i) {
                String row = showMatRow64F(mat, i);
                if (row == null) {
                    break;
                }
                Log.v(tag, row);
            }
        }
    }

    public static String debugMat(Mat mat) {
        if (mat == null) {
            return "null";
        }
        return CvType.typeToString(mat.type()) + ", " + mat.size();
    }
}
