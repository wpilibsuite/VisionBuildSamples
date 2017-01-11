// Workaround for working with GCC 5.4. Do not remove
#define _GLIBCXX_USE_CXX11_ABI 0

#include "cscore.h"
#include "networktables/NetworkTable.h"
#include "tables/ITable.h"
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/core/core.hpp>
#include "llvm/StringRef.h"
#include "llvm/ArrayRef.h"
#include <thread>
#include <string>
#include <chrono>

cs::VideoCamera SetHttpCamera(llvm::StringRef cameraName, cs::MjpegServer& server);

cs::UsbCamera SetUsbCamera(int cameraId, cs::MjpegServer& server);

int main() {
  // Connect NetworkTables, and get access to the publishing table
  NetworkTable::SetClientMode();
  // Set your team number here
  NetworkTable::SetTeam(9999);

  NetworkTable::Initialize();

  // This is the network port you want to stream the raw received image to
  // By rules, this has to be between 1180 and 1190, so 1185 is a good choice
  int streamPort = 1185;

  // This stores our reference to our mjpeg server for streaming the input image
  cs::MjpegServer inputStream("MJPEG Server", streamPort);

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
  llvm::StringRef cameraName("USB Camera 0");
  cs::VideoCamera camera = SetHttpCamera(cameraName, inputStream);
  // It is possible for the camera to be null. If it is, that means no camera could
  // be found using NetworkTables to connect to. Create an HttpCamera by giving a specified stream
  if (!camera) {
    camera = cs::HttpCamera{"CoprocessorCamera", "YourURLHere"};
    inputStream.SetSource(camera);
  }
  */
  


  /***********************************************/

  // USB Camera
  /*
  // This gets the image from a USB camera 
  // Usually this will be on device 0, but there are other overloads
  // that can be used
  cs::UsbCamera camera = SetUsbCamera(0, inputStream);
  // Set the resolution for our camera, since this is over USB
  camera.SetResolution(640,480);
  */

  // This creates a CvSink for us to use. This grabs images from our selected camera, 
  // and will allow us to use those images in opencv
  cs::CvSink imageSink("CV Image Grabber");
  imageSink.SetSource(camera);

  // This creates a CvSource to use. This will take in a Mat image that has had OpenCV operations
  // operations 
  cs::CvSource imageSource("CV Image Source", cs::VideoMode::PixelFormat::kMJPEG, 640, 480, 30);
  cs::MjpegServer cvStream("CV Image Stream", 1186);
  cvStream.SetSource(imageSource);

  // All Mats and Lists should be stored outside the loop to avoid allocations
  // as they are expensive to create
  cv::Mat inputImage;
  cv::Mat hsv;

  // Infinitely process image
  while (true) {
    // Grab a frame. If it has a frame time of 0, there was an error.
    // Just skip and continue
    auto frameTime = imageSink.GrabFrame(inputImage);
    if (frameTime == 0) continue;

    // Below is where you would do your OpenCV operations on the provided image
    // The sample below just changes color source to HSV
    cvtColor(inputImage, hsv, cv::COLOR_BGR2HSV);

    // Here is where you would write a processed image that you want to restreams
    // This will most likely be a marked up image of what the camera sees
    // For now, we are just going to stream the HSV image
    imageSource.PutFrame(hsv);
  }
}

cs::VideoCamera SetHttpCamera(llvm::StringRef cameraName, cs::MjpegServer& server) {
  // Start by grabbing the camera from NetworkTables
  auto publishingTable = NetworkTable::GetTable("CameraPublisher");
  // Wait for robot to connect. Allow this to be attempted indefinitely
  while (true) {
    if (publishingTable->GetSubTables().size() > 0) {
      break;
    }
    std::this_thread::sleep_for(std::chrono::milliseconds(500));
  }

  if (!publishingTable->ContainsSubTable(cameraName)) {
    return cs::VideoCamera();
  }
  auto cameraTable = publishingTable->GetSubTable(cameraName);
  auto urls = cameraTable->GetStringArray("streams", llvm::ArrayRef<std::string>());
  if (urls.size() == 0) {
    return cs::VideoCamera();
  }
  llvm::SmallVector<std::string, 8> fixedUrls;
  for (auto&& i : urls) {
    llvm::StringRef url{i};
    if (url.startswith("mjpg")) {
      fixedUrls.emplace_back(url.split(":").second);
    }
  }
  cs::HttpCamera camera("CoprocessorCamera", fixedUrls);
  server.SetSource(camera);
  return camera;
} 

cs::UsbCamera SetUsbCamera(int cameraId, cs::MjpegServer& server) {
#ifdef _WIN32
  // On windows, return empty because this doesn't work
  throw std::exception("Cannot use USB cameras on windows");
#else
  cs::UsbCamera camera("CoprocessorCamera", cameraId);
  server.SetSource(camera);
  return camera;
#endif
}