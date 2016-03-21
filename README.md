Wa-Tor
======

Introduction
------------

This app simulates a torus (or donut) shaped planet, which is home to
fish and shark. The fish happily reproduce while the shark need to eat fish to survive.

The world is represented by a 2D grid that wraps around at the edges, that is a creature
moving past the top edge reappears at the bottom and one leaving on the left side reappears
on the right side (and vice versa).

Time in this world is measured in ticks. In one tick each creature moves to a neighbour space
according to the following rules:

  1. A fish can move to any of the eight adjacent spaces (top, top left, top right, right, etc.)
     that is not occupied by either fish or shark.
  2. A shark prefers to move to one of the eight adjacent spaces that hosts a fish (to eat the
     the fish). If there is no adjacent fish the shark moves to any of the eight adjacent spaces
     that is not occupied by either fish or shark.
  3. If a shark cannot eat a fish it moves to one of the eight adjacent spaces that is not
     occupied by either fish or shark.
  4. If a shark cannot eat a fish for a certain number of ticks it dies (starve time)
  5. Fish and shark reproduce after a certain number of ticks (fish breed time and shark breed
     time). This means that after the fish or shark moved a new fish or shark is born in the
     previously occupied cell.
  6. If a fish or shark cannot move it also cannot reproduce.

The original idea was devised by
[Alexander Keewatin Dewdney](https://en.wikipedia.org/wiki/Alexander_Dewdney)
and originally presented in the December 1984 issue of Scientific American.

[Wikipedia](https://en.wikipedia.org/wiki/Wa-Tor) has a good article about the idea.

License
-------

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Building
--------

This app uses Gradle to build. Run

	export BUILD_NUMBER=x
	./gradlew build

Replace _`x`_ with the desired `versionCode` (the current gradle build file does _not_ currently
specify the `versionCode`; this will be changed in a future version).For now, a `versionCode` of 1
is recommended. If the gradle build script does not find the `BUILD_NUMBER` environment it assumes
a `versionCode` of 9999.

The above syntax is valid for a POSIX compliant shell (e.g., `bash`). For any other environment,
please look up how to define environment variables.

This will build the `APK` files for debug and release. These can be found in
`./app/build/outputs/apk/`. The (few) unit tests are also exceuted as well as lint (reports
saved in `app/build/outputs/`).

Note that the `APK` files will be _unsigned_. To sign the release `APK`  you need to create the
file `app/gradle.properties`, which should contain the following line:

	signing.properties=path/to/signing.properties

The `signing.properties` line should point to a file that contains information about the keystore
and its password as well as the key alias and the key password to sign the `APK` with, e.g.:

	keystore=/my/super/secret.keystore
    keystore.password=This should be kept secret
    key.alias=wa-tor
    key.password=This should be kept secret as well

Note that you do not need to sign the `APK` if you use `adb` to install it on a device (or an
emulator).

Details on how to build Android applications using gradle can be found
[in the Android Developer Documentation](http://developer.android.com/tools/building/building-cmdline.html).

Build Requirements
------------------

 * Android SDK
 * You need to have [ImageMagick](http://www.imagemagick.org) installed (all graphics are stored as
   SVG files, which are converted to PNG in their respective resolutions at build time).