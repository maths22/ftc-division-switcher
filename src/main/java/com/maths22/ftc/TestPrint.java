package com.maths22.ftc;

import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;


public class TestPrint {
    public static void main(String args[]) {
        PrinterJob job = PrinterJob.createPrinterJob();
        job.showPrintDialog(null);

        Group root = new Group();
        Scene scene = new Scene(root, 500, 500, Color.BLACK);

        Rectangle r = new Rectangle(25,25,250,250);
        r.setFill(Color.BLUE);
        root.getChildren().add(r);

        job.printPage(root);
        job.endJob();
    }
}
