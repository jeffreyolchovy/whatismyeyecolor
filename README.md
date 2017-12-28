# What is my eye color?
A computer vision project that determines eye color from images of human faces.

[OpenCV](https://opencv.org) is leveraged for most of the heavy lifting.

![Screenshot of CLI output when run against the 'Lena' image](screenshots/wimec-lena-output.png)

## Project structure

- [`library`](#library)
- [`cli`](#cli)
- [`gui`](#gui)
- [`share`](#share)

### `library`
A high-level Scala library that exposes the following capabilities:

- face detection
- eye detection
- pupil detection
- iris detection
- eye color detection
- color quantization

### `cli`
A command line interface for executing the capabilities present in the library.

Please refer to the following usage doc for the currently available options:
```
Usage: wimec [GLOBAL_OPTIONS] [SUBCOMMAND] [OPTION]...

What is my eye color?

Global Options:
  -d, --debug
  -v, --verbose
  -W, --window
      --help      Show help message

Subcommand: exec - Perform facial, eye, pupil, iris, and eye color detection on a given image
  -E, --eye-classifier-type  <arg>    Choices: haar
  -F, --face-classifier-type  <arg>   Choices: haar, lbp
  -i, --input  <arg>
  -O, --outputTarget  <arg>
  -w, --width  <arg>
      --help                          Show help message
Subcommand: resize - Resize an image to the given width
  -i, --input  <arg>
      --output-basename  <arg>
  -O, --outputTarget  <arg>
  -w, --width  <arg>
      --help                     Show help message
Subcommand: recolor - Reduce the number of colors used in an image to k
  -i, --input  <arg>
  -k, --num-colors  <arg>
      --output-basename  <arg>
  -O, --outputTarget  <arg>
      --help                     Show help message
Subcommand: face - Perform facial detection on a given image
  -C, --classifier-type  <arg>   Choices: haar, lbp
  -i, --input  <arg>
  -O, --outputTarget  <arg>
      --help                     Show help message
Subcommand: eyes - Perform eye detection on a given image of a face
  -C, --classifier-type  <arg>   Choices: haar
  -i, --input  <arg>
  -O, --outputTarget  <arg>
      --help                     Show help message
Subcommand: pupil - Perform pupil detection on a given image of an eye
  -i, --input  <arg>
  -O, --outputTarget  <arg>
      --help                  Show help message
Subcommand: iris - Perform iris detection on a given image of an eye
  -i, --input  <arg>
  -O, --outputTarget  <arg>
      --help                  Show help message
Subcommand: colors - Perform eye color detection on a given image of an eye
  -i, --input  <arg>
  -O, --outputTarget  <arg>
      --help                  Show help message
```

### gui
*Not yet implemented*

A web application that performs eye color detection via image capture from a client's webcam.

### share
Bundled OpenCV libraries.

## Credits and notes
Face and eye detection simply defers to the LBP and Haar classifiers present in OpenCV.

Pupil center detection is largely based on [the work of Tristan Hume](http://thume.ca/projects/2012/11/04/simple-accurate-eye-center-tracking-in-opencv/), which is largely based on [the research of Timm and Barth](http://www.inb.uni-luebeck.de/publikationen/pdfs/TiBa11b.pdf).

Pupil radius and iris detection is both naive and (somewhat) novel. Both detection mechanisms search for circles originating at the pupil center. Pupil radius detection iteratively searches for the largest, darkest contour in a given search space.

Iris detection generates possible choices and decides which radius is best given the fact that if the search space extends too wide, the resulting circle will begin to accumulate white pixels since the sclera of the eye is white in color.

## License
Copyright (c) 2017 Jeffrey Olchovy

Published under the [MIT License](https://opensource.org/licenses/MIT), see [LICENSE](LICENSE).
