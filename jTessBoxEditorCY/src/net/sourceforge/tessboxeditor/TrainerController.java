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
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import net.sourceforge.tess4j.util.ImageIOHelper;
import net.sourceforge.tessboxeditor.utilities.Utils;
import javafx.stage.FileChooser.ExtensionFilter;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class TrainerController implements Initializable {

    @FXML
    private TextField tfTessDir;
    @FXML
    private Button btnBrowseTess;
    @FXML
    private TextField tfDataDir;
    @FXML
    private Button btnBrowseData;
    @FXML
    protected TextField tfLang;
    @FXML
    private TextField tfBootstrapLang;
    @FXML
    private CheckBox chbRTL;
    @FXML
    private ComboBox<TrainingMode> cbOps;
    @FXML
    private Button btnTrain;
    @FXML
    protected Button btnValidate;
    @FXML
    private Button btnCancel;
    @FXML
    private Button btnSaveLog;
    @FXML
    private Button btnClearLog;
    @FXML
    protected TextArea taOutput;
    @FXML
    protected ProgressBar progressBar1;
    @FXML
    protected Label labelStatus;
    @FXML
    private Button btnMakeTiff;
    @FXML
    private Button btnSplitTiff;
    protected static final String DIALOG_TITLE = "Train Tesseract";
    protected String tessDirectory;
    protected String trainDataDirectory;
    private DirectoryChooser fcTrainingData;
    private FileChooser fcTessExecutables;
    final Preferences prefs = MainController.prefs;

    private TrainingWorker trainWorker;
    private ResourceBundle bundle;
    private File imageFolder;
    ExtensionFilter selectedFilter;
    Logger logger = Logger.getLogger(TrainerController.class.getName());

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        tessDirectory = prefs.get("tessDirectory", MainController.WINDOWS ? new File(System.getProperty("user.dir"), "tesseract-ocr").getPath() : "/usr/bin");
        tfTessDir.setText(tessDirectory);
        tfTessDir.setOnKeyReleased(event -> {
            tessDirectory = tfTessDir.getText();
            prefs.put("tessDirectory", tfTessDir.getText());
        });
        tfDataDir.setOnKeyReleased(event -> {
            trainDataDirectory = tfDataDir.getText();
            prefs.put("trainDataDirectory", tfDataDir.getText());
        });
        tfTessDir.setStyle("-fx-focus-color: transparent;");
        fcTessExecutables = new FileChooser();
        fcTessExecutables.setTitle("选择tesseract.exe路径");
         if (!new File(tessDirectory).exists()){
            tessDirectory = "/";
            tfTessDir.setText(tessDirectory);
        }
        fcTessExecutables.setInitialDirectory(new File(tessDirectory));
        trainDataDirectory = prefs.get("trainDataDirectory", new File(System.getProperty("user.dir"), "samples/vie").getPath());
        if (!Files.exists(Paths.get(trainDataDirectory))) {
            trainDataDirectory = System.getProperty("user.home");
        }
        tfDataDir.setText(trainDataDirectory);
        tfDataDir.setStyle("-fx-focus-color: transparent;");

        fcTrainingData = new DirectoryChooser();
        fcTrainingData.setTitle("选择训练数据文件夹路径");
        fcTrainingData.setInitialDirectory(new File(trainDataDirectory));
        tfLang.setText(prefs.get("trainnedLanguage", null));
        tfBootstrapLang.setText(prefs.get("bootstrapLanguage", null));
        cbOps.getItems().addAll(TrainingMode.values());
        cbOps.getSelectionModel().select(prefs.getInt("trainingMode", 0));
        chbRTL.setSelected(prefs.getBoolean("trainingRTL", false));

        bundle = ResourceBundle.getBundle("net.sourceforge.tessboxeditor.Gui"); // NOI18N
        imageFolder = new File(prefs.get("ImageFolder", System.getProperty("user.home")));
    }

    @FXML
    protected void handleAction(ActionEvent event) {
        if (event.getSource() == btnTrain) {
            train();
        } else if (event.getSource() == btnCancel) {
            if (trainWorker != null && !trainWorker.isDone()) {
                trainWorker.cancel(true);
                taOutput.appendText("** Cancel Training **");
            }
            this.btnCancel.setDisable(true);
        } else if (event.getSource() == btnValidate) {
            validate();
        } else if (event.getSource() == btnBrowseTess) {
            File file = fcTessExecutables.showOpenDialog(btnBrowseTess.getScene().getWindow());
            if (file != null) {
                tessDirectory = file.getParentFile().getPath();
                prefs.put("tessDirectory", tessDirectory);
                tfTessDir.setText(tessDirectory);
            }
        } else if (event.getSource() == btnBrowseData) {
            File dir = fcTrainingData.showDialog(btnBrowseData.getScene().getWindow());
            if (dir != null) {
                trainDataDirectory = dir.getPath();
                prefs.put("trainDataDirectory", trainDataDirectory);
                tfDataDir.setText(trainDataDirectory);
            }
        } else if (event.getSource() == btnSaveLog) {
            if (taOutput.getLength() == 0) {
                return;
            }

            try {
                File outFile = new File(trainDataDirectory, "training.log");
                try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), StandardCharsets.UTF_8))) {
                    out.write(taOutput.getText());
                }

                String msg = String.format("Log has been saved as \"%s\".", outFile.getPath());
                Alert alert = new Alert(AlertType.NONE, msg, ButtonType.OK);
                alert.setTitle(DIALOG_TITLE);
                alert.show();
            } catch (IOException e) {
                //ignore
            }
        } else if (event.getSource() == btnClearLog) {
            this.taOutput.clear();
        } else if (event.getSource() == btnMakeTiff) {
            FileChooser fc = new FileChooser();
            fc.setTitle("选择要做成tif的图片");
            fc.setInitialDirectory(new File(trainDataDirectory));
            ExtensionFilter tiffFilter = new ExtensionFilter("TIFF", "*.tif", "*.tiff");
            ExtensionFilter jpegFilter = new ExtensionFilter("JPEG", "*.jpg", "*.jpeg");
            ExtensionFilter gifFilter = new ExtensionFilter("GIF", "*.gif");
            ExtensionFilter pngFilter = new ExtensionFilter("PNG", "*.png");
            ExtensionFilter bmpFilter = new ExtensionFilter("Bitmap", "*.bmp");
            ExtensionFilter allImageFilter = new ExtensionFilter("所有图像文件", "*.jpg", "*.jpeg", "*.gif", "*.png", "*.bmp");
            fc.getExtensionFilters().addAll(allImageFilter, tiffFilter, jpegFilter, gifFilter, pngFilter, bmpFilter);

            final List<File> inputs = fc.showOpenMultipleDialog(btnMakeTiff.getScene().getWindow());
            if (inputs != null) {
                selectedFilter = fc.getSelectedExtensionFilter();
                imageFolder = inputs.get(0).getParentFile();

                fc.setTitle(bundle.getString("Save_Multi-page_TIFF_Image"));
                fc.setInitialDirectory(imageFolder);
                fc.getExtensionFilters().clear();
                fc.getExtensionFilters().add(tiffFilter);

                File selectedFile = fc.showSaveDialog(btnMakeTiff.getScene().getWindow());
                if (selectedFile != null) {
                    if (!(selectedFile.getName().endsWith(".tif") || selectedFile.getName().endsWith(".tiff"))) {
                        selectedFile = new File(selectedFile.getParent(), selectedFile.getName() + ".tif");
                    }

                    final File outputTiff = selectedFile;
                    if (outputTiff.exists()) {
                        outputTiff.delete();
                    }

                    labelStatus.getScene().setCursor(Cursor.WAIT);
                    //labelStatus.setText("MergeTIFF_running...");
                    Task<Void> worker = new Task<Void>() {

                        @Override
                        protected Void call() throws Exception {
                            ImageIOHelper.mergeTiff(inputs.toArray(new File[0]), outputTiff);
                            return null;
                        }

                        @Override
                        protected void succeeded() {
                            super.succeeded();
                            updateMessage("Done!");
                            // labelStatus.setText(bundle.getString("MergeTIFFcompleted"));
                            new Alert(Alert.AlertType.NONE, bundle.getString("MergeTIFFcompleted") + outputTiff.getName() + bundle.getString("created"), ButtonType.OK).showAndWait();
                            labelStatus.getScene().setCursor(Cursor.DEFAULT);
                            labelStatus.setText(null);
                        }

                        @Override
                        protected void failed() {
                            super.failed();
                            Throwable ex = getException();
                            new Alert(Alert.AlertType.NONE, ex.getMessage(), ButtonType.OK).showAndWait();
//                            progressBar.setVisible(false);
                            labelStatus.setVisible(false);
                            labelStatus.getScene().setCursor(Cursor.DEFAULT);
                        }
                    };

                    new Thread(worker).start();
                }
            }
        } else if (event.getSource() == btnSplitTiff) {
            FileChooser fc = new FileChooser();
            fc.setTitle(bundle.getString("Select_Input_TIFF"));
            fc.setInitialDirectory(imageFolder);
            ExtensionFilter tiffFilter = new ExtensionFilter("TIFF", "*.tif", "*.tiff");
            fc.getExtensionFilters().add(tiffFilter);

            if (selectedFilter != null) {
                fc.setSelectedExtensionFilter(selectedFilter);
            }

            final File file = fc.showOpenDialog(btnSplitTiff.getScene().getWindow());
            if (file != null) {
                selectedFilter = fc.getSelectedExtensionFilter();
                imageFolder = file.getParentFile();

                labelStatus.setText(bundle.getString("SplitTIFF_running..."));
                //progressBar1.setVisible(true);
                labelStatus.getScene().setCursor(Cursor.WAIT);
                Task<Void> worker = new Task<Void>() {

                    @Override
                    protected Void call() throws Exception {
                        String basefilename = net.sourceforge.vietocr.util.Utils.stripExtension(file.getPath());
                        List<File> files = ImageIOHelper.createTiffFiles(file, -1, true);

                        // move temp TIFF files to selected folder
                        for (int i = 0; i < files.size(); i++) {
                            String outfilename = String.format("%s-%03d.tif", basefilename, i + 1);
                            File outfile = new File(outfilename);
                            outfile.delete();
                            files.get(i).renameTo(outfile);
                        }
                        return null;
                    }

                    @Override
                    protected void succeeded() {
                        super.succeeded();
                        updateMessage(bundle.getString("SplitTIFFcompleted"));
                        labelStatus.setText(bundle.getString("SplitTIFFcompleted"));
                        new Alert(Alert.AlertType.NONE, bundle.getString("SplitTIFFcompleted"), ButtonType.OK).showAndWait();
                        //progressBar1.setVisible(false);
                        labelStatus.getScene().setCursor(Cursor.DEFAULT);
                    }
                };

                new Thread(worker).start();
            }
        }
    }

    void train() {
        String msg = "";

        TrainingMode selectedMode = TrainingMode.getValueByDesc(this.cbOps.getSelectionModel().getSelectedItem().toString());
        if (this.tfTessDir.getLength() == 0 || this.tfDataDir.getLength() == 0) {
            msg = "选择路径错误,请检查";
        } else if (this.tfLang.getText() == null || this.tfLang.getText().trim().length() == 0) {
            msg = "请输入语言";
        } else if (selectedMode == TrainingMode.HeaderText) {
            msg = "请选择训练模式";
        }

        if (msg.length() > 0) {
            new Alert(AlertType.NONE, msg, ButtonType.OK).showAndWait();
            return;
        }

        // make sure all required data files exist before training
        if (selectedMode == TrainingMode.Train_with_Existing_Box || selectedMode == TrainingMode.Dictionary || selectedMode == TrainingMode.Train_from_Scratch) {
            final String lang = tfLang.getText();

            File font_propertiesFile = new File(trainDataDirectory, lang + ".font_properties");
            Utils.createFile(font_propertiesFile);
            File frequent_words_listFile = new File(trainDataDirectory, lang + ".frequent_words_list");
            Utils.createFile(frequent_words_listFile);
            File words_listFile = new File(trainDataDirectory, lang + ".words_list");
            Utils.createFile(words_listFile);

            boolean otherFilesExist = font_propertiesFile.exists() && frequent_words_listFile.exists() && words_listFile.exists();
            if (!otherFilesExist) {
                msg = String.format("The required file %1$s.font_properties, %1$s.frequent_words_list, or %1$s.words_list does not exist.", lang);
                new Alert(AlertType.NONE, msg, ButtonType.OK).showAndWait();
                return;
            }
        }

        String[] boxFiles = new File(trainDataDirectory).list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.endsWith(".box");
            }
        });

        // warn about potential box overwrite
        if (selectedMode == TrainingMode.Make_Box_File_Only || selectedMode == TrainingMode.Train_from_Scratch) {
            if (boxFiles.length > 0) {
                Alert alert = new Alert(AlertType.CONFIRMATION, "已经有了一个box. 继续操作将会覆盖此box");
                alert.setHeaderText(null);
                Optional<ButtonType> option = alert.showAndWait();
                if (option.isPresent() && option.get() == ButtonType.CANCEL) {
                    return;
                }
            }
        } else if (boxFiles.length == 0) {
            new Alert(AlertType.NONE, "没有找到box文件", ButtonType.OK).showAndWait();
            return;
        }

        this.btnTrain.setDisable(true);
        this.btnCancel.setDisable(false);
        this.progressBar1.setVisible(true);
        labelStatus.getScene().setCursor(Cursor.WAIT);
        taOutput.setCursor(Cursor.WAIT);
        this.btnCancel.setDisable(false);
        trainWorker = new TrainingWorker();
        new Thread(trainWorker).start();
    }

    /**
     * A worker class for training process.
     */
    public class TrainingWorker extends Task<Void> {

        TessTrainer trainer;
        long startTime;

        public TrainingWorker() {
            trainer = new TessTrainer(tessDirectory, trainDataDirectory, tfLang.getText(), tfBootstrapLang.getText(), chbRTL.isSelected());
            progressBar1.progressProperty().unbind();
            progressBar1.progressProperty().bind(this.progressProperty());
            labelStatus.textProperty().unbind();
            labelStatus.textProperty().bind(this.messageProperty());
            trainer.textProperty().addListener((observable, oldValue, newValue) -> Platform.runLater(() -> {
                taOutput.appendText(newValue + "\n");
//                        taOutput.positionCaret(taOutput.getLength());
            }));

            // listen for any failure during training
            this.exceptionProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null) {
                    Exception ex = (Exception) newValue;
                    String msg = ex.getMessage();

                    if (msg != null) {
                        new Alert(AlertType.NONE, msg, ButtonType.OK).show();
                    }
                }
            });
        }

        @Override
        protected Void call() throws Exception {
            startTime = System.currentTimeMillis();
            updateMessage("Training...");
            trainer.generate(TrainingMode.getValueByDesc(cbOps.getSelectionModel().getSelectedItem().toString()));
            return null;
        }

        @Override
        protected void succeeded() {
            super.succeeded();

            long millis = System.currentTimeMillis() - startTime;
            updateMessage("训练完成. 耗时: " + getDisplayTime(millis));

            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    progressBar1.progressProperty().unbind();
                    progressBar1.setProgress(1);
                    btnTrain.setDisable(false);
                    btnCancel.setDisable(true);
                    labelStatus.getScene().setCursor(Cursor.DEFAULT);
                    taOutput.setCursor(Cursor.DEFAULT);
                }
            });
        }

        @Override
        protected void cancelled() {
            super.cancelled();
            updateMessage("训练取消.");

            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    progressBar1.progressProperty().unbind();
                    progressBar1.setProgress(0);
                    btnTrain.setDisable(false);
                    btnCancel.setDisable(true);
                    labelStatus.getScene().setCursor(Cursor.DEFAULT);
                    taOutput.setCursor(Cursor.DEFAULT);
                }
            });
        }

        @Override
        protected void failed() {
            super.failed();
            updateMessage("训练失败!");

            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    progressBar1.progressProperty().unbind();
                    progressBar1.setProgress(0);
                    btnTrain.setDisable(false);
                    btnCancel.setDisable(true);
                    labelStatus.getScene().setCursor(Cursor.DEFAULT);
                    taOutput.setCursor(Cursor.DEFAULT);
                }
            });
        }
    }

    public static String getDisplayTime(long millis) {
        String elapsedTime = String.format("%02d:%02d:%02d",
                TimeUnit.MILLISECONDS.toHours(millis),
                TimeUnit.MILLISECONDS.toMinutes(millis) % 60,
                TimeUnit.MILLISECONDS.toSeconds(millis) % 60
        );
        return elapsedTime;
    }

    void validate() {
        // to be implemented in subclass
    }

    void setFont(Font font) {
        this.taOutput.setFont(font);
    }

    public void savePrefs() {
        if (tessDirectory != null) {
            prefs.put("tessDirectory", tessDirectory);
        }
        if (trainDataDirectory != null) {
            prefs.put("trainDataDirectory", trainDataDirectory);
        }
        if (this.tfLang.getText() != null) {
            prefs.put("trainnedLanguage", this.tfLang.getText());
        }
        if (this.tfBootstrapLang.getText() != null) {
            prefs.put("bootstrapLanguage", this.tfBootstrapLang.getText());
        }
        prefs.putInt("trainingMode", this.cbOps.getSelectionModel().getSelectedIndex());
        prefs.putBoolean("trainingRTL", this.chbRTL.isSelected());
    }

}
