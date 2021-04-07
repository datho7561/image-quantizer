///usr/bin/env jbang "$0" "$@" ; exit $?

import static java.lang.System.exit;
import static java.lang.System.out;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

public class ImageQuantizer {

	public static void main(String... args) throws Exception {
		validateArgs(args);
		File inputFile = new File(args[0]);
		if (!inputFile.exists()) {
			System.err.println("Image " + inputFile.getAbsolutePath() + " does not exist!");
			exit(1);
		}
		BufferedImage inputImage = ImageIO.read(new File(args[0]));
		BufferedImage outputImage;
		// pre blur to reduce noise
		outputImage = ImageBlur.imageBlur(inputImage, 5);
		// quantize to palette
		outputImage = ImageQuantize.quantizeImage(outputImage, Palettes.MONOKAI);
		ImageIO.write(outputImage, "png", new FileOutputStream(new File(args[1])));
	}

	public static void validateArgs(String... args) {
		if (Arrays.asList(args).stream().filter(arg -> "--help".equals(arg)).collect(Collectors.toList()).size() > 0) {
			usage(Optional.empty());
			exit(0);
		}
		if (args.length != 2) {
			usage(Optional.of("Wrong number of arguments: " + args.length));
			exit(1);
		}
	}

	public static void usage(Optional<String> reason) {
		reason.ifPresent(r -> {
			System.err.println(r);
			System.err.println();
		});
		out.println("Usage: " + ImageQuantizer.class.getName() + ".java [IMAGE] [OUT]");
	}
}

/**
 * Static class to quantize a buffered image to a palette
 */
class ImageQuantize {

	private ImageQuantize() {
	}

	/**
	 * Returns a copy of the given image that is quantized to the given palette
	 *
	 * @param image   the image to quantize
	 * @param palette the palette to quantize the image to
	 * @return a copy of the given image that is quantized to the given palette
	 */
	public static BufferedImage quantizeImage(BufferedImage image, List<Color> palette) {
		if (palette.size() == 0) {
			throw new IllegalArgumentException("palette must consist of more than zero colours");
		}
		BufferedImage outputImage = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());

		int availableProcessors = Runtime.getRuntime().availableProcessors();

		// multi thread it to make it speed
		if (GoatUtils.isGoat() /* availableProcessors > 1 */) {
			multiThreadQuantize(image, outputImage, palette, availableProcessors);
		} else {
			singleThreadQuantize(image, outputImage, palette);
		}

		return outputImage;
	}

	private static void singleThreadQuantize(BufferedImage input, BufferedImage output, List<Color> palette) {
		quantizeRange(input, output, 0, input.getWidth(), 0, input.getHeight(), palette);
	}

	private static void multiThreadQuantize(BufferedImage input, BufferedImage output, List<Color> palette,
			int availableProcessors) {
		// TODO:
	}

	/**
	 * Quantize the specified rectangle, exclusive of the bottom and right edges
	 *
	 * @param input
	 * @param output
	 * @param startX
	 * @param endX
	 * @param startY
	 * @param endY
	 * @param palette
	 */
	private static void quantizeRange(BufferedImage input, BufferedImage output, int startX, int endX, int startY,
			int endY, List<Color> palette) {
		for (int x = startX; x < endX; x++) {
			for (int y = startY; y < endY; y++) {
				Color toMatch = new Color(input.getRGB(x, y));
				output.setRGB(x, y, Palettes.findClosestColour(toMatch, palette).getRGB());
			}
		}
	}

}

/**
 * Bargain bin Bokeh blur emulation
 */
class ImageBlur {

	private ImageBlur() {
	}

	/**
	 * Returns a copy of the given image that is blurred
	 *
	 * @param image  the image to blur
	 * @param radius the radius of the blur effect
	 * @return a copy of the given image that is blurred
	 */
	public static BufferedImage imageBlur(BufferedImage image, int radius) {
		Kernel kernel = makeBlurKernel(radius);
		ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);
		BufferedImage outputImage = new BufferedImage(image.getWidth(), image.getHeight(), image.getType());
		op.filter(image, outputImage);
		return outputImage;
	}

	/**
	 * Bargain Bokeh blur
	 */
	private static Kernel makeBlurKernel(int radius) {
		// the kernel is an aliased circle,
		// with the amplitude divided by the number of filled pixels
		int filled = 0;
		int size = radius * 2 + 1;
		float[] kernelArray = new float[size * size];

		for (int x = 0; x < size; x++) {
			for (int y = 0; y < size; y++) {

				double x2 = x - radius;
				x2 *= x2;
				double y2 = y - radius;
				y2 *= y2;

				double dist = Math.sqrt(x2 + y2);

				if (dist < radius) {
					kernelArray[y * size + x] = 1;
					filled++;
				} else {
					kernelArray[y * size + x] = 0;
				}
			}
		}

		for (int i = 0; i < kernelArray.length; i++) {
			kernelArray[i] /= filled;
		}

		return new Kernel(size, size, kernelArray);
	}

}

/**
 * Utilities for working with java.awt.Color
 */
class ColorUtils {

	private ColorUtils() {
	}

	private static final Pattern HEX_CODE_PATTERN = Pattern.compile("#?([0-9A-F]{6})");

	/**
	 * Returns the given hex colour code as a Color
	 *
	 * @param hex the hex colour code as a string
	 * @throws IllegalArgumentException if the string passed to the function is not
	 *                                  a hex colour code
	 * @return the given hex colour code as a Color
	 */
	public static Color createColourFromHex(String hex) {
		hex = hex.toUpperCase();
		Matcher m = HEX_CODE_PATTERN.matcher(hex);
		if (!m.matches()) {
			throw new IllegalArgumentException("hex code must be in the form '#Abc123' or '1aB2C3'");
		}
		hex = m.group(1);

		return new Color(Integer.parseInt(hex.substring(0, 2), 16), Integer.parseInt(hex.substring(2, 4), 16),
				Integer.parseInt(hex.substring(4, 6), 16));
	}

	/**
	 * Returns the 3 dimensional Gaussian distance between the two colours
	 *
	 * eg. consider each colour band as a dimension, use the 3 dimensional Gaussian
	 * distance to calculate a distance between the two colours
	 *
	 * @param a the first colour
	 * @param b the second colour
	 * @return the 3 dimensional Gaussian distance between the two colours
	 */
	public static double gaussianDistance(Color a, Color b) {
		return Math.sqrt(Math.pow(a.getRed() - b.getRed(), 2) + Math.pow(a.getGreen() - b.getGreen(), 2)
				+ Math.pow(a.getBlue() - b.getBlue(), 2));
	}

}

/**
 * Colour palettes and tools to work with colour palettes
 */
class Palettes {

	// This my "head canon" Monokai, not the original
	public static List<Color> MONOKAI = Arrays.asList( //
			ColorUtils.createColourFromHex("#1e1f1c"), //
			ColorUtils.createColourFromHex("#EAE9E1"), //
			ColorUtils.createColourFromHex("#272822"), //
			ColorUtils.createColourFromHex("#f92672"), //
			ColorUtils.createColourFromHex("#A6E22E"), //
			ColorUtils.createColourFromHex("#E6DB74"), //
			ColorUtils.createColourFromHex("#6A7EC8"), //
			ColorUtils.createColourFromHex("#AE81FF"), //
			ColorUtils.createColourFromHex("#66D9EF"), //
			ColorUtils.createColourFromHex("#f8f8f2"), //
			ColorUtils.createColourFromHex("#414339"), //
			ColorUtils.createColourFromHex("#CECCC0") //
	);

	public static List<Color> MONOCHROME = Arrays.asList(new Color(0, 0, 0), new Color(255, 255, 255));

	/**
	 * Returns the best match for the colour in the palette
	 *
	 * @param toMatch colour to match
	 * @param palette palette of 1+ palette
	 * @throws IllegalArgumentException if the palette has 0 colours
	 * @return the best match for the colour in the palette
	 */
	public static Color findClosestColour(Color toMatch, List<Color> palette) {
		if (palette.size() == 0) {
			throw new IllegalArgumentException("palette must have 1+ colours");
		}
		int closestIndex = 0;
		double closestValue = ColorUtils.gaussianDistance(toMatch, palette.get(0));
		for (int i = 1; i < palette.size(); i++) {
			if (ColorUtils.gaussianDistance(toMatch, palette.get(i)) < closestValue) {
				closestIndex = i;
				closestValue = ColorUtils.gaussianDistance(toMatch, palette.get(i));
			}
		}
		return palette.get(closestIndex);
	}

}

class GoatUtils {
	public static boolean isGoat() {
		return false;
	}
}