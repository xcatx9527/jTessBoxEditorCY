/**
 * Copyright @ 2013 Quan Nguyen
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.sourceforge.tessboxeditor;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.text.Font;

import net.sourceforge.vietocr.util.*;
import net.sourceforge.vietpad.utilities.TextUtilities;

public class TessTrainer {

    private static final String cmdtext2image = "text2image --text=%s --outputbase=%s --font=%s --ptsize=%d --fonts_dir=%s --exposure=%d --char_spacing=%f --leading=%d --xsize=%d --ysize=%d";
    private static final String cmdmake_box = "tesseract imageFile boxFile -l bootstrapLang batch.nochop makebox";
    private static final String cmdtess_train = "tesseract imageFile boxFile box.train";
    private static final String cmdunicharset_extractor = "unicharset_extractor"; // lang.fontname.exp0.box lang.fontname.exp1.box ...
    private static final String cmdset_unicharset_properties = "set_unicharset_properties -U unicharset -O unicharset --script_dir=%s";
    private static final String cmdshapeclustering = "shapeclustering -F %s.font_properties -U unicharset"; // lang.fontname.exp0.tr lang.fontname.exp1.tr ...";
    private static final String cmdmftraining = "mftraining -F %1$s.font_properties -U unicharset -O %1$s.unicharset"; // lang.fontname.exp0.tr lang.fontname.exp1.tr ...";
    private static final String cmdcntraining = "cntraining"; // lang.fontname.exp0.tr lang.fontname.exp1.tr ...";
    private static final String cmdwordlist2dawg = "wordlist2dawg %2$s %1$s.frequent_words_list %1$s.freq-dawg %1$s.unicharset";
    private static final String cmdwordlist2dawg2 = "wordlist2dawg %2$s %1$s.words_list %1$s.word-dawg %1$s.unicharset";
    private static final String cmdpunc2dawg = "wordlist2dawg %2$s %1$s.punc %1$s.punc-dawg %1$s.unicharset";
    private static final String cmdnumber2dawg = "wordlist2dawg %2$s %1$s.numbers %1$s.number-dawg %1$s.unicharset";
    private static final String cmdbigrams2dawg = "wordlist2dawg %2$s %1$s.word.bigrams %1$s.bigram-dawg %1$s.unicharset";
    private static final String cmdcombine_tessdata = "combine_tessdata %s.";

    ProcessBuilder pb;
    String tessDir;
    String inputDataDir;
    String lang;
    String bootstrapLang;
    boolean rtl;

    private final static Logger logger = Logger.getLogger(TessTrainer.class.getName());

    public TessTrainer(String tessDir, String inputDataDir, String lang, String bootstrapLang, boolean rtl) {
        pb = new ProcessBuilder();
//        pb.directory(new File(System.getProperty("user.home")));
        pb.directory(new File(inputDataDir));
        pb.redirectErrorStream(true);

        this.tessDir = tessDir;
        this.inputDataDir = inputDataDir;
        this.lang = lang;
        this.bootstrapLang = bootstrapLang;
        this.rtl = rtl;
    }

    /**
     * Generates data based on selection of training mode.
     *
     * @param mode 1: Generate Boxes Only; 2: Train with Existing Boxes; 3:
     * Train without Boxes
     * @throws Exception
     */
    public void generate(TrainingMode mode) throws Exception {
        switch (mode) {
            case Make_Box_File_Only:
                makeBox();
                break;
            case Train_with_Existing_Box:
                generateTraineddata(true);
                break;
            case Shape_Clustering:
                runShapeClustering();
                break;
            case Dictionary:
                runDictionary();
                break;
            case Train_from_Scratch:
                generateTraineddata(false);
                break;
            default:
                break;
        }
    }

    /**
     * Run text2image command to generate Tiff/Box pair.
     * 
     * @param inputTextFile
     * @param outputbase
     * @param font
     * @param fontFolder
     * @param exposure
     * @param char_spacing or letter tracking
     * @param leading
     * @param width
     * @param height
     * @throws Exception 
     */
    void text2image(String inputTextFile, String outputbase, Font font, String fontFolder, int exposure, float char_spacing, int leading, int width, int height) throws Exception {
        logger.info("text2image");
        writeMessage("** text2image **");
        List<String> cmd = getCommand(String.format(cmdtext2image, inputTextFile, outputbase, font.getName().replace(" ", "_").replace("Oblique", "Italic"), (int) font.getSize(), fontFolder, exposure, char_spacing, leading, width, height));
        cmd.set(3, cmd.get(3).replace("_", " ")); // handle spaces in font name
        try {
            runCommand(cmd);
        } catch (Exception e) {
            String fontFamilyname = font.getFamily();
            // work around comma issue in Pango-originating fontnames
            cmd.set(3, cmd.get(3).replace(fontFamilyname, fontFamilyname + ","));
            runCommand(cmd);
        }
    }

    /**
     * Makes box files.
     *
     * @throws Exception
     */
    void makeBox() throws Exception {
        //cmdmake_box
        List<String> cmd = getCommand(cmdmake_box);

        // if no bootstrap
        if (bootstrapLang.length() == 0) {
            cmd.remove(4);
            cmd.remove(3);
        } else {
            cmd.set(4, bootstrapLang);
        }

        String[] files = getImageFiles();

        if (files.length == 0) {
            throw new RuntimeException("There are no training images.");
        }

        logger.info("Make Box Files");
        writeMessage("** Make Box Files **");
        for (String file : files) {
            cmd.set(1, file);
            cmd.set(2, TextUtilities.stripExtension(file));
            runCommand(cmd);
        }
    }

    /**
     * Generates traineddata file.
     *
     * @param skipBoxGeneration
     * @throws Exception
     */
    void generateTraineddata(boolean skipBoxGeneration) throws Exception {
        if (!skipBoxGeneration) {
            makeBox();
        }

        String[] files = getImageFilesWithBox();

        if (files.length == 0) {
            throw new RuntimeException("There are no training image/box pairs.");
        }

        logger.info("Run Tesseract for Training");
        writeMessage("** Run Tesseract for Training **");
        //cmdtess_train
        List<String> cmd = getCommand(cmdtess_train);
        for (String file : files) {
            cmd.set(1, file);
            cmd.set(2, TextUtilities.stripExtension(file));
            runCommand(cmd);
        }

        logger.info("Compute Character Set");
        writeMessage("** Compute Character Set **");
        //cmdunicharset_extractor
        cmd = getCommand(cmdunicharset_extractor);
        files = new File(inputDataDir).list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.endsWith(".box");
            }
        });
        cmd.addAll(Arrays.asList(files));
        runCommand(cmd);

        //set_unicharset_properties
        if (new File(this.tessDir, "set_unicharset_properties.exe").exists() || new File(this.tessDir, "set_unicharset_properties").exists()) {
            logger.info("Set Character Set Properties");
            writeMessage("** Set Character Set Properties **");
            cmd = getCommand(String.format(cmdset_unicharset_properties, inputDataDir));
            runCommand(cmd);
        }

//        if (rtl) {
//            //fix Unicode character directionality in unicharset
//            logger.info("Fixed unicharset's Unicode character directionality.");
//            writeMessage("Fixed unicharset's Unicode character directionality.\n");
//            fixUniCharDirectionality();
//        }
        runShapeClustering();
    }

    /**
     * Perform training from shape clustering on...
     *
     * @throws Exception
     */
    void runShapeClustering() throws Exception {
        String[] files = new File(inputDataDir).list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.endsWith(".tr");
            }
        });

        if (files.length == 0) {
            throw new RuntimeException("There are no .tr files. Need to train Tesseract first.");
        }

        logger.info("Shape Clustering");
        writeMessage("** Shape Clustering **");
        //cmdshapeclustering
        List<String> cmd = getCommand(String.format(cmdshapeclustering, lang));
        cmd.addAll(Arrays.asList(files));
        runCommand(cmd);

        logger.info("MF Training");
        writeMessage("** MF Training **");
        //cmdmftraining
        cmd = getCommand(String.format(cmdmftraining, lang));
        cmd.addAll(Arrays.asList(files));
        runCommand(cmd);

        logger.info("CN Training");
        writeMessage("** CN Training **");
        //cmdcntraining
        cmd = getCommand(cmdcntraining);
        cmd.addAll(Arrays.asList(files));
        runCommand(cmd);

        logger.info("Rename files");
        renameFile("inttemp");
        renameFile("pffmtable");
        renameFile("normproto");
        renameFile("shapetable");

        runDictionary();
    }

    /**
     * Perform training from dictionary on...
     *
     * @throws Exception
     */
    void runDictionary() throws Exception {
        if (!new File(inputDataDir, lang + ".unicharset").exists()) {
            String msg = String.format("There is no %1$s.unicharset. Need to train Tesseract first.", lang);
            throw new RuntimeException(msg);
        }

        logger.info("Dictionary Data");
        writeMessage("** Dictionary Data **");
        //cmdwordlist2dawg
        List<String> cmd = getCommand(String.format(cmdwordlist2dawg, lang, (rtl ? "-r 1" : "")));
        runCommand(cmd);

        //cmdwordlist2dawg2
        cmd = getCommand(String.format(cmdwordlist2dawg2, lang, (rtl ? "-r 1" : "")));
        runCommand(cmd);

        //cmdpunc2dawg
        if (new File(inputDataDir, lang + ".punc").exists()) {
            cmd = getCommand(String.format(cmdpunc2dawg, lang, (rtl ? "-r 2" : "")));
            runCommand(cmd);
        }

        //cmdnumber2dawg
        if (new File(inputDataDir, lang + ".numbers").exists()) {
            cmd = getCommand(String.format(cmdnumber2dawg, lang, ""));
            runCommand(cmd);
        }

        //cmdbigrams2dawg
        if (new File(inputDataDir, lang + ".word.bigrams").exists()) {
            cmd = getCommand(String.format(cmdbigrams2dawg, lang, (rtl ? "-r 1" : "")));
            runCommand(cmd);
        }

        logger.info("Combine Data Files");
        writeMessage("** Combine Data Files **");
        //cmdcombine_tessdata
        cmd = getCommand(String.format(cmdcombine_tessdata, lang));
        runCommand(cmd);

        String traineddata = lang + ".traineddata";
        logger.info("Moving generated traineddata file to tessdata folder");
        writeMessage("** Moving generated traineddata file to tessdata folder **");
        File tessdata = new File(inputDataDir, "tessdata");
        if (!tessdata.exists()) {
            tessdata.mkdir();
        }
        boolean success = new File(inputDataDir, traineddata).renameTo(new File(tessdata, traineddata));

        logger.info("Training Completed");
        writeMessage("** Training Completed **");
    }

    /**
     * Fixes Unicode Character Directionality in <code>unicharset</code> file.
     *
     * http://tesseract-ocr.googlecode.com/svn/trunk/doc/unicharset.5.html
     */
    void fixUniCharDirectionality() throws IOException {
        Path path = FileSystems.getDefault().getPath(inputDataDir, "unicharset");
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String[] parts = lines.get(i).split(" ");
            if (parts.length < 8) {
                continue;
            }

            boolean change = false;
            int codePoint = parts[0].codePointAt(0);
            String scriptName = Character.UnicodeScript.of(codePoint).toString();
            if (parts[3].equals("NULL")) {
                parts[3] = Utils.capitalize(scriptName);
                change = true;
            }

            byte diValue = Character.getDirectionality(codePoint);
            diValue = customRuleOverride(diValue);

            String diVal = String.valueOf(diValue);
            if (!parts[5].equals(diVal)) {
                parts[5] = diVal;
                change = true;
            }

            if (change) {
                lines.set(i, Utils.join(Arrays.asList(parts), " "));
            }
        }
        Files.write(path, lines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * Custom rules for overriding directionality, mainly for RTL scripts.
     *
     * @param diVal
     * @return
     */
    byte customRuleOverride(byte diVal) {
        switch (diVal) {
            case Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC:
            case Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING:
            case Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE:
                diVal = Character.DIRECTIONALITY_RIGHT_TO_LEFT;
                break;
        }

        return diVal;
    }

    /**
     * Gets training image files.
     *
     * @return
     */
    String[] getImageFiles() {
        String[] files = new File(inputDataDir).list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String filename) {
                return filename.toLowerCase().matches(".*\\.(tif|tiff|jpg|jpeg|png|bmp)$");
            }
        });

        return files;
    }

    /**
     * Gets training image files with box.
     *
     * @return
     */
    String[] getImageFilesWithBox() {
        List<String> filesWithBox = new ArrayList<String>();
        for (String file : getImageFiles()) {
            String withoutExt = TextUtilities.stripExtension(file);
            if (new File(inputDataDir, withoutExt + ".box").exists()) {
                filesWithBox.add(file);
            }
        }

        return filesWithBox.toArray(new String[0]);
    }

    /**
     * Prefixes filename with language code
     *
     * @param fileName
     */
    void renameFile(String fileName) {
        File file = new File(inputDataDir, fileName);
        if (file.exists()) {
            File fileWithPrefix = new File(inputDataDir, lang + "." + fileName);
            fileWithPrefix.delete();
            boolean result = file.renameTo(fileWithPrefix);
            String msg = (result ? "Successful" : "Unsuccessful") + " rename of " + fileName;
            writeMessage(msg);
        }
    }

    /**
     * Gets a training command.
     *
     * @param cmdStr
     * @return
     */
    List<String> getCommand(String cmdStr) {
        List<String> cmd = new LinkedList<>(Arrays.asList(cmdStr.split("\\s+")));
        cmd.set(0, tessDir + "/" + cmd.get(0));
        return cmd;
    }

    /**
     * Runs given command.
     *
     * @param cmd
     * @throws Exception
     */
    void runCommand(List<String> cmd) throws Exception {
        logger.log(Level.INFO, "Command: {0}", cmd.toString());
        writeMessage(cmd.toString());
        pb.command(cmd);
        Process process = pb.start();

        // any output?
        StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream());
        outputGobbler.start();

        int w = process.waitFor();
        logger.log(Level.INFO, "Exit value = {0}", w);
        writeMessage(outputGobbler.getMessage());

        if (w != 0) {
            String msg;
            if (cmd.get(0).contains("shapeclustering")) {
                msg = "An error has occurred. font_properties could be missing a font entry.";
            } else if (cmd.get(0).contains("text2image")) {
                msg = "text2image error.\n" + outputGobbler.getMessage().replace(",.", ".") + "Try a different font or use alternate methods.";
            } else {
                msg = outputGobbler.getMessage();
            }
            throw new RuntimeException(msg);
        }
    }

    /**
     * Writes a message.
     *
     * @param message
     */
    void writeMessage(String message) {
        setText(message);
//        System.out.println(message);
    }

    private final StringProperty textProp = new SimpleStringProperty();

    public StringProperty textProperty() {
        return textProp;
    }

    private void setText(String text) {
        textProperty().set(text);
    }
}
