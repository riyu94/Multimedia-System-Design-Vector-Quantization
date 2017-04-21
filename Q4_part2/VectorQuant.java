

import java.awt.image.*;
import java.io.*;
import javax.swing.*;
import java.util.*;

public class VectorQuant {

    static int IMAGE_WIDTH = 352;
    static int IMAGE_HEIGHT = 288;
    static int N;
    static Cluster[] clusters;
    static boolean isColor = false;

    public static void main(String[] args) {
       
       String fileName = args[0];
      N = Integer.parseInt(args[1]);



        if(fileName.split("\\.")[1].equals("rgb"))
            isColor = true;

        //output two images, one original image at left, the other result image at right
        BufferedImage imgOriginal = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        BufferedImage img = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);

        try {
            File file = new File(fileName);
            InputStream is = new FileInputStream(file);

            long len = file.length();
            byte[] bytes = new byte[(int)len];

            int offset = 0;
            int numRead = 0;
            while (offset < bytes.length && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
                offset += numRead;
            }

            int ind = 0;
            for(int y = 0; y < IMAGE_HEIGHT; y++){
                for(int x = 0; x < IMAGE_WIDTH; x++){
                    //for reading .raw image to show as a rgb image
                    byte r = bytes[ind];
                    byte g = r;
                    byte b = r;
                    if(isColor) {
                        g = bytes[ind + IMAGE_HEIGHT * IMAGE_WIDTH];
                        b = bytes[ind + 2 * IMAGE_HEIGHT * IMAGE_WIDTH];
                    }

                    //set pixel for display original image
                    int pixOriginal = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                    imgOriginal.setRGB(x,y,pixOriginal);
                    ind++;
                }
            }
            is.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        int[] vectorSpace = calculateVectorSpace(imgOriginal);

        //Quant
        for (int y=0; y < IMAGE_HEIGHT;y++) {
            for (int x=0; x < IMAGE_WIDTH;x += 2) {
                int clusterId = vectorSpace[IMAGE_WIDTH*y+x];
                img.setRGB(x, y, clusters[clusterId].getPixel1());
                if(x+1 < IMAGE_WIDTH)
                img.setRGB(x+1,y,clusters[clusterId].getPixel2());
            }
        }

        // Use a panel and label to display the image
        JPanel  panel = new JPanel ();
        panel.add (new JLabel (new ImageIcon (imgOriginal)));
        panel.add (new JLabel (new ImageIcon (img)));

        JFrame frame = new JFrame("Display images");

        frame.getContentPane().add (panel);
        frame.pack();
        frame.setVisible(true);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }

    public static int[] calculateVectorSpace(BufferedImage image) {
        clusters = createClusters(image);
        int[] vectorSpace = new int[IMAGE_WIDTH*IMAGE_HEIGHT];
        Arrays.fill(vectorSpace, -1);

        boolean refineNeeded = true;
        int loops = 0,pixel2 = 0;
        while (refineNeeded) {
            refineNeeded = false;
            loops++;

            for (int y=0; y < IMAGE_HEIGHT; y++) {
                for (int x=0; x < IMAGE_WIDTH; x += 2) {
                    int pixel1 = image.getRGB(x, y);
                    if(x >= IMAGE_WIDTH-2)
                    pixel2 = image.getRGB(x,y);             //pixel2 is horizontally adjacent pixel in 2 vector space
                    else
                    pixel2=image.getRGB(x+1,y);
                    Cluster cluster = getMinCluster(pixel1,pixel2);

                    if (vectorSpace[IMAGE_WIDTH*y+x] != cluster.getId()) {
                        if (vectorSpace[IMAGE_WIDTH*y+x] != -1) {
                            clusters[vectorSpace[IMAGE_WIDTH*y+x]].removePixel(pixel1,pixel2);
                        }
                        cluster.addPixel(pixel1,pixel2);
                        refineNeeded = true;
                        vectorSpace[IMAGE_WIDTH*y+x] = cluster.getId();
                    }
                }
            }
        }

        //System.out.println("Took "+ loops +" loops.");
        return vectorSpace;
    }

    //find the min distance cluster
    public static Cluster getMinCluster(int pixel1, int pixel2) {
        Cluster cluster = null;
        int min = Integer.MAX_VALUE;
        for(int i = 0;i < clusters.length;i++) {
            int distance = clusters[i].distance(pixel1,pixel2);
            if (distance < min) {
                min = distance;
                cluster = clusters[i];
            }
        }
        return cluster;
    }

    //create N numbers of clusters
    public static Cluster[] createClusters(BufferedImage image) {
        Cluster[] result = new Cluster[N];
        int x1 = 0;
        int y1 = 0;
        int x2 = 1;
        int y2 = 1;
        int dx = IMAGE_WIDTH/N;
        int dy = IMAGE_HEIGHT/N;

        for(int i=0;i < N;i++) {
            result[i] = new Cluster(i,image.getRGB(x1, y1),image.getRGB(x2,y2),isColor);
            x1 += dx;
            y1 += dy;
            x2 = x1+1;
            y2 = y1;
        }
        return result;
    }
}

class Cluster {
    int id;
    int averageRed1;
    int totalRed1;
    int averageGreen1;
    int totalGreen1;
    int averageBlue1;
    int totalBlue1;
    int averageRed2;
    int totalRed2;
    int averageGreen2;
    int totalGreen2;
    int averageBlue2;
    int totalBlue2;
    int pixelNum;
    boolean isColor;

    public Cluster(int id, int pixel1, int pixel2, boolean isColor) {
        this.id = id;
        this.isColor = isColor;
        averageRed1 = pixel1 >> 16 & 0x000000FF;
        averageRed2 = pixel2 >> 16 & 0x000000FF;

        if (isColor) {
            averageGreen1  = pixel1 >> 8 & 0x000000FF;
            averageBlue1 = pixel1 & 0x000000FF;
            averageGreen2  = pixel2 >> 8 & 0x000000FF;
            averageBlue2 = pixel2 & 0x000000FF;
        }
        addPixel(pixel1,pixel2);
    }

    int getId() {
        return id;
    }

    int getPixel1() {                                        //getpixel will have to return 2 pixels
        if (!isColor) {
            averageGreen1 = averageRed1;
            averageBlue1 = averageRed1;
        }
        return 0xff000000| averageRed1 <<16| averageGreen1 <<8| averageBlue1;
    }
    int getPixel2() {                                        //getpixel will have to return 2 pixels
        if (!isColor) {
            averageGreen2 = averageRed2;
            averageBlue2 = averageRed2;
        }
        return 0xff000000| averageRed2 <<16| averageGreen2 <<8| averageBlue2;
    }

    void addPixel(int pixel1,int pixel2) {
        int r1 = pixel1>>16&0x000000FF;
        int g1 = pixel1>>8&0x000000FF;
        int b1 = pixel1&0x000000FF;
        int r2 = pixel2>>16&0x000000FF;
        int g2 = pixel2>>8&0x000000FF;
        int b2 = pixel2&0x000000FF;
        totalRed1 += r1;
        totalRed2 += r2;
        pixelNum++;
        averageRed1 = totalRed1 /pixelNum;
        averageRed2 = totalRed2 /pixelNum;

        if (isColor) {
            totalGreen1 += g1;
            totalBlue1 += b1;
            totalGreen2 += g2;
            totalBlue2 += b2;

            averageGreen1 = totalGreen1 / pixelNum;
            averageBlue1 = totalBlue1 / pixelNum;
            averageGreen2 = totalGreen2 / pixelNum;
            averageBlue2 = totalBlue2 / pixelNum;
        }
    }

    void removePixel(int pixel1,int pixel2) {                       //incorporate both pixels
        int r1 = pixel1>>16&0x000000FF;
        int g1 = pixel1>>8&0x000000FF;
        int b1 = pixel1&0x000000FF;
        int r2 = pixel2>>16&0x000000FF;
        int g2 = pixel2>>8&0x000000FF;
        int b2 = pixel2&0x000000FF;

        totalRed1 -= r1;
        totalRed2 -= r2;
        pixelNum--;
        averageRed1 = totalRed1 /pixelNum;
        averageRed2 = totalRed2 /pixelNum;

        if (isColor) {
            totalGreen1 -= g1;
            totalBlue1 -= b1;
            averageGreen1 = totalGreen1 / pixelNum;
            averageBlue1 = totalBlue1 / pixelNum;
            totalGreen2 -= g2;
            totalBlue2 -= b2;
            averageGreen2 = totalGreen2 / pixelNum;
            averageBlue2 = totalBlue2 / pixelNum;
        }
    }

    int distance(int pixel1, int pixel2) {
        int r1 = pixel1>>16&0x000000FF;
        int g1 = pixel1>>8&0x000000FF;
        int b1 = pixel1&0x000000FF;
        int r2 = pixel2>>16&0x000000FF;
        int g2 = pixel2>>8&0x000000FF;
        int b2 = pixel2&0x000000FF;

       //int pixel_cluster_1 = 0xff000000| averageRed1 <<16| averageGreen1 <<8| averageBlue1;
        //int pixel_cluster_2 = 0xff000000| averageRed2 <<16| averageGreen2 <<8| averageBlue2;
        //int distance_cluster = (pixel_cluster_1 + pixel_cluster_2)/2;
        //int distance_pixels = (pixel1+pixel2)/2;
        //int distance = (int)Math.sqrt((Math.pow(pixel_cluster_1-pixel1,2) + Math.pow(pixel_cluster_2-pixel2,2)));


        int distance = (int)Math.sqrt((Math.pow(averageRed1 - r1,2) + Math.pow(averageRed2 - r2,2)));

        if (isColor){
            distance = (int)Math.sqrt((Math.pow(distance, 2) + Math.pow(averageGreen1 - g1, 2)
                        + Math.pow(averageBlue1 - b1, 2) + Math.pow(averageGreen2 - g2, 2) + Math.pow(averageBlue2 - b2, 2)
            ));
        }
        return distance;
    }
}