// Erik Icket, ON4PB - 2023
package testFilters.display;

import static common.Constants.FILTER_DEFAULT;
import static common.Constants.FILTER_LIMIT_HIGH;
import static common.Constants.FILTER_LIMIT_LOW;
import static common.Constants.FILTER_TAPS_HIGH;
import static common.Constants.FILTER_TAPS_LOW;
import fft.Complex;
import testFilters.extra.BandPassFilter;
import dsp.HilbertTransform;
import dsp.PolyPhaseFilter;
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
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TextField;
import javafx.scene.input.ScrollEvent;
import javafx.util.Duration;
import static dsp.MakeRaisedCosine.makeRaisedCosine;

public class MainController
{

    static final Logger logger = Logger.getLogger(testFilters.display.MainController.class.getName());

    private double samplingRate = 48000;
    private int filterCutoff = FILTER_DEFAULT;
    private int taps = 512;
    private int LowPasswidth = 200;
    private int BandPasswidth = 1000;
    private double alpha = 0.5;
    private int decimationRate = 4;
    private double[] coefficients = getPolyPhaseCoefficients(filterCutoff, taps);

    private XYChart.Series<Number, Number> coefficientsPoints = new XYChart.Series<>();
    private XYChart.Series<Number, Number> firPoints = new XYChart.Series<>();
    private Timeline timeline;

    @FXML
    private NumberAxis tapsXAxis;

    @FXML
    private LineChart<Number, Number> tapsLineChart;

    @FXML
    private NumberAxis filterXAxis;

    @FXML
    private LineChart<Number, Number> filterLineChart;

    @FXML
    void filterXAxisOnScroll(ScrollEvent event)
    {
        logger.fine("scroll : " + event.getDeltaY() + ", upper : " + filterXAxis.getUpperBound());
        double delta = 1000;

        if ((event.getDeltaY() > 0) && (filterXAxis.getUpperBound() < (samplingRate - delta)))
        {
            filterXAxis.setUpperBound(filterXAxis.getUpperBound() + delta);
        }
        else if ((event.getDeltaY() < 0) && (filterXAxis.getUpperBound() > delta))
        {
            filterXAxis.setUpperBound(filterXAxis.getUpperBound() - delta);
        }
    }

    @FXML
    private ChoiceBox<String> filterBox;

    @FXML
    private TextField filterCutoffField;

    @FXML
    void filterCutoffFieldScroll(ScrollEvent event)
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

        filterCutoffField.setText(Integer.toString(filterCutoff));
    }
    @FXML
    private TextField filterTapsField;

    @FXML
    void filterTapsFieldScroll(ScrollEvent event)
    {
        logger.fine("filter taps changed with : " + event.getDeltaY());

        if (event.getDeltaY() > 0)
        {
            // up     
            if (taps < FILTER_TAPS_HIGH)
            {
                taps *= 2;
            }
        }
        else
        {
            // scrolling down
            if (taps > FILTER_TAPS_LOW)
            {
                taps /= 2;
            }
        }

        tapsXAxis.setUpperBound(taps);
        filterTapsField.setText(Integer.toString(taps));
    }

    @FXML
    private TextField alphaField;

    @FXML
    void alphaFieldScroll(ScrollEvent event)
    {
        logger.fine("alpha changed with : " + event.getDeltaY());

        if (event.getDeltaY() > 0)
        {
            // up     
            if (alpha <= 0.9)
            {
                alpha += 0.1;
            }
        }
        else
        {
            // scrolling down
            if (alpha >= 0.1)
            {
                alpha -= 0.1;
            }
        }

        alphaField.setText(String.format("%.2f", alpha));
    }

    @FXML
    void initialize()
    {
        tapsLineChart.getData().add(coefficientsPoints);
        tapsXAxis.setUpperBound(taps);
        filterLineChart.getData().add(firPoints);

        filterBox.getItems().addAll("Polyphase filter", "Fir filter", "Band pass filter", "Hilbert transform");
        filterBox.setValue("Polyphase filter");

        filterCutoffField.setText(Integer.toString(filterCutoff));
        filterTapsField.setText(Integer.toString(taps));
        alphaField.setText(Double.toString(alpha));

        timeline = new Timeline();
        timeline.getKeyFrames().add(new KeyFrame(Duration.millis(500), new EventHandler<ActionEvent>()
        {
            @Override
            public void handle(ActionEvent actionEvent)
            {
                logger.fine("Animation event received");
                switch (filterBox.getValue())
                {
                    case "Polyphase filter":
                        coefficients = getPolyPhaseCoefficients(filterCutoff, taps);
                        break;
                    case "Fir filter":
                        coefficients = getFirCoefficients(filterCutoff, taps);
                        break;                 
                    case "Band pass filter":
                        coefficients = getBandPassCoefficients(filterCutoff, taps);
                        break;
                    case "Hilbert transform":
                        coefficients = getHilbertCoefficients(taps);
                        break;
                }

                //  lineChart.setAnimated(false);
                coefficientsPoints.getData().clear();

                // the filter coefficients
                for (int i = 0; i < coefficients.length; i++)
                {
                    logger.fine("Fir filter coeef : " + coefficients[i]);
                    coefficientsPoints.getData().add(new XYChart.Data<Number, Number>(i, coefficients[i]));
                }

                // the filter
                firPoints.getData().clear();
                float binSize = (float) samplingRate / taps;
                Complex[] fftIn = new Complex[taps];
                Complex[] fftOut = new Complex[taps];
                for (int i = 0; i < coefficients.length; i++)
                {
                    fftIn[i] = new Complex(coefficients[i], coefficients[i]);
                }
                fftOut = fft.FFT.fft(fftIn);

                for (int i = 0; i < coefficients.length / 2; i++)
                {
                    firPoints.getData().add(new XYChart.Data<Number, Number>(i * binSize, fftOut[i].abs()));
                }
                firPoints.setName("fir filter");
            }
        }
        ));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    double[] getPolyPhaseCoefficients(int iCutoff, int taps)
    {
        PolyPhaseFilter polyPhaseFilter = new PolyPhaseFilter(samplingRate, iCutoff, alpha, decimationRate, taps);
        logger.fine("PolyPhase filter coeefs size : " + polyPhaseFilter.coefficients.length);
        return polyPhaseFilter.coefficients;
    }

    double[] getFirCoefficients(int iCutoff, int taps)
    {
        double[] Coefficients = makeRaisedCosine(samplingRate, iCutoff, alpha, taps);
        logger.fine("Fir filter coeefs size : " + Coefficients.length);
        return Coefficients;
    }    

    double[] getBandPassCoefficients(int iCutoff, int taps)
    {
        double[] filter = new double[taps];
        BandPassFilter.CalculateFilter(filter, iCutoff, BandPasswidth, (float) samplingRate);
        return filter;
    }

    double[] getHilbertCoefficients(int taps)
    {

        HilbertTransform hilbertTransform = new HilbertTransform(samplingRate, taps);
        return hilbertTransform.coefficients;

    }
}
