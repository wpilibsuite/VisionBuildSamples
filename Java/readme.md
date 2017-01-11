# Java sample vision system

This is the WPILib sample build system for building Java based vision targeting for running on systems other than the roboRIO. This currently supports the following platforms

* Windows
* Raspberry Pi running Raspbian
* Generic Armhf devices (such as the BeagleBone Black or the Jetson)

It has been designed to be easy to setup and use, and only needs a few minor settings to pick which system you want to be ran on. It has samples for interfacing with NetworkTables and CsCore from
any device, along with performing OpenCV operations.

## Choosing which system to build for
As there is no way to autodetect which system you want to build for, such as building for a Raspberry Pi on a windows desktop, you have to manually select which system you want to build for.
To do this, open the `build.gradle` file. Near the top at line 10 starts a group of comments explaining what to do. For a basic rundown, there are 3 lines that start with `ext.buildType =`. 
To select a device, just uncomment the system you want to build for. 

Note it is possible to easily switch which system you want to target. To do so, just switch which build type is uncommented. When you do this, you will have to run a clean `gradlew clean` in order to
clear out any old artifacts. 

## Choosing the camera type
This sample includes 2 ways to get a camera image. The first way is from a stream coming from the roboRIO, which is created with `CameraServer.getInstance().startAutomaticCapture();`. This 
is the only method that is supported on windows. The second way is by opening a USB camera directly on the device. This will likely allow higher resolutions, however is only supported on Linux
devices.

To select between the types, open the `Main.java` file in `src/main/java`, and scroll down to the line that says "Selecting a Camera". Follow the directions there to select one.

## Building and running on the local device
If you are running the build for your specific platform on the device you plan on running, you can use `gradlew run` to run the code directly. You can also run `gradlew build` to run a build.
When doing this, the output files will be placed into `output\`. From there, you can run either the .bat file on windows or the shell script on unix in order to run your project.

## Building for another platform
If you are building for another platform, trying to run `gradlew run` will not work, as the OpenCV binaries will not be set up correctly. In that case, when you run `gradlew build`, a zip file
is placed in `output\`. This zip contains the built jar, the OpenCV library for your selected platform, and either a .bat file or shell script to run everything. All you have to do is copy
this file to the system, extract it, then run the .bat or shell script to run your program

## What this gives you
This sample gets an image either from a USB camera or an already existing stream. It then restreams the input image in it's raw form in order to make it viewable on another system.
It then creates an OpenCV sink from the camera, which allows us to grab OpenCV images. It then creates an output stream for an OpenCV image, for instance so you can stream an annotated
image. The default sample just performs a color conversion from BGR to HSV, however from there it is easy to create your own OpenCV processing in order to run everything. In addition, it is possible
to run a pipeline generated from GRIP. In addition, a connection to NetworkTables is set up, so you can send data regarding the targets back to your robot.

## Other configuration options
The build script provides a few other configuration options. These include selecting the main class name, and providing an output name for the project.
Please see the `build.gradle` file for where to change these. 