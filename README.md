# Image Quantizer

Simple j'bang script to quantize an image to a colour palette.

Heavily inspired by ImageGoNord.

By default it applies a pre blur to the image to reduce noise,
then quantizes each pixel to one of the colours in the Monokai colour scheme.

## Running

Requires j'bang to run

```bash
jbang ImageQuantizer.java input-image.png output-image.png
```

## Demo

Run `make` in the root to replicate this demo

| Input | Output |
| ----- | ------ |
| ![](./demo/sean-foley-kMpbE_-jCeI-unsplash.jpg) | ![](./demo/sean-foley-kMpbE_-jCeI-unsplash-quantized.png) |

## Limitations

 * The image is always output in png format

## Hacking

To use a different scheme, make a list of `java.awt.Color`,
and pass it to `ImageQuantize.quantizeImage` instead of `MONOKAI` in the `main` method.
