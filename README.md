# myIQRadio

Experimental IQ radio taking the IQ samples from an audio in device (in stereo) and plays back the decoded audio (in mono) on the audio out device.

Some interesting features :

- Selectable sample rates from 24000 to 192000 samples / sec

- Decodes USB, LSB, AM and FM
- Variable 100 .. 10000 Hz filter width
- Variable spectrum smoothing
- VFO control to a Flex Radio via TCP/IP

![Alt text](/screenshots/main.jpg)

## Some implementation details 

All audio processing and demodulation takes place in the time domain following a well known diagram :

![Alt text](/screenshots/demod.jpg)

The IQ samples first go through a low pass filter with a variable filter width, after which they are decimated by 4 (in the case of an input sampling rate of 48000 samples/sec).
The low pass filtering and decimation is done with a polyphase filter bank with as many parallel FIR filters as the decimation rate.
The sampling rate after the decimation process is always 12000 samples/sec, independent of the input sampling rate.

The I signal is then delayed by ((Hilbert Transform taps - 1) / 2) samples to maintain a synchronized signal flow with the Q signal going through the Hilbert Transform.
For USB, I minus Q produces the demodulated audio.
For LSB, this is I plus Q.
For AM and FM, the signals are calculated directly after the decimation.

The incoming IQ samples also go through a Fast Fourier Transform with a FFT size of 4096.
A further decimation of 8, produces a 512 point spectrum graph and waterfall.
The line spectrum graph can finally be smoothed by accumulating up to 20 spectral lines, before being displayed on the line chart.





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

The Java app can be started up as follows :
java --module-path "{your path to Java FX}\openjfx-15.0.1_windows-x64_bin-sdk\javafx-sdk-15.0.1\lib" --add-modules javafx.controls,javafx.fxml -Djava.util.logging.config.file=console_logging.properties -jar "dist\myWSJTEncoder.jar"

Two more Java apps are provided to display the Gaussian smoothed pulse and the Frequency deviation per mode.

## Some further useful reading :

[1] The FT4 and FT8 Communication Protocols - QEX July / August 2020 : https://physics.princeton.edu/pulsar/k1jt/FT4_FT8_QEX.pdf   
[2] Encoding process, by Andy G4JNT : http://www.g4jnt.com/WSPR_Coding_Process.pdf and http://www.g4jnt.com/WSJT-X_LdpcModesCodingProcess.pdf  
[3] Synchronisation in FT8 : http://www.sportscliche.com/wb2fko/FT8sync.pdf  
[4] Costas Arrays : http://www.sportscliche.com/wb2fko/TechFest_2019_WB2FKO_revised.pdf  
[5] FT8 - costas arrays - video : https://www.youtube.com/watch?v=rjLhTN59Bg4  

## Copyright notice and credits

The algorithms, source code, look-and-feel of WSJT-X and related programs, and protocol specifications for the modes FSK441, FT4, FT8, JT4, JT6M, JT9, JT65, JTMS, QRA64, ISCAT, and MSK144 
are Copyright © 2001-2020 by one or more of the following authors: Joseph Taylor, K1JT; Bill Somerville, G4WJS; Steven Franke, K9AN; Nico Palermo, IV3NWV; Greg Beam, KI7MT; Michael Black, W9MDB; 
Edson Pereira, PY2SDR; Philip Karn, KA9Q; and other members of the WSJT Development Group.

All credits to K1JT, Joe Taylor, and the WSJTX development team. Without their work, we would even not dream about weak signals and their processing !


Give it a try and 73's  
Erik  
ON4PB  
runningerik@gmail.com  