///usr/bin/env jbang "$0" "$@" ; exit $?

/******************************************************************************
 * Copyright (c) 2021 David Thompson
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *****************************************************************************/

import static java.lang.System.exit;
import static java.lang.System.out;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

/**
 * Program to quantize an image to a colour palette
 */
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
		outputImage = ImageBlur.imageBlur(inputImage, 2);
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

		// Multi thread if possible to speed up the process
		if (availableProcessors > 1) {
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
		ExecutorService pool = Executors.newFixedThreadPool(availableProcessors);
		List<CompletableFuture<Void>> futures = new ArrayList<>();
		int sliceHeight = input.getHeight() / availableProcessors;
		int startY = 0;

		for (int i = 0; i < availableProcessors; i++) {
			int endY = i - 1 == availableProcessors ? input.getHeight() : startY + sliceHeight;
			final int finalStartY = startY;
			final int finalEndY = endY;
			futures.add(CompletableFuture.runAsync(() -> {
				quantizeRange(input, output, 0, input.getWidth(), finalStartY, finalEndY, palette);
			}, pool));
			startY += sliceHeight;
		}

		for (CompletableFuture<Void> future : futures) {
			future.join();
		}
		pool.shutdown();
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
 * Library to blur a BufferedImage
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
		Kernel kernel = makeLinearBlurKernel(radius);
		ConvolveOp op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);

		BufferedImage intermediateImage = new BufferedImage(
				image.getWidth() + 2 * radius,
				image.getHeight() + 2 * radius,
				image.getType());
		Graphics g = intermediateImage.createGraphics();
		g.setColor(new Color(0, 0, 0, 0));
		g.fillRect(0, 0, intermediateImage.getWidth(), intermediateImage.getHeight());
		g.drawImage(image, radius + 1, radius + 1, null);
		g.dispose();

		BufferedImage outputImage = new BufferedImage(
			image.getWidth() + 2 * radius,
			image.getHeight() + 2 * radius,
			image.getType());
		op.filter(intermediateImage, outputImage);
		return outputImage.getSubimage(radius + 1, radius + 1, image.getWidth(), image.getHeight());
	}

	private static Kernel makeLinearBlurKernel(int radius) {
		int size = radius * 2 + 1;
		int area = size * size;
		float[] kernelArray = new float[size * size];
		for (int i = 0; i < kernelArray.length; i++) {
			kernelArray[i] = 1f / ((float)area);
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

	public static List<Color> MONOCHROME = Arrays.asList( //
			new Color(0, 0, 0), //
			new Color(255, 255, 255) //
	);

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