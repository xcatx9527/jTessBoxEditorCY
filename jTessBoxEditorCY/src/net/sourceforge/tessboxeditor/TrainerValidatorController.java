/**
 * Copyright @ 2016 Quan Nguyen
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.sourceforge.tessboxeditor;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import net.sourceforge.vietocr.OCR;
import net.sourceforge.vietocr.OCRFiles;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Logger;

/**
 * Extends <code>TrainerController</code> with validation functionality.
 */
public class TrainerValidatorController extends TrainerController {

    private ResourceBundle bundle;
    private FileChooser fc;
    private String language;
    private Stage stageResult;
    private OcrWorker ocrWorker;

    private final static Logger logger = Logger.getLogger(TrainerValidatorController.class.getName());
    private List<File> files;
    private ImageView imageView;

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);
        bundle = ResourceBundle.getBundle("net.sourceforge.tessboxeditor.Gui"); // NOI18N
        fc = new FileChooser();
        fc.setTitle("Select Image File");
        ExtensionFilter allImageFilter = new ExtensionFilter(bundle.getString("All_Image_Files"), "*.bmp", "*.jpg", "*.jpeg", "*.png", "*.tif", "*.tiff");
        fc.getExtensionFilters().add(allImageFilter);
        fc.setInitialDirectory(new File(trainDataDirectory));

        TextArea taValidationResult = new TextArea();
        taValidationResult.setId("textbox");
        taValidationResult.minHeight(100);
        taValidationResult.minWidth(100);
        imageView = new ImageView();
        imageView.setId("ivshow");
        imageView.minHeight(100);
        imageView.minWidth(100);
        imageView.maxHeight(1080);
        imageView.minWidth(1920);
        imageView.setFitWidth(540);
        imageView.setFitHeight(960);
        HBox root = new HBox();
        root.minHeight(100);
        root.minWidth(100);
        taValidationResult.fontProperty().bind(taOutput.fontProperty());
        root.getChildren().add(taValidationResult);
        root.getChildren().add(imageView);
        BorderPane.setAlignment(taValidationResult, Pos.CENTER_LEFT);
        BorderPane.setAlignment(imageView, Pos.CENTER);
        stageResult = new Stage();
        Scene scene = new Scene(root);
        stageResult.setTitle("Validation Result");
        stageResult.setScene(scene);
    }

    @Override
    void validate() {
        language = this.tfLang.getText();
        File tessdata = new File(trainDataDirectory, "tessdata");
        File traineddata = new File(tessdata, language + ".traineddata");
        if (!traineddata.exists()) {
            String msg = String.format("%s.traineddata does not exist in %s. Be sure to run training first.", language, tessdata.getPath());
            new Alert(Alert.AlertType.NONE, msg, ButtonType.OK).showAndWait();
            return;
        }

        btnValidate.setDisable(true);

        // perform OCR on the training image
        File imageFile = fc.showOpenDialog(progressBar1.getScene().getWindow());
        if (imageFile != null) {
            fc.setInitialDirectory(imageFile.getParentFile());
            progressBar1.setVisible(true);
            progressBar1.setProgress(0);
            labelStatus.getScene().setCursor(Cursor.WAIT);
            taOutput.setCursor(Cursor.WAIT);
            files = new ArrayList<File>();
            files.add(imageFile);

            // instantiate Task for OCR
            ocrWorker = new OcrWorker(files);
            new Thread(ocrWorker).start();
        } else {
            btnValidate.setDisable(false);
        }
    }

    /**
     * A worker class for managing OCR process.
     */
    class OcrWorker extends Task<String> {

        List<File> files;

        public OcrWorker(List<File> files) {
            this.files = files;
            progressBar1.progressProperty().unbind();
            progressBar1.progressProperty().bind(this.progressProperty());
            labelStatus.textProperty().unbind();
            labelStatus.textProperty().bind(this.messageProperty());
        }

        @Override
        protected String call() throws Exception {
            updateMessage(bundle.getString("OCR_running..."));
            OCR<File> ocrEngine = new OCRFiles(tessDirectory);
            ocrEngine.setDatapath(trainDataDirectory + "/tessdata");
            ocrEngine.setLanguage(language);
            String result = "";
            for (int i = 0; i < files.size(); i++) {
                if (!isCancelled()) {
                    result = ocrEngine.recognizeText(files.subList(i, i + 1));
                    updateValue(result);
                }
            }

            return result;
        }

        @Override
        protected void succeeded() {
            super.succeeded();
            updateMessage(bundle.getString("OCR_completed."));
            imageView.setImage(new Image("file:" + files.get(0).getPath()));
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    progressBar1.progressProperty().unbind();
                    progressBar1.setProgress(1);
                    labelStatus.getScene().setCursor(Cursor.DEFAULT);
                    taOutput.setCursor(Cursor.DEFAULT);
                    btnValidate.setDisable(false);

                    ((TextArea) stageResult.getScene().lookup("#textbox")).setText(getValue());
                    stageResult.show();
                    stageResult.setIconified(false);
                }
            });
        }

        @Override
        protected void cancelled() {
            super.cancelled();
            updateMessage("OCR " + bundle.getString("canceled"));

            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    progressBar1.setDisable(true);
                    labelStatus.getScene().setCursor(Cursor.DEFAULT);
                    taOutput.setCursor(Cursor.DEFAULT);
                    btnValidate.setDisable(false);
                }
            });
        }

        @Override
        protected void failed() {
            super.failed();
            updateMessage("Failed!");

            progressBar1.progressProperty().unbind();
            progressBar1.setProgress(0);
            progressBar1.setDisable(true);
            labelStatus.getScene().setCursor(Cursor.DEFAULT);
            taOutput.setCursor(Cursor.DEFAULT);
            btnValidate.setDisable(false);
        }
    }
}
