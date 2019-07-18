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
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Stage;
import net.sourceforge.tess4j.util.ImageIOHelper;
import net.sourceforge.tessboxeditor.control.ImageCanvas;
import net.sourceforge.tessboxeditor.datamodel.TessBox;
import net.sourceforge.tessboxeditor.datamodel.TessBoxCollection;
import net.sourceforge.tessboxeditor.utilities.ImageUtils;
import net.sourceforge.vietocr.util.Utils;
import net.sourceforge.vietpad.utilities.TextUtilities;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

public class BoxEditorController implements Initializable {

    @FXML
    private SplitPane spBoxImage;
    @FXML
    private Button btnOpen;
    @FXML
    private Button btnSave;
    @FXML
    private Button btnReload;
    @FXML
    private Button btnClearUp;
    @FXML
    private Button btnMovLeft;
    @FXML
    private Button btnMovRight;
    @FXML
    private Button btnMovUp;
    @FXML
    private Button btnMovBottom;
    @FXML
    private Button btnConvert;
    @FXML
    private Button btnFind;
    @FXML
    private Button btnReloadTxT;
    @FXML
    private Button btnGetData;
    @FXML
    private TextField tfFind;
    @FXML
    private TextField textPlus;
    @FXML
    protected Label labelCharacter;
    @FXML
    protected TextField tfCharacter;
    @FXML
    private TextField tfChar;
    @FXML
    private TextField tfCodepointValue;
    @FXML
    private StackPane stackPaneBoxView;
    @FXML
    protected TextField textww;
    @FXML
    protected TextField texthh;
    @FXML
    protected Spinner<Integer> spinnerH;
    @FXML
    protected Spinner<Integer> spinnerW;
    @FXML
    protected Spinner<Integer> spinnerX;
    @FXML
    protected Spinner<Integer> spinnerY;
    @FXML
    private Spinner<Integer> spnMargins;
    @FXML
    private Spinner<Integer> spnScales;
    @FXML
    protected Pagination paginationBox;
    @FXML
    private Pagination paginationPage;
    @FXML
    private Region rgn3;
    @FXML
    private TextArea taBoxData;
    @FXML
    protected TabPane tabPane;
    @FXML
    protected Tab tabBoxView;
    @FXML
    protected ImageCanvas imageCanvas;
    @FXML
    private ScrollPane scrollPaneImage;
    @FXML
    private ImageView charImageView;
    @FXML
    private Rectangle charRectangle;
    @FXML
    private Label labelPageNbr;
    @FXML
    protected TableView<TessBox> tableView;
    @FXML
    private TableColumn<TessBox, String> tcChar;
    @FXML
    private TableColumn<TessBox, Integer> tcX;
    @FXML
    private TableColumn<TessBox, Integer> tcY;
    @FXML
    private TableColumn<TessBox, Integer> tcWidth;
    @FXML
    private TableColumn<TessBox, Integer> tcHeight;
    @FXML
    private CheckBox cbCansee;
    @FXML
    public BorderPane bp_container;

    @FXML
    private TableColumn<TessBox, Integer> tcNum;

    private static final String IMAGE_PATTERN = "([^\\s]+(\\.(?i)(png|tif|tiff))$)";
    protected ResourceBundle bundle;
    final Preferences prefs = MainController.prefs;

    private File boxFile;
    protected String currentDirectory, outputDirectory;
    protected List<TessBoxCollection> boxPages;
    protected TessBoxCollection boxes; // boxes of current page
    private short imageIndex;
    private int filterIndex;
    protected List<BufferedImage> imageList;
    private boolean isTess2_0Format;
    private BooleanProperty boxChangedProp;
    protected boolean tableSelectAction;
    static final String EOL = System.getProperty("line.separator");
    final String[] headers = {"Char", "X", "Y", "Width", "Height"};
    ObservableList<ExtensionFilter> fileFilters; //extensionFilters
    FileChooser fc;
    protected static int iconMargin = 3;
    protected static int scaleFactor = 4;

    Image image;

    private final StringProperty fontFamily = new SimpleStringProperty(Font.getDefault().getFamily());
    private final IntegerProperty fontSize = new SimpleIntegerProperty((int) Font.getDefault().getSize());
    private final StringProperty style = new SimpleStringProperty();

    private final static Logger logger = Logger.getLogger(BoxEditorController.class.getName());
    private int selectedIndex;
    private File textFile;
    private String content;

    /**
     * Initializes the controller class.
     * 初始化
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        style.bind(Bindings.createStringBinding(() -> String.format(
                "-fx-font-family: \"%s\"; -fx-font-size: %d;",
                fontFamily.get(), fontSize.get()
                ), fontFamily, fontSize
        ));
        currentDirectory = prefs.get("currentDirectory", System.getProperty("user.home"));
        if (!new File(currentDirectory).exists()) {
            currentDirectory = System.getProperty("user.home");
        }
        outputDirectory = currentDirectory;
        boxPages = new ArrayList<TessBoxCollection>();
        filterIndex = prefs.getInt("filterIndex", 0);

        if (MainController.LINUX) {
            stackPaneBoxView.setStyle("-fx-background-color: LightGray;");
        }

        boxChangedProp = new SimpleBooleanProperty();
//        btnSave.disableProperty().bind(boxChangedProp.not());

        bundle = ResourceBundle.getBundle("net.sourceforge.tessboxeditor.Gui"); // NOI18N
        fc = new FileChooser();
        fc.setTitle("Open Image File");
        ExtensionFilter allImageFilter = new ExtensionFilter(bundle.getString("All_Image_Files"), "*.bmp", "*.jpg", "*.jpeg", "*.png", "*.tif", "*.tiff");
        ExtensionFilter pngFilter = new ExtensionFilter("PNG", "*.png");
        ExtensionFilter tiffFilter = new ExtensionFilter("TIFF", "*.tif", "*.tiff");

        fileFilters = fc.getExtensionFilters();
        fileFilters.addAll(allImageFilter, pngFilter, tiffFilter);
        if (filterIndex < fileFilters.size()) {
            fc.setSelectedExtensionFilter(fileFilters.get(filterIndex));
        }

        tfCharacter.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
            if (!isNowFocused && !this.btnConvert.isFocused()) {
                if (boxes != null && boxes.getSelectedBoxes().size() == 1) {
                    String str = tfCharacter.getText();
                    boxes.getSelectedBoxes().get(0).setCharacter(str);
                    tfChar.setText(str);
                    tfCodepointValue.setText(Utils.toHex(str));
                }
            }
        });

        tableView.setRowFactory(tv -> {
            TableRow<TessBox> row = new TableRow<>();
            row.styleProperty().bind(style);
            return row;
        });
        scrollPaneImage.setOnKeyPressed(event -> {
            System.out.println(event.getCode().getName());
            if (event.getCode().getName().equals("Right")) {
                int position = boxes.toList().indexOf(boxes.getSelectedBoxes().get(0));
                if (position > boxes.toList().size()) {
                    return;
                }
                boxes.toList().get(position).setSelected(false);
                boxes.toList().get(position + 1).setSelected(true);
                imageCanvas.setBoxes(boxes);
                imageCanvas.setTable(tableView, scrollPaneImage);
                imageCanvas.paint(cbCansee.isSelected());
            }
            if (event.getCode().getName().equals("Left")) {
                int position = boxes.toList().indexOf(boxes.getSelectedBoxes().get(0));
                if (position - 1 < 0) {
                    return;
                }
                boxes.toList().get(position).setSelected(false);
                boxes.toList().get(position - 1).setSelected(true);
                imageCanvas.setBoxes(boxes);
                imageCanvas.setTable(tableView, scrollPaneImage);
                imageCanvas.paint(cbCansee.isSelected());
            }
            switch (event.getText()) {
                case "a":
                    if (event.isShiftDown()) {
                        valuesChanged("a");
                    } else {
                        moveBoxes(-1, 0, 3);
                    }
                    break;
                case "z":
                    for (int i = 0; i < boxes.getSelectedBoxes().size(); i++) {
                        Rectangle2D re = new Rectangle2D(boxes.getSelectedBoxes().get(i).getX() + 1, boxes.getSelectedBoxes().get(i).getY() + 1, boxes.getSelectedBoxes().get(i).getWidth() - 1, boxes.getSelectedBoxes().get(i).getHeight() - 1);
                        boxes.getSelectedBoxes().get(i).setRect(re);
                    }
                    imageCanvas.setBoxes(boxes);
                    imageCanvas.setTable(tableView, scrollPaneImage);
                    imageCanvas.paint(cbCansee.isSelected());
                    break;
                case "r":
                    List<TessBox> se = boxes.getSelectedBoxes();
                    for (int i = 1; i < se.size(); i++) {
                        se.get(i).setRect(new Rectangle2D(se.get(i - 1).getX() + boxes.toList().get(0).getWidth(), se.get(0).getY(), boxes.toList().get(0).getWidth(), boxes.toList().get(0).getHeight()));
                    }
                    imageCanvas.setBoxes(boxes);
                    imageCanvas.setTable(tableView, scrollPaneImage);
                    imageCanvas.paint(cbCansee.isSelected());
                    break;
                case "x":
                    for (int i = 0; i < boxes.getSelectedBoxes().size(); i++) {
                        Rectangle2D re1 = new Rectangle2D(boxes.getSelectedBoxes().get(i).getX() - 1, boxes.getSelectedBoxes().get(i).getY() - 1, boxes.getSelectedBoxes().get(i).getWidth() + 2, boxes.getSelectedBoxes().get(i).getHeight() + 2);
                        boxes.getSelectedBoxes().get(i).setRect(re1);
                    }
                    imageCanvas.setBoxes(boxes);
                    imageCanvas.setTable(tableView, scrollPaneImage);
                    imageCanvas.paint(cbCansee.isSelected());
                    break;
                case "w":
                    if (event.isShiftDown()) {
                        valuesChanged("w");

                    } else {
                        moveBoxes(0, -1, 3);
                    }
                    break;
                case "d":
                    if (event.isShiftDown()) {
                        valuesChanged("d");

                    } else {
                        moveBoxes(1, 0, 3);
                    }
                    break;
                case "s":
                    if (event.isShiftDown()) {
                        valuesChanged("s");
                    } else {
                        moveBoxes(0, 1, 3);

                    }
                    break;
                case "e"://调整大小:
                    moveBoxes(0, 0, 1);
                    break;
                case "g"://调整大小:
                    TessBox box = boxes.toList().get(boxes.toList().indexOf(boxes.getSelectedBoxes().get(0)) + 1);
                    TessBox box1 = boxes.toList().get(boxes.toList().indexOf(boxes.getSelectedBoxes().get(0)));
                    box1.setCharacter(box1.getCharacter() + box.getCharacter());
                    Rectangle2D rectangle2D = new Rectangle2D(box1.getX(), box1.getY(), box.getX() - box1.getX() + box.getWidth(), box1.getHeight());
                    this.boxes.remove(box);
                    box1.setRect(rectangle2D);
                    imageCanvas.setBoxes(boxes);
                    imageCanvas.setTable(tableView, scrollPaneImage);
                    imageCanvas.paint(cbCansee.isSelected());

                    break;
                case "q"://增加:
                    if (boxes.getSelectedBoxes().size() > 1) {
                        List<TessBox> newBoses = new ArrayList<>();
                        List<TessBox> selBoxes = boxes.getSelectedBoxes();
                        for (int i = 0; i < selBoxes.size(); i++) {
                            TessBox sl = selBoxes.get(i);
                            TessBox newBox = new TessBox("a", new Rectangle2D(sl.getX(), sl.getY() +Integer.parseInt(texthh.getText()), sl.getWidth(), sl.getHeight()), sl.getPage());
                            newBoses.add(newBox);
                        }
                        for (int i = 0; i < selBoxes.size(); i++) {
                            selBoxes.get(i).setSelected(false);
                        }
                        for (int i = 0; i < newBoses.size(); i++) {
                            newBoses.get(i).setSelected(true);
                            boxes.add(newBoses.get(i));
                        }
                        imageCanvas.setBoxes(boxes);
                        imageCanvas.setTable(tableView, scrollPaneImage);
                        imageCanvas.paint(cbCansee.isSelected());
                    } else {
                        insertAction();
                    }
                    break;
                case "t"://删除
                    deleteAction();
                    break;
                case "f"://删除:
                    int position = boxes.toList().indexOf(boxes.getSelectedBoxes().get(0));
                    if (position > boxes.toList().size()) {
                        return;
                    }
                    boxes.toList().get(position).setSelected(false);
                    boxes.toList().get(position + 1).setSelected(true);
                    imageCanvas.setBoxes(boxes);
                    imageCanvas.setTable(tableView, scrollPaneImage);
                    imageCanvas.paint(cbCansee.isSelected());
                    break;
                case "c":
                    TextInputDialog dialog = new TextInputDialog(boxes.getSelectedBoxes().get(0).getCharacter());
                    dialog.setTitle(null);
                    dialog.setHeaderText(null);
                    dialog.setX(900);
                    dialog.setY(500);
                    Optional result = dialog.showAndWait();
                    result.isPresent();
                    if (result.isPresent()) {
                        boxes.getSelectedBoxes().get(0).setCharacter(result.get().toString());
                        imageCanvas.paint(cbCansee.isSelected());
                    }
                    break;
            }
        });

        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableView.getSelectionModel().selectedItemProperty().addListener((ObservableValue<? extends TessBox> obs, TessBox oldSelection, TessBox newSelection) -> {
            if (newSelection != null) {
                selectedIndex = tableView.getSelectionModel().getSelectedIndex();
                if (selectedIndex != -1) {
                    if (!imageCanvas.isBoxClickAction()) { // not from image block click
                        boxes.deselectAll();
                    }
                    ObservableList<TessBox> boxesOfCurPage = boxes.toList(); // boxes of current page
                    for (int index : tableView.getSelectionModel().getSelectedIndices()) {
                        TessBox box = boxesOfCurPage.get(index);
                        // select box
                        logger.info(box.getCharacter());
                        box.setSelected(true);
                        scrollRectToVisible(scrollPaneImage, box.getRect());
                    }
                    imageCanvas.paint(cbCansee.isSelected());

                    if (tableView.getSelectionModel().getSelectedIndices().size() == 1) {
                        enableReadout(true);
                        // update Character field
                        String str = newSelection.getCharacter();
                        tfCharacter.setText(str);
                        tfChar.setText(str);
                        tfCodepointValue.setText(Utils.toHex(str));
                        // mark this as table action event to prevent cyclic firing of events by spinners or box pagination
                        tableSelectAction = true;
                        paginationBox.setDisable(false);
                        paginationBox.setCurrentPageIndex(selectedIndex);
                        // update subimage
                        TessBox curBox = boxesOfCurPage.get(selectedIndex);
                        Rectangle2D rect = curBox.getRect();
                        updateSubimage(rect);
                        logger.info(curBox.getCharacter());
                        // update spinners
                        spinnerX.getValueFactory().setValue((int) rect.getMinX());
                        spinnerY.getValueFactory().setValue((int) rect.getMinY());
                        spinnerW.getValueFactory().setValue((int) rect.getWidth());
                        spinnerH.getValueFactory().setValue((int) rect.getHeight());
                        tableSelectAction = false;
                    } else {
                        enableReadout(false);
                        resetReadout();
                    }
                } else {
                    boxes.deselectAll();
                    imageCanvas.paint(cbCansee.isSelected());
                    enableReadout(false);
                    tableSelectAction = true;
                    resetReadout();
                    tableSelectAction = false;
                    paginationBox.setDisable(true);
                }
            } else {
                int lastSelectedIndex = tableView.getSelectionModel().getSelectedIndex();
                if (lastSelectedIndex != -1) {
                    TessBox box = boxes.toList().get(lastSelectedIndex);
                    // deselect box
                    box.setSelected(false);
                    imageCanvas.paint(cbCansee.isSelected());
                }

                tfChar.setText(null);
                tfCharacter.setText(null);
                tfCodepointValue.setText(null);
                paginationBox.setDisable(true);
                enableReadout(false);
                tableSelectAction = true;
                resetReadout();
                tableSelectAction = false;
            }
        });

//修改字符
        tcChar.setCellValueFactory(new PropertyValueFactory<>("character"));
        tcChar.setCellFactory(TextFieldTableCell.forTableColumn());
        tcChar.setOnEditCommit(e -> {
            String str = e.getNewValue();
            ((TessBox) e.getTableView().getItems().get(e.getTablePosition().getRow())).setCharacter(str);
            tfCharacter.setText(str);
            tfChar.setText(str);
            tfCodepointValue.setText(Utils.toHex(str));
        });
        tcX.setCellValueFactory(new PropertyValueFactory<>("x"));
        tcY.setCellValueFactory(new PropertyValueFactory<>("y"));
        tcWidth.setCellValueFactory(new PropertyValueFactory<>("width"));
        tcHeight.setCellValueFactory(new PropertyValueFactory<>("height"));
//序号
        tcNum.setCellValueFactory(column -> new ReadOnlyObjectWrapper<>(tableView.getItems().indexOf(column.getValue()) + 1));
        // alternatively
//        tcNum.setCellFactory(new Callback<TableColumn, TableCell>() {
//            @Override
//            public TableCell call(TableColumn p) {
//                return new TableCell() {
//                    @Override
//                    public void updateItem(Object item, boolean empty) {
//                        super.updateItem(item, empty);
//                        setGraphic(null);
//                        setText(empty ? null : String.valueOf(getIndex() + 1));
//                    }
//                };
//            }
//        });

        spBoxImage.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                File file = db.getFiles().get(0);
                boolean isAccepted = file.getName().matches(IMAGE_PATTERN);
                if (isAccepted) {
                    event.acceptTransferModes(TransferMode.COPY);
                } else {
                    event.consume();
                }
            } else {
                event.consume();
            }
        });

        spBoxImage.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                success = true;
                MainController.getInstance().openFile(db.getFiles().get(0));
            }

            event.setDropCompleted(success);
            event.consume();
        });

        this.spinnerX.valueProperty().addListener((obs, oldValue, newValue) -> {
            valuesChanged("X", newValue);
        });

        this.spinnerY.valueProperty().addListener((obs, oldValue, newValue) -> {
            valuesChanged("Y", newValue);
        });

        this.spinnerW.valueProperty().addListener((obs, oldValue, newValue) -> {
            valuesChanged("W", newValue);
        });

        this.spinnerH.valueProperty().addListener((obs, oldValue, newValue) -> {
            valuesChanged("H", newValue);
        });

        this.spnMargins.valueProperty().addListener((obs, oldValue, newValue) -> {
            iconMargin = (int) newValue;
            int index = tableView.getSelectionModel().getSelectedIndex();
            tableView.getSelectionModel().clearSelection();
            tableView.getSelectionModel().select(index);
            charImageView.requestFocus();
        });

        this.spnScales.valueProperty().addListener((obs, oldValue, newValue) -> {
            scaleFactor = (int) newValue;
            int index = tableView.getSelectionModel().getSelectedIndex();
            tableView.getSelectionModel().clearSelection();
            tableView.getSelectionModel().select(index);
            charImageView.requestFocus();
        });

        paginationBox.setStyle("-fx-page-information-visible: false;");
        paginationBox.currentPageIndexProperty().addListener((ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
            if (tableSelectAction) {
                return;
            }
            if (boxes != null) {
                tableView.getSelectionModel().clearAndSelect(newValue.intValue());
            }
        });

        paginationPage.setStyle("-fx-page-information-alignment: left;");
        paginationPage.currentPageIndexProperty().addListener((ObservableValue<? extends Number> observable, Number oldValue, Number newValue) -> {
            if (imageList != null) {
                imageIndex = newValue.shortValue();
                loadImage();
                loadTable(0, 0, 0);
            }
        });

    }

    void setMenuBar(MenuBar menuBar) {
        Menu fileMenu = menuBar.getMenus().get(0);
        FilteredList<MenuItem> menuItems = fileMenu.getItems().filtered(item -> item.getId().equals("miSave"));
        menuItems.get(0).disableProperty().bind(this.btnSave.disabledProperty());
    }

    @FXML
    protected void handleAction(ActionEvent event) {
        if (event.getSource() == btnOpen) {
            fc.setInitialDirectory(new File(currentDirectory));
            File file = fc.showOpenDialog(btnOpen.getScene().getWindow());
            if (file != null) {
                currentDirectory = file.getParent();
                filterIndex = fileFilters.indexOf(fc.getSelectedExtensionFilter());
                MainController.getInstance().openFile(file);
            }
        } else if (event.getSource() == btnClearUp) {
            cleraUpBoxes();
        } else if (event.getSource() == btnMovLeft) {
            moveBoxes(-1, 0, 1);
        } else if (event.getSource() == btnGetData) {
            setData();
        } else if (event.getSource() == btnMovRight) {
            moveBoxes(1, 0, 1);
        } else if (event.getSource() == btnReloadTxT) {
            loadBoxes(boxFile, true);
        } else if (event.getSource() == btnMovUp) {
            moveBoxes(0, -1, 1);
        } else if (event.getSource() == btnMovBottom) {
            moveBoxes(0, 1, 1);
        } else if (event.getSource() == btnSave) {
            saveAction();
        } else if (event.getSource() == btnReload) {
            if (!promptToDiscardChanges()) {
                return;
            }

            if (boxFile != null) {
                btnReload.setDisable(true);
                btnReload.getScene().setCursor(Cursor.WAIT);

                Task<Void> loadWorker = new Task<Void>() {

                    @Override
                    protected Void call() throws Exception {
                        loadBoxes(boxFile);
                        return null;
                    }

                    @Override
                    protected void succeeded() {
                        Platform.runLater(new Runnable() {
                            @Override
                            public void run() {
                                btnReload.setDisable(false);
                                btnReload.getScene().setCursor(Cursor.DEFAULT);
                            }
                        });
                    }
                };

                new Thread(loadWorker).start();
            }
        } else if (event.getSource() == btnConvert) {
            String curChar = this.tfCharacter.getText();
            if (curChar.trim().length() == 0) {
                return;
            }
            this.tfCharacter.setText(TextUtilities.convertNCR(this.tfCharacter.getText()));
            if (curChar.equals(this.tfCharacter.getText())) {
                tfCharacter.commitValue();
                handleAction(new ActionEvent(tfCharacter, null));
            }
        } else if (event.getSource() == tfCharacter) {
            if (boxes.getSelectedBoxes().size() == 1) {
                String str = tfCharacter.getText();
                boxes.getSelectedBoxes().get(0).setCharacter(str);
                tfChar.setText(str);
                tfCodepointValue.setText(Utils.toHex(str));
                boxChangedProp.set(true);
            }
        } else if (event.getSource() == btnFind || event.getSource() == tfFind) {
            if (imageList == null) {
                return;
            }
            int pageHeight = imageList.get(imageIndex).getHeight();
            String[] items = this.tfFind.getText().split("\\s+");
            try {
                TessBox findBox;

                if (items.length == 1) {
                    String chrs = items[0];
                    if (chrs.length() == 0) {
                        throw new Exception("Empty search values.");
                    }
                    chrs = TextUtilities.convertNCR(chrs);
                    findBox = new TessBox(chrs, Rectangle2D.EMPTY, imageIndex);
                    findBox = boxes.selectByChars(findBox);
                } else {
                    int x = Integer.parseInt(items[0]);
                    int y = Integer.parseInt(items[1]);
                    int w = Integer.parseInt(items[2]) - x;
                    int h = Integer.parseInt(items[3]) - y;
                    y = pageHeight - y - h; // flip the y-coordinate
                    findBox = new TessBox("", new Rectangle2D(x, y, w, h), imageIndex);
                    findBox = boxes.select(findBox);
                }

                if (findBox != null) {
                    int index = boxes.toList().indexOf(findBox);
                    this.tableView.getSelectionModel().clearAndSelect(index);
                    this.tableView.scrollTo(index > 10 ? index - 4 : index);
                } else {
                    this.tableView.getSelectionModel().clearSelection();
                    String msg = String.format("No box with the specified %s was found.", items.length == 1 ? "character(s)" : "coordinates");
                    new Alert(AlertType.NONE, msg, ButtonType.OK).showAndWait();
                }
            } catch (Exception e) {
                new Alert(AlertType.NONE, "Please enter box character(s) or coordinates (x1 y1 x2 y2).", ButtonType.OK).showAndWait();
            }
        }
    }

    private void setData() {
        int x = boxes.getSelectedBoxes().get(1).getX() - boxes.getSelectedBoxes().get(0).getX();
        int y = boxes.getSelectedBoxes().get(2).getY() - boxes.getSelectedBoxes().get(1).getY();
        textww.setText(x + "");
        texthh.setText(y + "");
    }

    private void valuesChanged(String changedValue) {
        valuesChanged(changedValue, 0);
    }

    private void valuesChanged(String changedValue, int value) {
        if (tableSelectAction || boxes == null) {
            return;
        }
        TessBox selectedBox = null;
        if (boxes.getSelectedBoxes().size() == 1) {
            selectedBox = boxes.getSelectedBoxes().get(0);
        }
        if (selectedBox != null) {
            int x = selectedBox.getX();
            int y = selectedBox.getY();
            int w = selectedBox.getWidth();
            int h = selectedBox.getHeight();
            if (changedValue.equals("X")) {
                x = value;
            } else if (changedValue.equals("Y")) {
                y = value;
            } else if (changedValue.equals("W")) {
                w = value;
            } else if (changedValue.equals("H")) {
                h = value;
            } else if (changedValue.equals("a")) {
                w -= 1;
                if (w < 0) {
                    w = 0;
                }
            } else if (changedValue.equals("s")) {
                h -= 1;
                if (h < 0) {
                    h = 0;
                }
            } else if (changedValue.equals("d")) {
                w += 1;
            } else if (changedValue.equals("w")) {
                h += 1;
            }

            Rectangle2D newRect = new Rectangle2D(x, y, w, h);
            if (!selectedBox.getRect().equals(newRect)) {
                selectedBox.setRect(newRect);
                boxChangedProp.set(true);
                imageCanvas.paint(cbCansee.isSelected());
            }

            // update subimage
            updateSubimage(newRect);
        }

    }

    void insertAction() {
        if (boxes == null) {
            return;
        }
        List<TessBox> selected = boxes.getSelectedBoxes();
        if (selected.size() <= 0) {
            Alert alert = new Alert(Alert.AlertType.NONE, "Please select the box to insert after.", ButtonType.OK);
            alert.show();
            return;
        } else if (selected.size() > 1) {
            Alert alert = new Alert(Alert.AlertType.NONE, "Please select only one box for Insert operation.", ButtonType.OK);
            alert.show();
            return;
        }

        TessBox box = selected.get(0);
        int index = this.boxes.toList().indexOf(box);
        index++;
        // offset the new box 15 pixel from the base one
        TessBox newBox = new TessBox(box.getCharacter(), new Rectangle2D(box.getX() + box.getWidth() + 5, box.getY(), box.getWidth(), box.getHeight()), box.getPage());
        boxes.add(index, newBox);
        tableView.getSelectionModel().clearAndSelect(index);
        this.imageCanvas.paint(false);
    }

    void deleteAction() {
        if (boxes == null) {
            return;
        }
        List<TessBox> selected = boxes.getSelectedBoxes();
        if (selected.size() <= 0) {
            Alert alert = new Alert(Alert.AlertType.NONE, "Please select a box or more to delete.", ButtonType.OK);
            alert.show();
            return;
        }

        this.tableView.getSelectionModel().clearSelection();
        for (TessBox box : selected) {
            this.boxes.remove(box);
        }

        resetReadout();
        this.imageCanvas.paint(false);
    }

    /**
     * Draws bounding box for individual box view.
     *
     * @param newRect
     */
    void updateSubimage(Rectangle2D newRect) {
        Image subImage = ImageUtils.getSubimage(image, newRect, iconMargin);
        Image rescaledImage = ImageUtils.resample(subImage, scaleFactor);
        charImageView.setImage(rescaledImage);
        charImageView.setFitWidth(rescaledImage.getWidth());
        charImageView.setFitHeight(rescaledImage.getHeight());
        charRectangle.setX(iconMargin * scaleFactor);
        charRectangle.setY(iconMargin * scaleFactor);
        charRectangle.setWidth(rescaledImage.getWidth() - iconMargin * scaleFactor * 2);
        charRectangle.setHeight(rescaledImage.getHeight() - iconMargin * scaleFactor * 2);
    }

    /**
     * Open image and box file.
     *
     * @param selectedFile
     */
    public void openFile(final File selectedFile) {
        if (!selectedFile.exists()) {
            Alert alert = new Alert(AlertType.ERROR, bundle.getString("File_not_exist"));
            alert.show();
            return;
        }
        if (!promptToSave()) {
            return;
        }

        Task loadWorker = new Task<Void>() {

            @Override
            public Void call() {
                readImageFile(selectedFile);
                int lastDot = selectedFile.getName().lastIndexOf(".");
                boxFile = new File(selectedFile.getParentFile(), selectedFile.getName().substring(0, lastDot) + ".box");
                loadBoxes(boxFile);
                return null;
            }
        };

        new Thread(loadWorker).start();
    }

    void readImageFile(File selectedFile) {
        try {
            imageList = ImageIOHelper.getImageList(selectedFile);
            if (imageList == null) {
                new Alert(AlertType.ERROR, bundle.getString("Cannotloadimage")).show();
                return;
            }
            imageIndex = 0;

            Platform.runLater(() -> {
                paginationPage.setPageCount(imageList.size());
                paginationPage.setCurrentPageIndex(0);
                loadImage();
                this.scrollPaneImage.setVvalue(0); // scroll to top
                this.scrollPaneImage.setHvalue(0); // scroll to left
                ((Stage) tableView.getScene().getWindow()).setTitle(JTessBoxEditor.APP_NAME + " - " + selectedFile.getName());
            });
        } catch (OutOfMemoryError oome) {
            new Alert(AlertType.ERROR, "Out-Of-Memory Exception").show();
        } catch (IOException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            if (e.getMessage() != null) {
                new Alert(AlertType.ERROR, e.getMessage()).show();
            }
        }
    }

    /**
     * 指定编码按行读取文件到字符串中
     *
     * @param file        文件
     * @param charsetName 编码格式
     * @return 字符串
     */
    public static String readFile2String(File file, String charsetName) {
        if (file == null) return null;
        BufferedReader reader = null;
        try {
            StringBuilder sb = new StringBuilder();
            if (isSpace(charsetName)) {
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            } else {
                reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), charsetName));
            }
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\r\n");// windows系统换行为\r\n，Linux为\n
            }
            // 要去除最后的换行符
            return sb.delete(sb.length() - 2, sb.length()).toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean isSpace(String s) {
        if (s == null) return true;
        for (int i = 0, len = s.length(); i < len; ++i) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    void loadBoxes(File boxFile) {
        loadBoxes(boxFile, false);
    }

    void loadBoxes(File boxFile, boolean isReLoadTxt) {
        if (boxFile.exists()) {
            try {
                if (!isReLoadTxt) {
                    boxPages.clear();
                    // load into textarea first
                    content = readBoxFile(boxFile);
                    boxPages = parseBoxString(content, imageList);
                }

                for (int i = 0; i < boxPages.size(); i++) {
                    File textFile = new File(boxFile.getParent() + "/" + (i + 1) + ".txt");
                    if (textFile.exists()) {
                        char[] texts = readFile2String(textFile, "utf-8").replace(" ", "").replace("\n", "").replace("\r", "").toCharArray();
                        ObservableList<TessBox> boxs = boxPages.get(i).toList();
                        for (int j = 0; j < texts.length; j++) {
                            if (j < boxs.size()) {
                                boxs.get(j).setCharacter(String.valueOf(texts[j]));
                            }

                        }
                    }

                }


                Platform.runLater(() -> {
                    this.taBoxData.setText(content);
                    loadTable(0, 0, 0);
                });
                boxChangedProp.set(false);
            } catch (OutOfMemoryError oome) {
                logger.log(Level.SEVERE, oome.getMessage(), oome);
                new Alert(AlertType.NONE, oome.getMessage(), ButtonType.OK).showAndWait();
            } catch (IOException | NumberFormatException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
                if (e.getMessage() != null) {
                    new Alert(AlertType.NONE, e.getMessage(), ButtonType.OK).showAndWait();
                }
            }
        } else {
            // clear table and box display
            tableView.setItems(null);
            taBoxData.setText(null);
            imageCanvas.setBoxes(null);
            imageCanvas.setTable(null, scrollPaneImage);
            imageCanvas.paint(cbCansee.isSelected());
        }
    }

    void cleraUpBoxes() {
        Platform.runLater(() -> {
            loadTable(0, 0, 2);
        });
        boxChangedProp.set(true);
    }

    void moveBoxes(int intervalX, int intervalY, int type) {
        Platform.runLater(() -> {
            loadTable(intervalX, intervalY, type);
        });
        boxChangedProp.set(true);
    }

    String readBoxFile(File boxFile) throws IOException {
        return new String(Files.readAllBytes(Paths.get(boxFile.getPath())), StandardCharsets.UTF_8);
    }

    List<TessBoxCollection> parseBoxString(String boxStr, List<BufferedImage> imageList) throws IOException {
        List<TessBoxCollection> allBoxPages = new ArrayList<TessBoxCollection>();

        String[] boxdata = boxStr.split("\\R"); // or "\\r?\\n"
        if (boxdata.length > 0) {
            // if only 5 fields, it's Tess 2.0x format
            isTess2_0Format = boxdata[0].split("\\s+").length == 5;
        }

        int startBoxIndex = 0;

        for (int curPage = 0; curPage < imageList.size(); curPage++) {
            TessBoxCollection boxCol = new TessBoxCollection();
            // Note that the coordinate system used in the box file has (0,0) at the bottom-left.
            // On computer graphics device, (0,0) is defined as top-left.
            int pageHeight = imageList.get(curPage).getHeight();
            for (int i = startBoxIndex; i < boxdata.length; i++) {
                String[] items = boxdata[i].split("(?<!^) +");

                // skip invalid data
                if (items.length < 5 || items.length > 6) {
                    continue;
                }

                String chrs = items[0];
                int x = Integer.parseInt(items[1]);
                int y = Integer.parseInt(items[2]);
                int w = Integer.parseInt(items[3]) - x;
                int h = Integer.parseInt(items[4]) - y;
                y = pageHeight - y - h; // flip the y-coordinate

                short page;
                if (items.length == 6) {
                    page = Short.parseShort(items[5]); // Tess 3.0x format
                } else {
                    page = 0; // Tess 2.0x format
                }
                if (page > curPage) {
                    startBoxIndex = i; // mark begin of next page
                    break;
                }
                boxCol.add(new TessBox(chrs, new Rectangle2D(x, y, w, h), page));
            }
            allBoxPages.add(boxCol); // add the last page
        }

        return allBoxPages;
    }

    /**
     * Displays a dialog to discard changes.
     *
     * @return false if user canceled or discard, true else
     */
    protected boolean promptToDiscardChanges() {
        if (!boxChangedProp.get()) {
            return false;
        }

        Alert alert = new Alert(AlertType.CONFIRMATION, JTessBoxEditor.APP_NAME, ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
        alert.setTitle(JTessBoxEditor.APP_NAME);
        alert.setHeaderText(null);
        alert.setContentText(bundle.getString("Do_you_want_to_discard_the_changes_to_") + boxFile.getName() + "?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.get() == ButtonType.YES) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Displays a dialog to save changes.
     *
     * @return false if user canceled, true else
     */
    protected boolean promptToSave() {
        if (!boxChangedProp.get()) {
            return true;
        }

        Alert alert = new Alert(AlertType.CONFIRMATION, JTessBoxEditor.APP_NAME, ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
        alert.setTitle(JTessBoxEditor.APP_NAME);
        alert.setHeaderText(null);
        alert.setContentText(bundle.getString("Do_you_want_to_save_the_changes_to_")
                + (boxFile == null ? bundle.getString("Untitled") : boxFile.getName()) + "?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.get() == ButtonType.YES) {
            return saveAction();
        } else if (result.get() == ButtonType.NO) {
            return true;
        } else {
            return false;
        }
    }

    //保存
    boolean saveAction() {
        if (boxFile == null || !boxFile.exists()) {
            return saveFileDlg();
        } else {
            return saveBoxFile(boxFile);
        }
    }

    //保存为
    boolean saveFileDlg() {
        FileChooser fc = new FileChooser();
        fc.setTitle(bundle.getString("Save_As"));
        fc.setInitialDirectory(new File(outputDirectory));
        ExtensionFilter boxFilter = new ExtensionFilter("Box Files", "*.box");
        fc.getExtensionFilters().addAll(boxFilter);

        if (boxFile != null) {
            fc.setInitialDirectory(boxFile.getParentFile());
            fc.setInitialFileName(boxFile.getName());
        }

        File f = fc.showSaveDialog(btnSave.getScene().getWindow());
        if (f != null) {
            outputDirectory = f.getParent();
            boxFile = f;
            return saveBoxFile(boxFile);
        } else {
            return false;
        }
    }

    //保存box文件
    boolean saveBoxFile(File file) {
        try {
            try (BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                out.write(formatOutputString(imageList, boxPages));
            }
            boxChangedProp.set(false);
        } catch (OutOfMemoryError oome) {
            logger.log(Level.SEVERE, oome.getMessage(), oome);
            new Alert(AlertType.NONE, oome.getMessage(), ButtonType.OK).showAndWait();
        } catch (FileNotFoundException fnfe) {
            logger.log(Level.SEVERE, fnfe.getMessage(), fnfe);
        } catch (Exception e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        } finally {

        }

        return true;
    }

    //格式化输出字符
    String formatOutputString(List<BufferedImage> imageList, List<TessBoxCollection> boxPages) {
        StringBuilder sb = new StringBuilder();
        for (short pageIndex = 0; pageIndex < imageList.size(); pageIndex++) {
            int pageHeight = ((BufferedImage) imageList.get(pageIndex)).getHeight(); // each page (in an image) can have different height
            for (TessBox box : boxPages.get(pageIndex).toList()) {
                Rectangle2D rect = box.getRect();
                sb.append(String.format("%s %.0f %.0f %.0f %.0f %d", box.getCharacter(), rect.getMinX(), pageHeight - rect.getMinY() - rect.getHeight(), rect.getMinX() + rect.getWidth(), pageHeight - rect.getMinY(), pageIndex)).append(EOL);
            }
        }
        if (isTess2_0Format) {
            return sb.toString().replace(" 0" + EOL, EOL); // strip the ending zeroes
        }
        return sb.toString();
    }

    //加载图片
    void loadImage() {
        image = SwingFXUtils.toFXImage(imageList.get(imageIndex), null);
        imageCanvas.setImage(image);
        tableSelectAction = true;
        resetReadout();
        tableSelectAction = false;
        imageCanvas.paint(cbCansee.isSelected());
    }

    //加载表
    void loadTable(int intervalX, int intervalY, int type) {
        if (!this.boxPages.isEmpty()) {
            switch (type) {
                case 0:
                    boxes = this.boxPages.get(imageIndex);
                    for (int i = 1; i < boxes.toList().size(); i++) {
                        if (boxes.toList().get(i).getWidth() == 0 || boxes.toList().get(i).getHeight() == 0) {
                            boxes.toList().get(i).setRect(new Rectangle2D(boxes.toList().get(i - 1).getX() + boxes.toList().get(i - 1).getWidth() + 5, boxes.toList().get(i - 1).getY(), boxes.toList().get(i - 1).getWidth(), boxes.toList().get(i - 1).getHeight()));
                        }
                    }
                    break;
                default:
                    boxes = makeOrder(boxPages.get(imageIndex), intervalX, intervalY, type);
                    break;
            }
//            boxes.deselectAll();
            tableSelectAction = true;
            paginationBox.setPageCount(boxes.toList().size());
            paginationBox.setDisable(true);
            tableSelectAction = false;
            tableView.setItems(boxes.toList());
            tableView.getSelectionModel().clearSelection();
            boxes.toList().addListener((ListChangeListener<TessBox>) change -> boxChangedProp.set(true));
            imageCanvas.setBoxes(boxes);
            imageCanvas.setTable(tableView, scrollPaneImage);
            imageCanvas.paint(cbCansee.isSelected());
        }
    }


    TessBoxCollection makeOrder(TessBoxCollection boxes, int intervalX, int intervalY, int type) {
        Rectangle2D rectangle2D = null;
        ObservableList<TessBox> listBoxes = null;
        if (boxes.getObservableSelectedBoxes() != null && boxes.getObservableSelectedBoxes().size() > 1) {
            type = 1;
        }
        int x, y, w, h = 0;
        switch (type) {
            case 1://移动整排:
                listBoxes = boxes.getObservableSelectedBoxes();

                if (listBoxes.get(0).getCharacter().matches("[\u4e00-\u9fa5]")) {
                    for (int i = 0; i < listBoxes.size(); i++) {
                        rectangle2D = new Rectangle2D(listBoxes.get(i).getX() + intervalX, listBoxes.get(0).getY() + intervalY,
                                boxes.toList().get(0).getWidth(), boxes.toList().get(0).getHeight());
                        listBoxes.get(i).setRect(rectangle2D);
                    }
                } else {
                    for (int i = 0; i < listBoxes.size(); i++) {
                        rectangle2D = new Rectangle2D(listBoxes.get(i).getX() + intervalX, listBoxes.get(i).getY() + intervalY,
                                listBoxes.get(i).getWidth(), listBoxes.get(i).getHeight());
                        listBoxes.get(i).setRect(rectangle2D);
                    }
                }
                break;
            case 3://移动整排:
                listBoxes = boxes.getObservableSelectedBoxes();
                for (int i = 0; i < listBoxes.size(); i++) {
                    x = listBoxes.get(i).getX() + i * Integer.parseInt(textww.getText());
                    if (i == 0) {
                        x = x + intervalX;
                    }
                    rectangle2D = new Rectangle2D(x, listBoxes.get(i).getY() + intervalY,
                            listBoxes.get(i).getWidth(), listBoxes.get(i).getHeight());
                    listBoxes.get(i).setRect(rectangle2D);
                }
                break;
            case 2://移动全部:
                listBoxes = boxes.toList();
                y = listBoxes.get(0).getY();
                w = listBoxes.get(0).getWidth();
                h = listBoxes.get(0).getHeight();
                if (Integer.parseInt(textPlus.getText()) == 0) {
                    new Alert(AlertType.ERROR, "请输入每行最后一个字与第一字的x之差").show();
                    return boxes;
                }
                for (int i = 1; i < listBoxes.size(); i++) {
                    logger.info((listBoxes.get(i - 1).getX() - listBoxes.get(i).getX()) + listBoxes.get(i - 1).getCharacter() + listBoxes.get(i - 1).getX() + "--" + listBoxes.get(i).getCharacter() + listBoxes.get(i).getX());
                    if ((listBoxes.get(i - 1).getX() - listBoxes.get(i).getX()) > Integer.parseInt(textPlus.getText())) {
                        y = listBoxes.get(i).getY();//取每一行中第一列box
                        x = listBoxes.get(0).getX();
                        logger.info(listBoxes.get(i).getCharacter() + "--换行了" + y);
                    } else {
                        x = listBoxes.get(i - 1).getX() + Integer.parseInt(textww.getText());
                    }
                    rectangle2D = new Rectangle2D(x, y, w, h);
                    listBoxes.get(i).setRect(rectangle2D);
                }
                break;

        }
//        boxes.setBoxes(listBoxes);
        return boxes;
    }

    //重新读取
    void resetReadout() {
        tfCharacter.setText(null);
        tfChar.setText(null);
        tfCodepointValue.setText(null);
        spinnerH.getValueFactory().setValue(0);
        spinnerW.getValueFactory().setValue(0);
        spinnerX.getValueFactory().setValue(0);
        spinnerY.getValueFactory().setValue(0);
        charImageView.setImage(null);
    }

    //允许重新读取
    void enableReadout(boolean enabled) {
        tfCharacter.setDisable(!enabled);
        spinnerX.setDisable(!enabled);
        spinnerY.setDisable(!enabled);
        spinnerH.setDisable(!enabled);
        spinnerW.setDisable(!enabled);
    }

    //设置字体
    void setFont(Font font) {
        // set font for TableColumn, TextField controls, etc.
        this.taBoxData.setFont(font);
        Font font15 = net.sourceforge.tessboxeditor.utilities.Utils.deriveFont(font, Font.getDefault().getSize());
        this.tfCharacter.setFont(font15);
        this.tfFind.setFont(font15);
        this.tfChar.setFont(font15);

        fontFamily.set(font.getFamily());
        fontSize.set((int) font.getSize());

//        Font tableFont = tableView.getFont().deriveFont(font.getSize2D());
//        tableView.setFont(tableFont);
//        FontMetrics metrics = tableView.getFontMetrics(tableFont);
//        tableView.setRowHeight(metrics.getHeight()); // set row height to match font
        //rowHeader.setFont(tableFont);
//        ((MyTableCellEditor)jTable.getDefaultEditor(String.class)).setFont(font);
        this.imageCanvas.setFont(font);
    }

    /**
     * Scrolls pane to rectangle.
     *
     * @param pane
     * @param rect
     */
    private static void scrollRectToVisible(ScrollPane pane, Rectangle2D rect) {
//        // if already visible (inside viewport), do not scroll
//        Bounds viewport = pane.getViewportBounds();

//        if (!viewport.contains(rect.getMinX(), rect.getMinY(), rect.getWidth(), rect.getHeight())) {
//            double width = pane.getContent().getBoundsInLocal().getWidth();
//            double height = pane.getContent().getBoundsInLocal().getHeight();
//
//            pane.setHvalue(rect.getMinX() / width);
//            pane.setVvalue(rect.getMinY() / height);
//        }
//        double contentHeight = pane.getContent().getBoundsInLocal().getHeight();
//        double nodeMinY = rect.getMinY();
//        double nodeMaxY = rect.getMaxY();
//        double viewportMinY = (contentHeight - viewport.getHeight()) * pane.getVvalue();
//        double viewportMaxY = viewportMinY + viewport.getHeight();
//        if (nodeMinY < viewportMinY) {
//            pane.setVvalue(nodeMinY / (contentHeight - viewport.getHeight()));
//        } else if (nodeMaxY > viewportMaxY) {
//            pane.setVvalue((nodeMaxY - viewport.getHeight()) / (contentHeight - viewport.getHeight()));
//        }
//
//        double contentWidth = pane.getContent().getBoundsInLocal().getWidth();
//        double nodeMinX = rect.getMinX();
//        double nodeMaxX = rect.getMaxX();
//        double viewportMinX = (contentWidth - viewport.getWidth()) * pane.getVvalue();
//        double viewportMaxX = viewportMinX + viewport.getWidth();
//        if (nodeMinX < viewportMinX) {
//            pane.setHvalue(nodeMinX / (contentWidth - viewport.getWidth()));
//        } else if (nodeMaxX > viewportMaxX) {
//            pane.setHvalue((nodeMaxX - viewport.getWidth()) / (contentWidth - viewport.getWidth()));
//        }
        double hmin = pane.getHmin();
        double hmax = pane.getHmax();
        double hvalue = pane.getHvalue();
        double contentWidth = pane.getContent().getLayoutBounds().getWidth();
        double viewportWidth = pane.getViewportBounds().getWidth();

        double hoffset = Math.max(0, contentWidth - viewportWidth) * (hvalue - hmin) / (hmax - hmin);

        double vmin = pane.getVmin();
        double vmax = pane.getVmax();
        double vvalue = pane.getVvalue();
        double contentHeight = pane.getContent().getLayoutBounds().getHeight();
        double viewportHeight = pane.getViewportBounds().getHeight();

        double voffset = Math.max(0, contentHeight - viewportHeight) * (vvalue - vmin) / (vmax - vmin);

        Rectangle2D viewBounds = new Rectangle2D(hoffset, voffset, viewportWidth, viewportHeight);

        // is current box inside viewport?
        if (!viewBounds.contains(rect)) {
            pane.setHvalue(rect.getMinX() / contentWidth);
            pane.setVvalue(rect.getMinY() / contentHeight);
        }
    }

    public void savePrefs() {
        if (currentDirectory != null) {
            prefs.put("currentDirectory", currentDirectory);
        }

        prefs.putInt("filterIndex", filterIndex);
    }

}
