# FFmpegKit audio: licences of the bundled native libraries

Shiny ships `com.arthenica:ffmpeg-kit-audio:6.0-2` unmodified. Its native libraries were built with the
configure flags embedded in `libavcodec.so` (`--enable-version3`, no `--enable-gpl`, and
`--enable-libmp3lame --enable-iconv --enable-libvorbis --enable-libopencore-amrnb --enable-libshine
--enable-libspeex --enable-libilbc --enable-libopus --enable-libsoxr --enable-libtwolame --enable-libvo-amrwbenc`).

The sections below are copied verbatim from the FFmpegKit project's licence page,
https://github.com/arthenica/ffmpeg-kit/wiki/Licenses , for exactly those libraries, plus libogg (which
libvorbis requires), libsndfile (whose licence file the AAR also ships, as res/raw/license_libsndfile.txt)
and cpu_features (linked through `-lndk_compat`). FFmpegKit itself and the FFmpeg
libraries in this build are under the GNU LGPL v3.0; its full text is shipped in the AAR as
`res/raw/license.txt` and in Shiny's licence screen.

## FFmpeg

`FFmpeg` libraries created by `FFmpegKit` are licensed under the `LGPL v3.0`
by default. However, if the `--enable-gpl` flag is used during the
compilation, then `GPL` licensed parts of `FFmpeg` are enabled. Thus `FFmpeg`
becomes subject to `GPL v3.0`.

FFmpeg is a trademark of Fabrice Bellard, originator of the FFmpeg project.

### cpu_features

`cpu_features` is licensed under the `Apache License v2.0` except files under the `ndk_compat` folder,
which are licensed under the `BSD-2-Clause License` with the following copyright notice.

Copyright (C) 2010 The Android Open Source Project

### lame

`lame` is licensed under the `LGPL v2.0`.

### libiconv

`libiconv` is licensed under the `LGPL v2.1` or later.

### libilbc

`libilbc` is licensed under the `BSD-3-Clause License` with the following copyright notice.

Copyright (c) 2011, The WebRTC project authors

### libogg

`libogg` is licensed under the `BSD-3-Clause License` with the following copyright notice.

Copyright (c) 2002, Xiph.org Foundation

### libsndfile

`libsndfile` is licensed under the `LGPL v2.1` or later.

### libvorbis

`libvorbis` is licensed under the `BSD-3-Clause License` with the following copyright notice.

Copyright (c) 2002-2020 Xiph.org Foundation

### opencore-amr

`opencore-amr` is licensed under the `Apache License v2.0`.

### opus

`opus` is licensed under the `BSD-3-Clause License` with the following copyright notices.

Copyright 2001-2011 Xiph.Org, Skype Limited, Octasic,
                    Jean-Marc Valin, Timothy B. Terriberry,
                    CSIRO, Gregory Maxwell, Mark Borgerding,
                    Erik de Castro Lopo

### shine

`shine` is licensed under the `LGPL v2.0`.

### soxr

`soxr` is licensed under the `LGPL v2.1` or later, with the following copyright notice.

Copyright (c) 2007-18 robs@users.sourceforge.net

Except `pffft.c`, which is licensed under the `FFTPACK license` with the following copyright notice.

Copyright (c) 2013  Julien Pommier ( pommier@modartt.com )

### speex

`speex` is licensed under the `BSD-3-Clause	License` with the following copyright notices.

Copyright 2002-2008     Xiph.org Foundation
Copyright 2002-2008     Jean-Marc Valin
Copyright 2005-2007     Analog Devices Inc.
Copyright 2005-2008     Commonwealth Scientific and Industrial Research
Organisation (CSIRO)
Copyright 1993, 2002, 2006 David Rowe
Copyright 2003          EpicGames
Copyright 1992-1994     Jutta Degener, Carsten Bormann

### twolame

`twolame` is licensed under the `LGPL v2.1` or later.

### vo-amrwbenc

`vo-amrwbenc` is licensed under the `Apache License v2.0`.
