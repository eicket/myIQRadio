# myIQRadio

Experimental Software Defined Radio (SDR) client which reads the IQ samples from a Flex Radio VITA49 UDP stream and plays back the decoded audio (in mono) on the audio out device.
No audio devices are used for reading the sampled IQ stream. The Flex Radio DAX control panel is completely bypassed, and can be left closed.
The IQ sampling can be done at 24000, 48000, 96000 or 192000 samples per second and baseband samples are carried as 32-bit float little-endian interleaved I/Q pairs.

When using with a Flex Radio, select a pan adapter, and attach DAX IQ channel 1 to it.

Some interesting features :

- Selectable sample rates from 24000 to 192000 samples / sec
- Decodes USB, LSB, AM and FM
- Variable 100 .. 10000 Hz filter width
- Variable spectrum smoothing
- VFO control to a Flex Radio via TCP/IP

This is a pure Java implementation, and can easily be built with any recent Java development environment.

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

## Headless operation

This repository also contains two main classes to demo a minimal implementation without GUI.
The headless.DAXIQ class demonstrates a minimal implementation for receiving a baseband VITA49 IQ packet stream.
The headless.DAX class demonstrates how to receive an audio VITA49 stream at a fixed 24000 samples per sec rate from a slice receiver.

## Filter display utility 

A filter display utility is also available to display filter coefficients and the resulting frequency response. 

![Alt text](/screenshots/filter.jpg)

Different types of parameters (filter cutoff, number of taps, rolloff - alpha value) can be selected for a number of filters (FIR filter, band pass filter, Hilbert transform, ...).


## Java environment

This java application runs on all recent Java versions and was tested on Java 25.

The app uses the native javax library for all audio processing. So no external libraries, dll's .. are required.

A pom.xml file is included for building and packaging.
The user interface is developed with JavaFX version 25. The GUI layout is defined in the Main.fxml file and can be edited by hand, or better, with the JavaFX SceneBuilder.

Give it a try, and if you like it, give it a good fork !
73's  
Erik  
ON4PB  
runningerik@gmail.com  