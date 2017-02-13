import java.util.ArrayList;

import edu.wpi.first.wpilibj.networktables.*;
import edu.wpi.first.wpilibj.tables.*;
import edu.wpi.cscore.*;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import org.opencv.core.MatOfPoint;

import java.util.Collections;

public class Main {
  public static void main(String[] args) {
    // Loads our OpenCV library. This MUST be included
    System.loadLibrary("opencv_java310");

    // Connect NetworkTables, and get access to the publishing table
    NetworkTable.setClientMode();
    // Set your team number here
    NetworkTable.setTeam(6624);

    NetworkTable.initialize();


    // This is the network port you want to stream the raw received image to
    // By rules, this has to be between 1180 and 1190, so 1185 is a good choice
    int streamPort = 1185;

    // This stores our reference to our mjpeg server for streaming the input image
    MjpegServer inputStream = new MjpegServer("MJPEG Server", streamPort);

    // Selecting a Camera
    // Uncomment one of the 2 following camera options
    // The top one receives a stream from another device, and performs operations based on that
    // On windows, this one must be used since USB is not supported
    // The bottom one opens a USB camera, and performs operations on that, along with streaming
    // the input image so other devices can see it.

    // HTTP Camera
    /*
    // This is our camera name from the robot. this can be set in your robot code with the following command
    // CameraServer.getInstance().startAutomaticCapture("YourCameraNameHere");
    // "USB Camera 0" is the default if no string is specified
    String cameraName = "USB Camera 0";
    HttpCamera camera = setHttpCamera(cameraName, inputStream);
    // It is possible for the camera to be null. If it is, that means no camera could
    // be found using NetworkTables to connect to. Create an HttpCamera by giving a specified stream
    // Note if this happens, no restream will be created
    if (camera == null) {
      camera = new HttpCamera("CoprocessorCamera", "YourURLHere");
      inputStream.setSource(camera);
    }
    */



    /***********************************************/

    // USB Camera
    /*
    // This gets the image from a USB camera
    // Usually this will be on device 0, but there are other overloads
    // that can be used

    */
    UsbCamera camera = setUsbCamera(0, inputStream);
    // Set the resolution for our camera, since this is over USB
    camera.setResolution(640,480);

    // This creates a CvSink for us to use. This grabs images from our selected camera,
    // and will allow us to use those images in opencv
    CvSink imageSink = new CvSink("CV Image Grabber");
    imageSink.setSource(camera);

    // This creates a CvSource to use. This will take in a Mat image that has had OpenCV operations
    // operations
    CvSource imageSource = new CvSource("CV Image Source", VideoMode.PixelFormat.kMJPEG, 640, 480, 30);
    MjpegServer cvStream = new MjpegServer("CV Image Stream", 1186);
    cvStream.setSource(imageSource);

    // All Mats and Lists should be stored outside the loop to avoid allocations
    // as they are expensive to create
    Mat inputImage = new Mat();
    Mat hsv = new Mat();

    // Infinitely process image
    while (true) {
      // Grab a frame. If it has a frame time of 0, there was an error.
      // Just skip and continue
      long frameTime = imageSink.grabFrame(inputImage);
      if (frameTime == 0) continue;

      // Below is where you would do your OpenCV operations on the provided image
      // The sample below just changes color source to HSV
      Imgproc.cvtColor(inputImage, hsv, Imgproc.COLOR_BGR2HSV);

      GripPipeline pipeline = new GripPipeline();

      pipeline.process(inputImage);

      ArrayList<MatOfPoint> contours = pipeline.filterContoursOutput();

      ArrayList<MatOfPoint> tapeStrips = getTapeStrips(contours);

      if (tapeStrips.size() > 1) {
        for (MatOfPoint strip : tapeStrips) {
          if (strip != null)
            System.out.println("Width: "strip.width() + ", Height: ", + strip.height();
        }
      }

      System.out.println("------------------------------------------");




      // Here is where you would write a processed image that you want to restreams
      // This will most likely be a marked up image of what the camera sees
      // For now, we are just going to stream the HSV image
      imageSource.putFrame(hsv);
    }
  }

  private static ArrayList<MatOfPoint> getTapeStrips(ArrayList<MatOfPoint> contours) {
    //expcted ration between width() and height() of tape
    double expectedRatio = 2/5;

    //returned arraylist that will contain the (presumed) 2 tape strips
    ArrayList<MatOfPoint> returnList = new ArrayList<MatOfPoint>();

    if (contours.size() > 0) {

      MatOfPoint tapeStrip1 = contours.get(0);
      MatOfPoint tapeStrip2 = null;

      double tapeStrip1PercentError = getPercentError(tapeStrip1.width() / tapeStrip1.height(), expectedRatio);
      double tapeStrip2PercentError = 255; //fair dice roll

      for (MatOfPoint cont : contours) {
        double ratio = cont.width() / cont.height();
        double percentError = getPercentError(ratio, expectedRatio);


        if (percentError <= tapeStrip1PercentError) {
          tapeStrip2 = tapeStrip1;
          tapeStrip2PercentError = tapeStrip1PercentError;

          tapeStrip1 = cont;
          tapeStrip1PercentError = percentError;
        }
        else if (tapeStrip2 != null) {
          if (percentError <= tapeStrip2PercentError) {
            tapeStrip2 = cont;
            tapeStrip2PercentError = percentError;
          }
        }
      }

      returnList.add(tapeStrip1);
      returnList.add(tapeStrip2);

    }

    return returnList;

  }

  private static double getPercentError(double experimentalVal, double expectedVal) {
    return (Math.abs(expectedVal - experimentalVal)/(expectedVal));
  }

  private static HttpCamera setHttpCamera(String cameraName, MjpegServer server) {
    // Start by grabbing the camera from NetworkTables
    NetworkTable publishingTable = NetworkTable.getTable("CameraPublisher");
    // Wait for robot to connect. Allow this to be attempted indefinitely
    while (true) {
      try {
        if (publishingTable.getSubTables().size() > 0) {
          break;
        }
        Thread.sleep(500);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }


    HttpCamera camera = null;
    if (!publishingTable.containsSubTable(cameraName)) {
      return null;
    }
    ITable cameraTable = publishingTable.getSubTable(cameraName);
    String[] urls = cameraTable.getStringArray("streams", null);
    if (urls == null) {
      return null;
    }
    ArrayList<String> fixedUrls = new ArrayList<String>();
    for (String url : urls) {
      if (url.startsWith("mjpg")) {
        fixedUrls.add(url.split(":", 2)[1]);
      }
    }
    camera = new HttpCamera("CoprocessorCamera", fixedUrls.toArray(new String[0]));
    server.setSource(camera);
    return camera;
  }

  private static UsbCamera setUsbCamera(int cameraId, MjpegServer server) {
    // This gets the image from a USB camera
    // Usually this will be on device 0, but there are other overloads
    // that can be used
    UsbCamera camera = new UsbCamera("CoprocessorCamera", cameraId);
    server.setSource(camera);
    return camera;
  }
}
