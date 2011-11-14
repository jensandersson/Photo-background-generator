package jenstest;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.FilenameFilter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;

import com.sun.syndication.feed.synd.SyndEnclosure;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;


class PolaroidParameters {
	double randomPositionX;
	double randomPositionY;
	double randomRotation;
	double randomSize;
	double sepianess;
	
	PolaroidParameters(Random r, double theSepianess) {
		randomPositionX = BackgroundGenerator.PICTURE_PILE_STANDARD_DEVIATION*BackgroundGenerator.getGaussianDouble(r);
		randomPositionY = BackgroundGenerator.PICTURE_PILE_STANDARD_DEVIATION*BackgroundGenerator.getGaussianDouble(r);
		randomRotation = BackgroundGenerator.ROTATE_MAX_RADIANS*2.0*(r.nextDouble()-0.5);
		randomSize = (1.0-BackgroundGenerator.POLAROID_RANDOM_RESIZE_MINIMUM)*r.nextDouble()
				+ BackgroundGenerator.POLAROID_RANDOM_RESIZE_MINIMUM;
		this.sepianess = theSepianess;
	}
}

public class BackgroundGenerator {

	private static final double SPIRAL_NR_LAPS = 0; // 4
	private static final double SPIRAL_RADIUS_FACTOR = 0;  // 0.9

	private static int WALLPAPER_WIDTH = 2327; //1600;	// create a wallpaper output this wide
	private static int WALLPAPER_HEIGHT = 3319;	//1200;	// create a wallpaper output this high
	
	private static Color WALLPAPER_BACKGROUND_COLOR = Color.WHITE; //new Color(0, 78, 152);	// use this color as background to the pile of pictures

	private static double POLAROID_WIDTH_MAX = 700;	// fit polaroids in box this wide
	private static double POLAROID_HEIGHT_MAX = 700;	// fit polaroids in box this high
	
	static double POLAROID_RANDOM_RESIZE_MINIMUM = 1.0;	// then randomly resize down to this factor (i.e., 1.0 down to this)
	
	private static int POLAROID_WHITE_EDGE_PIXELS = 25;	//6;	// apply a "white polaroid" surface this wide, both x&y, around the input pictures
	private static Color POLAROID_NORMALLY_WHITE_AREA = Color.WHITE;

	private static int POLAROID_BLACK_EDGE_PIXELS = 5; //1;	// apply an outer black border this wide, both x&y, to outline the polaroid
	private static Color POLAROID_NORMALLY_BLACK_OUTLINE = Color.BLACK;
	
	private static double POLAROID_EXTRA_TEMP_SPACE = 0.6;	// 50% extra space to make room for rotation (increase if too little!)
	
	static double ROTATE_MAX_RADIANS = 0.25;	// rotate a maximum of this many radians, +/-
	
	static double PICTURE_PILE_STANDARD_DEVIATION = 0.05; //.4;	// standard deviation for the picture piling gaussian randomization

	private static String OUTPUT_FILE = "/Users/Jens/Desktop/SweTripPhotobook/Output/output";
	
	private static int PICTURES_PER_ROW = 3;				// use # pictures per row, and as many rows as necessary
	private static double PICTURE_MATRIX_SIZE_FACTOR_X = 0.5;	// use ##% of the screen area for picture matrix (center positions!)
	private static double PICTURE_MATRIX_SIZE_FACTOR_Y = 0.6;	// use ##% of the screen area for picture matrix (center positions!)
	private static double PICTURE_MATRIX_MIDDLE_VERTICAL_SPACE = 0.1;

	private static int PICTURES_PER_WALLPAPER = 9;
	
	private static boolean PUT_FIRST_IMAGE_ON_BACKGROUND = true;	// use first image in page set as background
	
	private static float BACKGROUND_IMAGE_ALPHA = 0.3f;
	
	private static float OUTPUT_JPEG_QUALITY = 0.8f;

	// ---

	
	private static BufferedImage getSepiaTonedImage(BufferedImage inputImage, double sepiaPercentage) {
		if (sepiaPercentage <= 0.0) {
			System.out.println("Sepianess is zero -- doing nothing.");
			return inputImage;
		}
		
		int w = inputImage.getWidth();
		int h = inputImage.getHeight();
		BufferedImage outputImage = new BufferedImage(
				inputImage.getWidth(),
				inputImage.getHeight(),
				BufferedImage.TYPE_3BYTE_BGR);
		WritableRaster wrIn = inputImage.getRaster();
		WritableRaster wrOut = outputImage.getRaster();
		int[] pixelData = new int[3];
		double grayness = 1.0*sepiaPercentage;
		double sepianess = 1.0*sepiaPercentage;
		for (int x=0; x<w; x++) {
			for (int y=0; y<h; y++) {
				pixelData = wrIn.getPixel(x, y, pixelData);
				int grayLevel = (pixelData[0] + pixelData[1] + pixelData[2])/3;
				pixelData[0] = (int) (grayness*grayLevel + (1-grayness)*pixelData[0]);
				pixelData[1] = (int) (grayness*grayLevel + (1-grayness)*pixelData[1]);
				pixelData[2] = (int) (grayness*grayLevel + (1-grayness)*pixelData[2]);

				pixelData[0] = (int) (255.99*Math.pow(pixelData[0]/255.0, 1-0.7*sepianess));
				pixelData[1] = (int) (255.99*Math.pow(pixelData[1]/255.0, 1-0.5*sepianess));
				//pixelData[2] = (int) (255.99*Math.pow(pixelData[2]/255.0, 1));	// no operation on blue
				wrOut.setPixel(x, y, pixelData);
			}
		}
		outputImage.setData(wrOut);
		return outputImage;
	}

	private static BufferedImage getPolaroidStyleImage(BufferedImage originalImage, PolaroidParameters pParams) {
		
		double width = originalImage.getWidth();
		double height = originalImage.getHeight();
		// fit width
		if (width > POLAROID_WIDTH_MAX) {
			double scaleFactor = POLAROID_WIDTH_MAX/width;
			width = width * scaleFactor;
			height = height * scaleFactor;
		}
		// fit height
		if (height > POLAROID_HEIGHT_MAX) {
			double scaleFactor = POLAROID_HEIGHT_MAX/height;
			width = width * scaleFactor;
			height = height * scaleFactor;
		}
		// apply additional random downsizing
		width = width * pParams.randomSize;
		height = height * pParams.randomSize;

		BufferedImage rescaledImage =
				new BufferedImage(
						(int) width,
						(int) height,
						BufferedImage.TYPE_3BYTE_BGR);  

		Graphics2D gScale = rescaledImage.createGraphics();
        gScale.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        gScale.drawImage(originalImage, 0, 0, (int) width, (int) height, 0, 0, originalImage.getWidth(), originalImage.getHeight(), null);
        gScale.dispose();
		
		BufferedImage sepiaTonedRescaled = getSepiaTonedImage(rescaledImage, pParams.sepianess);

        // create an extra large image area to have space for a) the bordering and b) rotating the image later on
        BufferedImage borderedRescaledImage =
				new BufferedImage(
						(int) (rescaledImage.getWidth()*(1+POLAROID_EXTRA_TEMP_SPACE)),
						(int) (rescaledImage.getHeight()*(1+POLAROID_EXTRA_TEMP_SPACE)),
						BufferedImage.TYPE_4BYTE_ABGR);

		Graphics2D gBordering = borderedRescaledImage.createGraphics();
		gBordering.setBackground(new Color(1.0f, 1.0f, 1.0f, 0.0f));	// color doesn't matter -- 0.0f = fully transparent
		gBordering.clearRect(0, 0, borderedRescaledImage.getWidth(), borderedRescaledImage.getHeight());

		gBordering.setBackground(POLAROID_NORMALLY_BLACK_OUTLINE);
		gBordering.clearRect(
				(int) (rescaledImage.getWidth()*POLAROID_EXTRA_TEMP_SPACE/2)-(POLAROID_BLACK_EDGE_PIXELS+POLAROID_WHITE_EDGE_PIXELS),
				(int) (rescaledImage.getHeight()*POLAROID_EXTRA_TEMP_SPACE/2)-(POLAROID_BLACK_EDGE_PIXELS+POLAROID_WHITE_EDGE_PIXELS),
				rescaledImage.getWidth()+2*(POLAROID_BLACK_EDGE_PIXELS+POLAROID_WHITE_EDGE_PIXELS),
				rescaledImage.getHeight()+2*(POLAROID_BLACK_EDGE_PIXELS+POLAROID_WHITE_EDGE_PIXELS));
		gBordering.setBackground(POLAROID_NORMALLY_WHITE_AREA);
		gBordering.clearRect(
				(int) (rescaledImage.getWidth()*POLAROID_EXTRA_TEMP_SPACE/2)-POLAROID_WHITE_EDGE_PIXELS,
				(int) (rescaledImage.getHeight()*POLAROID_EXTRA_TEMP_SPACE/2)-POLAROID_WHITE_EDGE_PIXELS,
				rescaledImage.getWidth()+2*POLAROID_WHITE_EDGE_PIXELS,
				rescaledImage.getHeight()+2*POLAROID_WHITE_EDGE_PIXELS);
		
		gBordering.drawImage(sepiaTonedRescaled, null,
				(int) (sepiaTonedRescaled.getWidth()*POLAROID_EXTRA_TEMP_SPACE/2),
				(int) (sepiaTonedRescaled.getHeight()*POLAROID_EXTRA_TEMP_SPACE/2));
		gBordering.dispose();

		BufferedImage rotatedFinalImage =
				new BufferedImage(
						borderedRescaledImage.getWidth(),
						borderedRescaledImage.getHeight(),
						BufferedImage.TYPE_4BYTE_ABGR);
		
		Graphics2D gRotation = rotatedFinalImage.createGraphics();
        gRotation.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		gRotation.rotate(pParams.randomRotation,
				rotatedFinalImage.getWidth()/2,	// rotate around center point of final image
				rotatedFinalImage.getHeight()/2);	// rotate around center point of final image
		gRotation.drawImage(borderedRescaledImage, null, 0, 0);
		gRotation.dispose();

		return rotatedFinalImage;
	}

	// return list of local file names for all jpg suffixed entries in a given directory (including that directory prefix)
	private static List<String> getImagesFromDirectory(String directory, int maxNumber) {
		File dir = new File(directory);

		FilenameFilter filter = new FilenameFilter() {
		    public boolean accept(File dir, String name) {
		        return name.toLowerCase().endsWith(".jpg");
		    }
		};
		String[] allResults = dir.list(filter);

		// sort the directory based on file name, ascending
		Arrays.sort(allResults);

		List<String> allImages = new ArrayList<String>();
		for (int i=0; i<allResults.length; i++) {
			allImages.add(directory + "/" + allResults[i]);
		}
		if (allImages.size() > maxNumber) {
			allImages = allImages.subList(0, maxNumber);	// if too many, take the N *first* entries
			//allImages = allImages.subList(allImages.size() - maxNumber, allImages.size());
		}
		return allImages;
	}
	
	// return list of URLs to RSS feed from Picasa, including only jpg suffixed URLs (not video URLs)
	private static List<String> getImagesFromXMLFeed(String xmlFeed, int maxNumber) {
		SyndFeedInput input = new SyndFeedInput();
		List<String> allImages = new ArrayList<String>();
		try {
			SyndFeed feed = input.build(new XmlReader(new URL(xmlFeed)));
	
			List<SyndEntry> entries = feed.getEntries();
			for (int i=0; i<entries.size(); i++) {
				SyndEnclosure enclosure = (SyndEnclosure) entries.get(i).getEnclosures().get(0);
				String url = enclosure.getUrl();
				if (url.toLowerCase().endsWith(".jpg")) {
					allImages.add(url);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		if (allImages.size() > maxNumber) {
			allImages = allImages.subList(0, maxNumber);	// if too many, take the N *first* entries
			//allImages = allImages.subList(allImages.size() - maxNumber, allImages.size());
		}
		return allImages;
	}

	// box-muller: gaussian random value from two rectangular ones
	public static double getGaussianDouble(Random r) {
		// for a nicer pile of pictures with more around the center
		double u1 = r.nextDouble();
		double u2 = r.nextDouble();
		return Math.sqrt(-2.0*Math.log(u1)) * Math.cos(2.0*Math.PI*u2);
	}

	// from a given source location (local folder or Picasa RSS link), pick at most the first N pictures and
	// generate a nice little pile of pictures (on top of a solid colour background).
	public static void generateWallpaper(String srcLocation, int maxNrOfImagesToUse) {
		List<String> inputFiles = null;
		if (srcLocation.toLowerCase().startsWith("http")) {
			inputFiles = getImagesFromXMLFeed(srcLocation, maxNrOfImagesToUse);
		} else {
			inputFiles = getImagesFromDirectory(srcLocation, maxNrOfImagesToUse);
		}
		if (inputFiles.size() <= PICTURES_PER_WALLPAPER) {
			generateWallpaper(inputFiles, maxNrOfImagesToUse, OUTPUT_FILE);
		} else {
			int totalBatches = (int) Math.ceil(1.0*inputFiles.size()/PICTURES_PER_WALLPAPER);
			for (int batchNr = 0; batchNr < totalBatches; batchNr++) {
				int firstInBatch = batchNr*PICTURES_PER_WALLPAPER;
				int endOfBatch = Math.min(firstInBatch+PICTURES_PER_WALLPAPER, inputFiles.size());
				List<String> inputFilesSublist = inputFiles.subList(firstInBatch, endOfBatch);
				generateWallpaper(inputFilesSublist, maxNrOfImagesToUse, OUTPUT_FILE + String.format("%03d", batchNr));
			}
		}
	}

	public static void drawImageCoveringBackground(BufferedImage image, Graphics2D gfxOfBackground, int backgroundWidth, int backgroundHeight) {
		double scaleFactor = Math.max(
				1.0*backgroundWidth / image.getWidth(),
				1.0*backgroundHeight / image.getHeight() );

		System.out.println(scaleFactor);
		/*
		BufferedImage imageWithAlpha = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
		Graphics2D gAlpha = imageWithAlpha.createGraphics();
		gAlpha.drawImage(image, 0, 0, null);
		gAlpha.dispose();

		BufferedImage imageWithTransparency = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
		// Create a rescale filter op that makes the image 50% opaque 
		float[] scales = { 0.5f, 1f, 1f, 1f };
		float[] offsets = new float[4];
		RescaleOp rop = new RescaleOp(scales, offsets, null);
		
		Graphics2D g = imageWithTransparency.createGraphics();

		g.drawImage(imageWithAlpha, rop, 0, 0);

		//g.dispose();
		*/
		Composite origComposite = gfxOfBackground.getComposite();
		gfxOfBackground.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, BACKGROUND_IMAGE_ALPHA));

		gfxOfBackground.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		gfxOfBackground.drawImage(
				image,
				(int) (backgroundWidth/2 - scaleFactor*image.getWidth()/2),
				(int) (backgroundHeight/2 - scaleFactor*image.getHeight()/2),
				(int) (backgroundWidth/2 + scaleFactor*image.getWidth()/2),
				(int) (backgroundHeight/2 + scaleFactor*image.getHeight()/2),
				0, 0, image.getWidth(), image.getHeight(), null);
		gfxOfBackground.setComposite(origComposite);
	}
	
	public static void generateWallpaper(List<String> inputFiles, int maxNrOfImagesToUse, String outputFilename) {
		//Random r = new Random(4711);
		//Random r = new Random(srcLocation.hashCode());
		Random r = new Random();

		BufferedImage background = new BufferedImage(WALLPAPER_WIDTH, WALLPAPER_HEIGHT, BufferedImage.TYPE_3BYTE_BGR); //BufferedImage.TYPE_4BYTE_ABGR);
		Graphics2D gBackground = background.createGraphics();
		gBackground.setBackground(WALLPAPER_BACKGROUND_COLOR);
		gBackground.clearRect(0, 0, WALLPAPER_WIDTH, WALLPAPER_HEIGHT);
		boolean putFirstImageOnBackground = PUT_FIRST_IMAGE_ON_BACKGROUND;

		int skipNrOfImages = inputFiles.size() - maxNrOfImagesToUse;
		if (skipNrOfImages < 0) {
			skipNrOfImages = 0;
		}
		int actualPictureCount = inputFiles.size()-skipNrOfImages;
		int n = 0;
		for (String inputFile : inputFiles) {
			int selectedPictureNumber = n-skipNrOfImages;
			//double sepianess = Math.min(1.0, Math.max(0, 1.0-(1.0*(n-skipNrOfImages)/actualPictureCount)));	// limit sepianess to between 0 and 1, if arguments are strange
			double sepianess = 0.0;
			PolaroidParameters pParams = new PolaroidParameters(r, sepianess);	// first, use up another few random values from the feed

			if (n >= skipNrOfImages) {
				BufferedImage origImg = null;
				try {
					if (inputFile.toLowerCase().startsWith("http")) {
						origImg = ImageIO.read(new URL(inputFile));
					} else {
						origImg = ImageIO.read(new File(inputFile));
					}
					
					if (putFirstImageOnBackground) {
						drawImageCoveringBackground(origImg, gBackground, background.getWidth(), background.getHeight());
					}
					putFirstImageOnBackground = false;

					BufferedImage currentImage = getPolaroidStyleImage(origImg, pParams);

					int centerX = background.getWidth()/2;
					int centerY = background.getHeight()/2;

					double spiralPosition = 1.0 - sepianess;
					centerX += spiralPosition*SPIRAL_RADIUS_FACTOR*(background.getWidth()/2)*Math.sin(spiralPosition*Math.PI*2*SPIRAL_NR_LAPS);
					centerY -= spiralPosition*SPIRAL_RADIUS_FACTOR*(background.getHeight()/2)*Math.cos(spiralPosition*Math.PI*2*SPIRAL_NR_LAPS);
					// Math.sqrt(centerX*centerX+centerY*centerY)
					
					// if using row-column placement of pictures, adjust based on row/column here
					int requiredRows = (int) Math.ceil(1.0*actualPictureCount / PICTURES_PER_ROW);
					int picRow = selectedPictureNumber / PICTURES_PER_ROW;
					int picCol = selectedPictureNumber % PICTURES_PER_ROW;
					if (PICTURES_PER_ROW > 1) {
						centerX += (PICTURE_MATRIX_SIZE_FACTOR_X*background.getWidth()/(PICTURES_PER_ROW-1))*picCol - PICTURE_MATRIX_SIZE_FACTOR_X*background.getWidth()/2;
					}
					if (requiredRows > 1) {
						if (PICTURE_MATRIX_MIDDLE_VERTICAL_SPACE > 0) {
							centerY += (int) (PICTURE_MATRIX_MIDDLE_VERTICAL_SPACE*background.getHeight() * ((picRow < requiredRows/2) ? -1 : 1));
						}
						centerY += (PICTURE_MATRIX_SIZE_FACTOR_Y*background.getHeight()/(requiredRows-1))*picRow - PICTURE_MATRIX_SIZE_FACTOR_Y*background.getHeight()/2;
					}

					// pick a random position, normally-distributed with some std dev, with background center position as (0,0)
					int posX = centerX + (int) (pParams.randomPositionX*background.getWidth()/2);
					int posY = centerY + (int) (pParams.randomPositionY*background.getHeight()/2);
					
					posX -= currentImage.getWidth()/2;
					posY -= currentImage.getHeight()/2;
					/*
					// if it ends up outside, move it back inside
					while (posX < 0) posX += background.getWidth();
					while (posX > background.getWidth()) posX -= background.getWidth();
					while (posY < 0) posY += background.getHeight();
					while (posY > background.getHeight()) posY -= background.getHeight();
					*/
					// draw the current image at its randomized (own center) position
					gBackground.drawImage(currentImage, null, posX, posY);

				}
				catch (Exception e) {
					System.err.println("! Error for picture: " + inputFile);
					e.printStackTrace();	// just skip images which fail to load/process
				}
			}
			n++;
		}
		gBackground.dispose();

		try {
			// Save without setting quality settings:
			//ImageIO.write(background, "jpg", new File(outputFilename + ".jpg"));
			
			// Slightly dirty code (just take first ImageWriter) to write with custom quality
			Iterator iter = ImageIO.getImageWritersByFormatName("jpg");
			ImageWriter writer = (ImageWriter)iter.next();
			ImageWriteParam iwp = writer.getDefaultWriteParam();
			iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			iwp.setCompressionQuality(OUTPUT_JPEG_QUALITY); // 0.0f=low quality, 1.0f=highest
			
			File file = new File(outputFilename + ".jpg");
			FileImageOutputStream output = new FileImageOutputStream(file);
			writer.setOutput(output);
			IIOImage image = new IIOImage(background, null, null);
			writer.write(null, image, iwp);
			writer.dispose();
		}
		catch (Exception e) {
			e.printStackTrace();	// couldn't write output -- file likely already open?
		}
		System.out.println("Input size was " + inputFiles.size() + " images (limited at " + maxNrOfImagesToUse + "), skipped " + skipNrOfImages);
	}

	
	/*
	int bw = background.getWidth();
	int bh = background.getHeight();
	
	int dx1 = posX;
	int dy1 = posY;
	int dx2 = posX+currentImage.getWidth();
	int dy2 = posY+currentImage.getHeight();
	
	int sx1 = 0;
	int sy1 = 0;
	int sx2 = currentImage.getWidth();
	int sy2 = currentImage.getHeight();

	if (dx1<0) {
		sx1 += -dx1;
		dx1=0;
	}
	if (dy1<0) {
		sy1 += -dy1;
		dy1=0;
	}
	if (dx1+(sx2-sx1)>bw) {
		sx2 -= dx1+(sx2-sx1) - bw;
	}
	if (dy1+(sy2-sy1)>bh) {
		sy2 -= dy1+(sy2-sy1) - bh;
	}
	
	if (dx1<bw && dy1<bh && sx1 != sx2 && sy1 != sy2) {
		gDrawCurrentPicture.drawImage(currentImage, dx1, dy1, dx2, dy2, sx1, sy1, sx2, sy2, null);
	}
	*/
	
	public static void main(String[] args) throws Exception {
		Date start = new Date();
		
		// first argument = location: local drive folder, or RSS feed from Picasa
		// second argument = max number of pictures to use (counting from the start of the listing
		//generateWallpaper(args[0], Integer.parseInt(args[1]));

		// jhead for rotation fixing: http://www.sentex.net/~mwandel/jhead/usage.html
		// (for rotation fixing (EXIF vs true data in jpg), "jhead -autorot *.jpg" came in handy... )
		
		// exiftool for renaming files to alphabetical by creation date: http://www.sno.phy.queensu.ca/~phil/exiftool/exiftool_pod.html
		// (for file name renaming, this came in handy (rename all files to YYMMDDHHMMSS.jpg format: exiftool '-FileName<CreateDate' -d %Y%m%d_%H%M%S%%-c.%%e directory )
		
		// example: local folder, all images:
		generateWallpaper("/Users/Jens/Desktop/untitled folder", 24);
		
		Date end = new Date();
		System.out.println("Done! Took " + String.format("%1.1f", 0.001*(end.getTime()-start.getTime())) + " seconds. Output here: " + OUTPUT_FILE);
	}
}
