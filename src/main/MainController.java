// Erik Icket, ON4PB - 2023
package main;

import audio.AudioIn;
import audio.AudioInThread;
import audio.AudioOut;
import audio.AudioOutThread;
import static common.Constants.AM;
import static common.Constants.NR_OF_WATERFALL_LINES;
import common.PropertiesWrapper;
import java.util.logging.Logger;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;
import java.util.concurrent.LinkedBlockingDeque;
import static common.Constants.SPECTRUM_DECIMATION_FACTOR;
import java.util.LinkedList;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import static common.Constants.FFTSIZE;
import static common.Constants.FILTER_DEFAULT;
import static common.Constants.FILTER_LIMIT_HIGH;
import static common.Constants.FILTER_LIMIT_LOW;
import static common.Constants.FM;
import static common.Constants.LSB;
import static common.Constants.SAMPLE_AUDIO_OUT_RATE;
import static common.Constants.USB;
import static common.Constants.YAXIS_MAX;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import dsp.Demodulator;
import tcpip.client.FlexTCPThread;
import static common.Constants.NR_OF_SPECTRUM_POINTS;

public class MainController
{

    static final Logger logger = Logger.getLogger(MainController.class.getName());

    PropertiesWrapper propWrapper = new PropertiesWrapper();

    static public boolean invertIQ = true;
    static public LinkedBlockingDeque<double[]> lbdSpectrum = new LinkedBlockingDeque<>();
    static public LinkedBlockingDeque<int[]> lbdAudioOut = new LinkedBlockingDeque<>();
    static public LinkedBlockingDeque<int[][]> lbdDemodulatorInput = new LinkedBlockingDeque<>();
    static public int demodulationMode = USB;
    static public int slice0Frequency = 0;
    static public int slice1Frequency = 0;
    static public int filterCutoff = FILTER_DEFAULT;
    static public double volume;
    static public double rxGain;

    private XYChart.Series<Number, Number> spectrumSeries = new XYChart.Series<Number, Number>();
    private XYChart.Series<Number, Number> lowerFilterSeries = new XYChart.Series<Number, Number>();
    private XYChart.Series<Number, Number> upperFilterSeries = new XYChart.Series<Number, Number>();

    private Timeline timeline;
    private int sampleRate = 0;
    private int audioDecimationRate = 0;
    private float binSize = 0;
   private float waterfallPixelWidthInHz = 0;

    private AudioInThread audioInThread;
    private AudioOutThread audioOutThread;
    private Demodulator demodulatorThread = null;
    private FlexTCPThread flexTCPThread = null;

    @FXML
    private NumberAxis xAxis;

    @FXML
    private NumberAxis yAxis;

    @FXML
    void yAxisOnScroll(ScrollEvent event)
    {
        // check if cursor is upper or lower half of the yAxis and adapt the lower or upper bounds
        // mousePosition is before beginning the scroll
        double mousePosition = (double) yAxis.getValueForDisplay(event.getY());
        double halfYAxis = ((yAxis.getUpperBound() - yAxis.getLowerBound()) / 2) + yAxis.getLowerBound();
        logger.fine("Mouse y position : " + mousePosition + ", half yAxis at : " + halfYAxis);

        if (mousePosition > halfYAxis)
        {
            logger.fine("Mouse in upper part of yAxis");
            // scrolling up
            if (event.getDeltaY() < 0)
            {
                if (yAxis.getUpperBound() < 200)
                {
                    yAxis.setUpperBound((yAxis.getUpperBound() + 5));
                }
            }
            else
            // scrolling down
            {
                if (yAxis.getUpperBound() > 10)
                {
                    yAxis.setUpperBound((yAxis.getUpperBound() - 5));
                }
            }
        }
        else
        {
            logger.fine("Mouse in lower part of yAxis");
            // scrolling up
            if (event.getDeltaY() < 0)
            {
                if ((yAxis.getLowerBound() + 10) < yAxis.getUpperBound())
                {
                    yAxis.setLowerBound((yAxis.getLowerBound() + 5));
                }
            }
            else
            // scrolling down
            {
                if (yAxis.getLowerBound() > 10)
                {
                    yAxis.setLowerBound((yAxis.getLowerBound() - 5));
                }
            }
        }

        logger.fine("new yAxis bounds, lower : " + yAxis.getLowerBound() + ", upper : " + yAxis.getUpperBound());
        yAxis.requestLayout();
    }

    @FXML
    private LineChart<Number, Number> lineChart;

    @FXML
    private Canvas canvas;

    @FXML
    void canvasMousePressed(MouseEvent event)
    {
        if (event.isPrimaryButtonDown())
        {
            double d = event.getX();
            // d is between 0 - 1023
            int fOffset = (int) ((d / 2 - NR_OF_SPECTRUM_POINTS / 2) * waterfallPixelWidthInHz);
            logger.fine("canvas clicked : " + d + ", f offset : " + fOffset);

            slice0Frequency += fOffset;
        }
    }

    @FXML
    private TextField frequencyField;

    @FXML
    void frequencyFieldScroll(ScrollEvent event)
    {
        if (event.getDeltaY() > 0)
        {
            // up
            slice0Frequency += Integer.valueOf(stepBox.getValue());
        }
        else
        {
            // down
            slice0Frequency -= Integer.valueOf(stepBox.getValue());
        }
    }

    @FXML
    private ChoiceBox<String> stepBox;

    @FXML
    private TextField filterField;

    @FXML
    void filterFieldScroll(ScrollEvent event)
    {
        logger.fine("filter changed with : " + event.getDeltaY());

        if (event.getDeltaY() > 0)
        {
            // up     
            if (filterCutoff < FILTER_LIMIT_HIGH)
            {
                filterCutoff += 100;
            }
        }
        else
        {
            // scrolling down
            if (filterCutoff > FILTER_LIMIT_LOW)
            {
                filterCutoff -= 100;
            }
        }

        filterField.setText(Integer.toString(filterCutoff));
        displayFilter();
    }

    @FXML
    private ChoiceBox<String> averageBox;

    @FXML
    private Slider volumeSlider;

    // added manually in the fxml file -- the ValueChanged is not available in the fxml scene builder
    @FXML
    private void volumeSliderValueChanged(ObservableValue<Number> ovn, Number before, Number after)
    {
        logger.fine(before + " " + after);
        volume = (double) after;
    }

    @FXML
    private ChoiceBox<String> audioInBox;

    @FXML
    private ChoiceBox<String> audioOutBox;

    @FXML
    private ChoiceBox<String> sampleRateBox;

    @FXML
    private ChoiceBox<String> modeBox;

    // added manually in the fxml file -- the ActionEvent is not available in the fxml scene builder
    @FXML
    void modeBoxAction(ActionEvent event)
    {
        logger.fine("mode changed to : " + modeBox.getValue());

        switch (modeBox.getValue())
        {
            case "LSB":
                demodulationMode = LSB;
                break;

            case "USB":
                demodulationMode = USB;
                break;

            case "FM":
                demodulationMode = FM;
                break;

            case "AM":
                demodulationMode = AM;
                break;
        }
        displayFilter();
    }

    @FXML
    private Button RXButton;

    @FXML
    void RXButtonClicked(MouseEvent event)
    {
        logger.fine("RX button clicked");

        if ((audioInThread == null) || !audioInThread.isAlive())
        {
            sampleRate = Integer.valueOf(sampleRateBox.getValue());
            binSize = (float) (sampleRate) / FFTSIZE;
            audioDecimationRate = sampleRate / SAMPLE_AUDIO_OUT_RATE;
            waterfallPixelWidthInHz = binSize * SPECTRUM_DECIMATION_FACTOR;

            propWrapper.setProperty("ReceivedAudioIn", audioInBox.getValue());
            logger.fine("Audio in : " + audioInBox.getValue());

            audioInThread = new AudioInThread(audioInBox.getValue(), sampleRate);
            audioInThread.start();

            propWrapper.setProperty("ReceivedAudioOut", audioOutBox.getValue());
            logger.fine("Audio out : " + audioOutBox.getValue());

            demodulatorThread = new Demodulator(sampleRate, audioDecimationRate);
            demodulatorThread.start();

            audioOutThread = new AudioOutThread(audioOutBox.getValue());
            audioOutThread.start();

            flexTCPThread = new FlexTCPThread();
            flexTCPThread.start();

            xAxis.setLowerBound(-sampleRate / 2);
            xAxis.setUpperBound(sampleRate / 2);

            RXButton.setStyle("-fx-background-color: Salmon");
        }
        else
        {
            try
            {
                if (audioInThread.isAlive())
                {
                    audioInThread.stopRequest = true;
                    audioInThread.join();
                    logger.fine("AudioIn thread stopped");
                }

                if (demodulatorThread.isAlive())
                {
                    demodulatorThread.stopRequest = true;
                    demodulatorThread.join();
                    logger.fine("demodulatorThread thread stopped");
                }

                if (audioOutThread.isAlive())
                {
                    audioOutThread.stopRequest = true;
                    audioOutThread.join();
                    logger.fine("AudioOut thread stopped");
                }

                if (flexTCPThread.isAlive())
                {
                    flexTCPThread.stopRequest = true;
                    flexTCPThread.join();
                    logger.fine("flexTCPThread thread stopped");
                }
            }
            catch (InterruptedException ex)
            {
                logger.fine("Exception when closing a thread");
            }
            RXButton.setStyle("-fx-background-color: NavajoWhite");
        }
    }

    @FXML
    private Button invertButton;

    @FXML
    void invertButtonClicked(MouseEvent event)
    {
        logger.info("Invert button clicked");

        invertIQ = !invertIQ;

        if (invertIQ)
        {
            invertButton.setStyle("-fx-background-color: Salmon");
        }
        else
        {
            invertButton.setStyle("-fx-background-color: NavajoWhite");
        }
    }

    @FXML
    private Slider rxGainSlider;

    // added manually in the fxml file -- the ValueChanged is not available in the fxml scene builder
    @FXML
    private void rxGainSliderValueChanged(ObservableValue<Number> ovn, Number before, Number after)
    {
        logger.fine(before + " " + after);
        rxGain = (double) after;
    }

    @FXML
    void initialize()
    {

        LinkedList waterfallList = new LinkedList();
        LinkedList spectrumList = new LinkedList();

        final GraphicsContext gcWaterfall = canvas.getGraphicsContext2D();

        Color blue = new Color(0, 0, 0.2, 1);
        Color red = new Color(1, 0, 0, 1);

        lineChart.setCreateSymbols(false);
        lineChart.getData().add(spectrumSeries);
        lineChart.getData().add(lowerFilterSeries);
        lineChart.getData().add(upperFilterSeries);
        yAxis.setLowerBound(75);
        yAxis.setUpperBound(100);

        frequencyField.setText(Integer.toString(slice0Frequency));

        stepBox.getItems().addAll("10", "50", "100", "1000");
        stepBox.setValue("50");

        filterField.setText(Integer.toString(filterCutoff));

        averageBox.getItems().addAll("1", "5", "10", "20");
        averageBox.setValue("10");

        volumeSlider.setValue(0.5);
        volume = volumeSlider.getValue();

        rxGainSlider.setValue(0.5);
        rxGain = rxGainSlider.getValue();

        AudioIn.ListAudioIn(audioInBox);
        invertButton.setStyle("-fx-background-color: Salmon");

        AudioOut.ListAudioOut(audioOutBox);

        sampleRateBox.getItems().addAll("24000", "48000", "96000", "192000");
        sampleRateBox.setValue("48000");
        audioDecimationRate = sampleRate / SAMPLE_AUDIO_OUT_RATE;

        modeBox.getItems().addAll("LSB", "USB", "FM", "AM");
        // setValue() will trigger displayFilter();
        modeBox.setValue("USB");

        sampleRate = Integer.valueOf(sampleRateBox.getValue());
        binSize = (float) (sampleRate) / FFTSIZE;
        waterfallPixelWidthInHz = binSize * SPECTRUM_DECIMATION_FACTOR;

        RXButton.setStyle("-fx-background-color: NavajoWhite");

        timeline = new Timeline();

        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(100), new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent actionEvent)
            {
                logger.fine("Animation event received");

                if ((audioInThread != null) && audioInThread.isAlive())
                {
                    logger.fine("Spectrum queue size    : " + lbdSpectrum.size());
                    logger.fine("Demodulator queue size : " + lbdDemodulatorInput.size());
                    logger.fine("Audio out queue size   : " + lbdAudioOut.size());

                    if (!lbdSpectrum.isEmpty())
                    {
                        double[] spectrum = lbdSpectrum.pollFirst();
                        // 512 bytes 
                        logger.fine("New spectrum out with length : " + spectrum.length + ", queue depth : " + lbdSpectrum.size());

                        // average the spectrum in time
                        int nrOfAveragingSpectrumLines = Integer.valueOf(averageBox.getValue());
                        // remove head of the queue if capacity is reached
                        spectrumList.add(spectrum);
                        while (spectrumList.size() > nrOfAveragingSpectrumLines)
                        {
                            spectrumList.removeFirst();
                        }

                        lineChart.setAnimated(false);
                        spectrumSeries.getData().clear();
                        for (int i = 0; i < spectrum.length; i++)
                        {
                            float average = 0;

                            // first iterations do it with less spectrum lines 
                            for (int j = 0; j < spectrumList.size(); j++)
                            {
                                double[] spectrumListEntry = (double[]) spectrumList.get(j);
                                average += spectrumListEntry[i];
                            }
                            average = average / nrOfAveragingSpectrumLines;

                            spectrumSeries.getData().add(new XYChart.Data<Number, Number>(i * binSize * SPECTRUM_DECIMATION_FACTOR - sampleRate / 2, average));
                            logger.fine("added point, x : " + i * binSize * SPECTRUM_DECIMATION_FACTOR + ", y : " + spectrum[i]);
                        }
                        lineChart.setAnimated(true);

                        // remove head of the queue if capacity is reached
                        if (waterfallList.size() == NR_OF_WATERFALL_LINES)
                        {
                            waterfallList.removeFirst();
                        }
                        waterfallList.add(spectrum);

                        logger.fine("spectrumList size : " + waterfallList.size());

                        // redraw the waterfall  
                        // display all lines in the queue
                        // no need to clear
                        // gcWaterfall.clearRect(xGcWaterfall + 1, yGcWaterfall, NR_OF_SPECTRUM_POINTS, NR_OF_WATERFALL_LINES);
                        double lower = yAxis.getLowerBound();
                        double upper = yAxis.getUpperBound();
                        int lowerBinOfCutOff = 0;
                        int upperBinOfCutOff = 0;

                        Color targetColor;
                      
                        switch (demodulationMode)
                        {
                            case LSB:
                                lowerBinOfCutOff = NR_OF_SPECTRUM_POINTS / 2 - (int) (filterCutoff / waterfallPixelWidthInHz);
                                break;

                            case USB:
                                upperBinOfCutOff = NR_OF_SPECTRUM_POINTS / 2 + (int) (filterCutoff / waterfallPixelWidthInHz);
                                break;

                            case FM:
                                lowerBinOfCutOff = NR_OF_SPECTRUM_POINTS / 2 - (int) (filterCutoff / waterfallPixelWidthInHz);
                                upperBinOfCutOff = NR_OF_SPECTRUM_POINTS / 2 + (int) (filterCutoff / waterfallPixelWidthInHz);
                                break;

                            case AM:
                                lowerBinOfCutOff = NR_OF_SPECTRUM_POINTS / 2 - (int) (filterCutoff / waterfallPixelWidthInHz);
                                upperBinOfCutOff = NR_OF_SPECTRUM_POINTS / 2 + (int) (filterCutoff / waterfallPixelWidthInHz);
                                break;
                        }
                        logger.fine("pixelWidthInHz : " + waterfallPixelWidthInHz + ", lower filter bin : " + lowerBinOfCutOff + ", upper filter bin : " + upperBinOfCutOff);

                        for (int m = 0; m < waterfallList.size(); m++)
                        {
                            spectrum = (double[]) waterfallList.get(waterfallList.size() - 1 - m);
                            for (int n = 0; n < NR_OF_SPECTRUM_POINTS; n++)
                            {
                                if ((n == (NR_OF_SPECTRUM_POINTS / 2)) || (n == lowerBinOfCutOff) || (n == upperBinOfCutOff))
                                {
                                    // draw the filter line
                                    targetColor = new Color(0, 0, 1, 1);
                                }
                                else
                                {
                                    double level = ((spectrum[n] - lower) / (upper - lower)) * 2;
                                    if (level < 0)
                                    {
                                        level = 0;
                                    }
                                    if (level > 1)
                                    {
                                        level = 1;
                                    }

                                    logger.fine("spectrum : " + spectrum[n] + ", level :" + level);

                                    targetColor = blue.interpolate(red, level);
                                }
                                //   Color targetColor = new Color(level, 0, level, 1);
                                gcWaterfall.setFill(targetColor);
                                // x coord upper left bound of the oval
                                // y coord upper left bound of the oval
                                // width at the center of the oval
                                // height at the center of the oval
                                // canvas is 1024 bits wide, spectrum is 512 bytes buffer                               
                                gcWaterfall.fillOval(n * 2, m, 2, 2);
                            }
                        }

                        // drain the rest
                        lbdSpectrum.clear();
                    }
                }
                frequencyField.setText(Integer.toString(slice0Frequency));
            }
        }
        ));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    void displayFilter()
    {
        logger.fine("display filter with cutoff : " + filterCutoff);

        lineChart.setAnimated(false);

        // bug : a simple clear does not remove the line, have to remove the series
        lineChart.getData().remove(lowerFilterSeries);
        lineChart.getData().remove(upperFilterSeries);
        lowerFilterSeries.getData().clear();
        upperFilterSeries.getData().clear();
        lineChart.getData().add(lowerFilterSeries);
        lineChart.getData().add(upperFilterSeries);

        switch (demodulationMode)
        {
            case LSB:
                lowerFilterSeries.getData().add(new XYChart.Data<>(-filterCutoff, 0));
                lowerFilterSeries.getData().add(new XYChart.Data<>(-filterCutoff, YAXIS_MAX));
                break;

            case USB:
                upperFilterSeries.getData().add(new XYChart.Data<>(filterCutoff, 0));
                upperFilterSeries.getData().add(new XYChart.Data<>(filterCutoff, YAXIS_MAX));
                break;

            case FM:
                break;

            case AM:
                lowerFilterSeries.getData().add(new XYChart.Data<>(-filterCutoff, 0));
                lowerFilterSeries.getData().add(new XYChart.Data<>(-filterCutoff, YAXIS_MAX));
                upperFilterSeries.getData().add(new XYChart.Data<>(filterCutoff, 0));
                upperFilterSeries.getData().add(new XYChart.Data<>(filterCutoff, YAXIS_MAX));
                break;
        }
        lineChart.setAnimated(true);
    }
}
