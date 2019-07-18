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

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.stage.FileChooser.ExtensionFilter;

import java.io.File;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

public class MenuToolsController implements Initializable {


    MenuBar menuBar;


    protected ResourceBundle bundle;
    final Preferences prefs = MainController.prefs;
    File imageFolder;
    ExtensionFilter selectedFilter;

    private final static Logger logger = Logger.getLogger(MenuToolsController.class.getName());

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        bundle = ResourceBundle.getBundle("net.sourceforge.tessboxeditor.Gui"); // NOI18N
        imageFolder = new File(prefs.get("ImageFolder", System.getProperty("user.home")));
    }

    void setMenuBar(MenuBar menuBar) {
        this.menuBar = menuBar;
    }

    @FXML
    private void handleAction(ActionEvent event) {
        Label labelStatus = (Label) menuBar.getScene().lookup("#labelStatus");

     /*   if (event.getSource() == miMergeTIFF ) {
            FileChooser fc = new FileChooser();
            fc.setTitle(bundle.getString("Select_Input_Images"));
            fc.setInitialDirectory(imageFolder);
            ExtensionFilter tiffFilter = new ExtensionFilter("TIFF", "*.tif", "*.tiff");
            ExtensionFilter jpegFilter = new ExtensionFilter("JPEG", "*.jpg", "*.jpeg");
            ExtensionFilter gifFilter = new ExtensionFilter("GIF", "*.gif");
            ExtensionFilter pngFilter = new ExtensionFilter("PNG", "*.png");
            ExtensionFilter bmpFilter = new ExtensionFilter("Bitmap", "*.bmp");
            ExtensionFilter allImageFilter = new ExtensionFilter(bundle.getString("All_Image_Files"), "*.tif", "*.tiff", "*.jpg", "*.jpeg", "*.gif", "*.png", "*.bmp");
            fc.getExtensionFilters().addAll(tiffFilter, jpegFilter, gifFilter, pngFilter, bmpFilter, allImageFilter);

            final List<File> inputs = fc.showOpenMultipleDialog(menuBar.getScene().getWindow());
            if (inputs != null) {
                selectedFilter = fc.getSelectedExtensionFilter();
                imageFolder = inputs.get(0).getParentFile();

                fc.setTitle(bundle.getString("Save_Multi-page_TIFF_Image"));
                fc.setInitialDirectory(imageFolder);
                fc.getExtensionFilters().clear();
                fc.getExtensionFilters().add(tiffFilter);

                File selectedFile = fc.showSaveDialog(menuBar.getScene().getWindow());
                if (selectedFile != null) {
                    if (!(selectedFile.getName().endsWith(".tif") || selectedFile.getName().endsWith(".tiff"))) {
                        selectedFile = new File(selectedFile.getParent(), selectedFile.getName() + ".tif");
                    }

                    final File outputTiff = selectedFile;
                    if (outputTiff.exists()) {
                        outputTiff.delete();
                    }

                    labelStatus.setText(bundle.getString("MergeTIFF_running..."));
                    labelStatus.getScene().setCursor(Cursor.WAIT);
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
                            labelStatus.setText(bundle.getString("MergeTIFFcompleted"));
                            new Alert(Alert.AlertType.NONE, bundle.getString("MergeTIFFcompleted") + outputTiff.getName() + bundle.getString("created"), ButtonType.OK).showAndWait();
                            labelStatus.getScene().setCursor(Cursor.DEFAULT);
                            labelStatus.setText(null);
                        }

                        @Override
                        protected void failed() {
                            super.failed();
                            Throwable ex = getException();
//                            logger.log(Level.SEVERE, ex.getMessage(), ex);
                            new Alert(Alert.AlertType.NONE, ex.getMessage(), ButtonType.OK).showAndWait();
//                            progressBar.setVisible(false);
                            labelStatus.setVisible(false);
                            labelStatus.getScene().setCursor(Cursor.DEFAULT);
                        }
                    };

                    new Thread(worker).start();
                }
            }
        } else if (event.getSource() == miSplitTIFF ) {
            FileChooser fc = new FileChooser();
            fc.setTitle(bundle.getString("Select_Input_TIFF"));
            fc.setInitialDirectory(imageFolder);
            ExtensionFilter tiffFilter = new ExtensionFilter("TIFF", "*.tif", "*.tiff");
            fc.getExtensionFilters().add(tiffFilter);

            if (selectedFilter != null) {
                fc.setSelectedExtensionFilter(selectedFilter);
            }

            final File file = fc.showOpenDialog(menuBar.getScene().getWindow());
            if (file != null) {
                selectedFilter = fc.getSelectedExtensionFilter();
                imageFolder = file.getParentFile();

                labelStatus.setText(bundle.getString("SplitTIFF_running..."));
                //progressBar1.setVisible(true);
                labelStatus.getScene().setCursor(Cursor.WAIT);
                Task<Void> worker = new Task<Void>() {

                    @Override
                    protected Void call() throws Exception {
                        String basefilename = Utils.stripExtension(file.getPath());
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
        }*/
    }

}
