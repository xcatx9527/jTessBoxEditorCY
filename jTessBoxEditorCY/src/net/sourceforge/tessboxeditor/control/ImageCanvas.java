/**
 * Copyright @ 2016 Quan Nguyen
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.sourceforge.tessboxeditor.control;

import javafx.event.EventHandler;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import net.sourceforge.tessboxeditor.datamodel.TessBox;
import net.sourceforge.tessboxeditor.datamodel.TessBoxCollection;
import net.sourceforge.vietocr.util.Utils;

import java.util.Optional;

public class ImageCanvas extends Canvas {

    private TessBoxCollection boxes;
    private TableView tableView;
    private boolean boxClickAction;
    private Image image;
    private Font font = Font.font(24);
    Tooltip tooltip;
    TessBox prevBox;
    boolean installed;
    private ScrollPane parent;

    /**
     * Creates a new instance of ImageCanvas
     */
    public ImageCanvas() {
        tooltip = new Tooltip();
        this.setOnMousePressed((MouseEvent me) -> {
            if (boxes == null || tableView == null) {
                return;
            }
            System.out.println("-->" + me.getButton().name());
            TessBox box = boxes.hitObject(new Point2D(me.getX(), me.getY()));
            if (me.getClickCount() == 2&&!me.getButton().name().equals("SECONDARY")) {
                TextInputDialog dialog = new TextInputDialog(box.getCharacter());
                dialog.setTitle(null);
                dialog.setHeaderText(null);
                dialog.setX(me.getScreenX());
                dialog.setY(me.getScreenY() + 25);
                Optional result = dialog.showAndWait();
                result.isPresent();
                if (result.isPresent()) {
                    box.setCharacter(result.get().toString());
                    paint(true);
                }
            }

            if (box == null) {
                if (!me.isControlDown()) {
                    boxes.deselectAll();
                    //repaint();
                    tableView.getSelectionModel().clearSelection();
                }
            } else {

                if (!me.isControlDown() && !me.isAltDown() && me.getClickCount() != 2) {
                    boxes.deselectAll();
                    tableView.getSelectionModel().clearSelection();
                }

                box.setSelected(!box.isSelected()); // toggle selection}
                if (me.getButton().name().equals("SECONDARY")) {
                    int begin = boxes.toList().indexOf(boxes.getSelectedBoxes().get(0));
                    String match = "";
                    if (boxes.getSelectedBoxes().get(0).getCharacter().matches("[\u4e00-\u9fa5]")) {
                        match = "[\u4e00-\u9fa5]";
                    }
                    if (boxes.getSelectedBoxes().get(0).getCharacter().matches("[0-9]")) {
                        match = "[0-9]";
                    }
                    if (boxes.getSelectedBoxes().get(0).getCharacter().matches("[a-zA-Z]")) {
                        match = "[a-zA-Z]";
                    }
                    for (int i = begin + 1; i < begin + 14; i++) {
                        if (boxes.toList().size() > i && (boxes.toList().get(i).getCharacter().matches(match)) && (boxes.toList().get(i - 1).getX() - boxes.toList().get(i).getX() < 70)) {
                            boxes.toList().get(i).setSelected(true);
                        } else {
                            break;
                        }
                    }
                    switch (match) {
                        case "[0-9]":
                            for (int i = 0; i < boxes.getSelectedBoxes().size(); i++) {
                                boxes.getSelectedBoxes().get(i).setRect(new Rectangle2D(boxes.getSelectedBoxes().get(i).getX() - 1, boxes.getSelectedBoxes().get(i).getY()-1
                                        , boxes.getSelectedBoxes().get(i).getWidth()+1, boxes.getSelectedBoxes().get(i).getHeight()+2));
                            }
                            break;
                        case "[\u4e00-\u9fa5]":
                            boxes.getSelectedBoxes().get(0).setRect(new Rectangle2D(boxes.getSelectedBoxes().get(0).getX(), boxes.getSelectedBoxes().get(0).getY()
                                    , boxes.toList().get(0).getWidth(), boxes.toList().get(0).getHeight()));
                            for (int i = 1; i < boxes.getSelectedBoxes().size(); i++) {
                                boxes.getSelectedBoxes().get(i).setRect(new Rectangle2D(boxes.getSelectedBoxes().get(i - 1).getX() + boxes.toList().get(0).getWidth(), boxes.getSelectedBoxes().get(0).getY()
                                        , boxes.toList().get(0).getWidth(), boxes.toList().get(0).getHeight()));
                            }
                            break;
                        case "[a-zA-Z]":
                            for (int i = 0; i < boxes.getSelectedBoxes().size(); i++) {
                                boxes.getSelectedBoxes().get(i).setRect(new Rectangle2D(boxes.getSelectedBoxes().get(i).getX()-2, boxes.getSelectedBoxes().get(i).getY()-1
                                        , boxes.getSelectedBoxes().get(i).getWidth()+2, boxes.getSelectedBoxes().get(i).getHeight()+1));
                            }
                            break;
                    }

                }
                if (me.isAltDown() && boxes.getSelectedBoxes().size() == 2) {//如果按下shift而且已经有选中的box的时候

                    int begin = boxes.toList().indexOf(boxes.getSelectedBoxes().get(0));
                    int last = boxes.toList().indexOf(boxes.getSelectedBoxes().get(1));
                    if (begin < last) {
                        for (int i = begin; i < last; i++) {
                            if (boxes.toList().get(i).getHeight() >= boxes.getSelectedBoxes().get(0).getHeight() - 4) {
                                boxes.toList().get(i).setSelected(true);
                            }
                        }
                    } else {
                        for (int i = last; i < begin; i++) {
                            if (boxes.toList().get(i).getHeight() >= boxes.getSelectedBoxes().get(0).getHeight() - 4) {
                                boxes.toList().get(i).setSelected(true);
                            }
                        }
                    }

                }
                //paint();
                // select corresponding table rows
                boxClickAction = true;
                java.util.List<TessBox> boxesOfCurPage = boxes.toList(); // boxes of current page

                if (!box.isSelected()) {
                    int index = boxesOfCurPage.indexOf(box);
                    tableView.getSelectionModel().clearSelection(index);
                }
                for (TessBox selectedBox : boxes.getSelectedBoxes()) {
                    int index = boxesOfCurPage.indexOf(selectedBox);
                    tableView.getSelectionModel().select(index);
                    tableView.scrollTo(index > 10 ? index - 4 : index); // fix issue with selected row pegged at the top
                }
                boxClickAction = false;
            }

            paint(true);
        });
        this.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent me) {
                if (!me.isAltDown() && !me.getButton().name().equals("SECONDARY")) {
                    Rectangle2D rectangle2D = new Rectangle2D(me.getX() - boxes.getSelectedBoxes().get(0).getWidth() / 2,
                            boxes.getSelectedBoxes().get(0).getY(), boxes.getSelectedBoxes().get(0).getWidth(), boxes.getSelectedBoxes().get(0).getHeight());
                    boxes.getSelectedBoxes().get(0).setRect(rectangle2D);
                    paint(true);
                }

            }
        });
        this.setOnMouseMoved((MouseEvent me) -> {
            if (this.boxes != null) {
                TessBox curBox = this.boxes.hitObject(me.getX(), me.getY());

                if (curBox != null) {
                    if (prevBox != curBox) {
                        prevBox = curBox;
                        String curChrs = curBox.getCharacter();
//                        Tooltip.install(this, tooltip);
                        installed = true;
                        tooltip.hide();
                        tooltip.setText(curChrs + " : " + Utils.toHex(curChrs));
                        tooltip.setFont(font);
                        tooltip.show(this, me.getScreenX(), me.getScreenY() + 25);

                    } else {
                        me.consume();
                    }
                } else {
                    if (installed) {
                        installed = false;
                        prevBox = null;
                        tooltip.hide();
                        Tooltip.uninstall(this, tooltip);
                    }
                }
            }
        });

        this.setOnMouseExited((MouseEvent me) -> {
            if (this.boxes != null) {
                tooltip.hide();
            }
        });
    }


    public void paint(boolean canSee) {
        final GraphicsContext gc = getGraphicsContext2D();

        if (image == null) {
            return;
        }

        gc.clearRect(0, 0, getWidth(), getHeight());
        gc.drawImage(image, 0, 0);
        gc.setStroke(Color.BLUE);
        if (boxes == null) {
            return;
        }
        /*if (boxes.getSelectedBoxes().size() > 0) {
            gc.setFont(new Font(16));
            gc.setFill(Color.BLACK);
            gc.fillText(boxes.getSelectedBoxes().get(0).getCharacter(), boxes.getSelectedBoxes().get(0).getX(), boxes.getSelectedBoxes().get(0).getY());
        }*/
        boolean resetColor = false;
        for (TessBox box : boxes.toList()) {
            if (canSee) {
                gc.setFont(new Font(20));
                gc.setFill(Color.BLACK);
                gc.fillText(box.getCharacter(), box.getX(), box.getY());
            }
            if (box.isSelected()) {
                gc.setLineWidth(2);
                gc.setStroke(Color.RED);
                resetColor = true;
            }
            Rectangle2D rect = box.getRect();
            gc.strokeRect(rect.getMinX(), rect.getMinY(), rect.getWidth(), rect.getHeight());
            if (resetColor) {
                gc.setLineWidth(1);
                gc.setStroke(Color.BLUE);
                resetColor = false;
            }
        }
    }

    public void setImage(Image image) {
        this.image = image;
        this.setWidth(image.getWidth());
        this.setHeight(image.getHeight());
    }

    public void setBoxes(TessBoxCollection boxes) {
        this.boxes = boxes;
        //paint();
    }

    public void setFont(Font font) {
        this.font = net.sourceforge.tessboxeditor.utilities.Utils.deriveFont(font, "", 24);
    }

    /**
     * @param table the table to set
     */
    public void setTable(TableView table, ScrollPane parent) {
        this.parent = parent;
        this.tableView = table;
    }

    /**
     * @return the boxClickAction
     */
    public boolean isBoxClickAction() {
        return boxClickAction;
    }
}
