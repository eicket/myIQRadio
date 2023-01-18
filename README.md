# mySpectrum

Audio spectrum analyzer written in Java listening on the selected audio port. 

![Alt text](/Screenshot.jpg)

## Some implementation details 

The app uses a sampling rate of 48000 and a fft size of 4096.

## Java environment

This java application runs on all recent Java versions and was tested on Java 1.8, 15 and 17.
The app is developed with NetBeans 12.6 and the project properties can be found in the nbproject folder.

For all audio processing, the app uses the native javax library. So no external libraries, dll's .. are required.

The user interface is developed with JavaFX version 15. The GUI layout is defined in the Main.fxml file and can be edited by hand, or better, with the JavaFX SceneBuilder.

In your IDE, make sure that the following jar files are on the project classpath :  
javafx-swt.jar  
javafx.base.jar  
javafx.controls.jar  
javafx.fxml.jar  
javafx.graphics.jar  
javafx.media.jar  
javafx.swing.jar  
javafx.web.jar  
as well as charm-glisten-6.0.6.jar  

And finally, the app can be started up as follows  
java --module-path "{your path to Java FX}\openjfx-15.0.1_windows-x64_bin-sdk\javafx-sdk-15.0.1\lib" --add-modules javafx.controls,javafx.fxml -Djava.util.logging.config.file=console_logging.properties


Give it a try and 73's  
Erik  
ON4PB  
runningerik@gmail.com  