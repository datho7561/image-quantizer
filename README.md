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

## Hacking

To use a different scheme, make a list of `java.awt.Color`,
and pass it to `ImageQuantize.quantizeImage` instead of `MONOKAI` in the `main` method.

## Quality of the blurring algorithm

Real bad